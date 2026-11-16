package com.example.store

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.model.StoreAppItem
import com.example.model.StoreReleaseVersion
import com.example.model.StoreRepositorySource
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
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

object StoreRepository {

  private const val TAG = "StoreRepository"

  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  private val supportedAssetExtensions = listOf(
    ".apk", ".xapk", ".apks", ".apkm", ".aab", ".apex",
    ".tar.gz", ".tgz", ".apk.gz", ".tar", ".jar", ".ab", ".zip"
  )

  val defaultRepositories = listOf(
    StoreRepositorySource(
      id = "default_curated",
      name = "Default Curated Index",
      url = "https://github.com",
      description = "初期収録オープンソースアプリ (Termux / LocalSend)",
      appCount = 2,
      lastSynced = "初期組込済",
      isOfficial = true
    )
  )

  // Only LocalSend and Termux as requested
  val defaultCuratedApps = listOf(
    StoreAppItem(
      id = "localsend",
      name = "LocalSend",
      packageName = "org.localsend.localsend_app",
      summary = "インターネット不要でローカルLAN内のPC・スマホ間ファイル高速共有",
      description = "LocalSend は AirDrop のオープンソース・クロスプラットフォーム代替ツールです。外部サーバーを経由せず、Wi-Fi ネットワーク内で暗号化通信を用いて Android, Windows, macOS, Linux, iOS 間でファイルを直接送受信します。\n\n• REST API と HTTPS 証明書によるセキュアな P2P 転送\n• 複数ファイル・フォルダ・テキストの一括送信に対応\n• アカウント登録や外部クラウド不要",
      category = "ネットワーク",
      author = "localsend",
      iconUrl = "https://raw.githubusercontent.com/localsend/localsend/main/app/assets/img/logo-512.png",
      githubRepo = "localsend/localsend",
      websiteUrl = "https://localsend.org",
      sourceRepoName = "Default Curated Index",
      license = "MIT",
      minSdk = 21,
      architecture = "arm64-v8a / Universal",
      latestVersion = "v1.15.4",
      downloadUrl = "https://github.com/localsend/localsend/releases/download/v1.15.4/LocalSend-1.15.4-android-arm64v8.apk",
      sizeBytes = 21_500_000L,
      releaseNotes = "フォルダ一括転送の高速化とWeb共有モードの改善。",
      releaseDate = "2024-08-29",
      versions = listOf(
        StoreReleaseVersion(
          versionName = "v1.15.4 (arm64-v8a)",
          downloadUrl = "https://github.com/localsend/localsend/releases/download/v1.15.4/LocalSend-1.15.4-android-arm64v8.apk",
          sizeBytes = 21_500_000L,
          releaseDate = "2024-08-29",
          changelog = "フォルダ一括転送の高速化とWeb共有モードの改善",
          abi = "arm64-v8a"
        )
      )
    ),
    StoreAppItem(
      id = "termux",
      name = "Termux",
      packageName = "com.termux",
      summary = "ルート権限不要で動作する本格派Linux環境エミュレータ",
      description = "Termux は Android 上でそのまま動作する強力なターミナルエミュレータおよび Linux パッケージ環境です。\n\n• apt / pkg パッケージマネージャーによる Python, Node.js, Rust, Clang, Git, OpenSSH 等の導入\n• cloudflared (Cloudflare Tunnel) や各種 CLI ツールのビルド・実行環境としても最適\n• Zsh, Bash, Fish シェルおよび Neovim / Emacs / Tmux による本格開発環境",
      category = "開発・ターミナル",
      author = "termux",
      iconUrl = "https://raw.githubusercontent.com/termux/termux-app/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
      githubRepo = "termux/termux-app",
      websiteUrl = "https://termux.dev",
      sourceRepoName = "Default Curated Index",
      license = "GPL-3.0",
      minSdk = 24,
      architecture = "Universal / arm64-v8a",
      latestVersion = "v0.118.1",
      downloadUrl = "https://github.com/termux/termux-app/releases/download/v0.118.1/termux-app_v0.118.1+github-debug_universal.apk",
      sizeBytes = 98_000_000L,
      releaseNotes = "パッケージ管理aptの更新とAndroid 14+への適合性向上。",
      releaseDate = "2024-06-12",
      versions = listOf(
        StoreReleaseVersion(
          versionName = "v0.118.1 (Universal)",
          downloadUrl = "https://github.com/termux/termux-app/releases/download/v0.118.1/termux-app_v0.118.1+github-debug_universal.apk",
          sizeBytes = 98_000_000L,
          releaseDate = "2024-06-12",
          changelog = "パッケージ管理aptの更新とAndroid 14+への適合性向上",
          abi = "Universal"
        )
      )
    )
  )

