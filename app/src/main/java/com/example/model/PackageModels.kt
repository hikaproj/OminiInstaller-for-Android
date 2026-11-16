package com.example.model

import android.graphics.Bitmap
import android.net.Uri

enum class ArchiveFormat(
  val extension: String,
  val displayName: String,
  val category: String = "Standard"
) {
  APK("apk", "単体APK (.apk)", "APK"),
  SPLIT_APKS("apks", "分割APK (.apks)", "Split"),
  XAPK("xapk", "XAPK パッケージ (.xapk)", "XAPK"),
  APKM("apkm", "APKM バンドル (.apkm)", "Split"),
  AAB("aab", "Android App Bundle (.aab)", "Bundle"),
  BUNDLE_ZIP("zip", "ZIP アーカイブ (.zip)", "Archive"),
  APEX("apex", "Android Pony Express (.apex)", "System"),
  APKS_TAR("tar", "TAR アーカイブ (.tar)", "Archive"),
  APKS_TAR_GZ("tar.gz", "TAR.GZ 圧縮アーカイブ (.tar.gz)", "Archive"),
  APKS_TGZ("tgz", "TGZ 圧縮アーカイブ (.tgz)", "Archive"),
  APKS_GZIP("apk.gz", "GZIP 圧縮APK (.apk.gz)", "Archive"),
  JAR_APK("jar", "JAR コンテナ (.jar)", "Archive"),
  OBB_ZIP("obb.zip", "OBB同梱ZIP (.obb.zip)", "XAPK"),
  MULTI_APK_DIR("apks", "複数APK一括バンドル (Multi-APK)", "Split"),
  BACKUP_AB("ab", "Android Backup (.ab)", "Backup"),
  UNKNOWN("unknown", "不明な形式", "Unknown");

  val primaryExtension: String
    get() = extension

  companion object {
    fun fromExtension(fileName: String): ArchiveFormat {
      val lower = fileName.lowercase().trim()
      return when {
        lower.endsWith(".tar.gz") -> APKS_TAR_GZ
        lower.endsWith(".apk.gz") || lower.endsWith(".gz") -> APKS_GZIP
        lower.endsWith(".obb.zip") -> OBB_ZIP
        lower.endsWith(".xapk") -> XAPK
        lower.endsWith(".apks") -> SPLIT_APKS
        lower.endsWith(".apkm") -> APKM
        lower.endsWith(".aab") -> AAB
        lower.endsWith(".apex") -> APEX
        lower.endsWith(".tgz") -> APKS_TGZ
        lower.endsWith(".tar") -> APKS_TAR
        lower.endsWith(".jar") -> JAR_APK
        lower.endsWith(".ab") -> BACKUP_AB
        lower.endsWith(".zip") -> BUNDLE_ZIP
        else -> APK
      }
    }
  }
}

enum class BackupExportFormat(
  val id: String,
  val extension: String,
  val label: String,
  val description: String,
  val isTar: Boolean = false,
  val isGzip: Boolean = false,
  val includeManifest: Boolean = true
) {
  APK("apk", "apk", "標準 .apk", "単体APK形式でそのまま書き出し"),
  APKS("apks", "apks", "分割 .apks (SAI / Bundletool互換)", "Base + 全Split APKを標準APKSコンテナに格納"),
  XAPK("xapk", "xapk", "標準 .xapk (Manifest+OBB)", "manifest.json と分割APK・OBBを同梱"),
  APKM("apkm", "apkm", "バンドル .apkm", "info.json メタデータ付きの分割APKアーカイブ"),
  ZIP("zip", "zip", "標準 .zip アーカイブ", "PCや標準解凍ツールで展開可能なZIP形式"),
  TAR("tar", "tar", "非圧縮 .tar アーカイブ", "POSIX準拠の高速無圧縮TARストリーム", isTar = true),
  TAR_GZ("tar.gz", "tar.gz", "高圧縮 .tar.gz", "TAR + GZIPによる高圧縮アーカイブ", isTar = true, isGzip = true),
  TGZ("tgz", "tgz", "高圧縮 .tgz", "TAR + GZIP 短縮拡張子アーカイブ", isTar = true, isGzip = true),
  APK_GZ("apk.gz", "apk.gz", "GZIP圧縮 .apk.gz", "APKをGZIPストリームで直接圧縮した省スペース形式", isGzip = true),
  AAB("aab", "aab", "App Bundle .aab", "モジュール構造を保持したバンドルアーカイブ"),
  APEX("apex", "apex", "システムモジュール .apex", "APEX モジュールコンテナ形式"),
  JAR("jar", "jar", "Java Archive .jar", "META-INF互換マニフェスト付きJARコンテナ"),
  AB("ab", "ab", "Android Backup .ab", "Android Backup 互換アーカイブ");

  companion object {
    fun fromFileName(fileName: String): BackupExportFormat {
      val lower = fileName.lowercase().trim()
      return values().sortedByDescending { it.extension.length }.firstOrNull {
        lower.endsWith(".${it.extension}")
      } ?: APK
    }
  }
}

