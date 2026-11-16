package com.example.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import com.example.model.ApkArchiveInfo
import com.example.model.AppMetadataOverride
import com.example.model.InstallSessionState
import com.example.model.InstallStatus
import com.example.optimizer.AppsManagerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

object PackageInstallerManager {

  private const val TAG = "PackageInstallerManager"

  private val _sessionState = MutableStateFlow(InstallSessionState())
  val sessionState: StateFlow<InstallSessionState> = _sessionState.asStateFlow()

  private val _installEvents = MutableSharedFlow<Pair<Boolean, String>>()
  val installEvents: SharedFlow<Pair<Boolean, String>> = _installEvents.asSharedFlow()

  private val scope = CoroutineScope(Dispatchers.IO)

  fun notifyInstallResult(success: Boolean, packageName: String, message: String) {
    _sessionState.value = _sessionState.value.copy(
      status = if (success) InstallStatus.SUCCESS else InstallStatus.ERROR,
      currentTask = if (success) "インストール完了" else "エラー発生",
      errorDetails = if (success) null else message,
      packageName = packageName,
      progress = 1.0f
    )
    scope.launch {
      _installEvents.emit(Pair(success, message))
    }
  }

  fun resetState() {
    _sessionState.value = InstallSessionState()
  }

  fun updateProgress(status: InstallStatus, progress: Float, task: String, error: String? = null) {
    _sessionState.value = _sessionState.value.copy(
      status = status,
      progress = progress,
      currentTask = task,
      errorDetails = error
    )
  }