  fun normalizeGitHubRepo(input: String): String {
    return input
      .trim()
      .removePrefix("https://")
      .removePrefix("http://")
      .removePrefix("www.")
      .removePrefix("github.com/")
      .substringBefore("/releases")
      .substringBefore("/tree")
      .substringBefore("/blob")
      .substringBefore("?")
      .substringBefore("#")
      .trim()
      .trim('/')
  }

  suspend fun fetchGitHubRelease(repo: String): StoreAppItem? = withContext(Dispatchers.IO) {
    try {
      val cleanRepo = normalizeGitHubRepo(repo)
      if (!cleanRepo.contains("/")) return@withContext null

      val url = "https://api.github.com/repos/$cleanRepo/releases"
      val request = Request.Builder()
        .url(url)
        .header("User-Agent", "OmniInstaller-Android")
        .header("Accept", "application/vnd.github.v3+json")
        .build()

      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          Log.w(TAG, "GitHub API returned ${response.code} for $cleanRepo")
          return@withContext null
        }
        val body = response.body?.string() ?: return@withContext null
        val releasesArray = if (body.trim().startsWith("[")) {
          JSONArray(body)
        } else {
          JSONArray().put(JSONObject(body))
        }
        if (releasesArray.length() == 0) return@withContext null

        val versionsList = mutableListOf<StoreReleaseVersion>()
        var primaryAssetUrl: String? = null
        var primaryAssetSize: Long = 0L
        var latestTag = "latest"
        var latestTitle = ""
        var latestBody = ""
        var latestDate = ""

        val devicePrimaryAbi = Build.SUPPORTED_ABIS.firstOrNull()?.lowercase() ?: "arm64-v8a"

        for (rIdx in 0 until minOf(releasesArray.length(), 8)) {
          val relObj = releasesArray.getJSONObject(rIdx)
          val tagName = relObj.optString("tag_name", "v1.0")
          val releaseName = relObj.optString("name", tagName)
          val bodyText = relObj.optString("body", "GitHub リリース")
          val publishedAt = relObj.optString("published_at", "").take(10)

          val assets = relObj.optJSONArray("assets") ?: JSONArray()
          var bestUrlForRelease: String? = null
          var bestSizeForRelease = 0L

          for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            val lowerName = name.lowercase()
            val downloadUrl = asset.optString("browser_download_url", "")
            val size = asset.optLong("size", 0L)

            val isSupportedPkg = supportedAssetExtensions.any { lowerName.endsWith(it) }
            if (isSupportedPkg && downloadUrl.isNotBlank()) {
              val abiLabel = when {
                lowerName.contains("arm64") || lowerName.contains("v8a") -> "arm64-v8a"
                lowerName.contains("armeabi") || lowerName.contains("v7a") -> "armeabi-v7a"
                lowerName.contains("x86_64") -> "x86_64"
                lowerName.contains("x86") -> "x86"
                else -> "Universal"
              }
              versionsList.add(
                StoreReleaseVersion(
                  versionName = "$tagName ($name)",
                  downloadUrl = downloadUrl,
                  sizeBytes = size,
                  releaseDate = publishedAt,
                  changelog = bodyText.take(300),
                  abi = abiLabel
                )
              )

              val isPreferredAbi = lowerName.contains(devicePrimaryAbi) ||
                lowerName.contains("arm64") ||
                lowerName.contains("universal")
              if (bestUrlForRelease == null || isPreferredAbi) {
                bestUrlForRelease = downloadUrl
                bestSizeForRelease = size
              }
            }
          }

          if (primaryAssetUrl == null && bestUrlForRelease != null) {
            primaryAssetUrl = bestUrlForRelease
            primaryAssetSize = bestSizeForRelease
            latestTag = tagName
            latestTitle = releaseName
            latestBody = bodyText
            latestDate = publishedAt
          }
        }

