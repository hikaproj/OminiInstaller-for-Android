package com.example.installer

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object PackageParser {

  private const val TAG = "PackageParser"

  private data class ExtractedApkItem(
    val file: File,
    val originalFileName: String,
    val zipEntryPath: String
  )

  private data class ExtractedObbItem(
    val file: File,
    val originalFileName: String,
    val zipEntryPath: String
  )

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
        Log.w(TAG, "Failed to query DISPLAY_NAME", e)
      }
    }
    if (name.isBlank()) {
      name = uri.lastPathSegment?.substringAfterLast("/") ?: "package_${System.currentTimeMillis()}.apk"
    }
    return name.replace("/", "_").replace("\\", "_")
  }

  fun detectFormatByExtension(fileName: String): ArchiveFormat {
    return ArchiveFormat.fromExtension(fileName)
  }

  fun detectFormat(inputFile: File, fileName: String): ArchiveFormat {
    val extFormat = ArchiveFormat.fromExtension(fileName)
    if (extFormat != ArchiveFormat.APK) {
      return extFormat
    }

    // Check magic bytes for GZIP (1F 8B)
    try {
      FileInputStream(inputFile).use { fis ->
        val magic = ByteArray(4)
        if (fis.read(magic) >= 2) {
          if (magic[0] == 0x1F.toByte() && magic[1] == 0x8B.toByte()) {
            return ArchiveFormat.APKS_GZIP
          }
        }
      }
    } catch (_: Exception) {}

    // Inspect ZIP central directory first (reliable for XAPK / APKS / APKM even when renamed to .apk or .zip)
    try {
      ZipFile(inputFile).use { zip ->
        var apkCount = 0
        var hasObb = false
        var hasManifestJson = false
        var hasInfoJson = false
        var hasRootAndroidManifest = false

        val entries = zip.entries()
        while (entries.hasMoreElements()) {
          val entry = entries.nextElement()
          if (entry.isDirectory) continue
          val name = entry.name.lowercase()
          if (name == "manifest.json" || name.endsWith("/manifest.json")) hasManifestJson = true
          if (name == "info.json" || name.endsWith("/info.json")) hasInfoJson = true
          if (name.endsWith(".apk")) apkCount++
          if (name == "androidmanifest.xml") hasRootAndroidManifest = true
          if (name.endsWith(".obb")) hasObb = true
        }

        if (hasInfoJson && apkCount > 0) return ArchiveFormat.APKM
        if (hasObb || (hasManifestJson && apkCount > 0)) return ArchiveFormat.XAPK
        if (apkCount > 1) return ArchiveFormat.SPLIT_APKS
        if (apkCount == 1 && !hasRootAndroidManifest) return ArchiveFormat.SPLIT_APKS
      }
    } catch (_: Exception) {}

    return ArchiveFormat.APK
  }

  fun parsePackage(
    context: Context,
    uri: Uri?,
    inputFile: File,
    deleteInputIfExtracted: Boolean = false
  ): ApkArchiveInfo {
    val fileName = inputFile.name.replaceFirst("^\\d+_".toRegex(), "")
    val format = detectFormat(inputFile, fileName)

    return when (format) {
      ArchiveFormat.APK -> parseSingleApk(context, uri, inputFile, fileName, format)
      ArchiveFormat.APKS_GZIP -> parseGzipPackage(context, uri, inputFile, fileName, deleteInputIfExtracted)
      ArchiveFormat.APKS_TAR, ArchiveFormat.APKS_TAR_GZ, ArchiveFormat.APKS_TGZ ->
        parseTarPackage(context, uri, inputFile, fileName, format, deleteInputIfExtracted)
      else -> parseArchivePackage(context, uri, inputFile, fileName, format, deleteInputIfExtracted)
    }
  }

  /**
   * Multi-APK selection parser: combines multiple user-selected .apk files (e.g. base.apk + split_config.arm64.apk + split_config.ja.apk)
   * into a unified split installation session.
   */
  fun parseMultipleApkFiles(context: Context, files: List<File>): ApkArchiveInfo {
    val extractDir = File(context.cacheDir, "multi_apks_${System.currentTimeMillis()}")
    extractDir.mkdirs()

    val extractedApks = mutableListOf<ExtractedApkItem>()
    val extractedObbs = mutableListOf<ExtractedObbItem>()
    var manifestJson: JSONObject? = null
    var iconFromArchive: Bitmap? = null

    files.forEachIndexed { idx, file ->
      val cleanOrig = file.name.replaceFirst("^\\d+_".toRegex(), "")
      if (cleanOrig.lowercase().endsWith(".apk") && isDirectApkFile(file)) {
        val safeName = cleanOrig.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
        val target = File(extractDir, "${idx}_$safeName")
        file.copyTo(target, overwrite = true)
        extractedApks.add(ExtractedApkItem(target, cleanOrig, cleanOrig))
      } else {
        val (mJson, iconBmp) = extractZipApksAndObbs(file, extractDir, extractedApks, extractedObbs)
        if (manifestJson == null && mJson != null) manifestJson = mJson
        if (iconFromArchive == null && iconBmp != null) iconFromArchive = iconBmp
      }
    }

    return buildArchiveInfoFromExtractedFiles(
      context = context,
      uri = null,
      extractDir = extractDir,
      fileName = "複数APKバンドル (${extractedApks.size}個)",
      format = ArchiveFormat.MULTI_APK_DIR,
      apkItems = extractedApks,
      obbItems = extractedObbs,
      manifestJson = manifestJson,
      archiveIconBitmap = iconFromArchive,
      totalBytes = extractedApks.sumOf { it.file.length() }
    )
  }

  private fun isDirectApkFile(file: File): Boolean {
    return try {
      ZipFile(file).use { zip ->
        zip.getEntry("AndroidManifest.xml") != null
      }
    } catch (_: Exception) {
      false
    }
  }

  private fun parseSingleApk(
    context: Context,
    uri: Uri?,
    file: File,
    fileName: String,
    format: ArchiveFormat = ArchiveFormat.APK
  ): ApkArchiveInfo {
    val pm = context.packageManager
    val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_PERMISSIONS
    val pkgInfo: PackageInfo? = getArchiveInfoCompat(pm, file.absolutePath, flags)
      ?: getArchiveInfoCompat(pm, file.absolutePath, 0)

    if (pkgInfo != null) {
      val appInfo = pkgInfo.applicationInfo ?: ApplicationInfo()
      appInfo.sourceDir = file.absolutePath
      appInfo.publicSourceDir = file.absolutePath

      val label = try {
        appInfo.loadLabel(pm).toString().ifBlank { pkgInfo.packageName }
      } catch (_: Exception) {
        pkgInfo.packageName
      }
      val icon = try {
        drawableToBitmap(appInfo.loadIcon(pm))
      } catch (_: Exception) {
        null
      }
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
          name = fileName,
          sizeBytes = file.length(),
          isBase = true,
          description = "メインアプリモジュール (Base APK)",
          filePath = file.absolutePath
        )
      )

      return ApkArchiveInfo(
        uri = uri,
        filePath = file.absolutePath,
        fileName = fileName,
        format = format,
        packageName = pkgInfo.packageName,
        originalPackageName = pkgInfo.packageName,
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
      return ApkArchiveInfo(
        uri = uri,
        filePath = file.absolutePath,
        fileName = fileName,
        format = format,
        packageName = "unknown.package",
        originalPackageName = "unknown.package",
        appLabel = fileName.substringBeforeLast("."),
        versionName = "1.0",
        versionCode = 1L,
        minSdk = 21,
        targetSdk = 34,
        iconBitmap = null,
        totalSizeBytes = file.length(),
        splits = listOf(SplitEntry("base.apk", file.length(), true, "Base APK", file.absolutePath)),
        obbEntries = emptyList(),
        isCompatible = true,
        warningMessage = "パッケージ情報の直接解析が制限されましたが、インストールセッションから続行できます。"
      )
    }
  }

  private fun parseGzipPackage(
    context: Context,
    uri: Uri?,
    gzipFile: File,
    fileName: String,
    deleteInputIfExtracted: Boolean
  ): ApkArchiveInfo {
    val extractDir = File(context.cacheDir, "extracted_gz_${System.currentTimeMillis()}")
    extractDir.mkdirs()
    val decompressedName = fileName.removeSuffix(".gz").removeSuffix(".gzip").let {
      if (it.endsWith(".apk", ignoreCase = true) || it.endsWith(".tar", ignoreCase = true)) it else "$it.apk"
    }
    val outFile = File(extractDir, decompressedName)
    try {
      GZIPInputStream(BufferedInputStream(FileInputStream(gzipFile))).use { gis ->
        FileOutputStream(outFile).use { fos ->
          gis.copyTo(fos)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "GZIP decompress error", e)
    }

    if (deleteInputIfExtracted && gzipFile.exists()) {
      gzipFile.delete()
    }

    if (outFile.name.endsWith(".tar", ignoreCase = true)) {
      return parseTarPackage(context, uri, outFile, fileName, ArchiveFormat.APKS_TAR_GZ, true)
    }
    val info = parsePackage(context, uri, outFile, false)
    return info.copy(fileName = fileName, format = ArchiveFormat.APKS_GZIP)
  }

  private fun parseTarPackage(
    context: Context,
    uri: Uri?,
    tarOrGzFile: File,
    fileName: String,
    format: ArchiveFormat,
    deleteInputIfExtracted: Boolean
  ): ApkArchiveInfo {
    val extractDir = File(context.cacheDir, "extracted_tar_${System.currentTimeMillis()}")
    extractDir.mkdirs()

    val apkItems = mutableListOf<ExtractedApkItem>()
    val obbItems = mutableListOf<ExtractedObbItem>()
    var manifestJson: JSONObject? = null
    val totalArchiveSize = tarOrGzFile.length()

    try {
      val rawStream: InputStream = FileInputStream(tarOrGzFile)
      val tarStream: InputStream = if (
        format == ArchiveFormat.APKS_TAR_GZ ||
        format == ArchiveFormat.APKS_TGZ ||
        fileName.lowercase().endsWith(".gz") ||
        fileName.lowercase().endsWith(".tgz")
      ) {
        GZIPInputStream(BufferedInputStream(rawStream))
      } else {
        BufferedInputStream(rawStream)
      }

      tarStream.use { input ->
        extractTarStream(input, extractDir, apkItems, obbItems) { json ->
          manifestJson = json
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "TAR extraction error", e)
    }

    if (deleteInputIfExtracted && tarOrGzFile.exists() && apkItems.isNotEmpty()) {
      tarOrGzFile.delete()
    }

    return buildArchiveInfoFromExtractedFiles(
      context = context,
      uri = uri,
      extractDir = extractDir,
      fileName = fileName,
      format = format,
      apkItems = apkItems,
      obbItems = obbItems,
      manifestJson = manifestJson,
      archiveIconBitmap = null,
      totalBytes = totalArchiveSize
    )
  }

  private fun extractTarStream(
    input: InputStream,
    extractDir: File,
    apkItems: MutableList<ExtractedApkItem>,
    obbItems: MutableList<ExtractedObbItem>,
    onManifest: (JSONObject) -> Unit
  ) {
    val header = ByteArray(512)
    var index = 0
    while (true) {
      var readTotal = 0
      while (readTotal < 512) {
        val r = input.read(header, readTotal, 512 - readTotal)
        if (r == -1) break
        readTotal += r
      }
      if (readTotal < 512) break
      if (header.all { it == 0.toByte() }) break

      val nameBytes = header.copyOfRange(0, 100)
      val nameLen = nameBytes.indexOfFirst { it == 0.toByte() }.let { if (it == -1) 100 else it }
      val entryName = String(nameBytes, 0, nameLen, Charsets.UTF_8).trim()

      val sizeStr = String(header, 124, 12, Charsets.US_ASCII)
        .trim { it <= ' ' || it == '\u0000' }
      val entrySize = sizeStr.toLongOrNull(8) ?: 0L
      val typeFlag = header[156].toInt().toChar()

      if (entrySize > 0 && (typeFlag == '0' || typeFlag == '\u0000')) {
        val rawFileName = File(entryName).name
        val safeBaseName = rawFileName.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
        val uniqueName = "split_${index}_$safeBaseName"
        index++

        if (rawFileName.equals("manifest.json", ignoreCase = true) ||
          rawFileName.equals("info.json", ignoreCase = true) ||
          rawFileName.equals("manifest_omni.json", ignoreCase = true)
        ) {
          val bytes = ByteArray(entrySize.toInt())
          readFully(input, bytes)
          try {
            onManifest(JSONObject(String(bytes, Charsets.UTF_8)))
          } catch (_: Exception) {}
        } else if (rawFileName.endsWith(".apk", ignoreCase = true)) {
          val outFile = File(extractDir, uniqueName)
          copyFixedBytes(input, outFile, entrySize)
          if (outFile.length() > 0) {
            apkItems.add(ExtractedApkItem(outFile, rawFileName, entryName))
          }
        } else if (rawFileName.endsWith(".obb", ignoreCase = true)) {
          val outFile = File(extractDir, safeBaseName)
          copyFixedBytes(input, outFile, entrySize)
          if (outFile.length() > 0) {
            obbItems.add(ExtractedObbItem(outFile, rawFileName, entryName))
          }
        } else {
          skipFully(input, entrySize)
        }

        val padding = ((512 - (entrySize % 512)) % 512)
        if (padding > 0) skipFully(input, padding)
      }
    }
  }

  private fun readFully(input: InputStream, dest: ByteArray) {
    var offset = 0
    while (offset < dest.size) {
      val r = input.read(dest, offset, dest.size - offset)
      if (r <= 0) break
      offset += r
    }
  }

  private fun copyFixedBytes(input: InputStream, outFile: File, size: Long) {
    FileOutputStream(outFile).use { fos ->
      val buf = ByteArray(32768)
      var remaining = size
      while (remaining > 0) {
        val toRead = minOf(buf.size.toLong(), remaining).toInt()
        val r = input.read(buf, 0, toRead)
        if (r <= 0) break
        fos.write(buf, 0, r)
        remaining -= r
      }
    }
  }

  private fun skipFully(input: InputStream, bytesToSkip: Long) {
    val buf = ByteArray(8192)
    var remaining = bytesToSkip
    while (remaining > 0) {
      val toRead = minOf(buf.size.toLong(), remaining).toInt()
      val r = input.read(buf, 0, toRead)
      if (r <= 0) break
      remaining -= r
    }
  }

  /**
   * Extracts APKs, OBBs, manifest.json, and icon.png from XAPK / APKS / APKM / ZIP archives.
   * Uses [ZipFile] (Central Directory) first for 100% compatibility with APKPure XAPK files,
   * falling back to [ZipInputStream] only if needed.
   */
  private fun extractZipApksAndObbs(
    zipFile: File,
    extractDir: File,
    apkItems: MutableList<ExtractedApkItem>,
    obbItems: MutableList<ExtractedObbItem>
  ): Pair<JSONObject?, Bitmap?> {
    var manifestJson: JSONObject? = null
    var archiveIcon: Bitmap? = null
    var entryIndex = apkItems.size
    var extractedWithZipFile = false

    // 1. Primary path: ZipFile (reads Central Directory, handles data descriptors and large XAPKs cleanly)
    try {
      ZipFile(zipFile).use { zip ->
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
          val entry = entries.nextElement()
          if (entry.isDirectory) continue
          val entryPath = entry.name.replace("\\", "/")
          val rawFileName = entryPath.substringAfterLast("/")
          val safeFileName = rawFileName.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")

          when {
            rawFileName.equals("manifest.json", ignoreCase = true) ||
              rawFileName.equals("info.json", ignoreCase = true) ||
              rawFileName.equals("manifest_omni.json", ignoreCase = true) -> {
              zip.getInputStream(entry).use { ins ->
                val content = ins.readBytes().toString(Charsets.UTF_8)
                manifestJson = try { JSONObject(content) } catch (_: Exception) { manifestJson }
              }
            }
            (rawFileName.equals("icon.png", ignoreCase = true) || rawFileName.equals("icon.webp", ignoreCase = true)) && archiveIcon == null -> {
              zip.getInputStream(entry).use { ins ->
                archiveIcon = try { BitmapFactory.decodeStream(ins) } catch (_: Exception) { null }
              }
            }
            rawFileName.endsWith(".apk", ignoreCase = true) -> {
              val uniqueName = "apk_${entryIndex}_$safeFileName"
              entryIndex++
              val outFile = File(extractDir, uniqueName)
              zip.getInputStream(entry).use { ins ->
                FileOutputStream(outFile).use { fos -> ins.copyTo(fos, 65536) }
              }
              if (outFile.length() > 0) {
                apkItems.add(ExtractedApkItem(outFile, rawFileName, entryPath))
              }
            }
            rawFileName.endsWith(".obb", ignoreCase = true) -> {
              val outFile = File(extractDir, safeFileName)
              zip.getInputStream(entry).use { ins ->
                FileOutputStream(outFile).use { fos -> ins.copyTo(fos, 65536) }
              }
              if (outFile.length() > 0) {
                obbItems.add(ExtractedObbItem(outFile, rawFileName, entryPath))
              }
            }
            rawFileName.endsWith(".apks", ignoreCase = true) ||
              rawFileName.endsWith(".xapk", ignoreCase = true) ||
              rawFileName.endsWith(".apkm", ignoreCase = true) ||
              rawFileName.endsWith(".zip", ignoreCase = true) -> {
              val nestedZip = File(extractDir, "nested_${entryIndex}_$safeFileName")
              entryIndex++
              zip.getInputStream(entry).use { ins ->
                FileOutputStream(nestedZip).use { fos -> ins.copyTo(fos, 65536) }
              }
              val (nestedManifest, nestedIcon) = extractZipApksAndObbs(nestedZip, extractDir, apkItems, obbItems)
              if (manifestJson == null && nestedManifest != null) manifestJson = nestedManifest
              if (archiveIcon == null && nestedIcon != null) archiveIcon = nestedIcon
              nestedZip.delete()
            }
          }
        }
        extractedWithZipFile = true
      }
    } catch (e: Exception) {
      Log.w(TAG, "ZipFile extraction fallback to ZipInputStream: ${e.message}")
    }

    // 2. Fallback path: ZipInputStream if ZipFile failed or extracted 0 APKs
    if (!extractedWithZipFile || (apkItems.isEmpty() && obbItems.isEmpty())) {
      try {
        zipFile.inputStream().buffered().use { fis ->
          ZipInputStream(fis).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
              if (!entry.isDirectory) {
                val entryPath = entry.name.replace("\\", "/")
                val rawFileName = entryPath.substringAfterLast("/")
                val safeFileName = rawFileName.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
                when {
                  rawFileName.equals("manifest.json", ignoreCase = true) ||
                    rawFileName.equals("info.json", ignoreCase = true) ||
                    rawFileName.equals("manifest_omni.json", ignoreCase = true) -> {
                    val content = zis.readBytes().toString(Charsets.UTF_8)
                    manifestJson = try { JSONObject(content) } catch (_: Exception) { manifestJson }
                  }
                  rawFileName.equals("icon.png", ignoreCase = true) && archiveIcon == null -> {
                    val bytes = zis.readBytes()
                    archiveIcon = try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Exception) { null }
                  }
                  rawFileName.endsWith(".apk", ignoreCase = true) -> {
                    val uniqueName = "apk_${entryIndex}_$safeFileName"
                    entryIndex++
                    val outFile = File(extractDir, uniqueName)
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos, 65536) }
                    if (outFile.length() > 0) {
                      apkItems.add(ExtractedApkItem(outFile, rawFileName, entryPath))
                    }
                  }
                  rawFileName.endsWith(".obb", ignoreCase = true) -> {
                    val outFile = File(extractDir, safeFileName)
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos, 65536) }
                    if (outFile.length() > 0) {
                      obbItems.add(ExtractedObbItem(outFile, rawFileName, entryPath))
                    }
                  }
                }
              }
              zis.closeEntry()
              entry = zis.nextEntry
            }
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "ZipInputStream fallback error", e)
      }
    }

    return Pair(manifestJson, archiveIcon)
  }

  private fun parseArchivePackage(
    context: Context,
    uri: Uri?,
    archiveFile: File,
    fileName: String,
    format: ArchiveFormat,
    deleteInputIfExtracted: Boolean
  ): ApkArchiveInfo {
    val extractDir = File(context.cacheDir, "extracted_${System.currentTimeMillis()}")
    extractDir.mkdirs()

    val apkItems = mutableListOf<ExtractedApkItem>()
    val obbItems = mutableListOf<ExtractedObbItem>()
    val totalArchiveSize = archiveFile.length()
    val (manifestJson, archiveIcon) = extractZipApksAndObbs(archiveFile, extractDir, apkItems, obbItems)

    // If no inner .apk files were found, maybe the file itself is a single APK with a non-.apk extension
    if (apkItems.isEmpty()) {
      val pm = context.packageManager
      val directInfo = getArchiveInfoCompat(pm, archiveFile.absolutePath, 0)
      if (directInfo != null) {
        return parseSingleApk(context, uri, archiveFile, fileName, format)
      }
    } else if (deleteInputIfExtracted && archiveFile.exists()) {
      // Free up temporary archive copy once all inner APKs and OBBs are extracted
      archiveFile.delete()
    }

    return buildArchiveInfoFromExtractedFiles(
      context = context,
      uri = uri,
      extractDir = extractDir,
      fileName = fileName,
      format = format,
      apkItems = apkItems,
      obbItems = obbItems,
      manifestJson = manifestJson,
      archiveIconBitmap = archiveIcon,
      totalBytes = totalArchiveSize
    )
  }

  private fun detectAbiFromSplitName(rawFileName: String): String? {
    val lower = rawFileName.lowercase()
    return when {
      lower.contains("arm64_v8a") || lower.contains("arm64-v8a") || lower.contains("arm64") -> "arm64-v8a"
      lower.contains("armeabi_v7a") || lower.contains("armeabi-v7a") || lower.contains("armv7") -> "armeabi-v7a"
      lower.contains("armeabi") -> "armeabi"
      lower.contains("x86_64") || lower.contains("x86-64") -> "x86_64"
      lower.contains("x86") -> "x86"
      else -> null
    }
  }

  private fun isLikelyConfigSplitName(rawFileName: String): Boolean {
    val lower = rawFileName.lowercase()
    return lower.startsWith("config.") ||
      lower.startsWith("split_config.") ||
      lower.startsWith("split_") ||
      lower.contains(".config.")
  }

  private fun buildArchiveInfoFromExtractedFiles(
    context: Context,
    uri: Uri?,
    extractDir: File,
    fileName: String,
    format: ArchiveFormat,
    apkItems: List<ExtractedApkItem>,
    obbItems: List<ExtractedObbItem>,
    manifestJson: JSONObject?,
    archiveIconBitmap: Bitmap?,
    totalBytes: Long
  ): ApkArchiveInfo {
    val pm = context.packageManager

    // Parse manifest.json metadata first (XAPK / APKM / Backup manifest)
    var pkgName = manifestJson?.optString("package_name")
      ?.ifBlank { manifestJson.optString("pname") }
      ?.ifBlank { manifestJson.optString("packageName") }
      ?: ""
    var versionName = manifestJson?.optString("version_name")
      ?.ifBlank { manifestJson.optString("versionName") }
      ?: ""
    val versionCodeStr = manifestJson?.optString("version_code", "")
      ?.ifBlank { manifestJson.optString("versionCode", "") }
      ?: ""
    var versionCode = versionCodeStr.toLongOrNull()
      ?: manifestJson?.optLong("version_code", 0L)?.takeIf { it > 0L }
      ?: manifestJson?.optLong("versionCode", 1L)
      ?: 1L
    var minSdk = manifestJson?.optString("min_sdk_version", "")?.toIntOrNull()
      ?: manifestJson?.optInt("min_sdk_version", 21)
      ?: 21
    var targetSdk = manifestJson?.optString("target_sdk_version", "")?.toIntOrNull()
      ?: manifestJson?.optInt("target_sdk_version", 34)
      ?: 34
    var label = manifestJson?.optString("name")
      ?.ifBlank { manifestJson.optString("app_name") }
      ?.ifBlank { manifestJson.optString("appLabel") }
      ?: ""

    // Check if manifest.json explicitly declares `split_apks` with `"id": "base"` (XAPK v2 standard)
    var manifestBaseFileName: String? = null
    val splitApksArr = manifestJson?.optJSONArray("split_apks")
    if (splitApksArr != null) {
      for (i in 0 until splitApksArr.length()) {
        val obj = splitApksArr.optJSONObject(i) ?: continue
        val id = obj.optString("id", "")
        val fileRef = obj.optString("file", "")
        if (id.equals("base", ignoreCase = true) && fileRef.isNotBlank()) {
          manifestBaseFileName = fileRef.substringAfterLast("/")
          break
        }
      }
    }

    // Query Android PackageManager on all extracted APKs
    // Note: Pure config split APKs (config.arm64_v8a.apk, config.xxhdpi.apk, etc.) do NOT have an <application> tag,
    // so getPackageArchiveInfo returns non-null with valid applicationInfo ONLY for Base APKs (or feature splits).
    val parsedApkMeta = mutableMapOf<File, PackageInfo?>()
    for (item in apkItems) {
      val pi = getArchiveInfoCompat(pm, item.file.absolutePath, 0)
      parsedApkMeta[item.file] = pi
    }

    // 1. Identify Base APK using 4-tier priority
    var baseItem: ExtractedApkItem? = null

    // Priority 1: Explicit `"id": "base"` in XAPK `manifest.json`
    if (manifestBaseFileName != null) {
      baseItem = apkItems.find {
        it.originalFileName.equals(manifestBaseFileName, ignoreCase = true) ||
          it.zipEntryPath.equals(manifestBaseFileName, ignoreCase = true)
      }
    }

    // Priority 2: Standard base APK naming (`base.apk`, `base-master.apk`, `<package_name>.apk`)
    if (baseItem == null) {
      baseItem = apkItems.find { item ->
        val lower = item.originalFileName.lowercase()
        lower == "base.apk" ||
          lower == "base-master.apk" ||
          lower == "main.apk" ||
          (pkgName.isNotBlank() && lower == "${pkgName.lowercase()}.apk")
      }
    }

    // Priority 3: APK with valid PackageInfo + ApplicationInfo that is NOT a config split
    if (baseItem == null) {
      baseItem = apkItems.filter { item ->
        parsedApkMeta[item.file] != null && !isLikelyConfigSplitName(item.originalFileName)
      }.maxByOrNull { it.file.length() }
    }

    // Priority 4: Any APK that is not a config split, or largest APK
    if (baseItem == null) {
      baseItem = apkItems.filter { !isLikelyConfigSplitName(it.originalFileName) }
        .maxByOrNull { it.file.length() }
        ?: apkItems.maxByOrNull { it.file.length() }
    }

    // Extract accurate package metadata from the Base APK
    var iconBitmap: Bitmap? = archiveIconBitmap
    if (baseItem != null) {
      val pi = parsedApkMeta[baseItem.file]
        ?: getArchiveInfoCompat(pm, baseItem.file.absolutePath, PackageManager.GET_ACTIVITIES)
      if (pi != null) {
        val appInfo = pi.applicationInfo ?: ApplicationInfo()
        appInfo.sourceDir = baseItem.file.absolutePath
        appInfo.publicSourceDir = baseItem.file.absolutePath

        // Always trust Base APK's internal packageName for PackageInstaller compatibility!
        if (pi.packageName.isNotBlank()) {
          pkgName = pi.packageName
        }
        if (versionName.isBlank() || !pi.versionName.isNullOrBlank()) {
          versionName = pi.versionName ?: versionName.ifBlank { "1.0" }
        }
        val piVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          pi.longVersionCode
        } else {
          @Suppress("DEPRECATION")
          pi.versionCode.toLong()
        }
        if (piVersionCode > 0L) {
          versionCode = piVersionCode
        }
        val loadedLabel = try { appInfo.loadLabel(pm).toString() } catch (_: Exception) { "" }
        if (loadedLabel.isNotBlank() && !loadedLabel.startsWith("com.")) {
          label = loadedLabel
        }
        val loadedIcon = try { drawableToBitmap(appInfo.loadIcon(pm)) } catch (_: Exception) { null }
        if (loadedIcon != null) {
          iconBitmap = loadedIcon
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && appInfo.minSdkVersion > 0) {
          minSdk = appInfo.minSdkVersion
        }
        if (appInfo.targetSdkVersion > 0) {
          targetSdk = appInfo.targetSdkVersion
        }
      }
    }

    if (pkgName.isBlank()) pkgName = "com.bundle.app"
    if (label.isBlank()) label = fileName.substringBeforeLast(".")
    if (versionName.isBlank()) versionName = "1.0"

    // 2. Single-Best-ABI Selection for Split APKs:
    // Android PackageInstaller rejects sessions containing multiple conflicting ABI splits
    // (e.g. both config.arm64_v8a.apk and config.armeabi_v7a.apk).
    // Select ONLY the highest-priority ABI from Build.SUPPORTED_ABIS present in the archive!
    val deviceSupportedAbis = Build.SUPPORTED_ABIS.map { it.lowercase() }
    val splitAbisInArchive = apkItems
      .filter { it != baseItem }
      .mapNotNull { detectAbiFromSplitName(it.originalFileName) }
      .toSet()

    val bestMatchingAbi: String? = deviceSupportedAbis.firstOrNull { abi ->
      splitAbisInArchive.contains(abi)
    }

    val filteredApkItems = apkItems.filter { item ->
      if (item == baseItem) return@filter true
      val itemAbi = detectAbiFromSplitName(item.originalFileName)
      if (itemAbi != null) {
        // Keep only the single best matching ABI for this device
        if (bestMatchingAbi != null) {
          itemAbi == bestMatchingAbi
        } else {
          deviceSupportedAbis.contains(itemAbi)
        }
      } else {
        // Non-ABI split (DPI, language, feature module) -> keep all
        true
      }
    }.ifEmpty { apkItems }

    // Ensure Base APK is always first
    val sortedApkItems = filteredApkItems.sortedByDescending { it == baseItem }

    val splitEntries = sortedApkItems.map { item ->
      val isBase = item == baseItem
      val cleanDisplayName = item.originalFileName
      val detectedAbi = detectAbiFromSplitName(cleanDisplayName)
      val desc = when {
        isBase -> "ベースモジュール (Base APK)"
        detectedAbi != null -> "ABI ネイティブライブラリ ($detectedAbi)"
        cleanDisplayName.lowercase().contains("dpi") -> "画面密度リソース ($cleanDisplayName)"
        cleanDisplayName.lowercase().startsWith("config.") -> "構成スプリット ($cleanDisplayName)"
        else -> "スプリットモジュール ($cleanDisplayName)"
      }
      SplitEntry(
        name = cleanDisplayName,
        sizeBytes = item.file.length(),
        isBase = isBase,
        description = desc,
        filePath = item.file.absolutePath
      )
    }

    // Parse OBB expansion paths from manifest.json if available
    val expansionPathMap = mutableMapOf<String, String>()
    val expansionsArr = manifestJson?.optJSONArray("expansions")
    if (expansionsArr != null) {
      for (i in 0 until expansionsArr.length()) {
        val obj = expansionsArr.optJSONObject(i) ?: continue
        val fileRef = obj.optString("file", "").substringAfterLast("/")
        val installPath = obj.optString("install_path", "")
        if (fileRef.isNotBlank() && installPath.isNotBlank()) {
          expansionPathMap[fileRef] = installPath
        }
      }
    }

    val obbEntries = obbItems.map { obb ->
      val relPath = expansionPathMap[obb.originalFileName]
        ?: if (obb.zipEntryPath.contains("Android/obb/", ignoreCase = true)) {
          obb.zipEntryPath.substringAfter("Android/obb/", "Android/obb/$pkgName/${obb.originalFileName}").let {
            "Android/obb/$it"
          }
        } else {
          "Android/obb/$pkgName/${obb.originalFileName}"
        }
      ObbEntry(
        fileName = obb.file.name,
        relativePath = relPath,
        sizeBytes = obb.file.length()
      )
    }

    val sumSplitBytes = sortedApkItems.sumOf { it.file.length() } + obbItems.sumOf { it.file.length() }
    val isAbiCompatible = splitAbisInArchive.isEmpty() || bestMatchingAbi != null

    return ApkArchiveInfo(
      uri = uri,
      filePath = extractDir.absolutePath,
      fileName = fileName,
      format = format,
      packageName = pkgName,
      originalPackageName = pkgName,
      appLabel = label,
      versionName = versionName,
      versionCode = versionCode,
      minSdk = minSdk,
      targetSdk = targetSdk,
      iconBitmap = iconBitmap,
      totalSizeBytes = if (sumSplitBytes > 0) sumSplitBytes else totalBytes,
      splits = splitEntries,
      obbEntries = obbEntries,
      isCompatible = (minSdk <= Build.VERSION.SDK_INT) && isAbiCompatible
    )
  }

  private fun getArchiveInfoCompat(pm: PackageManager, path: String, flags: Int): PackageInfo? {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageArchiveInfo(path, flags)
      }
    } catch (_: Exception) {
      null
    }
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
    val tempFile = File(context.cacheDir, "${System.currentTimeMillis()}_$safeName")
    if (tempFile.exists()) tempFile.delete()
    context.contentResolver.openInputStream(uri)?.use { input ->
      FileOutputStream(tempFile).use { output ->
        input.copyTo(output, 65536)
      }
    }
    return tempFile
  }
}
