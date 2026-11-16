package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.BackupManager
import com.example.installer.PackageInstallerManager
import com.example.installer.PackageParser
import com.example.installer.RootHelper
import com.example.model.ApkArchiveInfo
import com.example.model.BackupArchiveItem
import com.example.model.BackupExportFormat
import com.example.model.DeepLinkConfirmationState
import com.example.model.DeepLinkType
import com.example.model.DeviceOptimizationState
import com.example.model.InstalledAppItem
import com.example.model.StoreAppItem
import com.example.model.StoreRepositorySource
import com.example.optimizer.AppsManagerRepository
import com.example.remote.RemoteInstallerServer
import com.example.store.DownloadProgress
import com.example.store.StoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class NavigationTab(val label: String) {
  INSTALLER("インストール"),
  STORE("ストア"),
  APPS("アプリ管理"),
  REMOTE("PC遠隔導入"),
  TOOLS("最適化・復元")
}

enum class AppsFilter(val title: String) {
  USER("ユーザーアプリ"),
  ALL("すべてのアプリ"),
  SYSTEM("システムアプリ"),
  SPLITS("分割APKのみ")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val context get() = getApplication<Application>()

  // Navigation
  private val _currentTab = MutableStateFlow(NavigationTab.INSTALLER)
  val currentTab: StateFlow<NavigationTab> = _currentTab.asStateFlow()

  // Feedback Snackbar
  private val _snackbarMessage = MutableStateFlow<String?>(null)
  val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

  // Installer State
  private val _parsedArchive = MutableStateFlow<ApkArchiveInfo?>(null)
  val parsedArchive: StateFlow<ApkArchiveInfo?> = _parsedArchive.asStateFlow()

  private val _isParsing = MutableStateFlow(false)
  val isParsing: StateFlow<Boolean> = _isParsing.asStateFlow()

  private val _directUrlProgress = MutableStateFlow<DownloadProgress?>(null)
  val directUrlProgress: StateFlow<DownloadProgress?> = _directUrlProgress.asStateFlow()

  val installSessionState = PackageInstallerManager.sessionState

  // Store State
  private val _storeApps = MutableStateFlow<List<StoreAppItem>>(StoreRepository.defaultCuratedApps)
  val storeApps: StateFlow<List<StoreAppItem>> = _storeApps.asStateFlow()

  private val _repositories = MutableStateFlow<List<StoreRepositorySource>>(StoreRepository.defaultRepositories)
  val repositories: StateFlow<List<StoreRepositorySource>> = _repositories.asStateFlow()

  private val _selectedStoreApp = MutableStateFlow<StoreAppItem?>(null)
  val selectedStoreApp: StateFlow<StoreAppItem?> = _selectedStoreApp.asStateFlow()

  private val _deepLinkState = MutableStateFlow<DeepLinkConfirmationState?>(null)
  val deepLinkState: StateFlow<DeepLinkConfirmationState?> = _deepLinkState.asStateFlow()

  private val _storeSearchQuery = MutableStateFlow("")
  val storeSearchQuery: StateFlow<String> = _storeSearchQuery.asStateFlow()

  private val _selectedCategory = MutableStateFlow("すべて")
  val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

  private val _downloadProgressMap = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
  val downloadProgressMap: StateFlow<Map<String, DownloadProgress>> = _downloadProgressMap.asStateFlow()

  private val _isSearchingRepo = MutableStateFlow(false)
  val isSearchingRepo: StateFlow<Boolean> = _isSearchingRepo.asStateFlow()

  // Installed Apps State
  private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
  val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

  private val _isLoadingApps = MutableStateFlow(false)
  val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

  private val _appsSearchQuery = MutableStateFlow("")
  val appsSearchQuery: StateFlow<String> = _appsSearchQuery.asStateFlow()

  private val _appsFilter = MutableStateFlow(AppsFilter.USER)
  val appsFilter: StateFlow<AppsFilter> = _appsFilter.asStateFlow()

