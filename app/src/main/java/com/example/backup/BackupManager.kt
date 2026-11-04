package com.example.backup

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.example.installer.RootHelper
import com.example.model.BackupArchiveItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BackupManager {

  private const val TAG = "BackupManager"

  fun getBackupDir(context: Context): File {
    val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups")
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  suspend fun backupApp(
    context: Context,
    packageName: String,
    backupDataWithRoot: Boolean = false
  ): Result<BackupArchiveItem> = withContext(Dispatchers.IO) {
    try {
      val pm = context.packageManager
      val appInfo: ApplicationInfo = pm.getApplicationInfo(packageName, 0)
      val pkgInfo = pm.getPackageInfo(packageName, 0)

      val label = appInfo.loadLabel(pm).toString()
      val vName = pkgInfo.versionName ?: "1.0"
      val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkgInfo.longVersionCode
      } else {
        @Suppress("DEPRECATION")
        pkgInfo.versionCode.toLong()
      }

      val sourceApk = File(appInfo.sourceDir)
      val splitDirs = appInfo.splitSourceDirs ?: emptyArray()

      val backupDir = getBackupDir(context)
      val timestamp = System.currentTimeMillis()
      val safePkg = packageName.replace(".", "_")

      val targetFile: File
      val splitCount: Int

      if (splitDirs.isEmpty()) {
        // Single APK backup
        targetFile = File(backupDir, "${safePkg}_${vName}_${timestamp}.apk")
        sourceApk.copyTo(targetFile, overwrite = true)
        splitCount = 1
      } else {
        // Split APKs -> bundle into .apks (zip format)
        targetFile = File(backupDir, "${safePkg}_${vName}_${timestamp}.apks")
        ZipOutputStream(FileOutputStream(targetFile).buffered()).use { zos ->
          // Base APK
          zos.putNextEntry(ZipEntry("base.apk"))
          FileInputStream(sourceApk).use { it.copyTo(zos) }
          zos.closeEntry()

          // Splits
          splitDirs.forEach { splitPath ->
            val splitFile = File(splitPath)
            if (splitFile.exists()) {
              zos.putNextEntry(ZipEntry(splitFile.name))
              FileInputStream(splitFile).use { it.copyTo(zos) }
              zos.closeEntry()
            }
          }
        }
        splitCount = splitDirs.size + 1
      }

      // Root App Data Backup if requested and available
      var hasDataBackup = false
      if (backupDataWithRoot && RootHelper.isRootAvailable()) {
        val dataBackupFile = File(backupDir, "${safePkg}_${timestamp}_data.tar.gz")
        val suCmd = "tar -czf \"${dataBackupFile.absolutePath}\" -C /data/data/$packageName ."
        val res = RootHelper.runSu(suCmd)
        if (res.success && dataBackupFile.exists()) {
          hasDataBackup = true
        }
      }

      // Save companion metadata JSON
      val metaFile = File(backupDir, "${targetFile.name}.meta.json")
      val metaJson = JSONObject().apply {
        put("id", "${safePkg}_$timestamp")
        put("packageName", packageName)
        put("appLabel", label)
        put("versionName", vName)
        put("versionCode", vCode)
        put("backupDate", timestamp)
        put("splitCount", splitCount)
        put("hasDataBackup", hasDataBackup)
        put("archiveSize", targetFile.length())
      }
      metaFile.writeText(metaJson.toString())

      val item = BackupArchiveItem(
        id = "${safePkg}_$timestamp",
        packageName = packageName,
        appLabel = label,
        versionName = vName,
        versionCode = vCode,
        backupDate = timestamp,
        filePath = targetFile.absolutePath,
        sizeBytes = targetFile.length(),
        splitCount = splitCount,
        hasObb = hasDataBackup
      )

      Result.success(item)
    } catch (e: Exception) {
      Log.e(TAG, "Backup failed for $packageName", e)
      Result.failure(e)
    }
  }

  suspend fun listBackups(context: Context): List<BackupArchiveItem> = withContext(Dispatchers.IO) {
    val backupDir = getBackupDir(context)
    val files = backupDir.listFiles() ?: return@withContext emptyList()
    val items = mutableListOf<BackupArchiveItem>()

    for (file in files) {
      if (file.name.endsWith(".apk") || file.name.endsWith(".apks") || file.name.endsWith(".xapk") || file.name.endsWith(".zip")) {
        val metaFile = File(backupDir, "${file.name}.meta.json")
        if (metaFile.exists()) {
          try {
            val json = JSONObject(metaFile.readText())
            items.add(
              BackupArchiveItem(
                id = json.optString("id", file.name),
                packageName = json.optString("packageName", "unknown"),
                appLabel = json.optString("appLabel", file.name.substringBefore("_")),
                versionName = json.optString("versionName", "1.0"),
                versionCode = json.optLong("versionCode", 1L),
                backupDate = json.optLong("backupDate", file.lastModified()),
                filePath = file.absolutePath,
                sizeBytes = file.length(),
                splitCount = json.optInt("splitCount", 1),
                hasObb = json.optBoolean("hasDataBackup", false)
              )
            )
            continue
          } catch (_: Exception) {}
        }

        // Fallback from archive inspection
        var pkg = file.name.substringBeforeLast(".")
        var ver = "1.0"
        var label = pkg
        if (file.name.endsWith(".apk")) {
          try {
            val pi = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            if (pi != null) {
              pkg = pi.packageName
              ver = pi.versionName ?: "1.0"
              val appInfo = pi.applicationInfo ?: ApplicationInfo()
              appInfo.sourceDir = file.absolutePath
              appInfo.publicSourceDir = file.absolutePath
              label = appInfo.loadLabel(context.packageManager).toString().ifBlank { pkg }
            }
          } catch (_: Exception) {}
        }
        items.add(
          BackupArchiveItem(
            id = file.name,
            packageName = pkg,
            appLabel = label,
            versionName = ver,
            versionCode = 1L,
            backupDate = file.lastModified(),
            filePath = file.absolutePath,
            sizeBytes = file.length(),
            splitCount = if (file.name.endsWith(".apks")) 2 else 1,
            hasObb = false
          )
        )
      }
    }

    items.sortedByDescending { it.backupDate }
  }

  suspend fun deleteBackup(item: BackupArchiveItem): Boolean = withContext(Dispatchers.IO) {
    val file = File(item.filePath)
    val metaFile = File("${item.filePath}.meta.json")
    val d1 = file.delete()
    val d2 = metaFile.delete()
    d1 || d2
  }

  fun shareBackup(context: Context, item: BackupArchiveItem) {
    val file = File(item.filePath)
    if (!file.exists()) return
    val authority = "${context.packageName}.provider"
    val uri = FileProvider.getUriForFile(context, authority, file)

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "application/vnd.android.package-archive"
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, "${item.appLabel} のバックアップを共有")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
  }
}
