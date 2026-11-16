package com.example.model

import android.graphics.Bitmap
import android.net.Uri

enum class ArchiveFormat(
  val extension: String,
  val displayName: String,
  val category: String = "Standard"
) {
  APK("apk", "単体APK (Single APK)", "APK"),
  SPLIT_APKS("apks", "分割APK (SAI / Split APKs)", "Split"),
  XAPK("xapk", "XAPK パッケージ (APKPure / OBB同梱)", "XAPK"),
  APKM("apkm", "APKM バンドル (APKMirror)", "Split"),
  AAB("aab", "Android App Bundle (AAB)", "Bundle"),
  BUNDLE_ZIP("zip", "ZIP アーカイブバンドル", "Archive"),
  OMNI("omni", "OmniInstaller 専用パッケージ (.omni)", "Omni"),
  HPJPKG("hpjpkg", "Hikaproj パッケージ (.hpjpkg)", "Omni"),
  APEX("apex", "Android Pony Express (.apex)", "System"),
  APKS_TAR("tar", "TAR アーカイブ (.tar)", "Archive"),
  APKS_TAR_GZ("tar.gz", "TAR.GZ 圧縮パッケージ (.tar.gz)", "Archive"),
  APKS_TGZ("tgz", "TGZ 圧縮パッケージ (.tgz)", "Archive"),
  APKS_GZIP("apk.gz", "GZIP 圧縮APK (.apk.gz)", "Archive"),
  APK_PLUS("apk+", "APK+ 拡張バンドル (.apk+)", "Split"),
  ZAPK("zapk", "ZAPK 大容量OBBアーカイブ (.zapk)", "XAPK"),
  SAPK("sapk", "SAPK 分割パッケージ (.sapk)", "Split"),
  MAPK("mapk", "MAPK マルチAPK (.mapk)", "Split"),
  BAPK("bapk", "BAPK ベース+分割バンドル (.bapk)", "Split"),
  PACK("pack", "汎用アプリパック (.pack)", "Archive"),
  JAR_APK("jar", "JAR コンテナAPK (.jar)", "Archive"),
  OBB_ZIP("obb.zip", "OBBデータ同梱ZIP (.obb.zip)", "XAPK"),
  MULTI_APK_DIR("dir", "複数APK一括バンドル (Multi-APK)", "Split"),
  BACKUP_AB("ab", "Android Backup コンテナ (.ab)", "Backup"),
  OMNIBAK("omnibak", "Omni 完全バックアップ (.omnibak)", "Backup"),
  HPJBAK("hpjbak", "Hikaproj バックアップ (.hpjbak)", "Backup"),
  BIN_APK("bin", "バイナリパッケージストリーム (.bin)", "Raw"),
  UNKNOWN("unknown", "不明な形式", "Unknown");

  val primaryExtension: String
    get() = extension

  companion object {
    fun fromExtension(fileName: String): ArchiveFormat {
      val lower = fileName.lowercase().trim()
      return when {
        lower.endsWith(".tar.gz") -> APKS_TAR_GZ
        lower.endsWith(".apk.gz") -> APKS_GZIP
        lower.endsWith(".obb.zip") -> OBB_ZIP
        lower.endsWith(".xapk") -> XAPK
        lower.endsWith(".apks") -> SPLIT_APKS
        lower.endsWith(".apkm") -> APKM
        lower.endsWith(".aab") -> AAB
        lower.endsWith(".omni") -> OMNI
        lower.endsWith(".hpjpkg") -> HPJPKG
        lower.endsWith(".apex") -> APEX
        lower.endsWith(".tgz") -> APKS_TGZ
        lower.endsWith(".tar") -> APKS_TAR
        lower.endsWith(".apk+") -> APK_PLUS
        lower.endsWith(".zapk") -> ZAPK
        lower.endsWith(".sapk") -> SAPK
        lower.endsWith(".mapk") -> MAPK
        lower.endsWith(".bapk") -> BAPK
        lower.endsWith(".pack") -> PACK
        lower.endsWith(".jar") -> JAR_APK
        lower.endsWith(".ab") -> BACKUP_AB
        lower.endsWith(".omnibak") -> OMNIBAK
        lower.endsWith(".hpjbak") -> HPJBAK
        lower.endsWith(".bin") -> BIN_APK
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
  APK("apk", "apk", "標準 .apk", "単体APK形式でそのまま書き出し（分割アプリは自動統合）"),
  APKS("apks", "apks", "分割 .apks (SAI互換)", "Base + 全Split APKを標準ZIPコンテナに格納"),
  XAPK("xapk", "xapk", "標準 .xapk (OBB+Manifest)", "manifest.json と分割APK・OBBを完全同梱"),
  APKM("apkm", "apkm", "バンドル .apkm", "info.json メタデータ付きの分割APKアーカイブ"),
  ZIP("zip", "zip", "汎用 .zip アーカイブ", "PCや任意の解凍ソフトで展開可能な標準ZIP"),
  OMNI("omni", "omni", "Omniパッケージ .omni", "OmniInstaller独自高速リストア用メタデータ同梱形式"),
  HPJPKG("hpjpkg", "hpjpkg", "Hikaproj .hpjpkg", "Hikaproj標準の署名・チェックサム検証付き形式"),
  OMNIBAK("omnibak", "omnibak", "完全バックアップ .omnibak", "APK + 権限一覧 + 詳細メタデータ統合アーカイブ"),
  HPJBAK("hpjbak", "hpjbak", "Hikaproj バックアップ .hpjbak", "世代管理・タグ情報を含む長期保存用アーカイブ"),
  TAR("tar", "tar", "非圧縮 .tar アーカイブ", "POSIX準拠の高速無圧縮TARストリーム", isTar = true),
  TAR_GZ("tar.gz", "tar.gz", "高圧縮 .tar.gz", "TAR + GZIPによる高圧縮 UNIX標準アーカイブ", isTar = true, isGzip = true),
  TGZ("tgz", "tgz", "高圧縮 .tgz", "携帯性に優れた短縮拡張子 TAR+GZIP アーカイブ", isTar = true, isGzip = true),
  APK_GZ("apk.gz", "apk.gz", "GZIP単体 .apk.gz", "APKをGZIPストリームで直接圧縮した省スペース形式", isGzip = true),
  AAB("aab", "aab", "App Bundle互換 .aab", "モジュール構造を保持したバンドルアーカイブ"),
  ZAPK("zapk", "zapk", "大容量 .zapk", "大型ゲーム・OBBデータ向けに最適化されたアーカイブ"),
  SAPK("sapk", "sapk", "Split Package .sapk", "ABI/DPI/言語スプリット明示型パッケージ"),
  MAPK("mapk", "mapk", "Multi-APK .mapk", "複数APK一括展開用マルチパッケージ"),
  BAPK("bapk", "bapk", "Bundle APK .bapk", "Base + Config Splits統合パック"),
  APK_PLUS("apk_plus", "apk+", "拡張 .apk+", "追加アセット・スクリプト情報同梱パッケージ"),
  APEX("apex", "apex", "システム互換 .apex", "モジュールイメージ互換コンテナ"),
  JAR("jar", "jar", "Java Archive .jar", "META-INF互換マニフェスト付きアーカイブ"),
  PACK("pack", "pack", "ユニバーサル .pack", "クロスプラットフォーム転送用パッケージ"),
  AB("ab", "ab", "Android Backup互換 .ab", "ADBバックアップ互換ヘッダー付きアーカイブ"),
  BIN("bin", "bin", "Rawバイナリ .bin", "フィルタ回避・直接ストリーム転送用バイナリ形式");

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
  val sourceRepoName: String = "Hikaproj Official",
  val license: String = "Open Source (MIT/GPL/Apache)",
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
  val ipAddress: String = "",
  val allIpAddresses: List<String> = emptyList(),
  val serverUrl: String = "",
  val cfTunnelEnabled: Boolean = false,
  val cfTunnelRunning: Boolean = false,
  val cfPublicUrl: String = "",
  val cfTunnelToken: String = "",
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
