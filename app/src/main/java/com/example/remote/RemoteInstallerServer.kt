package com.example.remote

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import android.util.Log
import com.example.backup.BackupManager
import com.example.installer.PackageInstallerManager
import com.example.installer.PackageParser
import com.example.installer.RootHelper
import com.example.model.AppMetadataOverride
import com.example.model.BackupExportFormat
import com.example.model.RemoteServerStatus
import com.example.model.StoreAppItem
import com.example.optimizer.AppsManagerRepository
import com.example.store.StoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

class RemoteInstallerServer(
  private val context: Context,
  private val onPackageReceived: (File) -> Unit,
  private val onDirectUrlReceived: ((String) -> Unit)? = null,
  private val onRemoteStateChanged: (() -> Unit)? = null,
  private val onPackageReceivedAdvanced: ((File, AppMetadataOverride?, Boolean) -> Unit)? = null,
  private val onMultiplePackagesReceived: ((List<File>, Boolean) -> Unit)? = null,
  private val getStoreAppsProvider: (() -> List<StoreAppItem>)? = null,
  private val onBatchImportStoreSources: ((String) -> Unit)? = null,
  private val onInstallStoreApp: ((StoreAppItem) -> Unit)? = null
) {

  companion object {
    private const val TAG = "RemoteInstallerServer"
    private const val PREFS_NAME = "omni_remote_server_prefs"
    const val DEFAULT_PORT = 8888
  }

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _status = MutableStateFlow(loadInitialStatus())
  val status: StateFlow<RemoteServerStatus> = _status.asStateFlow()

  private var serverJob: Job? = null
  private var serverSocket: ServerSocket? = null
  private var cfTunnelProcess: Process? = null
  private var cfTunnelJob: Job? = null
  private val serverScope = CoroutineScope(Dispatchers.IO)

  private val multiApkBatches = ConcurrentHashMap<String, MutableList<File>>()

  init {
    if (_status.value.autoStartOnLaunch) {
      start(_status.value.port)
    }
  }

  private fun loadInitialStatus(): RemoteServerStatus {
    val savedPort = prefs.getInt("port", DEFAULT_PORT).coerceIn(1024, 65535)
    val autoStart = prefs.getBoolean("autoStartOnLaunch", false)
    val allowAppMgmt = prefs.getBoolean("allowRemoteAppManagement", true)
    val allowPermEdit = prefs.getBoolean("allowRemotePermissionEdit", true)
    val allowUrlInstall = prefs.getBoolean("allowRemoteUrlInstall", true)
    val cfToken = prefs.getString("cfTunnelToken", "") ?: ""
    val cfHost = prefs.getString("cfCustomHost", "") ?: ""

    return RemoteServerStatus(
      isRunning = false,
      port = savedPort,
      autoStartOnLaunch = autoStart,
      allowRemoteAppManagement = allowAppMgmt,
      allowRemotePermissionEdit = allowPermEdit,
      allowRemoteUrlInstall = allowUrlInstall,
      cfTunnelToken = cfToken,
      cfCustomHost = cfHost
    )
  }

  private fun persistSettings(state: RemoteServerStatus = _status.value) {
    prefs.edit()
      .putInt("port", state.port)
      .putBoolean("autoStartOnLaunch", state.autoStartOnLaunch)
      .putBoolean("allowRemoteAppManagement", state.allowRemoteAppManagement)
      .putBoolean("allowRemotePermissionEdit", state.allowRemotePermissionEdit)
      .putBoolean("allowRemoteUrlInstall", state.allowRemoteUrlInstall)
      .putString("cfTunnelToken", state.cfTunnelToken)
      .putString("cfCustomHost", state.cfCustomHost)
      .apply()
  }

  fun setPort(newPort: Int) {
    val validPort = newPort.coerceIn(1024, 65535)
    val wasRunning = _status.value.isRunning
    _status.value = _status.value.copy(port = validPort)
    persistSettings()
    if (wasRunning) {
      stop()
      start(validPort)
    }
  }

  fun setAutoStartOnLaunch(enabled: Boolean) {
    _status.value = _status.value.copy(autoStartOnLaunch = enabled)
    persistSettings()
  }

  fun setRemotePermissions(
    allowAppManagement: Boolean = _status.value.allowRemoteAppManagement,
    allowPermissionEdit: Boolean = _status.value.allowRemotePermissionEdit,
    allowUrlInstall: Boolean = _status.value.allowRemoteUrlInstall
  ) {
    _status.value = _status.value.copy(
      allowRemoteAppManagement = allowAppManagement,
      allowRemotePermissionEdit = allowPermissionEdit,
      allowRemoteUrlInstall = allowUrlInstall
    )
    persistSettings()
    addLog("遠隔操作権限を更新しました (アプリ管理:$allowAppManagement / 権限・情報編集:$allowPermissionEdit)")
  }

  fun setCfTunnelToken(token: String) {
    _status.value = _status.value.copy(cfTunnelToken = token)
    persistSettings()
  }

  fun setCfCustomHost(host: String) {
    _status.value = _status.value.copy(cfCustomHost = host)
    persistSettings()
  }

  fun start(port: Int = _status.value.port) {
    if (serverJob?.isActive == true) return

    val validPort = port.coerceIn(1024, 65535)
    val allIps = getAllLocalIpAddresses()
    val primaryIp = allIps.firstOrNull()?.substringBefore(" ") ?: "127.0.0.1"

    serverJob = serverScope.launch {
      try {
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress("0.0.0.0", validPort))
        serverSocket = socket

        val url = "http://$primaryIp:$validPort"
        addLog("PC遠隔サーバー起動: $url (0.0.0.0:$validPort)")

        _status.value = _status.value.copy(
          isRunning = true,
          port = validPort,
          ipAddress = primaryIp,
          allIpAddresses = allIps,
          serverUrl = url
        )
        persistSettings()

        while (isActive && serverSocket?.isClosed == false) {
          try {
            val client = serverSocket?.accept() ?: break
            launch { handleClient(client) }
          } catch (e: Exception) {
            if (!isActive) break
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Server error: ${e.message}", e)
        addLog("サーバー起動エラー (Port $validPort): ${e.message}")
        stop()
      }
    }
  }

  fun stop() {
    try {
      stopCloudflareTunnel()
      serverSocket?.close()
      serverSocket = null
      serverJob?.cancel()
      serverJob = null
      _status.value = _status.value.copy(
        isRunning = false,
        serverUrl = ""
      )
      addLog("PC遠隔サーバーを停止しました")
    } catch (e: Exception) {
      Log.e(TAG, "Stop error: ${e.message}")
    }
  }

  fun startCloudflareTunnel(customPublicHost: String = _status.value.cfCustomHost) {
    if (!_status.value.isRunning) {
      start(_status.value.port)
    }
    val port = _status.value.port
    val token = _status.value.cfTunnelToken.trim()
    val hostToUse = customPublicHost.trim()

    if (hostToUse != _status.value.cfCustomHost) {
      setCfCustomHost(hostToUse)
    }

    if (hostToUse.isNotBlank()) {
      val cleanUrl = if (hostToUse.startsWith("http")) hostToUse else "https://$hostToUse"
      _status.value = _status.value.copy(
        cfTunnelEnabled = true,
        cfTunnelRunning = true,
        cfPublicUrl = cleanUrl
      )
      addLog("Cloudflare Tunnel カスタムホスト設定完了: $cleanUrl -> localhost:$port")
      return
    }

    cfTunnelJob?.cancel()
    cfTunnelJob = serverScope.launch {
      _status.value = _status.value.copy(cfTunnelEnabled = true)
      addLog("Cloudflare Tunnel (cloudflared) 接続を開始中... (http://127.0.0.1:$port)")

      val candidatePaths = listOf(
        "cloudflared",
        "/data/data/com.termux/files/usr/bin/cloudflared",
        "/system/bin/cloudflared",
        "/system/xbin/cloudflared",
        File(context.filesDir, "cloudflared").absolutePath
      )

      val binaryPath = candidatePaths.firstOrNull { path ->
        path == "cloudflared" || File(path).canExecute()
      } ?: "cloudflared"

      try {
        val cmd = if (token.isNotBlank()) {
          arrayOf(binaryPath, "tunnel", "--no-autoupdate", "run", "--token", token, "--url", "http://127.0.0.1:$port")
        } else {
          arrayOf(binaryPath, "tunnel", "--no-autoupdate", "--url", "http://127.0.0.1:$port")
        }

        val proc = Runtime.getRuntime().exec(cmd)
        cfTunnelProcess = proc
        _status.value = _status.value.copy(cfTunnelRunning = true)

        val reader = BufferedReader(InputStreamReader(proc.errorStream))
        val urlRegex = Regex("""https://[a-zA-Z0-9-]+\.trycloudflare\.com""")
        while (isActive) {
          val l = reader.readLine() ?: break
          val match = urlRegex.find(l)
          if (match != null) {
            val publicUrl = match.value
            _status.value = _status.value.copy(cfPublicUrl = publicUrl, cfTunnelRunning = true)
            addLog("Cloudflare Tunnel 公開URL発行完了: $publicUrl")
          }
        }
      } catch (e: Exception) {
        addLog("端末内cloudflared未検出のため、PC/Termux連携コマンドモードで待機中 (Port $port)")
        _status.value = _status.value.copy(
          cfTunnelRunning = false,
          cfPublicUrl = ""
        )
      }
    }
  }

  fun stopCloudflareTunnel() {
    try {
      cfTunnelProcess?.destroy()
      cfTunnelProcess = null
      cfTunnelJob?.cancel()
      cfTunnelJob = null
      _status.value = _status.value.copy(
        cfTunnelEnabled = false,
        cfTunnelRunning = false,
        cfPublicUrl = ""
      )
      addLog("Cloudflare Tunnel 連携を停止しました")
    } catch (_: Exception) {}
  }

  fun addLog(message: String) {
    val updated = (_status.value.logs + message).takeLast(50)
    _status.value = _status.value.copy(logs = updated)
  }

  private fun handleClient(socket: Socket) {
    socket.use { s ->
      try {
        s.soTimeout = 120_000
        val input = BufferedInputStream(s.getInputStream())
        val output = BufferedOutputStream(s.getOutputStream())

        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0].uppercase()
        val rawPath = parts[1]
        val path = rawPath.substringBefore("?")
        val query = rawPath.substringAfter("?", "")

        val headers = mutableMapOf<String, String>()
        while (true) {
          val headerLine = readLine(input) ?: break
          if (headerLine.isEmpty()) break
          val idx = headerLine.indexOf(":")
          if (idx != -1) {
            val key = headerLine.substring(0, idx).trim().lowercase()
            val value = headerLine.substring(idx + 1).trim()
            headers[key] = value
          }
        }

        when {
          method == "OPTIONS" -> sendCorsPreflight(output)
          method == "GET" && (path == "/" || path.startsWith("/index")) -> serveHtml(output)
          method == "GET" && path == "/status" -> serveStatus(output)
          method == "POST" && path == "/upload" -> handleUpload(headers, input, output)
          method == "POST" && path == "/api/upload-bundle" -> handleUploadBundle(query, headers, input, output)
          method == "POST" && path == "/install-url" -> handleRemoteUrlInstall(headers, input, output)
          method == "POST" && path == "/api/session/install" -> handleTriggerSessionInstall(output)
          method == "GET" && path == "/api/apps" -> handleListApps(output)
          method == "GET" && path == "/api/apps/icon" -> handleAppIcon(query, output)
          method == "POST" && path == "/api/apps/launch" -> handleLaunchApp(headers, input, output)
          method == "POST" && path == "/api/apps/uninstall" -> handleUninstallApp(headers, input, output)
          method == "GET" && path == "/api/apps/export" -> handleExportApp(query, output)
          method == "POST" && path == "/api/apps/metadata" -> handleUpdateMetadata(headers, input, output)
          method == "POST" && path == "/api/apps/permission" -> handleTogglePermission(headers, input, output)
          method == "GET" && path == "/api/backups" -> handleListBackups(output)
          method == "POST" && path == "/api/backups/create" -> handleCreateBackup(headers, input, output)
          method == "GET" && path == "/api/backups/download" -> handleDownloadBackup(query, output)
          method == "POST" && path == "/api/backups/restore" -> handleRestoreBackup(headers, input, output)
          method == "POST" && path == "/api/backups/delete" -> handleDeleteBackup(headers, input, output)
          method == "GET" && path == "/api/store" -> handleListStore(output)
          method == "POST" && path == "/api/store/add-repos" -> handleAddStoreRepos(headers, input, output)
          method == "POST" && path == "/api/store/install" -> handleInstallStoreApp(headers, input, output)
          method == "POST" && path == "/api/system/clean-cache" -> handleCleanCache(output)
          else -> sendResponse(output, 404, "text/plain; charset=UTF-8", "Not Found".toByteArray(Charsets.UTF_8))
        }
      } catch (e: Exception) {
        Log.e(TAG, "Client handle exception", e)
      }
    }
  }

  private fun parseQueryParams(query: String): Map<String, String> {
    if (query.isBlank()) return emptyMap()
    val map = mutableMapOf<String, String>()
    query.split("&").forEach { pair ->
      val kv = pair.split("=", limit = 2)
      if (kv.size == 2) {
        val k = runCatching { URLDecoder.decode(kv[0], "UTF-8") }.getOrDefault(kv[0])
        val v = runCatching { URLDecoder.decode(kv[1], "UTF-8") }.getOrDefault(kv[1])
        map[k] = v
      }
    }
    return map
  }

  private fun readJsonBody(headers: Map<String, String>, input: InputStream): JSONObject {
    val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 524288) ?: 0
    if (contentLength <= 0) return JSONObject()
    val buf = ByteArray(contentLength)
    var read = 0
    while (read < contentLength) {
      val r = input.read(buf, read, contentLength - read)
      if (r <= 0) break
      read += r
    }
    return try {
      JSONObject(String(buf, 0, read, Charsets.UTF_8))
    } catch (_: Exception) {
      JSONObject()
    }
  }

  private fun serveHtml(output: OutputStream) {
    val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
    val osVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    val abis = Build.SUPPORTED_ABIS.joinToString(", ")
    val html = RemoteWebConsoleHtml.buildHtml(deviceModel, osVer, abis, _status.value)
    sendResponse(output, 200, "text/html; charset=UTF-8", html.toByteArray(Charsets.UTF_8))
  }

  private fun serveStatus(output: OutputStream) {
    val st = _status.value
    val session = PackageInstallerManager.sessionState.value

    // Storage telemetry
    val statFs = StatFs(Environment.getDataDirectory().path)
    val totalBytes = statFs.totalBytes
    val freeBytes = statFs.availableBytes
    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
    val totalGB = String.format("%.1f", totalBytes / 1073741824.0)
    val freeGB = String.format("%.1f", freeBytes / 1073741824.0)
    val storageUsedPct = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 30

    // Memory telemetry
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am?.getMemoryInfo(memInfo)
    val totalRamMB = (memInfo.totalMem / 1048576L).coerceAtLeast(1L)
    val availRamMB = (memInfo.availMem / 1048576L).coerceAtLeast(0L)
    val usedRamMB = (totalRamMB - availRamMB).coerceAtLeast(0L)
    val ramUsedPct = ((usedRamMB * 100) / totalRamMB).toInt()

    val isRoot = runBlocking { RootHelper.isRootAvailable() }

    val json = JSONObject().apply {
      put("running", st.isRunning)
      put("port", st.port)
      put("uploads", st.uploadedFilesCount)
      put("allowRemoteAppManagement", st.allowRemoteAppManagement)
      put("allowRemotePermissionEdit", st.allowRemotePermissionEdit)
      put("allowRemoteUrlInstall", st.allowRemoteUrlInstall)
      put("storageText", "$freeGB GB Free / $totalGB GB")
      put("storageUsedPct", storageUsedPct)
      put("ramText", "$usedRamMB / $totalRamMB MB")
      put("ramUsedPct", ramUsedPct)
      put("privilegeText", if (isRoot) "Root Privileged" else "Standard PackageInstaller")
      put("sessionStatus", session.status.name)
      put("sessionTask", session.currentTask)
      put("sessionProgress", session.progress)
      put("parsedPackageName", session.packageName)
      put("parsedAppLabel", session.archiveInfo?.appLabel ?: "")
      val logArr = JSONArray()
      st.logs.forEach { logArr.put(it) }
      put("logs", logArr)
    }
    sendResponse(output, 200, "application/json; charset=UTF-8", json.toString().toByteArray(Charsets.UTF_8))
  }

  private fun handleTriggerSessionInstall(output: OutputStream) {
    val archive = PackageInstallerManager.sessionState.value.archiveInfo
    if (archive == null) {
      sendResponse(output, 400, "application/json", """{"status":"error","message":"No package currently loaded on device"}""".toByteArray())
      return
    }
    serverScope.launch {
      PackageInstallerManager.installPackage(context, archive, RootHelper.isRootAvailable())
    }
    sendResponse(output, 200, "application/json", """{"status":"ok","message":"Installation session started on device"}""".toByteArray())
  }

  private fun handleListApps(output: OutputStream) {
    if (!_status.value.allowRemoteAppManagement) {
      sendResponse(output, 403, "application/json", """{"status":"error","message":"Remote App Management permission is disabled on device"}""".toByteArray())
      return
    }
    val apps = runBlocking { AppsManagerRepository.loadInstalledApps(context) }
    val arr = JSONArray()
    apps.take(250).forEach { app ->
      val permArr = JSONArray()
      app.permissions.forEach { p ->
        permArr.put(JSONObject().apply {
          put("name", p.name)
          put("fullName", p.fullName)
          put("granted", p.isGranted)
          put("group", p.group)
        })
      }
      arr.put(JSONObject().apply {
        put("packageName", app.packageName)
        put("customPackageName", app.customPackageName)
        put("appLabel", app.appLabel)
        put("versionName", app.versionName)
        put("versionCode", app.versionCode)
        put("category", app.category)
        put("isSystemApp", app.isSystemApp)
        put("isSplitApk", app.isSplitApk)
        put("sizeMB", (app.apkSizeBytes / 1024 / 1024).coerceAtLeast(1))
        put("permissions", permArr)
      })
    }
    val res = JSONObject().apply {
      put("status", "ok")
      put("apps", arr)
    }
    sendResponse(output, 200, "application/json; charset=UTF-8", res.toString().toByteArray(Charsets.UTF_8))
  }

  private fun handleAppIcon(query: String, output: OutputStream) {
    val params = parseQueryParams(query)
    val pkg = params["pkg"] ?: ""
    if (pkg.isNotBlank()) {
      try {
        val drawable = context.packageManager.getApplicationIcon(pkg)
        val bmp = Bitmap.createBitmap(
          drawable.intrinsicWidth.coerceAtLeast(64),
          drawable.intrinsicHeight.coerceAtLeast(64),
          Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 90, baos)
        sendResponse(output, 200, "image/png", baos.toByteArray())
        return
      } catch (_: Exception) {}
    }
    // Fallback clean SVG icon
    val fallbackSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" rx="14" fill="#2563eb"/><path d="M20 26h24v20a4 4 0 0 1-4 4H24a4 4 0 0 1-4-4V26z" fill="#fff"/></svg>"""
    sendResponse(output, 200, "image/svg+xml", fallbackSvg.toByteArray(Charsets.UTF_8))
  }

  private fun handleLaunchApp(headers: Map<String, String>, input: InputStream, output: OutputStream) {
    if (!_status.value.allowRemoteAppManagement) {
      sendResponse(output, 403, "application/json", """{"status":"error","message":"Remote App Management permission is disabled"}""".toByteArray())
      return
    }
    val json = readJsonBody(headers, input)
    val pkg = json.optString("packageName", "")
    val ok = AppsManagerRepository.launchApp(context, pkg)
    addLog("PCから遠隔起動リクエスト: $pkg (${if (ok) "成功" else "失敗"})")
    sendResponse(
      output,
      if (ok) 200 else 400,
      "application/json",
      """{"status":"${if (ok) "ok" else "error"}","message":"${if (ok) "Launched $pkg on device" else "Launch intent not found"}"}""".toByteArray()
    )
  }

  private fun handleUninstallApp(headers: Map<String, String>, input: InputStream, output: OutputStream) {
    if (!_status.value.allowRemoteAppManagement) {
      sendResponse(output, 403, "application/json", """{"status":"error","message":"Remote App Management permission is disabled"}""".toByteArray())
      return
    }
    val json = readJsonBody(headers, input)
    val pkg = json.optString("packageName", "")
    if (pkg.isBlank()) {
      sendResponse(output, 400, "application/json", """{"status":"error","message":"packageName required"}""".toByteArray())
      return
    }
    runBlocking {
      if (RootHelper.isRootAvailable()) {
        RootHelper.silentUninstall(pkg)
      } else {
        AppsManagerRepository.requestUninstall(context, pkg)
      }
    }
    addLog("PCから遠隔アンインストール要求: $pkg")
    onRemoteStateChanged?.invoke()
    sendResponse(output, 200, "application/json", """{"status":"ok","message":"Uninstall requested on device for $pkg"}""".toByteArray())
  }

  private fun handleExportApp(query: String, output: OutputStream) {
    if (!_status.value.allowRemoteAppManagement) {
      sendResponse(output, 403, "text/plain; charset=UTF-8", "Forbidden: Remote App Management disabled".toByteArray())
      return
    }
    val params = parseQueryParams(query)
    val pkg = params["pkg"] ?: ""
    val fmtStr = params["format"] ?: "apk"
    val fmt = BackupExportFormat.values().find { it.extension.equals(fmtStr, ignoreCase = true) } ?: BackupExportFormat.APK

    val backupResult = runBlocking {
      BackupManager.backupApp(context, pkg, false, fmt)
    }
    val item = backupResult.getOrNull()
    val file = item?.let { File(it.filePath) }
    if (file != null && file.exists()) {
      addLog("PCへパッケージ抽出送信: ${file.name}")
      streamFileDownload(output, file)
    } else {
      sendResponse(output, 404, "text/plain", "Package not found".toByteArray())
    }
  }

  private fun handleUpdateMetadata(headers: Map<String, String>, input: InputStream, output: OutputStream) {
    if (!_status.value.allowRemotePermissionEdit) {
      sendResponse(output, 403, "application/json", """{"status":"error","message":"Remote metadata editing permission is disabled"}""".toByteArray())
      return
    }
    val json = readJsonBody(headers, input)
    val origPkg = json.optString("originalPackageName", "")
    if (origPkg.isBlank()) {
      sendResponse(output, 400, "application/json", """{"status":"error","message":"originalPackageName required"}""".toByteArray())
      return
    }
    val override = AppMetadataOverride(
      originalPackageName = origPkg,
      customAppLabel = json.optString("customAppLabel", ""),
      customPackageName = json.optString("customPackageName", origPkg),
      customVersionName = json.optString("customVersionName", "1.0"),
      customVersionCode = json.optLong("customVersionCode", 1L),
      customCategory = json.optString("customCategory", "ユーティリティ")
    )
    AppsManagerRepository.saveMetadataOverride(context, override)
    val reinstallModified = json.optBoolean("reinstallModified", false)

    if (reinstallModified) {
      serverScope.launch {
        val apps = AppsManagerRepository.loadInstalledApps(context)
        val targetApp = apps.find { it.packageName == origPkg }
        if (targetApp != null) {
          val patched = AppsManagerRepository.createModifiedApkFile(
            context = context,
            sourceApkPath = targetApp.sourceDir,
            originalPackageName = origPkg,
            newPackageName = override.customPackageName,
            originalLabel = targetApp.appLabel,
            newLabel = override.customAppLabel,
            originalVersionName = targetApp.versionName,
            newVersionName = override.customVersionName
          )
          onPackageReceivedAdvanced?.invoke(patched, override, true)
        }
      }
    }

    addLog("PCからアプリ情報変更: $origPkg -> ${override.customAppLabel} (${override.customPackageName})")
    onRemoteStateChanged?.invoke()
    sendResponse(output, 200, "application/json", """{"status":"ok","message":"Metadata updated (${override.customPackageName})"}""".toByteArray())
  }

  private fun handleTogglePermission(headers: Map<String, String>, input: InputStream, output: OutputStream) {
    if (!_status.value.allowRemotePermissionEdit) {
      sendResponse(output, 403, "application/json", """{"status":"error","message":"Remote permission editing is disabled"}""".toByteArray())
      return
    }
    val json = readJsonBody(headers, input)
    val pkg = json.optString("packageName", "")
    val perm = json.optString("permission", "")
    val grant = json.optBoolean("grant", true)

    val (ok, msg) = runBlocking {
      AppsManagerRepository.toggleAppPermission(context, pkg, perm, grant)
    }
    addLog("PCから遠隔権限変更: $pkg [$perm -> ${if (grant) "Grant" else "Revoke"}]")
    onRemoteStateChanged?.invoke()
    sendResponse(output, if (ok) 200 else 400, "application/json", JSONObject().apply {
      put("status", if (ok) "ok" else "error")
      put("message", msg)
    }.toString().toByteArray(Charsets.UTF_8))
  }

  private fun handleListBackups(output: OutputStream) {
    val backups = runBlocking { BackupManager.listBackups(context) }
    val arr = JSONArray()
    backups.forEach { b ->
      arr.put(JSONObject().apply {
        put("id", b.id)
        put("packageName", b.packageName)
        put("appLabel", b.appLabel)
        put("versionName", b.versionName)
        put("format", b.exportFormat.extension)
        put("sizeMB", (b.sizeBytes / 1024 / 1024).coerceAtLeast(1))
      })
    }
    val res = JSONObject().apply {
      put("status", "ok")
      put("backups", arr)
    }
    sendResponse(output, 200, "application/json; charset=UTF-8", res.toString().toByteArray(Charsets.UTF_8))
  }

  private fun handleCreateBackup(headers: Map<String, String>, input: InputStream, output: OutputStream) {
    if (!_status.value.allowRemoteAppManagement) {
      sendResponse(output, 403, "application/json", """{"status":"error","message":"Remote App Management permission is disabled"}""".toByteArray())
      return
    }
    val json = readJsonBody(headers, input)
    val pkg = json.optString("packageName", "").trim()
    val fmtExt = json.optString("format", "apks").trim()
    val fmt = BackupExportFormat.values().find { it.extension.equals(fmtExt, ignoreCase = true) } ?: BackupExportFormat.APKS

    val res = runBlocking {
      BackupManager.backupApp(context, pkg, false, fmt)
    }
    onRemoteStateChanged?.invoke()
    if (res.isSuccess) {
      val item = res.getOrNull()
      val exportedName = item?.let { File(it.filePath).name } ?: pkg
      addLog("PCからバックアップ作成: $exportedName")
      sendResponse(output, 200, "application/json", """{"status":"ok","message":"Created backup: $exportedName"}""".toByteArray())
    } else {
      sendResponse(output, 400, "application/json", """{"status":"error","message":"Backup failed: ${res.exceptionOrNull()?.message}"}""".toByteArray())
    }
  }

  private fun handleDownloadBackup(query: String, output: OutputStream) {
    val params = parseQueryParams(query)
    val id = params["id"] ?: ""
    val item = runBlocking { BackupManager.listBackups(context) }.find { it.id == id }
    val file = item?.let { File(it.filePath) }
    if (file != null && file.exists()) {
      streamFileDownload(output, file)
    } else {
      sendResponse(output, 404, "text/plain", "Backup archive not found".toByteArray())
    }
  }

  private fun handleRestoreBackup(headers: Map<String, String>, input: InputStream, output: OutputStream) {
    val json = readJsonBody(headers, input)
    val id = json.optString("id", "")
    val item = runBlocking { BackupManager.listBackups(context) }.find { it.id == id }
    val file = item?.let { File(it.filePath) }
    if (file != null && file.exists()) {
      onPackageReceivedAdvanced?.invoke(file, null, true) ?: onPackageReceived(file)
      sendResponse(output, 200, "application/json", """{"status":"ok","message":"Restoring ${item.appLabel} on device"}""".toByteArray())
    } else {
      sendResponse(output, 404, "application/json", """{"status":"error","message":"Backup not found"}""".toByteArray())
    }
  }

  private fun handleDeleteBackup(headers: Map<String, String>, input: InputStream, output: OutputStream) {
    val json = readJsonBody(headers, input)
    val id = json.optString("id", "")
    val item = runBlocking { BackupManager.listBackups(context) }.find { it.id == id }
    if (item != null) {
      val name = File(item.filePath).name
      runBlocking { BackupManager.deleteBackup(item) }
      onRemoteStateChanged?.invoke()
      sendResponse(output, 200, "application/json", """{"status":"ok","message":"Deleted backup $name"}""".toByteArray())
    } else {
      sendResponse(output, 404, "application/json", """{"status":"error","message":"Backup not found"}""".toByteArray())
    }
  }

  private fun handleListStore(output: OutputStream) {
    val apps = getStoreAppsProvider?.invoke() ?: StoreRepository.defaultCuratedApps
    val arr = JSONArray()
    apps.forEach { app ->
      arr.put(JSONObject().apply {
        put("id", app.id)
        put("name", app.name)
        put("packageName", app.packageName)
        put("author", app.author)
        put("latestVersion", app.latestVersion)
        put("category", app.category)
      })
    }
    val res = JSONObject().apply {
      put("status", "ok")
      put("apps", arr)
    }
    sendResponse(output, 200, "application/json; charset=UTF-8", res.toString().toByteArray(Charsets.UTF_8))
  }

  private fun handleAddStoreRepos(headers: Map<String, String>, input: InputStream, output: OutputStream) {
    val json = readJsonBody(headers, input)
    val rawInput = json.optString("rawInput", "").trim()
    if (rawInput.isNotBlank()) {
      onBatchImportStoreSources?.invoke(rawInput)
      addLog("PCからストアリポジトリ一括追加要求を受信")
    }
    sendResponse(output, 200, "application/json", """{"status":"ok","message":"Repository sync started on device"}""".toByteArray())
  }

  private fun handleInstallStoreApp(headers: Map<String, String>, input: InputStream, output: OutputStream) {
    val json = readJsonBody(headers, input)
    val id = json.optString("id", "")
    val apps = getStoreAppsProvider?.invoke() ?: StoreRepository.defaultCuratedApps
    val target = apps.find { it.id == id }
    if (target != null) {
      onInstallStoreApp?.invoke(target)
      sendResponse(output, 200, "application/json", """{"status":"ok","message":"Downloading ${target.name} on device"}""".toByteArray())
    } else {
      sendResponse(output, 404, "application/json", """{"status":"error","message":"Store app not found"}""".toByteArray())
    }
  }

  private fun handleCleanCache(output: OutputStream) {
    if (!_status.value.allowRemotePermissionEdit) {
      sendResponse(output, 403, "application/json", """{"status":"error","message":"Remote system maintenance permission is disabled"}""".toByteArray())
      return
    }
    val freedBytes = runBlocking { AppsManagerRepository.cleanCaches(context) }
    val mb = freedBytes / 1024 / 1024
    addLog("PCから遠隔キャッシュ消去を実行 (${mb}MB 解放)")
    onRemoteStateChanged?.invoke()
    sendResponse(output, 200, "application/json", """{"status":"ok","message":"Cleaned ${mb} MB of temporary cache"}""".toByteArray())
  }

  private fun handleRemoteUrlInstall(
    headers: Map<String, String>,
    input: InputStream,
    output: OutputStream
  ) {
    if (!_status.value.allowRemoteUrlInstall) {
      sendResponse(output, 403, "application/json", """{"status":"error","message":"Remote URL installation permission is disabled"}""".toByteArray())
      return
    }
    val json = readJsonBody(headers, input)
    val url = json.optString("url", "").trim()

    if (url.startsWith("http://") || url.startsWith("https://")) {
      addLog("PCから直リンク導入リクエストを受信: $url")
      onDirectUrlReceived?.invoke(url)
      sendResponse(output, 200, "application/json", """{"status":"ok","message":"Direct URL download started on device"}""".toByteArray())
    } else {
      sendResponse(output, 400, "application/json", """{"status":"error","message":"Invalid HTTP/HTTPS URL"}""".toByteArray())
    }
  }

  private fun extractOverrideFromHeaders(headers: Map<String, String>): AppMetadataOverride? {
    val customLabel = headers["x-custom-label"]?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) } ?: ""
    val customPkg = headers["x-custom-pkg"]?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) } ?: ""
    val customVer = headers["x-custom-vername"]?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) } ?: ""
    val customCode = headers["x-custom-vercode"]?.toLongOrNull() ?: 0L
    val customCat = headers["x-custom-category"]?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) } ?: ""

    if (customLabel.isBlank() && customPkg.isBlank() && customVer.isBlank() && customCode <= 0L && customCat.isBlank()) {
      return null
    }
    return AppMetadataOverride(
      originalPackageName = "",
      customAppLabel = customLabel,
      customPackageName = customPkg,
      customVersionName = customVer,
      customVersionCode = customCode,
      customCategory = customCat
    )
  }

  private fun handleUploadBundle(
    query: String,
    headers: Map<String, String>,
    input: InputStream,
    output: OutputStream
  ) {
    val params = parseQueryParams(query)
    val batchId = params["batchId"] ?: "default_batch"
    val finalize = params["finalize"] == "1"
    val autoInstall = params["autoInstall"] == "1" || headers["x-auto-install"] == "1"

    val savedFile = saveUploadedStreamToFile(headers, input)
    if (savedFile.exists() && savedFile.length() > 0) {
      val list = multiApkBatches.getOrPut(batchId) { mutableListOf() }
      synchronized(list) {
        list.add(savedFile)
      }
      _status.value = _status.value.copy(
        uploadedFilesCount = _status.value.uploadedFilesCount + 1
      )
      if (finalize) {
        val completedList = multiApkBatches.remove(batchId)?.toList() ?: listOf(savedFile)
        addLog("マルチAPKバンドル受信完了 (${completedList.size} 個のファイル)")
        onMultiplePackagesReceived?.invoke(completedList, autoInstall)
      }
      sendResponse(output, 200, "application/json", """{"status":"ok","message":"Split APK part uploaded"}""".toByteArray())
    } else {
      sendResponse(output, 400, "application/json", """{"status":"error","message":"Empty split file"}""".toByteArray())
    }
  }

  private fun handleUpload(
    headers: Map<String, String>,
    input: InputStream,
    output: OutputStream
  ) {
    val savedFile = saveUploadedStreamToFile(headers, input)
    val autoInstall = headers["x-auto-install"] == "1"
    val overrideMeta = extractOverrideFromHeaders(headers)

    if (savedFile.exists() && savedFile.length() > 0) {
      addLog("パッケージ受信完了: ${savedFile.name} (${savedFile.length() / 1024} KB)")
      _status.value = _status.value.copy(
        uploadedFilesCount = _status.value.uploadedFilesCount + 1
      )
      if (onPackageReceivedAdvanced != null) {
        onPackageReceivedAdvanced.invoke(savedFile, overrideMeta, autoInstall)
      } else {
        onPackageReceived(savedFile)
      }

      val responseJson = """{"status":"ok","message":"Package transferred to Android device"}"""
      sendResponse(output, 200, "application/json", responseJson.toByteArray(Charsets.UTF_8))
    } else {
      sendResponse(output, 400, "application/json", """{"status":"error","message":"No file uploaded"}""".toByteArray())
    }
  }

  private fun saveUploadedStreamToFile(
    headers: Map<String, String>,
    input: InputStream
  ): File {
    val contentType = headers["content-type"] ?: ""
    val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L

    val uploadDir = File(context.cacheDir, "remote_uploads")
    if (!uploadDir.exists()) uploadDir.mkdirs()

    val rawFilename = headers["x-filename"]?.let {
      try { URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
    }

    val savedFile: File
    if (!rawFilename.isNullOrBlank()) {
      val safeName = rawFilename.replace("/", "_").replace("\\", "_")
      savedFile = File(uploadDir, "${System.currentTimeMillis()}_$safeName")
      addLog("PCからのアップロードを受信中: $safeName (${contentLength / 1024} KB)")
      FileOutputStream(savedFile).use { fos ->
        val buffer = ByteArray(65536)
        var totalRead = 0L
        while (contentLength <= 0L || totalRead < contentLength) {
          val toRead = if (contentLength > 0L) {
            minOf(buffer.size.toLong(), contentLength - totalRead).toInt()
          } else {
            buffer.size
          }
          val bytesRead = input.read(buffer, 0, toRead)
          if (bytesRead == -1) break
          fos.write(buffer, 0, bytesRead)
          totalRead += bytesRead
          if (contentLength > 0L && totalRead >= contentLength) break
        }
      }
    } else {
      val boundary = contentType.substringAfter("boundary=", "").trim()
      if (boundary.isNotBlank()) {
        savedFile = parseMultipartAndSave(boundary, input, uploadDir)
      } else {
        savedFile = File(uploadDir, "remote_package_${System.currentTimeMillis()}.apk")
        FileOutputStream(savedFile).use { fos ->
          val buffer = ByteArray(65536)
          var totalRead = 0L
          while (contentLength <= 0L || totalRead < contentLength) {
            val bytesRead = input.read(buffer)
            if (bytesRead == -1) break
            fos.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            if (contentLength > 0L && totalRead >= contentLength) break
          }
        }
      }
    }
    return savedFile
  }

  private fun parseMultipartAndSave(boundary: String, input: InputStream, uploadDir: File): File {
    val tempFile = File(uploadDir, "upload_${System.currentTimeMillis()}.tmp")
    FileOutputStream(tempFile).use { fos ->
      val buffer = ByteArray(65536)
      while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        fos.write(buffer, 0, read)
      }
    }

    var filename = "package_${System.currentTimeMillis()}.apk"
    val headerBuffer = ByteArray(4096)
    var headerEnd = -1
    FileInputStream(tempFile).use { fis ->
      val readBytes = fis.read(headerBuffer)
      if (readBytes > 0) {
        val contentStr = String(headerBuffer, 0, readBytes, Charsets.ISO_8859_1)
        val nameMatch = Regex("""filename="([^"]+)"""").find(contentStr)
        if (nameMatch != null) {
          filename = nameMatch.groupValues[1]
        }
        for (i in 0 until readBytes - 3) {
          if (headerBuffer[i] == 13.toByte() && headerBuffer[i + 1] == 10.toByte() &&
            headerBuffer[i + 2] == 13.toByte() && headerBuffer[i + 3] == 10.toByte()) {
            headerEnd = i + 4
            break
          }
        }
      }
    }

    val finalFile = File(uploadDir, filename.replace("/", "_").replace("\\", "_"))
    if (headerEnd != -1) {
      val boundaryLen = boundary.length + 8
      val finalSize = (tempFile.length() - headerEnd - boundaryLen).coerceAtLeast(0)
      FileInputStream(tempFile).use { fis ->
        fis.skip(headerEnd.toLong())
        FileOutputStream(finalFile).use { fos ->
          val buffer = ByteArray(65536)
          var remaining = finalSize
          while (remaining > 0) {
            val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
            val num = fis.read(buffer, 0, toRead)
            if (num <= 0) break
            fos.write(buffer, 0, num)
            remaining -= num
          }
        }
      }
      tempFile.delete()
      return finalFile
    } else {
      tempFile.renameTo(finalFile)
      return finalFile
    }
  }

  private fun streamFileDownload(output: OutputStream, file: File) {
    val header = "HTTP/1.1 200 OK\r\n" +
      "Content-Type: application/octet-stream\r\n" +
      "Content-Disposition: attachment; filename=\"${file.name}\"\r\n" +
      "Content-Length: ${file.length()}\r\n" +
      "Access-Control-Allow-Origin: *\r\n" +
      "Connection: close\r\n\r\n"
    output.write(header.toByteArray(Charsets.UTF_8))
    FileInputStream(file).use { fis -> fis.copyTo(output, 65536) }
    output.flush()
  }

  private fun sendCorsPreflight(output: OutputStream) {
    val header = "HTTP/1.1 204 No Content\r\n" +
      "Access-Control-Allow-Origin: *\r\n" +
      "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
      "Access-Control-Allow-Headers: Content-Type, X-Filename, X-Auto-Install, X-Custom-Label, X-Custom-Pkg, X-Custom-VerName, X-Custom-VerCode, X-Custom-Category\r\n" +
      "Connection: close\r\n\r\n"
    output.write(header.toByteArray(Charsets.UTF_8))
    output.flush()
  }

  private fun sendResponse(output: OutputStream, statusCode: Int, contentType: String, data: ByteArray) {
    val statusText = when (statusCode) {
      200 -> "OK"
      400 -> "Bad Request"
      403 -> "Forbidden"
      404 -> "Not Found"
      else -> "Error"
    }
    val header = "HTTP/1.1 $statusCode $statusText\r\n" +
      "Content-Type: $contentType\r\n" +
      "Content-Length: ${data.size}\r\n" +
      "Connection: close\r\n" +
      "Access-Control-Allow-Origin: *\r\n" +
      "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
      "Access-Control-Allow-Headers: Content-Type, X-Filename, X-Auto-Install, X-Custom-Label, X-Custom-Pkg, X-Custom-VerName, X-Custom-VerCode, X-Custom-Category\r\n\r\n"
    output.write(header.toByteArray(Charsets.UTF_8))
    output.write(data)
    output.flush()
  }

  private fun readLine(input: InputStream): String? {
    val baos = ByteArrayOutputStream()
    while (true) {
      val b = input.read()
      if (b == -1) {
        if (baos.size() == 0) return null
        break
      }
      if (b == '\n'.code) {
        break
      }
      if (b != '\r'.code) {
        baos.write(b)
      }
    }
    return baos.toString(Charsets.UTF_8.name())
  }

  fun getAllLocalIpAddresses(): List<String> {
    val result = mutableListOf<String>()
    try {
      val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      @Suppress("DEPRECATION")
      val wifiIpInt = wifiManager?.connectionInfo?.ipAddress ?: 0
      if (wifiIpInt != 0) {
        @Suppress("DEPRECATION")
        val wifiIp = Formatter.formatIpAddress(wifiIpInt)
        if (!wifiIp.isNullOrBlank() && wifiIp != "0.0.0.0" && !wifiIp.startsWith("127.")) {
          result.add(wifiIp)
        }
      }

      val interfaces = NetworkInterface.getNetworkInterfaces()
      val prioritized = mutableListOf<Pair<Int, String>>()
      while (interfaces != null && interfaces.hasMoreElements()) {
        val intf = interfaces.nextElement()
        if (intf.isLoopback || !intf.isUp) continue
        val name = intf.name.lowercase()
        val priority = when {
          name.startsWith("wlan") || name.startsWith("wifi") -> 1
          name.startsWith("eth") || name.startsWith("en") -> 2
          name.startsWith("ap") || name.startsWith("rndis") || name.startsWith("usb") -> 3
          name.startsWith("tun") || name.startsWith("tap") || name.startsWith("rmnet") -> 5
          else -> 4
        }
        val addresses = intf.inetAddresses
        while (addresses.hasMoreElements()) {
          val addr = addresses.nextElement()
          if (!addr.isLoopbackAddress && addr is Inet4Address) {
            val host = addr.hostAddress
            if (!host.isNullOrBlank() && !host.startsWith("127.") && !host.startsWith("169.254.")) {
              prioritized.add(priority to host)
            }
          }
        }
      }
      prioritized.sortedBy { it.first }.forEach { (_, ip) ->
        if (!result.contains(ip)) {
          result.add(ip)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error getting local IPs", e)
    }
    if (result.isEmpty()) {
      result.add("127.0.0.1")
    }
    return result
  }
}