data class SplitEntry(
  val name: String,
  val sizeBytes: Long,
  val isBase: Boolean,
  val description: String,
  val filePath: String = ""
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
  val originalPackageName: String = packageName,
  val appLabel: String,
  val versionName: String,
  val versionCode: Long,
  val category: String = "ユーティリティ",
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
  val fullName: String = if (name.contains(".")) name else "android.permission.$name",
  val isGranted: Boolean,
  val isDangerous: Boolean,
  val group: String
)

data class AppMetadataOverride(
  val originalPackageName: String,
  val customAppLabel: String,
  val customPackageName: String,
  val customVersionName: String,
  val customVersionCode: Long,
  val customCategory: String
)

data class InstalledAppItem(
  val packageName: String,
  val customPackageName: String = packageName,
  val appLabel: String,
  val versionName: String,
  val versionCode: Long,
  val category: String = "ユーティリティ",
  val minSdk: Int,
  val targetSdk: Int,
  val isSystemApp: Boolean,
  val isSplitApk: Boolean,
  val splitNames: List<String>,
  val apkSizeBytes: Long,
  val sourceDir: String,
  val installTime: Long,
  val updateTime: Long,
  val iconBitmap: Bitmap? = null,
  val permissions: List<AppPermissionItem> = emptyList()
)

data class StoreReleaseVersion(
  val versionName: String,
  val downloadUrl: String,
  val sizeBytes: Long,
  val releaseDate: String,
  val changelog: String = "",
  val abi: String = "Universal"
)

data class StoreRepositorySource(
  val id: String,
  val name: String,
  val url: String,
  val description: String = "",
  val appCount: Int = 0,
  val lastSynced: String = "同期済",
  val isOfficial: Boolean = false
)

data class StoreAppItem(
  val id: String,
  val name: String,
  val packageName: String,
  val summary: String,
  val description: String = "",
  val category: String,
  val author: String,
  val iconUrl: String,
  val screenshots: List<String> = emptyList(),
  val githubRepo: String? = null,
  val websiteUrl: String = "",
  val sourceRepoName: String = "Official",
  val license: String = "Open Source",
  val minSdk: Int = 24,
  val architecture: String = "arm64-v8a / universal",
  val latestVersion: String,
  val downloadUrl: String,
  val sizeBytes: Long,
  val releaseNotes: String,
  val releaseDate: String,
  val versions: List<StoreReleaseVersion> = emptyList(),
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
  val formatName: String = "APK/APKS",
  val hasObb: Boolean = false,
  val exportFormat: BackupExportFormat = BackupExportFormat.fromFileName(filePath)
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
  val autoStartOnLaunch: Boolean = false,
  val allowRemoteAppManagement: Boolean = false,
  val allowRemotePermissionEdit: Boolean = false,
  val allowRemoteUrlInstall: Boolean = true,
  val ipAddress: String = "",
  val allIpAddresses: List<String> = emptyList(),
  val serverUrl: String = "",
  val cfTunnelEnabled: Boolean = false,
  val cfTunnelRunning: Boolean = false,
  val cfPublicUrl: String = "",
  val cfTunnelToken: String = "",
  val cfCustomHost: String = "",
  val uploadedFilesCount: Int = 0,
  val logs: List<String> = emptyList()
)

data class DeepLinkConfirmationState(
  val rawUri: String,
  val type: DeepLinkType,
  val repoUrls: List<String> = emptyList(),
  val githubRepos: List<String> = emptyList(),
  val directInstallUrl: String? = null,
  val targetApp: StoreAppItem? = null
)

enum class DeepLinkType {
  ADD_REPOS,
  OPEN_APP_DETAIL,
  DIRECT_INSTALL
}
