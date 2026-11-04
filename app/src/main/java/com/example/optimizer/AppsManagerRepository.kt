package com.example.optimizer

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.installer.RootHelper
import com.example.model.AppPermissionItem
import com.example.model.DeviceOptimizationState
import com.example.model.InstalledAppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppsManagerRepository {

  private const val TAG = "AppsManagerRepository"

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
      val label = appInfo.loadLabel(pm).toString().ifBlank { pi.packageName }

      val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pi.longVersionCode
      } else {
        @Suppress("DEPRECATION")
        pi.versionCode.toLong()
      }

      val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        appInfo.minSdkVersion
      } else 21

      val splits = appInfo.splitSourceDirs?.map { File(it).name } ?: emptyList()
      val sourceFile = File(appInfo.sourceDir)
      val totalSize = sourceFile.length() + (appInfo.splitSourceDirs?.sumOf { File(it).length() } ?: 0L)

      val permissionsList = mutableListOf<AppPermissionItem>()
      val reqPerms = pi.requestedPermissions
      val reqFlags = pi.requestedPermissionsFlags

      if (reqPerms != null) {
        for (i in reqPerms.indices) {
          val pName = reqPerms[i]
          val isGranted = if (reqFlags != null && i < reqFlags.size) {
            (reqFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
          } else false

          val group = classifyPermission(pName)
          val isDangerous = group != "通常権限"

          permissionsList.add(
            AppPermissionItem(
              name = pName.substringAfterLast("."),
              isGranted = isGranted,
              isDangerous = isDangerous,
              group = group
            )
          )
        }
      }

      list.add(
        InstalledAppItem(
          packageName = pi.packageName,
          appLabel = label,
          versionName = pi.versionName ?: "1.0",
          versionCode = vCode,
          minSdk = minSdk,
          targetSdk = appInfo.targetSdkVersion,
          isSystemApp = isSystem,
          isSplitApk = splits.isNotEmpty(),
          splitNames = splits,
          apkSizeBytes = totalSize,
          sourceDir = appInfo.sourceDir,
          installTime = pi.firstInstallTime,
          updateTime = pi.lastUpdateTime,
          permissions = permissionsList
        )
      )
    }

    // Sort: user apps first, then alphabetically
    list.sortedWith(compareBy({ it.isSystemApp }, { it.appLabel.lowercase() }))
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

    // Calculate cache size
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

    // Clean internal cache
    context.cacheDir.listFiles()?.forEach { file ->
      deletedBytes += file.walkTopDown().sumOf { it.length() }
      file.deleteRecursively()
    }

    // Clean external cache
    context.externalCacheDir?.listFiles()?.forEach { file ->
      deletedBytes += file.walkTopDown().sumOf { it.length() }
      file.deleteRecursively()
    }

    // If rooted, also run trim-caches
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

  fun requestUninstall(context: Context, packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE).apply {
      data = Uri.parse("package:$packageName")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }

  fun openAppDetails(context: Context, packageName: String) {
    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.parse("package:$packageName")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }
}