  private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
  val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

  private val _inspectingApp = MutableStateFlow<InstalledAppItem?>(null)
  val inspectingApp: StateFlow<InstalledAppItem?> = _inspectingApp.asStateFlow()

  // Backup Export Format State (24+ formats)
  private val _selectedBackupFormat = MutableStateFlow(BackupExportFormat.OMNI)
  val selectedBackupFormat: StateFlow<BackupExportFormat> = _selectedBackupFormat.asStateFlow()

  // Remote Server
  private val remoteServer: RemoteInstallerServer = RemoteInstallerServer(
    context = context,
    onPackageReceived = { file ->
      handleFileReceivedFromRemote(file)
    },
    onDirectUrlReceived = { url ->
      installFromDirectUrl(url)
    }
  )
  val remoteServerStatus = remoteServer.status

  // Optimization & Backups
  private val _optimizationState = MutableStateFlow(DeviceOptimizationState())
  val optimizationState: StateFlow<DeviceOptimizationState> = _optimizationState.asStateFlow()

  private val _backupsList = MutableStateFlow<List<BackupArchiveItem>>(emptyList())
  val backupsList: StateFlow<List<BackupArchiveItem>> = _backupsList.asStateFlow()

  private val _isOptimizing = MutableStateFlow(false)
  val isOptimizing: StateFlow<Boolean> = _isOptimizing.asStateFlow()

  init {
    refreshInstalledApps()
    refreshBackups()
    checkOptimization()

    // Listen to install events
    viewModelScope.launch {
      PackageInstallerManager.installEvents.collect { (success, msg) ->
        showMessage(msg)
        if (success) {
          refreshInstalledApps()
        }
      }
    }
  }

  fun setTab(tab: NavigationTab) {
    _currentTab.value = tab
  }

  fun showMessage(msg: String) {
    _snackbarMessage.value = msg
  }

  fun clearMessage() {
    _snackbarMessage.value = null
  }

  // INSTALLER ACTIONS
  fun parsePackageFromUri(uri: Uri) {
    viewModelScope.launch {
      _isParsing.value = true
      try {
        val fileName = PackageParser.getFileNameFromUri(context, uri)
        val tempFile = PackageParser.copyUriToTempFile(context, uri, fileName)
        val info = PackageParser.parsePackage(context, uri, tempFile)
        _parsedArchive.value = info
        PackageInstallerManager.resetState()
        _currentTab.value = NavigationTab.INSTALLER
      } catch (e: Exception) {
        showMessage("解析エラー: ${e.message}")
      } finally {
        _isParsing.value = false
      }
    }
  }

  /**
   * Parses multiple selected APK files together as a unified Split APK bundle.
   */
  fun parseMultiplePackageUris(uris: List<Uri>) {
    if (uris.isEmpty()) return
    if (uris.size == 1) {
      parsePackageFromUri(uris.first())
      return
    }
    viewModelScope.launch {
      _isParsing.value = true
      try {
        val copiedFiles = uris.mapIndexed { idx, uri ->
          val rawName = PackageParser.getFileNameFromUri(context, uri)
          val safeName = if (rawName.endsWith(".apk", ignoreCase = true)) rawName else "split_${idx}_$rawName.apk"
          PackageParser.copyUriToTempFile(context, uri, safeName)
        }
        val info = PackageParser.parseMultipleApkFiles(context, copiedFiles)
        _parsedArchive.value = info
        PackageInstallerManager.resetState()
        _currentTab.value = NavigationTab.INSTALLER
        showMessage("${copiedFiles.size} 個のAPKをマルチAPKバンドルとして統合解析しました")
      } catch (e: Exception) {
        showMessage("複数APK解析エラー: ${e.message}")
      } finally {
        _isParsing.value = false
      }
    }
  }

