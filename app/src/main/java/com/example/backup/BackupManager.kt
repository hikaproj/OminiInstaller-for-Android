package com.example.backup

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.example.installer.RootHelper
import com.example.model.BackupArchiveItem
import com.example.model.BackupExportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.GZIPOutputStream
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
    backupDataWithRoot: Boolean = false,
    exportFormat: BackupExportFormat = BackupExportFormat.APKS
  ): Result<BackupArchiveItem> = withContext(Dispatchers.IO) {
    try {
      val pm = context.packageManager
      val appInfo: ApplicationInfo = pm.getApplicationInfo(packageName, 0)
      val pkgInfo = pm.getPackageInfo(packageName, 0)

      val label = appInfo.loadLabel(pm).toString()
      val vName = (pkgInfo.versionName ?: "1.0").replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
      val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkgInfo.longVersionCode
      } else {
        @Suppress("DEPRECATION")
        pkgInfo.versionCode.toLong()
      }

      val sourceApk = File(appInfo.sourceDir)
      val splitDirs = appInfo.splitSourceDirs ?: emptyArray()
      val allApkFiles = mutableListOf<Pair<String, File>>()
      allApkFiles.add("base.apk" to sourceApk)
      splitDirs.forEachIndexed { idx, path ->
        val f = File(path)
        if (f.exists()) {
          val entryName = if (f.name.equals("base.apk", ignoreCase = true)) {
            "split_$idx.apk"
          } else {
            f.name
          }
          allApkFiles.add(entryName to f)
        }
      }

      val backupDir = getBackupDir(context)
      val timestamp = System.currentTimeMillis()
      val safePkg = packageName.replace(".", "_")

      // Choose final extension
      val chosenExt = if (exportFormat == BackupExportFormat.APK && splitDirs.isNotEmpty()) {
        // Even if APK was selected, if the app has multiple splits, export as .apks unless single
        "apk"
      } else {
        exportFormat.extension
      }

      val targetFile = File(backupDir, "${safePkg}_v${vName}_${timestamp}.$chosenExt")
      val splitCount = allApkFiles.size

      val manifestJsonObj = JSONObject().apply {
        put("xapk_version", 2)
        put("package_name", packageName)
        put("name", label)
        put("version_code", vCode)
        put("version_name", vName)
        put("min_sdk_version", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 21)
        put("target_sdk_version", appInfo.targetSdkVersion)
        put("developer", "Hikaproj OmniInstaller Backup")
        put("export_format", exportFormat.label)
        put("split_apks", JSONArray().apply {
          allApkFiles.forEach { (entryName, _) ->
            put(JSONObject().apply {
              put("file", entryName)
              put("id", entryName.substringBeforeLast("."))
            })
          }
        })
      }

      when {
        // 1. Direct Single APK or .bin
        (exportFormat == BackupExportFormat.APK || exportFormat == BackupExportFormat.BIN) && splitDirs.isEmpty() -> {
          sourceApk.copyTo(targetFile, overwrite = true)
        }
        // 2. Single GZIP (.apk.gz)
        exportFormat == BackupExportFormat.APK_GZ -> {
          GZIPOutputStream(BufferedOutputStream(FileOutputStream(targetFile))).use { gos ->
            FileInputStream(sourceApk).use { fis -> fis.copyTo(gos) }
          }
        }
        // 3. TAR / TAR.GZ / TGZ
        exportFormat.isTar || exportFormat == BackupExportFormat.TAR -> {
          val rawOut = BufferedOutputStream(FileOutputStream(targetFile))
          val tarOut: OutputStream = if (exportFormat.isGzip) GZIPOutputStream(rawOut) else rawOut
          tarOut.use { out ->
            val manifestBytes = manifestJsonObj.toString(2).toByteArray(Charsets.UTF_8)
            writeTarEntry(out, "manifest.json", manifestBytes)
            writeTarEntry(out, "omni_meta.json", manifestBytes)
            allApkFiles.forEach { (entryName, file) ->
              writeTarFileEntry(out, entryName, file)
            }
            // End of TAR archive: two 512-byte zero blocks
            out.write(ByteArray(1024))
          }
        }
        // 4. All ZIP-based container formats (.apks, .xapk, .apkm, .zip, .omni, .hpjpkg, .omnibak, .hpjbak, .aab, .zapk, .sapk, .mapk, .bapk, .apk+, .apex, .jar, .pack, .ab, or multi-split .apk)
        else -> {
          ZipOutputStream(BufferedOutputStream(FileOutputStream(targetFile))).use { zos ->
            val manifestBytes = manifestJsonObj.toString(2).toByteArray(Charsets.UTF_8)

            // Write format-appropriate metadata files inside archive
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestBytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("info.json"))
            zos.write(manifestBytes)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("omni_meta.json"))
            zos.write(manifestBytes)
            zos.closeEntry()

            allApkFiles.forEach { (entryName, file) ->
              zos.putNextEntry(ZipEntry(entryName))
              FileInputStream(file).use { it.copyTo(zos) }
              zos.closeEntry()
            }
          }
        }
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

      // Companion metadata JSON
      val metaFile = File(backupDir, "${targetFile.name}.meta.json")
      val metaJson = JSONObject().apply {
        put("id", "${safePkg}_$timestamp")
        put("packageName", packageName)
        put("appLabel", label)
        put("versionName", vName)
        put("versionCode", vCode)
        put("backupDate", timestamp)
        put("splitCount", splitCount)
        put("formatName", exportFormat.label)
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
        formatName = exportFormat.label,
        hasObb = hasDataBackup
      )

      Result.success(item)
    } catch (e: Exception) {
      Log.e(TAG, "Backup failed for $packageName", e)
      Result.failure(e)
    }
  }

  private fun writeTarEntry(out: OutputStream, name: String, data: ByteArray) {
    val header = createTarHeader(name, data.size.toLong())
    out.write(header)
    out.write(data)
    val padding = ((512 - (data.size % 512)) % 512)
    if (padding > 0) out.write(ByteArray(padding))
  }

  private fun writeTarFileEntry(out: OutputStream, name: String, file: File) {
    val size = file.length()
    val header = createTarHeader(name, size)
    out.write(header)
    FileInputStream(file).use { fis ->
      fis.copyTo(out)
    }
    val padding = ((512 - (size % 512)) % 512).toInt()
    if (padding > 0) out.write(ByteArray(padding))
  }

  private fun createTarHeader(name: String, size: Long): ByteArray {
    val header = ByteArray(512)
    val nameBytes = name.toByteArray(Charsets.UTF_8)
    System.arraycopy(nameBytes, 0, header, 0, minOf(nameBytes.size, 99))

    // File mode 0644
    writeOctal(header, 100, 8, 420L)
    // UID / GID 0
    writeOctal(header, 108, 8, 0L)
    writeOctal(header, 116, 8, 0L)
    // Size
    writeOctal(header, 124, 12, size)
    // Mtime
    writeOctal(header, 136, 12, System.currentTimeMillis() / 1000L)
    // Checksum placeholder (spaces)
    for (i in 148 until 156) header[i] = ' '.code.toByte()
    // Typeflag '0'
    header[156] = '0'.code.toByte()

    // Calculate checksum
    var sum = 0L
    for (b in header) {
      sum += (b.toInt() and 0xFF)
    }
    writeOctal(header, 148, 8, sum)
    return header
  }

  private fun writeOctal(buffer: ByteArray, offset: Int, length: Int, value: Long) {
    val octalStr = java.lang.Long.toOctalString(value)
    val padded = octalStr.padStart(length - 1, '0')
    val bytes = padded.toByteArray(Charsets.US_ASCII)
    System.arraycopy(bytes, 0, buffer, offset, minOf(bytes.size, length - 1))
    buffer[offset + length - 1] = 0
  }

  suspend fun listBackups(context: Context): List<BackupArchiveItem> = withContext(Dispatchers.IO) {
    val backupDir = getBackupDir(context)
    val files = backupDir.listFiles() ?: return@withContext emptyList()
    val items = mutableListOf<BackupArchiveItem>()

    for (file in files) {
      if (file.isDirectory || file.name.endsWith(".meta.json") || file.name.endsWith("_data.tar.gz")) {
        continue
      }
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
              formatName = json.optString("formatName", file.extension.uppercase()),
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
          splitCount = if (file.name.endsWith(".apks") || file.name.endsWith(".xapk")) 2 else 1,
          formatName = ".${file.extension}",
          hasObb = false
        )
      )
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
      type = "application/octet-stream"
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, "${item.appLabel} のバックアップを共有")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
  }
}