  suspend fun installPackage(
    context: Context,
    archiveInfo: ApkArchiveInfo,
    useRoot: Boolean = false
  ) = withContext(Dispatchers.IO) {
    try {
      _sessionState.value = InstallSessionState(
        status = InstallStatus.INSTALLING,
        progress = 0.05f,
        currentTask = "インストール準備中...",
        packageName = archiveInfo.packageName,
        archiveInfo = archiveInfo,
        isRootInstall = useRoot
      )

      // Persist custom metadata overrides (bundle name, app label, version, category)
      AppsManagerRepository.saveMetadataOverride(
        context,
        AppMetadataOverride(
          originalPackageName = archiveInfo.originalPackageName.ifBlank { archiveInfo.packageName },
          customAppLabel = archiveInfo.appLabel,
          customPackageName = archiveInfo.packageName,
          customVersionName = archiveInfo.versionName,
          customVersionCode = archiveInfo.versionCode,
          customCategory = archiveInfo.category
        )
      )

      // 1. Process OBB files if any (XAPK / OBB_ZIP)
      if (archiveInfo.obbEntries.isNotEmpty()) {
        _sessionState.value = _sessionState.value.copy(
          progress = 0.15f,
          currentTask = "OBB拡張データを配置中..."
        )
        copyObbFiles(archiveInfo)
      }

      // 2. Perform installation depending on root option
      if (useRoot && RootHelper.isRootAvailable()) {
        installWithRoot(context, archiveInfo)
      } else {
        installWithPackageInstaller(context, archiveInfo)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error installing package", e)
      _sessionState.value = _sessionState.value.copy(
        status = InstallStatus.ERROR,
        currentTask = "エラーが発生しました",
        errorDetails = e.localizedMessage ?: e.message ?: "不明なエラー"
      )
    }
  }

  private fun copyObbFiles(archiveInfo: ApkArchiveInfo) {
    try {
      val extRoot = Environment.getExternalStorageDirectory()
      val targetPkg = archiveInfo.originalPackageName.ifBlank { archiveInfo.packageName }
      val defaultObbDir = File(extRoot, "Android/obb/$targetPkg")
      defaultObbDir.mkdirs()

      val sourceDir = File(archiveInfo.filePath)
      for (obb in archiveInfo.obbEntries) {
        val srcFile = File(sourceDir, obb.fileName)
        if (srcFile.exists() && srcFile.isFile) {
          val destFile = if (obb.relativePath.startsWith("Android/obb/", ignoreCase = true)) {
            File(extRoot, obb.relativePath).also { it.parentFile?.mkdirs() }
          } else {
            File(defaultObbDir, obb.fileName)
          }
          srcFile.copyTo(destFile, overwrite = true)
          Log.d(TAG, "Copied OBB: ${destFile.absolutePath}")
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Could not copy OBB to primary external storage: ${e.message}")
    }
  }

  private suspend fun installWithPackageInstaller(context: Context, archiveInfo: ApkArchiveInfo) =
    withContext(Dispatchers.IO) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (!context.packageManager.canRequestPackageInstalls()) {
          try {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
              data = Uri.parse("package:${context.packageName}")
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
          } catch (e: Exception) {
            Log.w(TAG, "Failed to launch unknown sources settings", e)
          }
          notifyInstallResult(
            success = false,
            packageName = archiveInfo.packageName,
            message = "「提供元不明のアプリのインストール」権限を許可してください。"
          )
          return@withContext
        }
      }

      data class FileToWrite(val file: File, val entryName: String)
      val filesToInstall = mutableListOf<FileToWrite>()

      val sourceFileOrDir = File(archiveInfo.filePath)
      val isMetadataOverridden = archiveInfo.packageName != archiveInfo.originalPackageName &&
        archiveInfo.originalPackageName.isNotBlank()

      if (sourceFileOrDir.isFile) {
        val fileToUse = if (isMetadataOverridden) {
          AppsManagerRepository.createModifiedApkFile(
            context = context,
            sourceApkPath = sourceFileOrDir.absolutePath,
            originalPackageName = archiveInfo.originalPackageName,
            newPackageName = archiveInfo.packageName,
            originalLabel = "",
            newLabel = archiveInfo.appLabel,
            originalVersionName = "",
            newVersionName = archiveInfo.versionName
          )
        } else {
          sourceFileOrDir
        }
        filesToInstall.add(FileToWrite(fileToUse, "base.apk"))
      } else if (sourceFileOrDir.isDirectory) {
        var splitCounter = 1
        var hasAddedBase = false

        for (split in archiveInfo.splits) {
          val candidateFile = if (split.filePath.isNotBlank() && File(split.filePath).exists()) {
            File(split.filePath)
          } else {
            File(sourceFileOrDir, split.name)
          }
          if (candidateFile.exists() && candidateFile.isFile && candidateFile.length() > 0) {
            val isBaseSplit = split.isBase && !hasAddedBase
            val finalFile = if (isBaseSplit && isMetadataOverridden && archiveInfo.splits.size == 1) {
              AppsManagerRepository.createModifiedApkFile(
                context = context,
                sourceApkPath = candidateFile.absolutePath,
                originalPackageName = archiveInfo.originalPackageName,
                newPackageName = archiveInfo.packageName,
                originalLabel = "",
                newLabel = archiveInfo.appLabel,
                originalVersionName = "",
                newVersionName = archiveInfo.versionName
              )
            } else {
              candidateFile
            }
            val entryName = if (isBaseSplit) {
              hasAddedBase = true
              "base.apk"
            } else {
              val sName = "split_${splitCounter}.apk"
              splitCounter++
              sName
            }
            filesToInstall.add(FileToWrite(finalFile, entryName))
          }
        }

        // Fallback if splits list didn't resolve files
        if (filesToInstall.isEmpty()) {
          sourceFileOrDir.listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) && f.length() > 0 }
            ?.sortedByDescending { it.length() }
            ?.forEachIndexed { idx, f ->
              val entryName = if (idx == 0) "base.apk" else "split_${idx}.apk"
              filesToInstall.add(FileToWrite(f, entryName))
            }
        }
      }

      if (filesToInstall.isEmpty()) {
        throw IllegalStateException("インストール可能なAPKモジュールが見つかりませんでした。")
      }

      val exactTotalBytes = filesToInstall.sumOf { it.file.length() }