  /**
   * Downloads an APK/XAPK/APKS/archive from a direct HTTP/HTTPS URL and opens it in the installer.
   */
  fun installFromDirectUrl(directUrl: String) {
    val cleanUrl = directUrl.trim()
    if (cleanUrl.isBlank()) return
    if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
      showMessage("有効な http:// または https:// の直リンクを入力してください")
      return
    }

    viewModelScope.launch {
      _currentTab.value = NavigationTab.INSTALLER
      StoreRepository.downloadDirectUrlStream(context, cleanUrl).collect { progress ->
        _directUrlProgress.value = progress
        if (progress.targetFile != null && progress.progress >= 1.0f) {
          _directUrlProgress.value = null
          showMessage("直リンクからのダウンロード完了: パッケージを解析中...")
          parsePackageFromFile(progress.targetFile)
        } else if (progress.isError) {
          showMessage(progress.statusMessage)
        }
      }
    }
  }

  fun parsePackageFromFile(file: File) {
    viewModelScope.launch {
      _isParsing.value = true
      try {
        val info = PackageParser.parsePackage(context, null, file)
        _parsedArchive.value = info
        PackageInstallerManager.resetState()
        _currentTab.value = NavigationTab.INSTALLER
      } catch (e: Exception) {
        showMessage("解析エラー: ${e.message}")
      } finally {
        _isParsing.value = false
      }
    }
  }

  fun startInstall(useRoot: Boolean = false) {
    val info = _parsedArchive.value ?: return
    viewModelScope.launch {
      PackageInstallerManager.installPackage(context, info, useRoot)
    }
  }

  fun clearParsedArchive() {
    _parsedArchive.value = null
    PackageInstallerManager.resetState()
  }

  // STORE & DEEP LINK ACTIONS
  fun setStoreSearchQuery(query: String) {
    _storeSearchQuery.value = query
  }

  fun setSelectedCategory(cat: String) {
    _selectedCategory.value = cat
  }

  fun openStoreAppDetail(app: StoreAppItem?) {
    _selectedStoreApp.value = app
  }

  fun dismissDeepLinkDialog() {
    _deepLinkState.value = null
  }

  fun confirmDeepLinkAction() {
    val state = _deepLinkState.value ?: return
    _deepLinkState.value = null

    when (state.type) {
      DeepLinkType.ADD_REPOS -> {
        val combined = (state.repoUrls + state.githubRepos).joinToString("\n")
        batchImportStoreSources(combined)
      }
      DeepLinkType.OPEN_APP_DETAIL -> {
        state.targetApp?.let { app ->
          _currentTab.value = NavigationTab.STORE
          _selectedStoreApp.value = app
        }
      }
      DeepLinkType.DIRECT_INSTALL -> {
        state.directInstallUrl?.let { url ->
          installFromDirectUrl(url)
        }
      }
    }
  }

  /**
   * Handles `hpjominii://` custom URI scheme links:
   * - `hpjominii://store/repo?url=...&urls=url1,url2&github=owner/repo1,owner/repo2`
   * - `hpjominii://repo/add?urls=...`
   * - `hpjominii://store/app?pkg=...&name=...&version=...&apk=...&github=...&desc=...`
   * - `hpjominii://app?id=...`
   * - `hpjominii://install?url=https://.../app.apk`
   */
  fun handleDeepLink(uri: Uri) {
    if (uri.scheme?.lowercase() != "hpjominii") {
      parsePackageFromUri(uri)
      return
    }

    val host = uri.host?.lowercase() ?: ""
    val path = uri.path?.lowercase() ?: ""
    val fullRoute = "$host$path"

    // 1. Direct Install Link: hpjominii://install?url=...
    if (host == "install" || fullRoute.contains("install")) {
      val directUrl = uri.getQueryParameter("url") ?: uri.getQueryParameter("apk") ?: ""
      if (directUrl.isNotBlank()) {
        _currentTab.value = NavigationTab.INSTALLER
        _deepLinkState.value = DeepLinkConfirmationState(
          rawUri = uri.toString(),
          type = DeepLinkType.DIRECT_INSTALL,
          directInstallUrl = directUrl
        )
        return
      }
    }

    // 2. App Detail Link (even if repo is not added):
    // hpjominii://store/app?pkg=org.example&name=MyApp&apk=https://...&github=owner/repo
    // or hpjominii://app?id=newpipe
    if (host == "app" || path.startsWith("/app")) {
      _currentTab.value = NavigationTab.STORE
      val appId = uri.getQueryParameter("id") ?: path.removePrefix("/app").trim('/')
      val pkgParam = uri.getQueryParameter("pkg") ?: uri.getQueryParameter("package")
      val githubParam = uri.getQueryParameter("github") ?: uri.getQueryParameter("repo")
      val apkUrlParam = uri.getQueryParameter("apk") ?: uri.getQueryParameter("url") ?: uri.getQueryParameter("download_url")

      // First check if already in local store catalog
      val existing = _storeApps.value.find {
        (appId.isNotBlank() && it.id.equals(appId, ignoreCase = true)) ||
          (pkgParam != null && it.packageName.equals(pkgParam, ignoreCase = true)) ||
          (githubParam != null && it.githubRepo.equals(githubParam, ignoreCase = true))
      }

      if (existing != null && apkUrlParam == null) {
        _selectedStoreApp.value = existing
        showMessage("ストア詳細ページを開きました: ${existing.name}")
        return
      }

      // If not in added repositories, dynamically resolve from GitHub or construct standalone StoreAppItem!
      if (!githubParam.isNullOrBlank() && apkUrlParam.isNullOrBlank()) {
        viewModelScope.launch {
          _isSearchingRepo.value = true
          val fetched = StoreRepository.fetchGitHubRelease(githubParam)
          _isSearchingRepo.value = false
          if (fetched != null) {
            val enriched = StoreRepository.updateInstalledStatus(context, listOf(fetched)).first()
            _selectedStoreApp.value = enriched
            showMessage("未登録リポジトリからアプリ詳細を動的生成しました: ${enriched.name}")
          } else {
            showMessage("指定されたGitHubリポジトリからアプリ情報を取得できませんでした")
          }
        }
        return
      }

      // Construct standalone StoreAppItem directly from URI parameters without needing repository registration
      val dynamicName = uri.getQueryParameter("name") ?: pkgParam?.substringAfterLast('.') ?: appId.ifBlank { "External Store App" }
      val dynamicPkg = pkgParam ?: "com.hpjominii.dynamic.${dynamicName.lowercase().replace("[^a-z0-9]".toRegex(), "")}"
      val dynamicVer = uri.getQueryParameter("version") ?: "v1.0"
      val dynamicSummary = uri.getQueryParameter("summary") ?: "hpjominii:// リンクから直接開かれたアプリ詳細ページ"
      val dynamicDesc = uri.getQueryParameter("desc") ?: uri.getQueryParameter("description") ?: dynamicSummary
      val dynamicAuthor = uri.getQueryParameter("author") ?: "External Developer"
      val dynamicIcon = uri.getQueryParameter("icon") ?: ""
      val dynamicLicense = uri.getQueryParameter("license") ?: "Custom"

      val dynamicItem = StoreAppItem(
        id = appId.ifBlank { "deeplink_${System.currentTimeMillis()}" },
        name = dynamicName,
        packageName = dynamicPkg,
        summary = dynamicSummary,
        description = dynamicDesc,
        category = uri.getQueryParameter("category") ?: "ディープリンク",
        author = dynamicAuthor,
        iconUrl = dynamicIcon,
        githubRepo = githubParam,
        websiteUrl = uri.getQueryParameter("website") ?: "",
        sourceRepoName = "Direct DeepLink (リポジトリ未登録でも利用可能)",
        license = dynamicLicense,
        latestVersion = dynamicVer,
        downloadUrl = apkUrlParam ?: "",
        sizeBytes = uri.getQueryParameter("size")?.toLongOrNull() ?: 15_000_000L,
        releaseNotes = uri.getQueryParameter("changelog") ?: "ディープリンク経由のパッケージ",
        releaseDate = uri.getQueryParameter("date") ?: "最新"
      )
      val checkedItem = StoreRepository.updateInstalledStatus(context, listOf(dynamicItem)).first()
      _selectedStoreApp.value = checkedItem
      return
    }

    // 3. Repository / Multi-Repository Addition Link:
    // hpjominii://store/repo?url=... or hpjominii://store?urls=url1,url2&github=owner/repo1,owner/repo2
    _currentTab.value = NavigationTab.STORE
    val repoUrls = mutableListOf<String>()
    val githubRepos = mutableListOf<String>()

    uri.getQueryParameters("url").forEach { repoUrls.addAll(it.split(",").map(String::trim).filter(String::isNotBlank)) }
    uri.getQueryParameters("urls").forEach { repoUrls.addAll(it.split(",").map(String::trim).filter(String::isNotBlank)) }
    uri.getQueryParameters("repo").forEach {
      it.split(",").map(String::trim).filter(String::isNotBlank).forEach { item ->
        if (item.startsWith("http")) repoUrls.add(item) else githubRepos.add(item)
      }
    }
    uri.getQueryParameters("repos").forEach {
      it.split(",").map(String::trim).filter(String::isNotBlank).forEach { item ->
        if (item.startsWith("http")) repoUrls.add(item) else githubRepos.add(item)
      }
    }
    uri.getQueryParameters("github").forEach {
      githubRepos.addAll(it.split(",").map(String::trim).filter(String::isNotBlank))
    }

    _deepLinkState.value = DeepLinkConfirmationState(
      rawUri = uri.toString(),
      type = DeepLinkType.ADD_REPOS,
      repoUrls = repoUrls.distinct(),
      githubRepos = githubRepos.distinct()
    )
  }

  fun fetchGitHubRepo(repoInput: String) {
    if (repoInput.isBlank()) return
    batchImportStoreSources(repoInput)
  }

  fun syncCustomHost(url: String) {
    if (url.isBlank()) return
    batchImportStoreSources(url)
  }

  /**
   * Supports adding single or multiple GitHub repos, JSON repository URLs, or raw JSON app lists at once.
   */
  fun batchImportStoreSources(rawInput: String) {
    if (rawInput.isBlank()) return
    viewModelScope.launch {
      _isSearchingRepo.value = true
      try {
        val (newRepos, newApps) = StoreRepository.batchImportSources(rawInput)
        if (newRepos.isNotEmpty()) {
          val existingIds = newRepos.map { it.id }.toSet()
          _repositories.value = newRepos + _repositories.value.filter { it.id !in existingIds }
        }
        if (newApps.isNotEmpty()) {
          val newIds = newApps.map { it.id }.toSet()
          val merged = newApps + _storeApps.value.filter { it.id !in newIds }
          _storeApps.value = StoreRepository.updateInstalledStatus(context, merged)
          showMessage("${newApps.size} 件のアプリ（リポジトリ ${newRepos.size} 件）を一括追加・同期しました")
        } else {
          showMessage("有効なアプリまたはリリースが見つかりませんでした。入力内容を確認してください。")
        }
      } catch (e: Exception) {
        showMessage("一括同期エラー: ${e.message}")
      } finally {
        _isSearchingRepo.value = false
      }
    }
  }

  fun downloadAndInstallStoreApp(item: StoreAppItem, overrideDownloadUrl: String? = null) {
    viewModelScope.launch {
      StoreRepository.downloadApkStream(context, item, overrideDownloadUrl).collect { progress ->
        val map = _downloadProgressMap.value.toMutableMap()
        map[item.id] = progress
        _downloadProgressMap.value = map

        if (progress.targetFile != null && progress.progress >= 1.0f) {
          showMessage("ダウンロード完了: ${item.name} をインストール準備中...")
          parsePackageFromFile(progress.targetFile)
        } else if (progress.isError) {
          showMessage(progress.statusMessage)
        }
      }
    }
  }

  // APPS MANAGER ACTIONS
  fun setAppsSearchQuery(q: String) {
    _appsSearchQuery.value = q
  }

  fun setAppsFilter(f: AppsFilter) {
    _appsFilter.value = f
  }

  fun inspectApp(item: InstalledAppItem?) {
    _inspectingApp.value = item
  }

  fun toggleSelectPackage(pkg: String) {
    val set = _selectedPackages.value.toMutableSet()
    if (set.contains(pkg)) set.remove(pkg) else set.add(pkg)
    _selectedPackages.value = set
  }

  fun selectAllVisiblePackages(pkgs: List<String>) {
    _selectedPackages.value = pkgs.toSet()
  }

  fun clearSelection() {
    _selectedPackages.value = emptySet()
  }

  fun refreshInstalledApps() {
    viewModelScope.launch {
      _isLoadingApps.value = true
      try {
        val list = AppsManagerRepository.loadInstalledApps(context)
        _installedApps.value = list
        _storeApps.value = StoreRepository.updateInstalledStatus(context, _storeApps.value)
        _selectedStoreApp.value = _selectedStoreApp.value?.let { selected ->
          StoreRepository.updateInstalledStatus(context, listOf(selected)).firstOrNull()
        }
      } catch (e: Exception) {
        showMessage("アプリ一覧の取得エラー: ${e.message}")
      } finally {
        _isLoadingApps.value = false
      }
    }
  }

  // BACKUP FORMAT & EXPORT ACTIONS
  fun setBackupExportFormat(format: BackupExportFormat) {
    _selectedBackupFormat.value = format
  }

  fun batchBackupSelected(
    backupDataWithRoot: Boolean = false,
    format: BackupExportFormat = _selectedBackupFormat.value
  ) {
    val selected = _selectedPackages.value.toList()
    if (selected.isEmpty()) return

    viewModelScope.launch {
      var successCount = 0
      for (pkg in selected) {
        val res = BackupManager.backupApp(context, pkg, backupDataWithRoot, format)
        if (res.isSuccess) successCount++
      }
      showMessage("$successCount 件のアプリを .${format.extension} 形式で一括バックアップしました")
      clearSelection()
      refreshBackups()
    }
  }

  fun backupSingleApp(
    packageName: String,
    backupDataWithRoot: Boolean = false,
    format: BackupExportFormat = _selectedBackupFormat.value
  ) {
    viewModelScope.launch {
      val res = BackupManager.backupApp(context, packageName, backupDataWithRoot, format)
      if (res.isSuccess) {
        val app = res.getOrNull()
        showMessage("「${app?.appLabel ?: packageName}」を .${format.extension} 形式で書き出しました")
        refreshBackups()
      } else {
        showMessage("バックアップ失敗: ${res.exceptionOrNull()?.message}")
      }
    }
  }

  fun uninstallSingleApp(packageName: String) {
    viewModelScope.launch {
      if (RootHelper.isRootAvailable()) {
        val res = RootHelper.silentUninstall(packageName)
        if (res.success) {
          showMessage("$packageName をサイレント削除しました")
          refreshInstalledApps()
        } else {
          showMessage("アンインストール失敗: ${res.output}")
        }
      } else {
        AppsManagerRepository.requestUninstall(context, packageName)
      }
    }
  }

  fun batchUninstallSelected() {
    val selected = _selectedPackages.value.toList()
    if (selected.isEmpty()) return

    viewModelScope.launch {
      if (RootHelper.isRootAvailable()) {
        for (pkg in selected) {
          RootHelper.silentUninstall(pkg)
        }
        showMessage("${selected.size} 件のアプリをサイレントアンインストールしました")
        clearSelection()
        refreshInstalledApps()
      } else {
        for (pkg in selected) {
          AppsManagerRepository.requestUninstall(context, pkg)
        }
        clearSelection()
      }
    }
  }

  fun openAppSettings(packageName: String) {
    AppsManagerRepository.openAppDetails(context, packageName)
  }

  fun optimizeAppWithArt(packageName: String) {
    viewModelScope.launch {
      if (RootHelper.isRootAvailable()) {
        val ok = AppsManagerRepository.compileAppSpeed(packageName)
        if (ok) {
          showMessage("$packageName のARTコンパイル最適化 (Speed) が完了しました")
        } else {
          showMessage("最適化コマンドが失敗しました")
        }
      } else {
        showMessage("ARTコンパイルにはRoot権限が必要です")
      }
    }
  }

  // REMOTE SERVER & CLOUDFLARE TUNNEL ACTIONS
  fun toggleRemoteServer() {
    if (remoteServerStatus.value.isRunning) {
      remoteServer.stop()
      showMessage("PC遠隔導入サーバーを停止しました")
    } else {
      remoteServer.start(remoteServerStatus.value.port)
      showMessage("PC遠隔導入サーバーを開始しました (Port ${remoteServerStatus.value.port})")
    }
  }

  fun updateRemotePort(newPort: Int) {
    if (newPort !in 1024..65535) {
      showMessage("ポート番号は 1024 〜 65535 の範囲で指定してください")
      return
    }
    remoteServer.setPort(newPort)
    showMessage("遠隔サーバーのポートを :$newPort に変更しました")
  }

  fun setCfTunnelToken(token: String) {
    remoteServer.setCfTunnelToken(token)
  }

  fun toggleCloudflareTunnel(customPublicHost: String = "") {
    if (remoteServerStatus.value.cfTunnelRunning) {
      remoteServer.stopCloudflareTunnel()
      showMessage("Cloudflare Tunnel 連携を停止しました")
    } else {
      if (!remoteServerStatus.value.isRunning) {
        remoteServer.start(remoteServerStatus.value.port)
      }
      remoteServer.startCloudflareTunnel(customPublicHost)
      showMessage("Cloudflare Tunnel 連携を開始しました")
    }
  }

  private fun handleFileReceivedFromRemote(file: File) {
    viewModelScope.launch {
      showMessage("PCからパッケージを受信しました: ${file.name}")
      parsePackageFromFile(file)
    }
  }

  // OPTIMIZER & BACKUP ACTIONS
  fun checkOptimization() {
    viewModelScope.launch {
      _optimizationState.value = AppsManagerRepository.checkOptimizationState(context)
    }
  }

  fun cleanSystemCache() {
    viewModelScope.launch {
      _isOptimizing.value = true
      try {
        val deleted = AppsManagerRepository.cleanCaches(context)
        val mb = deleted / 1024 / 1024
        showMessage("不要キャッシュを ${mb}MB クリーンアップしました")
        checkOptimization()
      } catch (e: Exception) {
        showMessage("クリーンアップエラー: ${e.message}")
      } finally {
        _isOptimizing.value = false
      }
    }
  }

  fun refreshBackups() {
    viewModelScope.launch {
      _backupsList.value = BackupManager.listBackups(context)
    }
  }

  fun deleteBackup(item: BackupArchiveItem) {
    viewModelScope.launch {
      BackupManager.deleteBackup(item)
      refreshBackups()
      showMessage("バックアップ「${item.appLabel}」を削除しました")
    }
  }

  fun shareBackup(item: BackupArchiveItem) {
    BackupManager.shareBackup(context, item)
  }

  fun restoreBackup(item: BackupArchiveItem) {
    val file = File(item.filePath)
    if (file.exists()) {
      parsePackageFromFile(file)
    } else {
      showMessage("バックアップファイルが見つかりません")
    }
  }

  override fun onCleared() {
    super.onCleared()
    remoteServer.stop()
  }
}
