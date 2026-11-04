package com.example.store

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.model.StoreAppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object StoreRepository {

  private const val TAG = "StoreRepository"

  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  // Curated prominent open-source apps with GitHub releases
  val defaultCuratedApps = listOf(
    StoreAppItem(
      id = "newpipe",
      name = "NewPipe",
      packageName = "org.schabi.newpipe",
      summary = "軽量・広告なし・バックグラウンド再生対応のYouTube/メディアクライアント",
      category = "メディア",
      author = "TeamNewPipe",
      iconUrl = "https://raw.githubusercontent.com/TeamNewPipe/NewPipe/dev/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
      githubRepo = "TeamNewPipe/NewPipe",
      latestVersion = "v0.27.2",
      downloadUrl = "https://github.com/TeamNewPipe/NewPipe/releases/download/v0.27.2/NewPipe_v0.27.2.apk",
      sizeBytes = 11_500_000L,
      releaseNotes = "最新のストリーミング修正およびパフォーマンス最適化。",
      releaseDate = "2024-10-15"
    ),
    StoreAppItem(
      id = "revanced_mgr",
      name = "ReVanced Manager",
      packageName = "app.revanced.manager.flutter",
      summary = "各種Androidアプリに自由なパッチを適用できるオープンソースマネージャー",
      category = "ユーティリティ",
      author = "ReVanced",
      iconUrl = "https://raw.githubusercontent.com/ReVanced/revanced-manager/main/assets/branding/icon.png",
      githubRepo = "ReVanced/revanced-manager",
      latestVersion = "v1.21.1",
      downloadUrl = "https://github.com/ReVanced/revanced-manager/releases/download/v1.21.1/revanced-manager-v1.21.1.apk",
      sizeBytes = 36_000_000L,
      releaseNotes = "パッチ統合UIの改良とルート/非ルート環境への適応強化。",
      releaseDate = "2024-09-28"
    ),
    StoreAppItem(
      id = "mihon",
      name = "Mihon",
      packageName = "eu.kanade.tachiyomi",
      summary = "高機能なオンライン/ローカル漫画リーダー (Tachiyomi後継)",
      category = "コミック・読書",
      author = "mihonapp",
      iconUrl = "https://raw.githubusercontent.com/mihonapp/mihon/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
      githubRepo = "mihonapp/mihon",
      latestVersion = "v0.16.5",
      downloadUrl = "https://github.com/mihonapp/mihon/releases/download/v0.16.5/mihon-v0.16.5.apk",
      sizeBytes = 28_000_000L,
      releaseNotes = "バックアップ暗号化の強化およびリーダーのレンダリング向上。",
      releaseDate = "2024-08-10"
    ),
    StoreAppItem(
      id = "seal",
      name = "Seal Downloader",
      packageName = "com.junkfood.seal",
      summary = "yt-dlpベースのモダンな動画・音声ダウンローダー",
      category = "ツール",
      author = "JunkFood02",
      iconUrl = "https://raw.githubusercontent.com/JunkFood02/Seal/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
      githubRepo = "JunkFood02/Seal",
      latestVersion = "v1.12.4",
      downloadUrl = "https://github.com/JunkFood02/Seal/releases/download/v1.12.4/Seal-1.12.4-arm64-v8a-release.apk",
      sizeBytes = 39_000_000L,
      releaseNotes = "yt-dlpバイナリ更新、プレイリスト一括処理の高速化。",
      releaseDate = "2024-07-20"
    ),
    StoreAppItem(
      id = "termux",
      name = "Termux",
      packageName = "com.termux",
      summary = "ルート権限不要で動作する本格派Linux環境エミュレータ",
      category = "開発・ターミナル",
      author = "termux",
      iconUrl = "https://raw.githubusercontent.com/termux/termux-app/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
      githubRepo = "termux/termux-app",
      latestVersion = "v0.118.1",
      downloadUrl = "https://github.com/termux/termux-app/releases/download/v0.118.1/termux-app_v0.118.1+github-debug_universal.apk",
      sizeBytes = 98_000_000L,
      releaseNotes = "パッケージ管理aptの更新とAndroid 14+への適合性向上。",
      releaseDate = "2024-06-12"
    ),
    StoreAppItem(
      id = "aegis",
      name = "Aegis Authenticator",
      packageName = "com.beemdevelopment.aegis",
      summary = "セキュアで暗号化バックアップ対応の2段階認証 (2FA/OTP) アプリ",
      category = "セキュリティ",
      author = "beemdevelopment",
      iconUrl = "https://raw.githubusercontent.com/beemdevelopment/Aegis/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
      githubRepo = "beemdevelopment/Aegis",
      latestVersion = "v3.1.2",
      downloadUrl = "https://github.com/beemdevelopment/Aegis/releases/download/v3.1.2/aegis-v3.1.2.apk",
      sizeBytes = 19_000_000L,
      releaseNotes = "Material Youダイナミックテーマ、バイオメトリクス保護の安定化。",
      releaseDate = "2024-09-01"
    )
  )

  suspend fun fetchGitHubRelease(repo: String): StoreAppItem? = withContext(Dispatchers.IO) {
    try {
      val cleanRepo = repo
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("github.com/")
        .substringBefore("/releases")
        .substringBefore("/tree")
        .substringBefore("/blob")
        .trim()
        .trim('/')
      val url = "https://api.github.com/repos/$cleanRepo/releases/latest"
      val request = Request.Builder()
        .url(url)
        .header("User-Agent", "OmniInstaller-Android")
        .header("Accept", "application/vnd.github.v3+json")
        .build()

      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          Log.w(TAG, "GitHub API returned ${response.code}")
          return@withContext null
        }
        val body = response.body?.string() ?: return@withContext null
        val json = JSONObject(body)

        val tagName = json.optString("tag_name", "latest")
        val releaseName = json.optString("name", tagName)
        val bodyText = json.optString("body", "GitHub リリース")
        val publishedAt = json.optString("published_at", "").take(10)

        // Find APK / XAPK assets
        val assets = json.optJSONArray("assets") ?: JSONArray()
        var bestAssetUrl: String? = null
        var bestAssetName: String = ""
        var bestAssetSize: Long = 0L

        for (i in 0 until assets.length()) {
          val asset = assets.getJSONObject(i)
          val name = asset.optString("name", "")
          val downloadUrl = asset.optString("browser_download_url", "")
          val size = asset.optLong("size", 0L)

          if (name.endsWith(".apk", ignoreCase = true) || name.endsWith(".xapk", ignoreCase = true)) {
            // Prioritize arm64 or universal over x86 if multiple
            if (bestAssetUrl == null || name.contains("arm64") || name.contains("universal")) {
              bestAssetUrl = downloadUrl
              bestAssetName = name
              bestAssetSize = size
            }
          }
        }

        if (bestAssetUrl == null) {
          return@withContext null
        }

        val parts = cleanRepo.split("/")
        val appTitle = if (releaseName.isNotBlank() && !releaseName.startsWith("v")) {
          releaseName
        } else {
          parts.getOrNull(1) ?: cleanRepo
        }

        StoreAppItem(
          id = cleanRepo.replace("/", "_").lowercase(),
          name = appTitle,
          packageName = "com.github.${parts.getOrNull(0)?.lowercase()}.${parts.getOrNull(1)?.lowercase()}",
          summary = "GitHub: $cleanRepo の最新リリース",
          category = "GitHub リポ",
          author = parts.getOrNull(0) ?: "GitHub",
          iconUrl = "https://github.com/${parts.getOrNull(0)}.png",
          githubRepo = cleanRepo,
          latestVersion = tagName,
          downloadUrl = bestAssetUrl,
          sizeBytes = bestAssetSize,
          releaseNotes = bodyText.take(400),
          releaseDate = publishedAt
        )
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to fetch GitHub release: ${e.message}")
      null
    }
  }

  suspend fun syncCustomHost(url: String): List<StoreAppItem> = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url(url)
        .header("User-Agent", "OmniInstaller-Android")
        .build()

      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@withContext emptyList()
        val text = response.body?.string() ?: return@withContext emptyList()
        val jsonArray = JSONArray(text)
        val list = mutableListOf<StoreAppItem>()

        for (i in 0 until jsonArray.length()) {
          val obj = jsonArray.getJSONObject(i)
          list.add(
            StoreAppItem(
              id = obj.optString("id", "custom_$i"),
              name = obj.optString("name", "Custom App"),
              packageName = obj.optString("package_name", "com.custom.app$i"),
              summary = obj.optString("summary", "独自ホストから同期されたアプリ"),
              category = obj.optString("category", "独自リポジトリ"),
              author = obj.optString("author", "Self-hosted"),
              iconUrl = obj.optString("icon_url", ""),
              githubRepo = obj.optString("github_repo").ifBlank { null },
              latestVersion = obj.optString("version", "1.0"),
              downloadUrl = obj.optString("download_url", ""),
              sizeBytes = obj.optLong("size", 10_000_000L),
              releaseNotes = obj.optString("changelog", "更新情報"),
              releaseDate = obj.optString("date", "2024-10-01")
            )
          )
        }
        list
      }
    } catch (e: Exception) {
      Log.e(TAG, "Custom host sync error: ${e.message}")
      emptyList()
    }
  }

  fun updateInstalledStatus(context: Context, items: List<StoreAppItem>): List<StoreAppItem> {
    val pm = context.packageManager
    return items.map { item ->
      try {
        val pi = pm.getPackageInfo(item.packageName, 0)
        val installedVer = pi.versionName ?: "v${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else pi.versionCode}"
        val isUpdate = isVersionNewer(item.latestVersion, installedVer)
        item.copy(
          isInstalled = true,
          installedVersion = installedVer,
          isUpdateAvailable = isUpdate
        )
      } catch (_: PackageManager.NameNotFoundException) {
        item.copy(isInstalled = false, installedVersion = null, isUpdateAvailable = false)
      }
    }
  }

  private fun isVersionNewer(remoteVer: String, installedVer: String): Boolean {
    val cleanRemote = remoteVer.removePrefix("v").substringBefore("-")
    val cleanInstalled = installedVer.removePrefix("v").substringBefore("-")
    return cleanRemote != cleanInstalled
  }

  fun downloadApkStream(
    context: Context,
    item: StoreAppItem
  ): Flow<DownloadProgress> = flow {
    emit(DownloadProgress(0f, 0L, item.sizeBytes, null, "ダウンロード開始中..."))

    val extension = when {
      item.downloadUrl.lowercase().contains(".xapk") -> "xapk"
      item.downloadUrl.lowercase().contains(".apks") -> "apks"
      item.downloadUrl.lowercase().contains(".apkm") -> "apkm"
      item.downloadUrl.lowercase().contains(".zip") -> "zip"
      else -> "apk"
    }
    val cleanVer = item.latestVersion.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
    val safeName = "${item.id}_${cleanVer}.$extension"
    val outFile = File(context.cacheDir, safeName)

    try {
      val request = Request.Builder().url(item.downloadUrl).build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          emit(DownloadProgress(0f, 0L, item.sizeBytes, null, "ダウンロード失敗 (HTTP ${response.code})", isError = true))
          return@flow
        }
        val body = response.body ?: throw IllegalStateException("Empty body")
        val contentLength = if (body.contentLength() > 0) body.contentLength() else item.sizeBytes

        body.byteStream().use { input ->
          FileOutputStream(outFile).use { output ->
            val buffer = ByteArray(32768)
            var bytesRead: Int
            var totalRead = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
              output.write(buffer, 0, bytesRead)
              totalRead += bytesRead
              val progress = if (contentLength > 0) totalRead.toFloat() / contentLength else 0.5f
              emit(DownloadProgress(progress, totalRead, contentLength, null, "ダウンロード中... (${totalRead / 1024 / 1024}MB)"))
            }
          }
        }
      }

      emit(DownloadProgress(1f, outFile.length(), outFile.length(), outFile, "ダウンロード完了"))
    } catch (e: Exception) {
      Log.e(TAG, "Download error", e)
      emit(DownloadProgress(0f, 0L, item.sizeBytes, null, "ダウンロードエラー: ${e.message}", isError = true))
    }
  }.flowOn(Dispatchers.IO)
}

data class DownloadProgress(
  val progress: Float,
  val downloadedBytes: Long,
  val totalBytes: Long,
  val targetFile: File?,
  val statusMessage: String,
  val isError: Boolean = false
)
