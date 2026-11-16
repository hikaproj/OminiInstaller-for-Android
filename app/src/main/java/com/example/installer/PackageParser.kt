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
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object PackageParser {

  private const val TAG = "PackageParser"

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

    // Deep check if ZIP archive contains split APKs, OBBs, or nested archives
    try {
      inputFile.inputStream().buffered().use { fis ->
        ZipInputStream(fis).use { zis ->
          var entry = zis.nextEntry
          var apkCount = 0
          var hasObb = false
          var hasManifestJson = false
          var hasInfoJson = false
          var hasOmniJson = false
          var hasRootAndroidManifest = false

          while (entry != null) {
            val name = entry.name.lowercase()
            if (name == "manifest.json" || name.endsWith("/manifest.json")) hasManifestJson = true
            if (name == "info.json") hasInfoJson = true
            if (name == "omni_meta.json") hasOmniJson = true
            if (name.endsWith(".apk")) apkCount++
            if (name == "androidmanifest.xml") hasRootAndroidManifest = true
            if (name.endsWith(".obb")) hasObb = true
            zis.closeEntry()
            entry = zis.nextEntry
          }

          if (hasInfoJson && apkCount > 0) return ArchiveFormat.APKM
          if (hasObb || (hasManifestJson && apkCount > 0)) return ArchiveFormat.XAPK
          if (apkCount > 1) return ArchiveFormat.SPLIT_APKS
          if (apkCount == 1 && !hasRootAndroidManifest) return ArchiveFormat.SPLIT_APKS
        }
      }
    } catch (_: Exception) {}

    return ArchiveFormat.APK
  }

  fun parsePackage(context: Context, uri: Uri?, inputFile: File): ApkArchiveInfo {
    val fileName = inputFile.name
    val format = detectFormat(inputFile, fileName)

    return when (format) {
      ArchiveFormat.APK -> parseSingleApk(context, uri, inputFile, fileName, format)
      ArchiveFormat.APKS_GZIP -> parseGzipPackage(context, uri, inputFile, fileName)
      ArchiveFormat.APKS_TAR, ArchiveFormat.APKS_TAR_GZ, ArchiveFormat.APKS_TGZ ->
        parseTarPackage(context, uri, inputFile, fileName, format)
      else -> parseArchivePackage(context, uri, inputFile, fileName, format)
    }
  }

  /**
   * Multi-APK selection parser: combines multiple user-selected .apk files (e.g. base.apk + split_config.arm64.apk + split_config.ja.apk)
   * into a unified split installation session!
   */
  fun parseMultipleApkFiles(context: Context, files: List<File>): ApkArchiveInfo {
    val extractDir = File(context.cacheDir, "multi_apks_${System.currentTimeMillis()}")
    extractDir.mkdirs()

    val copiedApks = mutableListOf<File>()
    files.forEachIndexed { idx, file ->
      if (file.name.lowercase().endsWith(".apk")) {
        val target = File(extractDir, "${idx}_${file.name.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")}")
        file.copyTo(target, overwrite = true)
        copiedApks.add(target)
      } else {
        // What if one of the selected files is a zip/apks/xapk? Extract its apks too!
        extractZipApksAndObbs(file, extractDir, copiedApks, mutableListOf())
      }
    }

    return buildArchiveInfoFromExtractedFiles(
      context = context,
      uri = null,
      extractDir = extractDir,
      fileName = "複数APKバンドル (${copiedApks.size}個)",
      format = ArchiveFormat.MULTI_APK_DIR,
      apkFiles = copiedApks,
      obbFiles = emptyList(),
      manifestJson = null,
      totalBytes = copiedApks.sumOf { it.length() }
    )
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
          name = file.name,
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
    fileName: String
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

    if (outFile.name.endsWith(".tar", ignoreCase = true)) {
      return parseTarPackage(context, uri, outFile, fileName, ArchiveFormat.APKS_TAR_GZ)
    }
    val info = parsePackage(context, uri, outFile)
    return info.copy(fileName = fileName, format = ArchiveFormat.APKS_GZIP)
  }

  private fun parseTarPackage(
    context: Context,
    uri: Uri?,
    tarOrGzFile: File,
    fileName: String,
    format: ArchiveFormat
  ): ApkArchiveInfo {
    val extractDir = File(context.cacheDir, "extracted_tar_${System.currentTimeMillis()}")
    extractDir.mkdirs()

    val apkFiles = mutableListOf<File>()
    val obbFiles = mutableListOf<File>()
    var manifestJson: JSONObject? = null

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
        extractTarStream(input, extractDir, apkFiles, obbFiles) { json ->
          manifestJson = json
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "TAR extraction error", e)
    }

    return buildArchiveInfoFromExtractedFiles(
      context = context,
      uri = uri,
      extractDir = extractDir,
      fileName = fileName,
      format = format,
      apkFiles = apkFiles,
      obbFiles = obbFiles,
      manifestJson = manifestJson,
      totalBytes = tarOrGzFile.length()
    )
  }

  private fun extractTarStream(
    input: InputStream,
    extractDir: File,
    apkFiles: MutableList<File>,
    obbFiles: MutableList<File>,
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

      // Check empty block (end of tar)
      if (header.all { it == 0.toByte() }) break

      val nameBytes = header.copyOfRange(0, 100)
      val nameLen = nameBytes.indexOfFirst { it == 0.toByte() }.let { if (it == -1) 100 else it }
      val entryName = String(nameBytes, 0, nameLen, Charsets.UTF_8).trim()

      // Size in octal at offset 124, length 12
      val sizeStr = String(header, 124, 12, Charsets.US_ASCII)
        .trim { it <= ' ' || it == '\u0000' }
      val entrySize = sizeStr.toLongOrNull(8) ?: 0L
      val typeFlag = header[156].toInt().toChar()

      if (entrySize > 0 && (typeFlag == '0' || typeFlag == '\u0000')) {
        val baseName = File(entryName).name.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
        val uniqueName = "${index}_$baseName"
        index++

        if (baseName.equals("manifest.json", ignoreCase = true) ||
          baseName.equals("info.json", ignoreCase = true) ||
          baseName.equals("omni_meta.json", ignoreCase = true)
        ) {
          val bytes = ByteArray(entrySize.toInt())
          readFully(input, bytes)
          try {
            onManifest(JSONObject(String(bytes, Charsets.UTF_8)))
          } catch (_: Exception) {}
        } else if (baseName.endsWith(".apk", ignoreCase = true)) {
          val outFile = File(extractDir, uniqueName)
          copyFixedBytes(input, outFile, entrySize)
          apkFiles.add(outFile)
        } else if (baseName.endsWith(".obb", ignoreCase = true)) {
          val outFile = File(extractDir, baseName)
          copyFixedBytes(input, outFile, entrySize)
          obbFiles.add(outFile)
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

  private fun extractZipApksAndObbs(
    zipFile: File,
    extractDir: File,
    apkFiles: MutableList<File>,
    obbFiles: MutableList<File>
  ): JSONObject? {
    var manifestJson: JSONObject? = null
    var entryIndex = apkFiles.size
    try {
      zipFile.inputStream().buffered().use { fis ->
        ZipInputStream(fis).use { zis ->
          var entry: ZipEntry? = zis.nextEntry
          while (entry != null) {
            val entryName = entry.name
            if (!entry.isDirectory) {
              val cleanFileName = File(entryName).name.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
              when {
                cleanFileName.equals("manifest.json", ignoreCase = true) ||
                  cleanFileName.equals("info.json", ignoreCase = true) ||
                  cleanFileName.equals("omni_meta.json", ignoreCase = true) -> {
                  val content = zis.readBytes().toString(Charsets.UTF_8)
                  manifestJson = try { JSONObject(content) } catch (_: Exception) { manifestJson }
                }
                cleanFileName.endsWith(".apk", ignoreCase = true) -> {
                  val uniqueName = "${entryIndex}_$cleanFileName"
                  entryIndex++
                  val outFile = File(extractDir, uniqueName)
                  FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                  if (outFile.length() > 0) {
                    apkFiles.add(outFile)
                  }
                }
                cleanFileName.endsWith(".obb", ignoreCase = true) -> {
                  val outFile = File(extractDir, cleanFileName)
                  FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                  obbFiles.add(outFile)
                }
                cleanFileName.endsWith(".apks", ignoreCase = true) ||
                  cleanFileName.endsWith(".xapk", ignoreCase = true) ||
                  cleanFileName.endsWith(".zip", ignoreCase = true) -> {
                  // Nested bundle inside a zip!
                  val nestedZip = File(extractDir, "nested_${entryIndex}_$cleanFileName")
                  entryIndex++
                  FileOutputStream(nestedZip).use { fos -> zis.copyTo(fos) }
                  val nestedManifest = extractZipApksAndObbs(nestedZip, extractDir, apkFiles, obbFiles)
                  if (manifestJson == null && nestedManifest != null) {
                    manifestJson = nestedManifest
                  }
                  nestedZip.delete()
                }
              }
            }
            zis.closeEntry()
            entry = zis.nextEntry
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "extractZipApksAndObbs error", e)
    }
    return manifestJson
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
    val manifestJson = extractZipApksAndObbs(archiveFile, extractDir, apkFiles, obbFiles)

    // If no inner .apk files were found, maybe the file itself is an APK renamed to .zip / .omni / .bin
    if (apkFiles.isEmpty()) {
      val pm = context.packageManager
      val directInfo = getArchiveInfoCompat(pm, archiveFile.absolutePath, 0)
      if (directInfo != null) {
        return parseSingleApk(context, uri, archiveFile, fileName, format)
      }
    }

    return buildArchiveInfoFromExtractedFiles(
      context = context,
      uri = uri,
      extractDir = extractDir,
      fileName = fileName,
      format = format,
      apkFiles = apkFiles,
      obbFiles = obbFiles,
      manifestJson = manifestJson,
      totalBytes = archiveFile.length()
    )
  }

  private fun buildArchiveInfoFromExtractedFiles(
    context: Context,
    uri: Uri?,
    extractDir: File,
    fileName: String,
    format: ArchiveFormat,
    apkFiles: List<File>,
    obbFiles: List<File>,
    manifestJson: JSONObject?,
    totalBytes: Long
  ): ApkArchiveInfo {
    val pm = context.packageManager

    // Accurately detect the Base APK vs Config Split APKs:
    // Base APK has NO splitName (or its name is base.apk / main package apk) and has applicationInfo label/icon
    var baseApkFile: File? = null
    val parsedApkMeta = mutableMapOf<File, PackageInfo?>()

    for (apk in apkFiles) {
      val pi = getArchiveInfoCompat(pm, apk.absolutePath, PackageManager.GET_ACTIVITIES)
      parsedApkMeta[apk] = pi
    }

    // 1. Look for an APK where PackageInfo is non-null AND splitNames/split status indicates base
    baseApkFile = apkFiles.find { apk ->
      val clean = apk.name.substringAfter("_").lowercase()
      clean == "base.apk" || clean == "base-master.apk" || clean == "main.apk"
    }

    if (baseApkFile == null) {
      // Find APK that is NOT a config split
      baseApkFile = apkFiles.filter { apk ->
        val clean = apk.name.substringAfter("_").lowercase()
        !clean.startsWith("split_") && !clean.startsWith("config.")
      }.maxByOrNull { it.length() }
    }

    if (baseApkFile == null && apkFiles.isNotEmpty()) {
      baseApkFile = apkFiles.maxByOrNull { it.length() }
    }

    // Filter out incompatible ABI splits if multiple conflicting ABIs exist in an unpruned bundle
    val supportedAbis = Build.SUPPORTED_ABIS.map { it.lowercase() }
    val allKnownAbiTags = listOf(
      "arm64_v8a" to "arm64-v8a",
      "arm64-v8a" to "arm64-v8a",
      "armeabi_v7a" to "armeabi-v7a",
      "armeabi-v7a" to "armeabi-v7a",
      "x86_64" to "x86_64",
      "x86" to "x86"
    )

    val filteredApks = apkFiles.filter { apk ->
      if (apk == baseApkFile) return@filter true
      val lowerName = apk.name.lowercase()
      var matchedAbi: String? = null
      for ((tag, canonicalAbi) in allKnownAbiTags) {
        if (lowerName.contains(tag)) {
          // Avoid matching "x86" when it's actually "x86_64"
          if (tag == "x86" && lowerName.contains("x86_64")) continue
          matchedAbi = canonicalAbi
          break
        }
      }
      if (matchedAbi != null) {
        supportedAbis.contains(matchedAbi)
      } else {
        true
      }
    }.ifEmpty { apkFiles }

    var pkgName = manifestJson?.optString("package_name")
      ?.ifBlank { manifestJson.optString("pname") }
      ?.ifBlank { manifestJson.optString("packageName") }
      ?: ""
    var versionName = manifestJson?.optString("version_name")
      ?.ifBlank { manifestJson.optString("versionName") }
      ?: ""
    var versionCode = manifestJson?.optLong("version_code", 0L)
      ?.takeIf { it > 0L }
      ?: manifestJson?.optLong("versionCode", 1L)
      ?: 1L
    var minSdk = manifestJson?.optInt("min_sdk_version", 21) ?: 21
    var targetSdk = manifestJson?.optInt("target_sdk_version", 34) ?: 34
    var label = manifestJson?.optString("name")
      ?.ifBlank { manifestJson.optString("app_name") }
      ?.ifBlank { manifestJson.optString("appLabel") }
      ?: ""
    var iconBitmap: Bitmap? = null

    if (baseApkFile != null) {
      val pi = parsedApkMeta[baseApkFile] ?: getArchiveInfoCompat(pm, baseApkFile.absolutePath, PackageManager.GET_ACTIVITIES)
      if (pi != null) {
        val appInfo = pi.applicationInfo ?: ApplicationInfo()
        appInfo.sourceDir = baseApkFile.absolutePath
        appInfo.publicSourceDir = baseApkFile.absolutePath

        if (pkgName.isBlank()) pkgName = pi.packageName
        if (versionName.isBlank()) versionName = pi.versionName ?: "1.0"
        if (versionCode <= 1L) {
          versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.longVersionCode
          } else {
            @Suppress("DEPRECATION")
            pi.versionCode.toLong()
          }
        }
        if (label.isBlank()) {
          label = try { appInfo.loadLabel(pm).toString() } catch (_: Exception) { "" }
        }
        iconBitmap = try { drawableToBitmap(appInfo.loadIcon(pm)) } catch (_: Exception) { null }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && minSdk == 21) {
          minSdk = appInfo.minSdkVersion
        }
        if (targetSdk == 34) targetSdk = appInfo.targetSdkVersion
      }
    }

    if (pkgName.isBlank()) pkgName = "com.bundle.app"
    if (label.isBlank()) label = fileName.substringBeforeLast(".")
    if (versionName.isBlank()) versionName = "1.0"

    // Ensure Base APK is always first in the list of splits!
    val sortedApks = filteredApks.sortedByDescending { it == baseApkFile }

    val splitEntries = sortedApks.map { apk ->
      val isBase = apk == baseApkFile
      val cleanDisplayName = apk.name.substringAfter("_")
      val desc = when {
        isBase -> "ベースモジュール (Base APK)"
        cleanDisplayName.contains("arm64") || cleanDisplayName.contains("v8a") -> "ABI ネイティブライブラリ (arm64-v8a)"
        cleanDisplayName.contains("armeabi") || cleanDisplayName.contains("v7a") -> "ABI ネイティブライブラリ (armeabi-v7a)"
        cleanDisplayName.contains("x86_64") -> "ABI ネイティブライブラリ (x86_64)"
        cleanDisplayName.contains("x86") -> "ABI ネイティブライブラリ (x86)"
        cleanDisplayName.contains("dpi") -> "画面密度リソース (${cleanDisplayName})"
        cleanDisplayName.contains("ja") || cleanDisplayName.contains("en") -> "言語ローカライズ (${cleanDisplayName})"
        else -> "スプリットモジュール ($cleanDisplayName)"
      }
      SplitEntry(
        name = apk.name,
        sizeBytes = apk.length(),
        isBase = isBase,
        description = desc,
        filePath = apk.absolutePath
      )
    }

    val obbEntries = obbFiles.map { obb ->
      ObbEntry(
        fileName = obb.name,
        relativePath = "Android/obb/$pkgName/${obb.name}",
        sizeBytes = obb.length()
      )
    }

    val sumSplitBytes = sortedApks.sumOf { it.length() } + obbFiles.sumOf { it.length() }

    return ApkArchiveInfo(
      uri = uri,
      filePath = extractDir.absolutePath,
      fileName = fileName,
      format = format,
      packageName = pkgName,
      appLabel = label,
      versionName = versionName,
      versionCode = versionCode,
      minSdk = minSdk,
      targetSdk = targetSdk,
      iconBitmap = iconBitmap,
      totalSizeBytes = if (sumSplitBytes > 0) sumSplitBytes else totalBytes,
      splits = splitEntries,
      obbEntries = obbEntries,
      isCompatible = minSdk <= Build.VERSION.SDK_INT
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
        input.copyTo(output)
      }
    }
    return tempFile
  }
}
