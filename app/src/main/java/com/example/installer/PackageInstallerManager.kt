package com.example.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import com.example.model.ApkArchiveInfo
import com.example.model.ArchiveFormat
import com.example.model.InstallSessionState
import com.example.model.InstallStatus
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

      // 1. Process OBB files if any
      if (archiveInfo.obbEntries.isNotEmpty()) {
        _sessionState.value = _sessionState.value.copy(
          progress = 0.15f,
          currentTask = "OBBデータファイルを展開・配置中..."
        )
        copyObbFiles(context, archiveInfo)
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

  private fun copyObbFiles(context: Context, archiveInfo: ApkArchiveInfo) {
    try {
      val obbBaseDir = File(Environment.getExternalStorageDirectory(), "Android/obb/${archiveInfo.packageName}")
      obbBaseDir.mkdirs()

      val sourceDir = File(archiveInfo.filePath)
      for (obb in archiveInfo.obbEntries) {
        val srcFile = File(sourceDir, obb.fileName)
        if (srcFile.exists()) {
          val destFile = File(obbBaseDir, obb.fileName)
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
      // Check REQUEST_INSTALL_PACKAGES permission on Android 8.0+
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (!context.packageManager.canRequestPackageInstalls()) {
          try {
            val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
              data = android.net.Uri.parse("package:${context.packageName}")
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
          } catch (e: Exception) {
            Log.w(TAG, "Failed to launch unknown sources settings", e)
          }
          notifyInstallResult(
            success = false,
            packageName = archiveInfo.packageName,
            message = "「提供元不明のアプリのインストール」権限をOmniInstallerに許可してください。"
          )
          return@withContext
        }
      }

      val packageInstaller = context.packageManager.packageInstaller
      val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
        if (archiveInfo.packageName.isNotBlank() && !archiveInfo.packageName.startsWith("unknown") && !archiveInfo.packageName.startsWith("com.bundle")) {
          setAppPackageName(archiveInfo.packageName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          setInstallReason(PackageManager.INSTALL_REASON_USER)
        }
        if (archiveInfo.iconBitmap != null) {
          setAppIcon(archiveInfo.iconBitmap)
        }
        if (archiveInfo.appLabel.isNotBlank()) {
          setAppLabel(archiveInfo.appLabel)
        }
        if (archiveInfo.totalSizeBytes > 0) {
          setSize(archiveInfo.totalSizeBytes)
        }
      }

      val sessionId = packageInstaller.createSession(params)
      val session = packageInstaller.openSession(sessionId)

      try {
        _sessionState.value = _sessionState.value.copy(
          progress = 0.3f,
          currentTask = "APKストリーム書き込み中..."
        )

        data class FileToWrite(val file: File, val entryName: String)
        val filesToInstall = mutableListOf<FileToWrite>()

        if (archiveInfo.format == ArchiveFormat.APK) {
          val f = File(archiveInfo.filePath)
          if (f.exists()) {
            filesToInstall.add(FileToWrite(f, "base.apk"))
          }
        } else {
          val dir = File(archiveInfo.filePath)
          for (split in archiveInfo.splits) {
            val f = File(dir, split.name)
            if (f.exists()) {
              val entryName = if (split.isBase) "base.apk" else split.name.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
              filesToInstall.add(FileToWrite(f, entryName))
            }
          }
          // Fallback if splits list was empty
          if (filesToInstall.isEmpty() && dir.isDirectory) {
            dir.listFiles { f -> f.name.endsWith(".apk", ignoreCase = true) }?.forEachIndexed { idx, f ->
              val isBase = idx == 0 || f.name.contains("base", ignoreCase = true)
              val entryName = if (isBase) "base.apk" else f.name.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
              filesToInstall.add(FileToWrite(f, entryName))
            }
          }
        }

        if (filesToInstall.isEmpty()) {
          throw IllegalStateException("インストール可能なAPKファイルが見つかりませんでした。")
        }

        val totalFiles = filesToInstall.size
        filesToInstall.forEachIndexed { index, item ->
          _sessionState.value = _sessionState.value.copy(
            progress = 0.3f + (0.5f * (index + 1) / totalFiles),
            currentTask = "書き込み中: ${item.file.name}"
          )

          session.openWrite(item.entryName, 0, item.file.length()).use { out ->
            FileInputStream(item.file).use { inStream ->
              val buffer = ByteArray(65536)
              var bytesRead = 0
              while (inStream.read(buffer).also { bytesRead = it } != -1) {
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

        // Create PendingIntent to trigger InstallStatusReceiver with explicit action
        val callbackIntent = Intent(context, InstallStatusReceiver::class.java).apply {
          action = "com.example.ACTION_INSTALL_STATUS"
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
          currentTask = "システムの承認待機中 (画面の指示に従ってください)..."
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

      if (archiveInfo.format == ArchiveFormat.APK) {
        val result = RootHelper.runSu("pm install -r \"${archiveInfo.filePath}\"")
        if (result.success && result.output.contains("Success", ignoreCase = true)) {
          notifyInstallResult(true, archiveInfo.packageName, "Rootサイレントインストールに成功しました")
        } else {
          // Fallback to PackageInstaller if su returned non-success
          notifyInstallResult(false, archiveInfo.packageName, "Rootインストールエラー: ${result.output}")
        }
      } else {
        // Multi-apk split install using pm install-create
        val createCmd = RootHelper.runSu("pm install-create -r")
        val output = createCmd.output
        val sessionIdMatch = Regex("""\[(\d+)\]""").find(output) ?: Regex("""(\d+)""").find(output)
        val sessionId = sessionIdMatch?.groupValues?.get(1)

        if (sessionId != null) {
          val dir = File(archiveInfo.filePath)
          for (split in archiveInfo.splits) {
            val file = File(dir, split.name)
            if (file.exists()) {
              RootHelper.runSu("pm install-write -S ${file.length()} $sessionId ${split.name} \"${file.absolutePath}\"")
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
