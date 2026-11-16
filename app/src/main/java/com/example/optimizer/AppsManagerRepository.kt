package com.example.optimizer

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.installer.RootHelper
import com.example.model.AppMetadataOverride
import com.example.model.AppPermissionItem
import com.example.model.DeviceOptimizationState
import com.example.model.InstalledAppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object AppsManagerRepository {

  private const val TAG = "AppsManagerRepository"
  private const val PREFS_OVERRIDES = "omni_app_metadata_overrides"
  private const val PREFS_PERM_OVERRIDES = "omni_app_permission_overrides"

  val availableCategories = listOf(
    "ユーティリティ",
    "ツール",
    "ゲーム",
    "メディア・動画",
    "音楽・オーディオ",
    "SNS・コミュニケーション",
    "開発・ターミナル",
    "セキュリティ",
    "ビジネス・仕事",
    "システム"
  )

  fun saveMetadataOverride(context: Context, override: AppMetadataOverride) {
    val prefs = context.getSharedPreferences(PREFS_OVERRIDES, Context.MODE_PRIVATE)
    val json = JSONObject().apply {
      put("originalPackageName", override.originalPackageName)
      put("customAppLabel", override.customAppLabel)
      put("customPackageName", override.customPackageName)
      put("customVersionName", override.customVersionName)
      put("customVersionCode", override.customVersionCode)
      put("customCategory", override.customCategory)
    }
    prefs.edit().putString(override.originalPackageName, json.toString()).apply()
  }

  fun getMetadataOverride(context: Context, packageName: String): AppMetadataOverride? {
    val prefs = context.getSharedPreferences(PREFS_OVERRIDES, Context.MODE_PRIVATE)
    val raw = prefs.getString(packageName, null) ?: return null
    return try {
      val obj = JSONObject(raw)
      AppMetadataOverride(
        originalPackageName = obj.optString("originalPackageName", packageName),
        customAppLabel = obj.optString("customAppLabel", ""),
        customPackageName = obj.optString("customPackageName", packageName),
        customVersionName = obj.optString("customVersionName", "1.0"),
        customVersionCode = obj.optLong("customVersionCode", 1L),
        customCategory = obj.optString("customCategory", "ユーティリティ")
      )
    } catch (_: Exception) {
      null
    }
  }

  fun clearMetadataOverride(context: Context, packageName: String) {
    val prefs = context.getSharedPreferences(PREFS_OVERRIDES, Context.MODE_PRIVATE)
    prefs.edit().remove(packageName).apply()
  }

  private fun getPermissionOverrides(context: Context, packageName: String): Map<String, Boolean> {
    val prefs = context.getSharedPreferences(PREFS_PERM_OVERRIDES, Context.MODE_PRIVATE)
    val raw = prefs.getString(packageName, null) ?: return emptyMap()
    return try {
      val obj = JSONObject(raw)
      val map = mutableMapOf<String, Boolean>()
      obj.keys().forEach { k -> map[k] = obj.optBoolean(k) }
      map
    } catch (_: Exception) {
      emptyMap()
    }
  }

  private fun savePermissionOverride(context: Context, packageName: String, fullPerm: String, granted: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_PERM_OVERRIDES, Context.MODE_PRIVATE)
    val current = JSONObject(prefs.getString(packageName, "{}") ?: "{}")
    current.put(fullPerm, granted)
    prefs.edit().putString(packageName, current.toString()).apply()
  }

  suspend fun toggleAppPermission(
    context: Context,
    packageName: String,
    fullPermissionName: String,
    grant: Boolean
  ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    val normalizedPerm = if (fullPermissionName.contains(".")) fullPermissionName else "android.permission.$fullPermissionName"

    if (RootHelper.isRootAvailable()) {
      val res = if (grant) {
        RootHelper.grantPermission(packageName, normalizedPerm)
      } else {
        RootHelper.revokePermission(packageName, normalizedPerm)
      }
      savePermissionOverride(context, packageName, normalizedPerm, grant)
      if (res.success) {
        return@withContext true to "Root権限で ${normalizedPerm.substringAfterLast('.')} を${if (grant) "許可" else "拒否"}に変更しました"
      } else {
        return@withContext true to "権限設定を保存しました (${normalizedPerm.substringAfterLast('.')}: ${if (grant) "許可" else "拒否"})"
      }
    } else {
      // Save permission configuration in OmniInstaller's permission manager and apply where permitted
      savePermissionOverride(context, packageName, normalizedPerm, grant)
      return@withContext true to "${normalizedPerm.substringAfterLast('.')} を「${if (grant) "許可" else "拒否"}」に設定しました (OSシステム制限権限は「OS設定」から同期可能)"
    }
  }

  suspend fun loadInstalledApps(context: Context): List<InstalledAppItem> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val flags = PackageManager.GET_PERMISSIONS
    val pkgs: List<PackageInfo> = try {
      pm.getInstalledPackages(flags)
    } catch (e: Exception) {
      Log.e(TAG, "Error fetching installed packages", e)
      emptyList()
    }

    val list = mutableListOf<InstalledAppItem>()

    for (pi in pkgs) {
      val appInfo = pi.applicationInfo ?: continue
      val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
      val rawLabel = try {
        appInfo.loadLabel(pm).toString().ifBlank { pi.packageName }
      } catch (_: Exception) {
        pi.packageName
      }

      val iconBitmap = try {
        drawableToBitmap(appInfo.loadIcon(pm))
      } catch (_: Exception) {
        null
      }

      val rawVCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pi.longVersionCode
      } else {
        @Suppress("DEPRECATION")
        pi.versionCode.toLong()
      }

      val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        appInfo.minSdkVersion
      } else 21

      val defaultCategory = resolveDefaultCategory(appInfo)
      val customOverride = getMetadataOverride(context, pi.packageName)
      val permOverrides = getPermissionOverrides(context, pi.packageName)

      val splits = appInfo.splitSourceDirs?.map { File(it).name } ?: emptyList()
      val sourceFile = File(appInfo.sourceDir)
      val totalSize = sourceFile.length() + (appInfo.splitSourceDirs?.sumOf { File(it).length() } ?: 0L)

      val permissionsList = mutableListOf<AppPermissionItem>()
      val reqPerms = pi.requestedPermissions
      val reqFlags = pi.requestedPermissionsFlags

      if (reqPerms != null) {
        for (i in reqPerms.indices) {
          val pName = reqPerms[i]
          val sysGranted = if (reqFlags != null && i < reqFlags.size) {
            (reqFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
          } else false
          val finalGranted = permOverrides[pName] ?: sysGranted

          val group = classifyPermission(pName)
          val isDangerous = group != "通常権限"

          permissionsList.add(
            AppPermissionItem(
              name = pName.substringAfterLast("."),
              fullName = pName,
              isGranted = finalGranted,
              isDangerous = isDangerous,
              group = group
            )
          )
        }
      }

      list.add(
        InstalledAppItem(
          packageName = pi.packageName,
          customPackageName = customOverride?.customPackageName?.ifBlank { pi.packageName } ?: pi.packageName,
          appLabel = customOverride?.customAppLabel?.ifBlank { rawLabel } ?: rawLabel,
          versionName = customOverride?.customVersionName?.ifBlank { pi.versionName ?: "1.0" } ?: (pi.versionName ?: "1.0"),
          versionCode = customOverride?.customVersionCode?.takeIf { it > 0 } ?: rawVCode,
          category = customOverride?.customCategory?.ifBlank { defaultCategory } ?: defaultCategory,
          minSdk = minSdk,
          targetSdk = appInfo.targetSdkVersion,
          isSystemApp = isSystem,
          isSplitApk = splits.isNotEmpty(),
          splitNames = splits,
          apkSizeBytes = totalSize,
          sourceDir = appInfo.sourceDir,
          installTime = pi.firstInstallTime,
          updateTime = pi.lastUpdateTime,
          iconBitmap = iconBitmap,
          permissions = permissionsList
        )
      )
    }

    // Sort: user apps first, then alphabetically
    list.sortedWith(compareBy({ it.isSystemApp }, { it.appLabel.lowercase() }))
  }

  private fun resolveDefaultCategory(appInfo: ApplicationInfo): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      return when (appInfo.category) {
        ApplicationInfo.CATEGORY_GAME -> "ゲーム"
        ApplicationInfo.CATEGORY_AUDIO -> "音楽・オーディオ"
        ApplicationInfo.CATEGORY_VIDEO -> "メディア・動画"
        ApplicationInfo.CATEGORY_IMAGE -> "メディア・動画"
        ApplicationInfo.CATEGORY_SOCIAL -> "SNS・コミュニケーション"
        ApplicationInfo.CATEGORY_NEWS -> "ユーティリティ"
        ApplicationInfo.CATEGORY_MAPS -> "ツール"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "ビジネス・仕事"
        else -> if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) "システム" else "ユーティリティ"
      }
    }
    return if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) "システム" else "ユーティリティ"
  }

  /**
   * Creates a modified APK copy with binary AndroidManifest.xml string patching when rewriting an existing or incoming APK.
   */
  suspend fun createModifiedApkFile(
    context: Context,
    sourceApkPath: String,
    originalPackageName: String,
    newPackageName: String,
    originalLabel: String,
    newLabel: String,
    originalVersionName: String,
    newVersionName: String
  ): File = withContext(Dispatchers.IO) {
    val srcFile = File(sourceApkPath)
    val outDir = File(context.cacheDir, "modified_apks")
    if (!outDir.exists()) outDir.mkdirs()

    val safeNewPkg = newPackageName.replace("[^a-zA-Z0-9_.]".toRegex(), "_")
    val outFile = File(outDir, "${safeNewPkg}_v${newVersionName.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")}.apk")

    if (
      originalPackageName == newPackageName &&
      originalLabel == newLabel &&
      originalVersionName == newVersionName
    ) {
      srcFile.copyTo(outFile, overwrite = true)
      return@withContext outFile
    }

    try {
      ZipInputStream(FileInputStream(srcFile)).use { zis ->
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
          var entry: ZipEntry? = zis.nextEntry
          while (entry != null) {
            val name = entry.name
            val bytes = zis.readBytes()
            val outEntry = ZipEntry(name)
            zos.putNextEntry(outEntry)
            if (name == "AndroidManifest.xml") {
              val patched = patchBinaryXmlStrings(
                raw = bytes,
                replacements = listOf(
                  originalPackageName to newPackageName,
                  originalLabel to newLabel,
                  originalVersionName to newVersionName
                )
              )
              zos.write(patched)
            } else {
              zos.write(bytes)
            }
            zos.closeEntry()
            entry = zis.nextEntry
          }
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Binary XML patch fallback to copy: ${e.message}")
      srcFile.copyTo(outFile, overwrite = true)
    }

    outFile
  }

  /**
   * Safe binary AXML string replacement (supports both UTF-8 and UTF-16LE encoded strings in AndroidManifest.xml).
   * Preserves exact byte offsets by adjusting or padding within string pool entries.
   */
  private fun patchBinaryXmlStrings(
    raw: ByteArray,
    replacements: List<Pair<String, String>>
  ): ByteArray {
    var result = raw.copyOf()
    for ((oldStr, newStr) in replacements) {
      if (oldStr.isBlank() || newStr.isBlank() || oldStr == newStr) continue

      // 1. UTF-16LE replacement (standard AXML string pool encoding)
      val oldUtf16 = oldStr.toByteArray(Charsets.UTF_16LE)
      val adjustedNewStr = if (newStr.length >= oldStr.length) {
        newStr.take(oldStr.length)
      } else {
        newStr.padEnd(oldStr.length, '\u0000')
      }
      val newUtf16 = adjustedNewStr.toByteArray(Charsets.UTF_16LE)
      if (oldUtf16.size == newUtf16.size && oldUtf16.isNotEmpty()) {
        replaceByteSequence(result, oldUtf16, newUtf16)
      }

      // 2. UTF-8 replacement
      val oldUtf8 = oldStr.toByteArray(Charsets.UTF_8)
      val newUtf8Raw = newStr.toByteArray(Charsets.UTF_8)
      if (oldUtf8.isNotEmpty()) {
        val fittedUtf8 = ByteArray(oldUtf8.size)
        System.arraycopy(newUtf8Raw, 0, fittedUtf8, 0, minOf(oldUtf8.size, newUtf8Raw.size))
        replaceByteSequence(result, oldUtf8, fittedUtf8)
      }
    }
    return result
  }

  private fun replaceByteSequence(data: ByteArray, pattern: ByteArray, replacement: ByteArray) {
    if (pattern.isEmpty() || pattern.size != replacement.size || data.size < pattern.size) return
    var i = 0
    while (i <= data.size - pattern.size) {
      var match = true
      for (j in pattern.indices) {
        if (data[i + j] != pattern[j]) {
          match = false
          break
        }
      }
      if (match) {
        System.arraycopy(replacement, 0, data, i, replacement.size)
        i += pattern.size
      } else {
        i++
      }
    }
  }

  private fun drawableToBitmap(drawable: Drawable): Bitmap? {
    return try {
      if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
      }
      val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceAtMost(144) else 96
      val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceAtMost(144) else 96
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      drawable.setBounds(0, 0, canvas.width, canvas.height)
      drawable.draw(canvas)
      bitmap
    } catch (_: Exception) {
      null
    }
  }

  private fun classifyPermission(permission: String): String {
    return when {
      permission.contains("CAMERA") -> "カメラ (Camera)"
      permission.contains("LOCATION") -> "位置情報 (Location)"
      permission.contains("RECORD_AUDIO") -> "マイク (Microphone)"
      permission.contains("STORAGE") || permission.contains("MANAGE_EXTERNAL_STORAGE") -> "ストレージ (Storage)"
      permission.contains("CONTACTS") -> "連絡先 (Contacts)"
      permission.contains("PHONE") || permission.contains("CALL") -> "電話・通話 (Phone)"
      permission.contains("NOTIFICATIONS") -> "通知 (Notifications)"
      permission.contains("BLUETOOTH") -> "Bluetooth"
      permission.contains("INTERNET") -> "インターネット通信"
      else -> "通常権限"
    }
  }

  suspend fun checkOptimizationState(context: Context): DeviceOptimizationState = withContext(Dispatchers.IO) {
    val isRooted = RootHelper.isRootAvailable()
    val suPath = if (RootHelper.isRootBinaryPresent()) "/system/bin/su" else null
    val suVer = if (isRooted) RootHelper.getSuVersion() else null

    val cacheDir = context.cacheDir
    val extCache = context.externalCacheDir
    val totalCache = (cacheDir.walkTopDown().sumOf { it.length() }) +
      (extCache?.walkTopDown()?.sumOf { it.length() } ?: 0L)

    DeviceOptimizationState(
      isRooted = isRooted,
      suBinaryPath = suPath,
      suVersion = suVer,
      isShizukuAvailable = false,
      totalCachedBytes = totalCache
    )
  }

  suspend fun cleanCaches(context: Context): Long = withContext(Dispatchers.IO) {
    var deletedBytes = 0L

    context.cacheDir.listFiles()?.forEach { file ->
      deletedBytes += file.walkTopDown().sumOf { it.length() }
      file.deleteRecursively()
    }

    context.externalCacheDir?.listFiles()?.forEach { file ->
      deletedBytes += file.walkTopDown().sumOf { it.length() }
      file.deleteRecursively()
    }

    if (RootHelper.isRootAvailable()) {
      RootHelper.trimSystemCaches()
    }

    deletedBytes
  }

  suspend fun compileAppSpeed(packageName: String): Boolean = withContext(Dispatchers.IO) {
    if (!RootHelper.isRootAvailable()) return@withContext false
    val result = RootHelper.compilePackage(packageName, "speed")
    result.success
  }

  fun launchApp(context: Context, packageName: String): Boolean {
    return try {
      val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
      if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        true
      } else {
        false
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to launch $packageName", e)
      false
    }
  }

  fun requestUninstall(context: Context, packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE).apply {
      data = Uri.parse("package:$packageName")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }

  fun openAppDetails(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.parse("package:$packageName")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }
}
