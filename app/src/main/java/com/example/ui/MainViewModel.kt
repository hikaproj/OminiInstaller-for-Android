package com.example.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.BackupManager
import com.example.installer.PackageInstallerManager
import com.example.installer.PackageParser
import com.example.installer.RootHelper
import com.example.model.ApkArchiveInfo
import com.example.model.BackupArchiveItem
import com.example.model.DeviceOptimizationState
import com.example.model.InstalledAppItem
import com.example.model.StoreAppItem
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

  val installSessionState = PackageInstallerManager.sessionState

  // Store State
  private val _storeApps = MutableStateFlow<List<StoreAppItem>>(StoreRepository.defaultCuratedApps)
  val storeApps: StateFlow<List<StoreAppItem>> = _storeApps.asStateFlow()

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

  // Remote Server
  private val remoteServer: RemoteInstallerServer = RemoteInstallerServer(context) { file ->
    // On file received from PC
    handleFileReceivedFromRemote(file)
  }
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

  // STORE ACTIONS
  fun setStoreSearchQuery(query: String) {
    _storeSearchQuery.value = query
  }

  fun setSelectedCategory(cat: String) {
    _selectedCategory.value = cat
  }

  fun fetchGitHubRepo(repoInput: String) {
    if (repoInput.isBlank()) return
    viewModelScope.launch {
      _isSearchingRepo.value = true
      try {
        val item = StoreRepository.fetchGitHubRelease(repoInput)
        if (item != null) {
          val updated = listOf(item) + _storeApps.value.filter { it.id != item.id }
          _storeApps.value = StoreRepository.updateInstalledStatus(context, updated)
          showMessage("GitHubから「${item.name}」を取得しました")
        } else {
          showMessage("リリースまたはAPKが見つかりませんでした。リポジトリ名を確認してください。")
        }
      } catch (e: Exception) {
        showMessage("GitHub取得エラー: ${e.message}")
      } finally {
        _isSearchingRepo.value = false
      }
    }
  }

  fun syncCustomHost(url: String) {
    if (url.isBlank()) return
    viewModelScope.launch {
      _isSearchingRepo.value = true
      try {
        val items = StoreRepository.syncCustomHost(url)
        if (items.isNotEmpty()) {
          val updated = items + _storeApps.value.filter { existing -> items.none { it.id == existing.id } }
          _storeApps.value = StoreRepository.updateInstalledStatus(context, updated)
          showMessage("${items.size}件のアプリを独自ホストから同期しました")
        } else {
          showMessage("独自ホストからアプリを取得できませんでした")
        }
      } catch (e: Exception) {
        showMessage("同期エラー: ${e.message}")
      } finally {
        _isSearchingRepo.value = false
      }
    }
  }

  fun downloadAndInstallStoreApp(item: StoreAppItem) {
    viewModelScope.launch {
      StoreRepository.downloadApkStream(context, item).collect { progress ->
        val map = _downloadProgressMap.value.toMutableMap()
        map[item.id] = progress
        _downloadProgressMap.value = map

        if (progress.targetFile != null && progress.progress >= 1.0f) {
          // Finished download -> handover to installer!
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
      } catch (e: Exception) {
        showMessage("アプリ一覧の取得エラー: ${e.message}")
      } finally {
        _isLoadingApps.value = false
      }
    }
  }

  fun batchBackupSelected(backupDataWithRoot: Boolean = false) {
    val selected = _selectedPackages.value.toList()
    if (selected.isEmpty()) return

    viewModelScope.launch {
      var successCount = 0
      for (pkg in selected) {
        val res = BackupManager.backupApp(context, pkg, backupDataWithRoot)
        if (res.isSuccess) successCount++
      }
      showMessage("$successCount 件のアプリのバックアップが完了しました")
      clearSelection()
      refreshBackups()
    }
  }

  fun backupSingleApp(packageName: String, backupDataWithRoot: Boolean = false) {
    viewModelScope.launch {
      val res = BackupManager.backupApp(context, packageName, backupDataWithRoot)
      if (res.isSuccess) {
        val app = res.getOrNull()
        showMessage("「${app?.appLabel ?: packageName}」のバックアップが完了しました")
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
        // Non root: sequentially launch uninstall requests
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

  // REMOTE SERVER ACTIONS
  fun toggleRemoteServer() {
    if (remoteServerStatus.value.isRunning) {
      remoteServer.stop()
      showMessage("PC遠隔導入サーバーを停止しました")
    } else {
      remoteServer.start()
      showMessage("PC遠隔導入サーバーを開始しました")
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