      val packageInstaller = context.packageManager.packageInstaller
      val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          setInstallReason(PackageManager.INSTALL_REASON_USER)
        }
        if (archiveInfo.iconBitmap != null) {
          setAppIcon(archiveInfo.iconBitmap)
        }
        if (archiveInfo.appLabel.isNotBlank()) {
          setAppLabel(archiveInfo.appLabel)
        }
        if (exactTotalBytes > 0) {
          setSize(exactTotalBytes)
        }
      }

      val sessionId = packageInstaller.createSession(params)
      val session = packageInstaller.openSession(sessionId)

      try {
        _sessionState.value = _sessionState.value.copy(
          progress = 0.25f,
          currentTask = "APKストリーム書き込み中 (全${filesToInstall.size}モジュール)..."
        )

        val totalFiles = filesToInstall.size
        filesToInstall.forEachIndexed { index, item ->
          _sessionState.value = _sessionState.value.copy(
            progress = 0.25f + (0.55f * (index + 1) / totalFiles),
            currentTask = "書き込み中 (${index + 1}/$totalFiles): ${item.entryName}"
          )

          session.openWrite(item.entryName, 0, item.file.length()).use { out ->
            FileInputStream(item.file).use { inStream ->
              val buffer = ByteArray(65536)
              while (true) {
                val bytesRead = inStream.read(buffer)
                if (bytesRead == -1) break
                out.write(buffer, 0, bytesRead)
              }
              session.fsync(out)
            }
          }
        }

        _sessionState.value = _sessionState.value.copy(
          progress = 0.85f,
          currentTask = "システムへコミット中..."
        )

        val callbackIntent = Intent(context, InstallStatusReceiver::class.java).apply {
          action = "com.example.ACTION_INSTALL_STATUS"
          setPackage(context.packageName)
          putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, archiveInfo.packageName)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
          PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
          context,
          sessionId,
          callbackIntent,
          flags
        )

        session.commit(pendingIntent.intentSender)
        session.close()

        _sessionState.value = _sessionState.value.copy(
          progress = 0.95f,
          currentTask = "システムの承認待機中 (インストール確認ダイアログで許可してください)..."
        )
      } catch (e: Exception) {
        session.abandon()
        throw e
      }
    }

  private suspend fun installWithRoot(context: Context, archiveInfo: ApkArchiveInfo) =
    withContext(Dispatchers.IO) {
      _sessionState.value = _sessionState.value.copy(
        progress = 0.4f,
        currentTask = "Root権限でサイレントインストール実行中..."
      )

      val sourceFileOrDir = File(archiveInfo.filePath)
      if (sourceFileOrDir.isFile) {
        val len = sourceFileOrDir.length()
        val result = RootHelper.runSu("cat \"${sourceFileOrDir.absolutePath}\" | pm install -r -d -S $len")
        if (result.success && result.output.contains("Success", ignoreCase = true)) {
          notifyInstallResult(true, archiveInfo.packageName, "Rootサイレントインストールに成功しました")
        } else {
          notifyInstallResult(false, archiveInfo.packageName, "Rootインストールエラー: ${result.output}")
        }
      } else {
        val createCmd = RootHelper.runSu("pm install-create -r -d")
        val output = createCmd.output
        val sessionIdMatch = Regex("""\[(\d+)\]""").find(output) ?: Regex("""(\d+)""").find(output)
        val sessionId = sessionIdMatch?.groupValues?.get(1)

        if (sessionId != null) {
          var splitIdx = 1
          var wroteBase = false
          for (split in archiveInfo.splits) {
            val file = if (split.filePath.isNotBlank() && File(split.filePath).exists()) {
              File(split.filePath)
            } else {
              File(sourceFileOrDir, split.name)
            }
            if (file.exists() && file.length() > 0) {
              val entryName = if (split.isBase && !wroteBase) {
                wroteBase = true
                "base.apk"
              } else {
                val n = "split_${splitIdx}.apk"
                splitIdx++
                n
              }
              RootHelper.runSu("cat \"${file.absolutePath}\" | pm install-write -S ${file.length()} $sessionId $entryName -")
            }
          }
          val commitResult = RootHelper.runSu("pm install-commit $sessionId")
          if (commitResult.success && commitResult.output.contains("Success", ignoreCase = true)) {
            notifyInstallResult(true, archiveInfo.packageName, "Root分割APKインストールに成功しました")
          } else {
            notifyInstallResult(false, archiveInfo.packageName, "コミット失敗: ${commitResult.output}")
          }
        } else {
          notifyInstallResult(false, archiveInfo.packageName, "セッション作成失敗: $output")
        }
      }
    }
}
