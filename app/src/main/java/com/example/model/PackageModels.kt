package com.example.model

import android.graphics.Bitmap
import android.net.Uri

enum class ArchiveFormat(val extension: String, val displayName: String) {
  APK("apk", "単体APK (Single APK)"),
  SPLIT_APKS("apks", "分割APK (Split APKs)"),
  XAPK("xapk", "XAPK パッケージ (OBB同梱)"),
  APKM("apkm", "APKM バンドル"),
  BUNDLE_ZIP("zip", "ZIP/バンドル (Bundle)")
}

data class SplitEntry(
  val name: String,
  val sizeBytes: Long,
  val isBase: Boolean,
  val description: String
)

data class ObbEntry(
  val fileName: String,
  val relativePath: String,
  val sizeBytes: Long
)

data class ApkArchiveInfo(
  val uri: Uri?,
  val filePath: String,
  val fileName: String,
  val format: ArchiveFormat,
  val packageName: String,
  val appLabel: String,
  val versionName: String,
  val versionCode: Long,
  val minSdk: Int,
  val targetSdk: Int,
  val iconBitmap: Bitmap?,
  val totalSizeBytes: Long,
  val splits: List<SplitEntry>,
  val obbEntries: List<ObbEntry>,
  val isCompatible: Boolean = true,
  val warningMessage: String? = null
)

data class AppPermissionItem(
  val name: String,
  val isGranted: Boolean,
  val isDangerous: Boolean,
  val group: String
)

data class InstalledAppItem(
  val packageName: String,
  val appLabel: String,
  val versionName: String,
  val versionCode: Long,
  val minSdk: Int,
  val targetSdk: Int,
  val isSystemApp: Boolean,
  val isSplitApk: Boolean,
  val splitNames: List<String>,
  val apkSizeBytes: Long,
  val sourceDir: String,
  val installTime: Long,
  val updateTime: Long,
  val permissions: List<AppPermissionItem> = emptyList()
)

data class StoreAppItem(
  val id: String,
  val name: String,
  val packageName: String,
  val summary: String,
  val category: String,
  val author: String,
  val iconUrl: String,
  val githubRepo: String? = null,
  val latestVersion: String,
  val downloadUrl: String,
  val sizeBytes: Long,
  val releaseNotes: String,
  val releaseDate: String,
  val isInstalled: Boolean = false,
  val installedVersion: String? = null,
  val isUpdateAvailable: Boolean = false
)

data class BackupArchiveItem(
  val id: String,
  val packageName: String,
  val appLabel: String,
  val versionName: String,
  val versionCode: Long,
  val backupDate: Long,
  val filePath: String,
  val sizeBytes: Long,
  val splitCount: Int,
  val hasObb: Boolean = false
)

enum class InstallStatus {
  IDLE,
  PARSING,
  READY,
  INSTALLING,
  SUCCESS,
  ERROR
}

data class InstallSessionState(
  val status: InstallStatus = InstallStatus.IDLE,
  val progress: Float = 0f,
  val currentTask: String = "",
  val errorDetails: String? = null,
  val packageName: String? = null,
  val archiveInfo: ApkArchiveInfo? = null,
  val isRootInstall: Boolean = false
)

data class DeviceOptimizationState(
  val isRooted: Boolean = false,
  val suBinaryPath: String? = null,
  val suVersion: String? = null,
  val isShizukuAvailable: Boolean = false,
  val totalCachedBytes: Long = 0L,
  val lastOptimizationAction: String? = null,
  val isOptimizing: Boolean = false
)

data class RemoteServerStatus(
  val isRunning: Boolean = false,
  val port: Int = 8888,
  val ipAddress: String = "",
  val serverUrl: String = "",
  val uploadedFilesCount: Int = 0,
  val logs: List<String> = emptyList()
)