        val finalDownloadUrl = primaryAssetUrl ?: return@withContext null
        val parts = cleanRepo.split("/")
        val owner = parts.getOrNull(0) ?: "GitHub"
        val repoName = parts.getOrNull(1) ?: cleanRepo
        val appTitle = if (latestTitle.isNotBlank() && !latestTitle.startsWith("v", ignoreCase = true) && latestTitle.length < 36) {
          latestTitle
        } else {
          repoName
        }

        StoreAppItem(
          id = cleanRepo.replace("/", "_").lowercase(),
          name = appTitle,
          packageName = "com.github.${owner.lowercase().replace("[^a-z0-9]".toRegex(), "")}.${repoName.lowercase().replace("[^a-z0-9]".toRegex(), "")}",
          summary = "GitHub ($cleanRepo) から取得された最新リリース",
          description = latestBody.ifBlank { "GitHub リポジトリ $cleanRepo の公式リリースパッケージです。" },
          category = "GitHub",
          author = owner,
          iconUrl = "https://github.com/$owner.png",
          githubRepo = cleanRepo,
          websiteUrl = "https://github.com/$cleanRepo",
          sourceRepoName = "GitHub: $cleanRepo",
          license = "Open Source",
          minSdk = 24,
          architecture = "Multi-ABI / Universal",
          latestVersion = latestTag,
          downloadUrl = finalDownloadUrl,
          sizeBytes = primaryAssetSize,
          releaseNotes = latestBody.take(600),
          releaseDate = latestDate,
          versions = versionsList
        )
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to fetch GitHub release: ${e.message}")
      null
    }
  }

  suspend fun syncCustomHost(urlOrJson: String): Pair<StoreRepositorySource?, List<StoreAppItem>> = withContext(Dispatchers.IO) {
    try {
      val trimmed = urlOrJson.trim()
      val rawText: String
      val sourceUrl: String

      if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        rawText = trimmed
        sourceUrl = "local://inline-json-${System.currentTimeMillis()}"
      } else {
        sourceUrl = trimmed
        val request = Request.Builder()
          .url(sourceUrl)
          .header("User-Agent", "OmniInstaller-Android")
          .build()

        rawText = client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) return@withContext (null to emptyList())
          response.body?.string() ?: return@withContext (null to emptyList())
        }
      }

      parseRepositoryJsonText(rawText, sourceUrl)
    } catch (e: Exception) {
      Log.e(TAG, "Custom host sync error: ${e.message}")
      null to emptyList()
    }
  }

  suspend fun parseRepositoryJsonText(
    rawText: String,
    sourceUrl: String
  ): Pair<StoreRepositorySource?, List<StoreAppItem>> = withContext(Dispatchers.IO) {
    val cleanText = rawText.trim()
    val list = mutableListOf<StoreAppItem>()
    var repoName = "カスタムリポジトリ (${sourceUrl.substringAfter("://").substringBefore("/")})"
    var repoDesc = "外部ホストから同期されたアプリカタログ"

    if (cleanText.startsWith("[")) {
      val jsonArray = JSONArray(cleanText)
      for (i in 0 until jsonArray.length()) {
        val elem = jsonArray.get(i)
        when (elem) {
          is JSONObject -> {
            parseAppJsonObject(elem, i, repoName)?.let { list.add(it) }
          }
          is String -> {
            fetchGitHubRelease(elem)?.let { list.add(it) }
          }
        }
      }
    } else if (cleanText.startsWith("{")) {
      val root = JSONObject(cleanText)
      repoName = root.optString("repo_name", root.optString("name", repoName))
      repoDesc = root.optString("description", repoDesc)

      val fdroidRepoObj = root.optJSONObject("repo")
      val fdroidBaseAddress = fdroidRepoObj?.optString("address", "")?.trimEnd('/') ?: ""
      if (fdroidRepoObj != null) {
        repoName = fdroidRepoObj.optString("name", repoName)
        repoDesc = fdroidRepoObj.optString("description", repoDesc)
      }

      val packagesMap = root.optJSONObject("packages")
      val appsArray = root.optJSONArray("apps")

      if (appsArray != null) {
        for (i in 0 until appsArray.length()) {
          val obj = appsArray.optJSONObject(i) ?: continue
          if (packagesMap != null && fdroidBaseAddress.isNotBlank()) {
            val pkgName = obj.optString("packageName", "")
            val pkgVersions = packagesMap.optJSONArray(pkgName)
            val firstPkg = pkgVersions?.optJSONObject(0)
            val apkName = firstPkg?.optString("apkName", "") ?: ""
            val verName = firstPkg?.optString("versionName", "1.0") ?: "1.0"
            val size = firstPkg?.optLong("size", 10_000_000L) ?: 10_000_000L
            val downloadUrl = if (apkName.isNotBlank()) "$fdroidBaseAddress/$apkName" else ""
            if (pkgName.isNotBlank() && downloadUrl.isNotBlank()) {
              val localizedName = obj.optString("name", pkgName.substringAfterLast('.'))
              val summary = obj.optString("summary", "F-Droid リポジトリ掲載アプリ")
              val desc = obj.optString("description", summary)
              val cats = obj.optJSONArray("categories")
              val category = if (cats != null && cats.length() > 0) cats.optString(0, "F-Droid") else "F-Droid"
              list.add(
                StoreAppItem(
                  id = "fdroid_${pkgName.replace('.', '_')}",
                  name = localizedName,
                  packageName = pkgName,
                  summary = summary,
                  description = desc,
                  category = category,
                  author = obj.optString("authorName", "F-Droid Author"),
                  iconUrl = "",
                  githubRepo = obj.optString("sourceCode").takeIf { it.contains("github.com") }?.let { normalizeGitHubRepo(it) },
                  websiteUrl = obj.optString("webSite", ""),
                  sourceRepoName = repoName,
                  license = obj.optString("license", "FOSS"),
                  minSdk = firstPkg?.optInt("minSdkVersion", 21) ?: 21,
                  latestVersion = verName,
                  downloadUrl = downloadUrl,
                  sizeBytes = size,
                  releaseNotes = obj.optString("whatsNew", "F-Droid 同期パッケージ"),
                  releaseDate = ""
                )
              )
            }
          } else {
            parseAppJsonObject(obj, i, repoName)?.let { list.add(it) }
          }
        }
      } else if (root.has("configs")) {
        val configs = root.optJSONArray("configs") ?: JSONArray()
        for (i in 0 until configs.length()) {
          val cfg = configs.optJSONObject(i) ?: continue
          val url = cfg.optString("url", "")
          if (url.contains("github.com")) {
            fetchGitHubRelease(url)?.let { list.add(it) }
          }
        }
      } else if (root.has("download_url") || root.has("apk_url") || root.has("package_name")) {
        parseAppJsonObject(root, 0, repoName)?.let { list.add(it) }
      }
    }

    val source = StoreRepositorySource(
      id = "repo_${sourceUrl.hashCode()}",
      name = repoName,
      url = sourceUrl,
      description = repoDesc,
      appCount = list.size,
      lastSynced = "同期完了 (${list.size}件)"
    )
    source to list
  }

  private fun parseAppJsonObject(obj: JSONObject, index: Int, defaultRepoName: String): StoreAppItem? {
    val githubRepo = obj.optString("github_repo", obj.optString("github", "")).ifBlank { null }
    val downloadUrl = obj.optString("download_url", obj.optString("apk_url", obj.optString("url", "")))
    if (downloadUrl.isBlank()) return null

    val pkgName = obj.optString("package_name", obj.optString("packageName", "com.custom.app$index"))
    val name = obj.optString("name", obj.optString("title", pkgName.substringAfterLast('.')))
    val summary = obj.optString("summary", obj.optString("short_description", "カスタムリポジトリから同期されたアプリ"))
    val description = obj.optString("description", summary)
    val ver = obj.optString("version", obj.optString("latest_version", "1.0"))

    val versionsArr = obj.optJSONArray("versions")
    val parsedVersions = mutableListOf<StoreReleaseVersion>()
    if (versionsArr != null) {
      for (vIdx in 0 until versionsArr.length()) {
        val vObj = versionsArr.optJSONObject(vIdx) ?: continue
        parsedVersions.add(
          StoreReleaseVersion(
            versionName = vObj.optString("version", ver),
            downloadUrl = vObj.optString("download_url", downloadUrl),
            sizeBytes = vObj.optLong("size", 10_000_000L),
            releaseDate = vObj.optString("date", ""),
            changelog = vObj.optString("changelog", ""),
            abi = vObj.optString("abi", "Universal")
          )
        )
      }
    }

    val screenshotsArr = obj.optJSONArray("screenshots")
    val screenshots = mutableListOf<String>()
    if (screenshotsArr != null) {
      for (sIdx in 0 until screenshotsArr.length()) {
        screenshotsArr.optString(sIdx)?.takeIf { it.isNotBlank() }?.let { screenshots.add(it) }
      }
    }

    return StoreAppItem(
      id = obj.optString("id", "${pkgName}_$index"),
      name = name,
      packageName = pkgName,
      summary = summary,
      description = description,
      category = obj.optString("category", "独自リポジトリ"),
      author = obj.optString("author", obj.optString("developer", "Self-hosted")),
      iconUrl = obj.optString("icon_url", obj.optString("icon", "")),
      screenshots = screenshots,
      githubRepo = githubRepo,
      websiteUrl = obj.optString("website_url", obj.optString("website", "")),
      sourceRepoName = obj.optString("repo_name", defaultRepoName),
      license = obj.optString("license", "Open Source"),
      minSdk = obj.optInt("min_sdk", 24),
      architecture = obj.optString("architecture", "Universal"),
      latestVersion = ver,
      downloadUrl = downloadUrl,
      sizeBytes = obj.optLong("size", obj.optLong("size_bytes", 12_000_000L)),
      releaseNotes = obj.optString("changelog", obj.optString("release_notes", "更新情報")),
      releaseDate = obj.optString("date", obj.optString("release_date", "2024-10-01")),
      versions = parsedVersions
    )
  }

  suspend fun batchImportSources(
    rawInput: String
  ): Pair<List<StoreRepositorySource>, List<StoreAppItem>> = withContext(Dispatchers.IO) {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return@withContext (emptyList<StoreRepositorySource>() to emptyList())

    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
      val (repoSource, items) = parseRepositoryJsonText(trimmed, "local://batch-json")
      val repos = if (repoSource != null) listOf(repoSource) else emptyList()
      return@withContext (repos to items)
    }

    val addedRepos = mutableListOf<StoreRepositorySource>()
    val addedApps = mutableListOf<StoreAppItem>()

    val entries = trimmed
      .split("\n", "\r\n", ",", ";")
      .map { it.trim() }
      .filter { it.isNotBlank() && !it.startsWith("#") }

    for (entry in entries) {
      when {
        entry.startsWith("http://") || entry.startsWith("https://") -> {
          if (entry.contains("github.com/") && !entry.endsWith(".json", ignoreCase = true) && !entry.contains("raw.githubusercontent.com")) {
            fetchGitHubRelease(entry)?.let { app ->
              addedApps.add(app)
            }
          } else {
            val (source, items) = syncCustomHost(entry)
            if (source != null && items.isNotEmpty()) {
              addedRepos.add(source)
              addedApps.addAll(items)
            }
          }
        }
        entry.contains("/") && !entry.contains(" ") -> {
          fetchGitHubRelease(entry)?.let { app ->
            addedApps.add(app)
          }
        }
      }
    }

    addedRepos to addedApps
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
    val cleanRemote = remoteVer.removePrefix("v").substringBefore("-").substringBefore(" ").trim()
    val cleanInstalled = installedVer.removePrefix("v").substringBefore("-").substringBefore(" ").trim()
    return cleanRemote != cleanInstalled
  }

  fun downloadApkStream(
    context: Context,
    item: StoreAppItem,
    overrideDownloadUrl: String? = null
  ): Flow<DownloadProgress> = downloadDirectUrlStream(
    context = context,
    directUrl = overrideDownloadUrl ?: item.downloadUrl,
    preferredId = "${item.id}_${item.latestVersion.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")}",
    expectedSize = item.sizeBytes
  )

  fun downloadDirectUrlStream(
    context: Context,
    directUrl: String,
    preferredId: String? = null,
    expectedSize: Long = 0L
  ): Flow<DownloadProgress> = flow {
    emit(DownloadProgress(0f, 0L, expectedSize, null, "接続中..."))

    try {
      val request = Request.Builder()
        .url(directUrl.trim())
        .header("User-Agent", "OmniInstaller-Android")
        .build()

      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          emit(DownloadProgress(0f, 0L, expectedSize, null, "ダウンロード失敗 (HTTP ${response.code})", isError = true))
          return@flow
        }

        val body = response.body ?: throw IllegalStateException("レスポンスボディが空です")
        val contentLength = if (body.contentLength() > 0) body.contentLength() else expectedSize

        val disposition = response.header("Content-Disposition") ?: ""
        val fileNameFromHeader = Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
          .find(disposition)
          ?.groupValues
          ?.getOrNull(1)
          ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

        val urlPathSegment = directUrl.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val detectedExt = detectExtensionFromName(fileNameFromHeader ?: urlPathSegment)
        val baseName = preferredId
          ?: (fileNameFromHeader ?: urlPathSegment)
            .substringBeforeLast('.')
            .replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
            .ifBlank { "direct_pkg_${System.currentTimeMillis()}" }

        val outFile = File(context.cacheDir, "${baseName}.$detectedExt")

        body.byteStream().use { input ->
          FileOutputStream(outFile).use { output ->
            val buffer = ByteArray(32768)
            var totalRead = 0L

            while (true) {
              val bytesRead = input.read(buffer)
              if (bytesRead == -1) break
              output.write(buffer, 0, bytesRead)
              totalRead += bytesRead
              val progress = if (contentLength > 0) (totalRead.toFloat() / contentLength).coerceIn(0f, 0.99f) else 0.5f
              val mbRead = String.format("%.1f", totalRead / 1024f / 1024f)
              emit(DownloadProgress(progress, totalRead, contentLength, null, "ダウンロード中... (${mbRead} MB)"))
            }
          }
        }

        emit(DownloadProgress(1f, outFile.length(), outFile.length(), outFile, "ダウンロード完了: ${outFile.name}"))
      }
    } catch (e: Exception) {
      Log.e(TAG, "Direct download error", e)
      emit(DownloadProgress(0f, 0L, expectedSize, null, "ダウンロードエラー: ${e.message}", isError = true))
    }
  }.flowOn(Dispatchers.IO)

  private fun detectExtensionFromName(rawName: String): String {
    val lower = rawName.lowercase()
    return when {
      lower.endsWith(".tar.gz") -> "tar.gz"
      lower.endsWith(".apk.gz") -> "apk.gz"
      lower.endsWith(".tgz") -> "tgz"
      lower.endsWith(".tar") -> "tar"
      lower.endsWith(".xapk") -> "xapk"
      lower.endsWith(".apks") -> "apks"
      lower.endsWith(".apkm") -> "apkm"
      lower.endsWith(".aab") -> "aab"
      lower.endsWith(".apex") -> "apex"
      lower.endsWith(".jar") -> "jar"
      lower.endsWith(".ab") -> "ab"
      lower.endsWith(".zip") -> "zip"
      else -> "apk"
    }
  }
}

data class DownloadProgress(
  val progress: Float,
  val downloadedBytes: Long,
  val totalBytes: Long,
  val targetFile: File?,
  val statusMessage: String,
  val isError: Boolean = false
)
