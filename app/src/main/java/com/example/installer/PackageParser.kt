package com.example.installer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.ApkArchiveInfo
import com.example.model.ArchiveFormat
import com.example.model.ObbEntry
import com.example.model.SplitEntry
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object PackageParser {

  fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = ""
    if (uri.scheme == "content") {
      try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
          if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1) {
              name = cursor.getString(idx) ?: ""
            }
          }
        }
      } catch (e: Exception) {
        Log.w("PackageParser", "Failed to query DISPLAY_NAME", e)
      }
    }
    if (name.isBlank()) {
      name = uri.lastPathSegment?.substringAfterLast("/") ?: "package_${System.currentTimeMillis()}.apk"
    }
    return name.replace("/", "_").replace("\\", "_")
  }

  fun detectFormat(inputFile: File, fileName: String): ArchiveFormat {
    val lowerName = fileName.lowercase()
    if (lowerName.endsWith(".xapk")) return ArchiveFormat.XAPK
    if (lowerName.endsWith(".apks")) return ArchiveFormat.SPLIT_APKS
    if (lowerName.endsWith(".apkm")) return ArchiveFormat.APKM
    if (lowerName.endsWith(".zip")) return ArchiveFormat.BUNDLE_ZIP

    // Deep check if archive contains split APKs or OBBs even if named differently
    try {
      inputFile.inputStream().buffered().use { fis ->
        ZipInputStream(fis).use { zis ->
          var entry = zis.nextEntry
          var apkCount = 0
          var hasObb = false
          var hasManifestJson = false
          var hasRootAndroidManifest = false

          while (entry != null) {
            val name = entry.name.lowercase()
            if (name == "manifest.json") hasManifestJson = true
            if (name.endsWith(".apk")) apkCount++
            if (name == "androidmanifest.xml") hasRootAndroidManifest = true
            if (name.endsWith(".obb")) hasObb = true
            zis.closeEntry()
            entry = zis.nextEntry
          }

          if (hasObb || (hasManifestJson && apkCount > 0)) {
            return ArchiveFormat.XAPK
          }
          if (apkCount > 1) {
            return ArchiveFormat.SPLIT_APKS
          }
          if (apkCount == 1 && !hasRootAndroidManifest) {
            return ArchiveFormat.SPLIT_APKS
          }
        }
      }
    } catch (_: Exception) {}

    return ArchiveFormat.APK
  }

  fun parsePackage(context: Context, uri: Uri?, inputFile: File): ApkArchiveInfo {
    val fileName = inputFile.name
    val format = detectFormat(inputFile, fileName)

    return if (format == ArchiveFormat.APK) {
      parseSingleApk(context, uri, inputFile, fileName)
    } else {
      parseArchivePackage(context, uri, inputFile, fileName, format)
    }
  }

  private fun parseSingleApk(context: Context, uri: Uri?, file: File, fileName: String): ApkArchiveInfo {
    val pm = context.packageManager
    val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_PERMISSIONS
    val pkgInfo: PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
      @Suppress("DEPRECATION")
      pm.getPackageArchiveInfo(file.absolutePath, flags)
    }

    if (pkgInfo != null) {
      val appInfo = pkgInfo.applicationInfo ?: ApplicationInfo()
      appInfo.sourceDir = file.absolutePath
      appInfo.publicSourceDir = file.absolutePath

      val label = appInfo.loadLabel(pm).toString().ifBlank { pkgInfo.packageName }
      val icon = drawableToBitmap(appInfo.loadIcon(pm))
      val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkgInfo.longVersionCode
      } else {
        @Suppress("DEPRECATION")
        pkgInfo.versionCode.toLong()
      }
      val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        appInfo.minSdkVersion
      } else {
        21
      }
      val targetSdk = appInfo.targetSdkVersion

      val splits = listOf(
        SplitEntry(
          name = "base.apk",
          sizeBytes = file.length(),
          isBase = true,
          description = "メインアプリモジュール (Base APK)"
        )
      )

      return ApkArchiveInfo(
        uri = uri,
        filePath = file.absolutePath,
        fileName = fileName,
        format = ArchiveFormat.APK,
        packageName = pkgInfo.packageName,
        appLabel = label,
        versionName = pkgInfo.versionName ?: "1.0",
        versionCode = vCode,
        minSdk = minSdk,
        targetSdk = targetSdk,
        iconBitmap = icon,
        totalSizeBytes = file.length(),
        splits = splits,
        obbEntries = emptyList(),
        isCompatible = minSdk <= Build.VERSION.SDK_INT
      )
    } else {
      // Fallback if package archive parsing failed
      return ApkArchiveInfo(
        uri = uri,
        filePath = file.absolutePath,
        fileName = fileName,
        format = ArchiveFormat.APK,
        packageName = "unknown.package",
        appLabel = fileName.removeSuffix(".apk"),
        versionName = "1.0",
        versionCode = 1L,
        minSdk = 21,
        targetSdk = 34,
        iconBitmap = null,
        totalSizeBytes = file.length(),
        splits = listOf(SplitEntry("base.apk", file.length(), true, "Base APK")),
        obbEntries = emptyList(),
        isCompatible = true,
        warningMessage = "パッケージ情報の直接解析が制限されましたが、インストールセッションから続行できます。"
      )
    }
  }

  private fun parseArchivePackage(
    context: Context,
    uri: Uri?,
    archiveFile: File,
    fileName: String,
    format: ArchiveFormat
  ): ApkArchiveInfo {
    val extractDir = File(context.cacheDir, "extracted_${System.currentTimeMillis()}")
    extractDir.mkdirs()

    val apkFiles = mutableListOf<File>()
    val obbFiles = mutableListOf<File>()
    var manifestJson: JSONObject? = null

    try {
      archiveFile.inputStream().buffered().use { fis ->
        ZipInputStream(fis).use { zis ->
          var entry: ZipEntry? = zis.nextEntry
          while (entry != null) {
            val entryName = entry.name
            if (!entry.isDirectory) {
              if (entryName.equals("manifest.json", ignoreCase = true)) {
                val content = zis.readBytes().toString(Charsets.UTF_8)
                manifestJson = try { JSONObject(content) } catch (_: Exception) { null }
              } else if (entryName.endsWith(".apk", ignoreCase = true)) {
                val safeName = File(entryName).name
                val outFile = File(extractDir, safeName)
                FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                apkFiles.add(outFile)
              } else if (entryName.endsWith(".obb", ignoreCase = true)) {
                val safeName = File(entryName).name
                val outFile = File(extractDir, safeName)
                FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                obbFiles.add(outFile)
              }
            }
            zis.closeEntry()
            entry = zis.nextEntry
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }

    // Identify Base APK and Splits
    var baseApkFile: File? = apkFiles.find { it.name.equals("base.apk", ignoreCase = true) }
    if (baseApkFile == null && apkFiles.isNotEmpty()) {
      // Find the APK with valid PackageInfo
      val pm = context.packageManager
      for (apk in apkFiles) {
        val pi = pm.getPackageArchiveInfo(apk.absolutePath, 0)
        if (pi != null && pi.packageName.isNotBlank()) {
          baseApkFile = apk
          break
        }
      }
      if (baseApkFile == null) {
        baseApkFile = apkFiles.maxByOrNull { it.length() }
      }
    }

    val pm = context.packageManager
    var pkgName = manifestJson?.optString("package_name") ?: ""
    var versionName = manifestJson?.optString("version_name") ?: ""
    var versionCode = manifestJson?.optLong("version_code", 1L) ?: 1L
    var minSdk = manifestJson?.optInt("min_sdk_version", 21) ?: 21
    var targetSdk = manifestJson?.optInt("target_sdk_version", 34) ?: 34
    var label = manifestJson?.optString("name") ?: ""
    var iconBitmap: Bitmap? = null

    if (baseApkFile != null) {
      val pi = pm.getPackageArchiveInfo(baseApkFile.absolutePath, PackageManager.GET_ACTIVITIES)
      if (pi != null) {
        val appInfo = pi.applicationInfo ?: ApplicationInfo()
        appInfo.sourceDir = baseApkFile.absolutePath
        appInfo.publicSourceDir = baseApkFile.absolutePath

        if (pkgName.isBlank()) pkgName = pi.packageName
        if (versionName.isBlank()) versionName = pi.versionName ?: "1.0"
        if (versionCode == 1L) {
          versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.longVersionCode
          } else {
            @Suppress("DEPRECATION")
            pi.versionCode.toLong()
          }
        }
        if (label.isBlank()) label = appInfo.loadLabel(pm).toString()
        iconBitmap = drawableToBitmap(appInfo.loadIcon(pm))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && minSdk == 21) {
          minSdk = appInfo.minSdkVersion
        }
        if (targetSdk == 34) targetSdk = appInfo.targetSdkVersion
      }
    }

    if (pkgName.isBlank()) pkgName = "com.bundle.app"
    if (label.isBlank()) label = fileName.substringBeforeLast(".")
    if (versionName.isBlank()) versionName = "1.0"

    val splitEntries = apkFiles.map { apk ->
      val isBase = apk == baseApkFile
      val desc = when {
        isBase -> "ベースモジュール (Base APK)"
        apk.name.contains("arm64") || apk.name.contains("v8a") -> "ABIアーキテクチャ (arm64-v8a)"
        apk.name.contains("armeabi") || apk.name.contains("v7a") -> "ABIアーキテクチャ (armeabi-v7a)"
        apk.name.contains("x86") -> "ABIアーキテクチャ (x86/x86_64)"
        apk.name.contains("dpi") -> "画面密度リソース (${apk.name.substringAfter("config.").substringBefore(".apk")})"
        apk.name.contains("ja") || apk.name.contains("en") -> "言語ローカライズ (${apk.name.substringAfter("config.").substringBefore(".apk")})"
        else -> "スプリットモジュール (${apk.name})"
      }
      SplitEntry(
        name = apk.name,
        sizeBytes = apk.length(),
        isBase = isBase,
        description = desc
      )
    }

    val obbEntries = obbFiles.map { obb ->
      ObbEntry(
        fileName = obb.name,
        relativePath = "Android/obb/$pkgName/${obb.name}",
        sizeBytes = obb.length()
      )
    }

    return ApkArchiveInfo(
      uri = uri,
      filePath = extractDir.absolutePath, // contains extracted apks and obbs
      fileName = fileName,
      format = format,
      packageName = pkgName,
      appLabel = label,
      versionName = versionName,
      versionCode = versionCode,
      minSdk = minSdk,
      targetSdk = targetSdk,
      iconBitmap = iconBitmap,
      totalSizeBytes = archiveFile.length(),
      splits = splitEntries,
      obbEntries = obbEntries,
      isCompatible = minSdk <= Build.VERSION.SDK_INT
    )
  }

  private fun drawableToBitmap(drawable: Drawable): Bitmap? {
    try {
      if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
      }
      val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
      val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      drawable.setBounds(0, 0, canvas.width, canvas.height)
      drawable.draw(canvas)
      return bitmap
    } catch (_: Exception) {
      return null
    }
  }

  fun copyUriToTempFile(context: Context, uri: Uri, targetName: String): File {
    val safeName = targetName.replace("/", "_").replace("\\", "_").ifBlank { "package_${System.currentTimeMillis()}.apk" }
    val tempFile = File(context.cacheDir, safeName)
    if (tempFile.exists()) tempFile.delete()
    context.contentResolver.openInputStream(uri)?.use { input ->
      FileOutputStream(tempFile).use { output ->
        input.copyTo(output)
      }
    }
    return tempFile
  }
}
