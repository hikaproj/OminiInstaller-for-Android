package com.example.remote

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import android.util.Log
import com.example.backup.BackupManager
import com.example.installer.RootHelper
import com.example.model.AppMetadataOverride
import com.example.model.BackupExportFormat
import com.example.model.RemoteServerStatus
import com.example.optimizer.AppsManagerRepository
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

class RemoteInstallerServer(
  private val context: Context,
  private val onPackageReceived: (File) -> Unit,
  private val onDirectUrlReceived: ((String) -> Unit)? = null,
  private val onRemoteStateChanged: (() -> Unit)? = null
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

  init {
    if (_status.value.autoStartOnLaunch) {
      start(_status.value.port)
    }
  }

  private fun loadInitialStatus(): RemoteServerStatus {
    val savedPort = prefs.getInt("port", DEFAULT_PORT).coerceIn(1024, 65535)
    val autoStart = prefs.getBoolean("autoStartOnLaunch", false)
    val allowAppMgmt = prefs.getBoolean("allowRemoteAppManagement", false)
    val allowPermEdit = prefs.getBoolean("allowRemotePermissionEdit", false)
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
    val primaryIp = allIps.firstOrNull() ?: "127.0.0.1"

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
        addLog("端末内cloudflaredバイナリ未検出のため、PC/Termux連携コマンドモードで待機中 (Port $port)")
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
    val updated = (_status.value.logs + message).takeLast(40)
    _status.value = _status.value.copy(logs = updated)
  }

  private fun handleClient(socket: Socket) {
    socket.use { s ->
      try {
        s.soTimeout = 60_000
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
          method == "POST" && path == "/install-url" -> handleRemoteUrlInstall(headers, input, output)
          method == "GET" && path == "/api/apps" -> handleListApps(output)
          method == "POST" && path == "/api/apps/launch" -> handleLaunchApp(headers, input, output)
          method == "POST" && path == "/api/apps/uninstall" -> handleUninstallApp(headers, input, output)
          method == "GET" && path == "/api/apps/export" -> handleExportApp(query, output)
          method == "POST" && path == "/api/apps/metadata" -> handleUpdateMetadata(headers, input, output)
          method == "POST" && path == "/api/apps/permission" -> handleTogglePermission(headers, input, output)
          method == "POST" && path == "/api/system/clean-cache" -> handleCleanCache(output)
          else -> sendResponse(output, 404, "text/plain; charset=UTF-8", "Not Found".toByteArray(Charsets.UTF_8))
        }
      } catch (e: Exception) {
        Log.e(TAG, "Client handle exception", e)
      }
    }
  }

  private fun readJsonBody(headers: Map<String, String>, input: InputStream): JSONObject {
    val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 262144) ?: 0
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

  private fun handleListApps(output: OutputStream) {
    if (!_status.value.allowRemoteAppManagement) {
      sendResponse(output, 403, "application/json", """{"status":"error","message":"端末側で「遠隔アプリ管理権限」がOFFになっています。"}""".toByteArray())
      return
    }
    val apps = runBlocking { AppsManagerRepository.loadInstalledApps(context) }
    val arr = JSONArray()
    apps.take(200).forEach { app ->
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

  private fun handleLaunchApp(headers: Map<String, String>, input: InputStream, output: OutputStream) {
    if (!_status.value.allowRemoteAppManagement) {
      sendResponse(output, 403, "application/json", """{"status":"error","message":"遠隔アプリ管理権限が無効です"}""".toByteArray())
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
      """{"status":"${if (ok) "ok" else "error"}","message":"${if (ok) "端末上で $pkg を起動しました" else "起動インテントが見つかりません"}"}""".toByteArray()
    )
  }

  private fun handleUninstallApp(headers: Map<String, String>, input: InputStream, output: OutputStream) {
    if (!_status.value.allowRemoteAppManagement) {
      sendResponse(output, 403, "application/json", """{"status":"error","message":"遠隔アプリ管理権限が無効です"}""".toByteArray())
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
    sendResponse(output, 200, "application/json", """{"status":"ok","message":"端末側でアンインストールを実行しました"}""".toByteArray())
  }

  private fun handleExportApp(query: String, output: OutputStream) {
    if (!_status.value.allowRemoteAppManagement) {
      sendResponse(output, 403, "text/plain; charset=UTF-8", "Forbidden: Remote App Management disabled".toByteArray())
      return
    }
    val pkg = query.split("&").mapNotNull {
      val kv = it.split("=")
      if (kv.size == 2 && kv[0] == "pkg") URLDecoder.decode(kv[1], "UTF-8") else null
    }.firstOrNull() ?: ""

    val backupResult = runBlocking {
      BackupManager.backupApp(context, pkg, false, BackupExportFormat.APK)
    }
    val item = backupResult.getOrNull()
    val file = item?.let { File(it.filePath) }
    if (file != null && file.exists()) {
      addLog("PCへAPKエクスポート送信: ${file.name}")
      val header = "HTTP/1.1 200 OK\r\n" +
        "Content-Type: application/octet-stream\r\n" +
        "Content-Disposition: attachment; filename=\"${file.name}\"\r\n" +
        "Content-Length: ${file.length()}\r\n" +
        "Access-Control-Allow-Origin: *\r\n" +
        "Connection: close\r\n\r\n"
      output.write(header.toByteArray(Charsets.UTF_8))
      FileInputStream(file).use { fis -> fis.copyTo(output) }
      output.flush()
    } else {
      sendResponse(output, 404, "text/plain", "Package not found".toByteArray())
    }
  }

  private fun handleUpdateMetadata(headers: Map<String, String>, input: InputStream, output: OutputStream) {
    if (!_status.value.allowRemotePermissionEdit) {
      sendResponse(output, 403, "application/json", """{"status":"error","message":"遠隔での権限・メタデータ編集が許可されていません"}""".toByteArray())
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
    addLog("PCからアプリ情報変更: $origPkg -> ${override.customAppLabel} (${override.customPackageName})")
    onRemoteStateChanged?.invoke()
    sendResponse(output, 200, "application/json", """{"status":"ok","message":"アプリ情報を更新しました"}""".toByteArray())
  }

  private fun handleTogglePermission(headers: Map<String, String>, input: InputStream, output: OutputStream) {
    if (!_status.value.allowRemotePermissionEdit) {
      sendResponse(output, 403, "application/json", """{"status":"error","message":"遠隔での権限編集が許可されていません"}""".toByteArray())
      return
    }
    val json = readJsonBody(headers, input)
    val pkg = json.optString("packageName", "")
    val perm = json.optString("permission", "")
    val grant = json.optBoolean("grant", true)

    val (ok, msg) = runBlocking {
      AppsManagerRepository.toggleAppPermission(context, pkg, perm, grant)
    }
    addLog("PCから遠隔権限変更: $pkg [$perm -> ${if (grant) "許可" else "拒否"}]")
    onRemoteStateChanged?.invoke()
    sendResponse(output, if (ok) 200 else 400, "application/json", JSONObject().apply {
      put("status", if (ok) "ok" else "error")
      put("message", msg)
    }.toString().toByteArray(Charsets.UTF_8))
  }

  private fun handleCleanCache(output: OutputStream) {
    if (!_status.value.allowRemotePermissionEdit) {
      sendResponse(output, 403, "application/json", """{"status":"error","message":"遠隔でのシステム最適化権限がOFFです"}""".toByteArray())
      return
    }
    val freedBytes = runBlocking { AppsManagerRepository.cleanCaches(context) }
    val mb = freedBytes / 1024 / 1024
    addLog("PCから遠隔キャッシュ消去を実行 (${mb}MB 解放)")
    onRemoteStateChanged?.invoke()
    sendResponse(output, 200, "application/json", """{"status":"ok","message":"キャッシュを ${mb}MB クリーンアップしました"}""".toByteArray())
  }

  private fun handleRemoteUrlInstall(
    headers: Map<String, String>,
    input: InputStream,
    output: OutputStream
  ) {
    if (!_status.value.allowRemoteUrlInstall) {
      sendResponse(output, 403, "application/json", """{"status":"error","message":"遠隔URLインストール権限がOFFです"}""".toByteArray())
      return
    }
    val json = readJsonBody(headers, input)
    val url = json.optString("url", "").trim()

    if (url.startsWith("http://") || url.startsWith("https://")) {
      addLog("PCから直リンク導入リクエストを受信: $url")
      onDirectUrlReceived?.invoke(url)
      sendResponse(output, 200, "application/json", """{"status":"ok","message":"Direct URL queued on device!"}""".toByteArray())
    } else {
      sendResponse(output, 400, "application/json", """{"status":"error","message":"Invalid URL scheme"}""".toByteArray())
    }
  }

  private fun handleUpload(
    headers: Map<String, String>,
    input: InputStream,
    output: OutputStream
  ) {
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
          val bytesRead = input.read(buffer)
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

    if (savedFile.exists() && savedFile.length() > 0) {
      addLog("パッケージ受信完了: ${savedFile.name} (${savedFile.length() / 1024} KB)")
      _status.value = _status.value.copy(
        uploadedFilesCount = _status.value.uploadedFilesCount + 1
      )
      onPackageReceived(savedFile)

      val responseJson = """{"status":"ok","message":"Package received! Parsing & installing on Android..."}"""
      sendResponse(output, 200, "application/json", responseJson.toByteArray(Charsets.UTF_8))
    } else {
      sendResponse(output, 400, "application/json", """{"status":"error","message":"No file uploaded"}""".toByteArray())
    }
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

  private fun serveHtml(output: OutputStream) {
    val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
    val osVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    val st = _status.value

    val html = """
      <!DOCTYPE html>
      <html lang="ja">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>OmniInstaller - PC Remote Suite</title>
        <style>
          * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
          body { background: #090d16; color: #f1f5f9; min-height: 100vh; padding: 24px; display: flex; justify-content: center; }
          .container { width: 100%; max-width: 860px; background: #111827; border: 1px solid #1f2937; border-radius: 22px; padding: 28px; box-shadow: 0 20px 50px rgba(0,0,0,0.6); }
          .header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; flex-wrap: wrap; gap: 10px; }
          .brand { display: flex; align-items: center; gap: 14px; }
          .icon { width: 46px; height: 46px; background: linear-gradient(135deg, #2563eb, #06b6d4); border-radius: 14px; display: flex; align-items: center; justify-content: center; font-size: 22px; }
          h1 { font-size: 20px; font-weight: 700; color: #fff; }
          .dev { font-size: 11px; color: #38bdf8; font-weight: 600; }
          .badge { background: #064e3b; color: #34d399; font-size: 12px; padding: 5px 12px; border-radius: 999px; font-weight: 600; }
          .tabs { display: flex; gap: 8px; margin-bottom: 18px; border-bottom: 1px solid #1f2937; padding-bottom: 12px; flex-wrap: wrap; }
          .tab-btn { background: #1e293b; color: #cbd5e1; border: none; padding: 9px 16px; border-radius: 10px; cursor: pointer; font-weight: 600; font-size: 13px; }
          .tab-btn.active { background: #2563eb; color: #fff; }
          .panel { display: none; }
          .panel.active { display: block; }
          .info-card { background: #182234; border-radius: 12px; padding: 12px 16px; margin-bottom: 16px; font-size: 12px; color: #94a3b8; display: flex; justify-content: space-between; flex-wrap: wrap; gap: 8px; }
          .dropzone { border: 2px dashed #3b82f6; background: rgba(37, 99, 235, 0.06); border-radius: 16px; padding: 36px 20px; text-align: center; cursor: pointer; transition: all 0.2s; }
          .dropzone:hover, .dropzone.dragover { background: rgba(37, 99, 235, 0.16); border-color: #38bdf8; }
          .url-box { margin-top: 16px; background: #182234; border-radius: 14px; padding: 16px; }
          .url-row { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
          input[type="text"] { flex: 1; min-width: 180px; background: #090d16; border: 1px solid #334155; color: #fff; padding: 9px 12px; border-radius: 9px; font-size: 13px; }
          button { background: #2563eb; color: #fff; border: none; padding: 9px 15px; border-radius: 9px; font-weight: 600; cursor: pointer; font-size: 12px; }
          button.danger { background: #dc2626; }
          button.secondary { background: #334155; }
          .app-row { background: #182234; border-radius: 12px; padding: 14px; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }
          .app-title { font-weight: 700; font-size: 14px; color: #fff; }
          .app-sub { font-size: 11px; color: #94a3b8; font-family: monospace; }
          .status-msg { margin-top: 12px; font-size: 13px; color: #38bdf8; font-weight: 600; }
        </style>
      </head>
      <body>
        <div class="container">
          <div class="header">
            <div class="brand">
              <div class="icon">🚀</div>
              <div>
                <h1>OmniInstaller PC Remote Control</h1>
                <div class="dev">Developer: Hikaproj • $deviceModel ($osVer)</div>
              </div>
            </div>
            <span class="badge">● Port ${st.port}</span>
          </div>

          <div class="tabs">
            <button class="tab-btn active" onclick="switchTab('install')">📦 パッケージ導入</button>
            <button class="tab-btn" onclick="switchTab('apps')">📱 端末アプリ管理・情報編集</button>
            <button class="tab-btn" onclick="switchTab('system')">🛡️ 遠隔権限・キャッシュ管理</button>
          </div>

          <div id="panel-install" class="panel active">
            <div class="dropzone" id="dropzone">
              <div style="font-size: 36px; margin-bottom: 8px;">📦</div>
              <div style="font-size: 15px; font-weight: 600;">APK / XAPK / APKS / APKM / ZIP / TAR.GZ をドラッグ＆ドロップ</div>
              <div style="font-size: 12px; color: #94a3b8; margin-top: 4px;">クリックしてPC内のファイルを選択</div>
            </div>
            <input type="file" id="fileInput" style="display:none">
            <div class="url-box">
              <div style="font-size: 13px; font-weight: 600;">🔗 APK / パッケージ直リンクを端末へ送信してインストール</div>
              <div class="url-row">
                <input type="text" id="directUrlInput" placeholder="https://example.com/release/app.apk">
                <button onclick="sendDirectUrl()">端末でダウンロード＆導入</button>
              </div>
            </div>
          </div>

          <div id="panel-apps" class="panel">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px;">
              <div style="font-size:13px; color:#94a3b8;">端末側で「遠隔アプリ管理権限」がONの場合、一覧取得・起動・APK抽出・削除・バンドル名変更が可能です。</div>
              <button onclick="loadApps()">🔄 一覧を再取得</button>
            </div>
            <div id="appsContainer">「一覧を再取得」を押してください。</div>
          </div>

          <div id="panel-system" class="panel">
            <div class="url-box">
              <div style="font-size:14px; font-weight:700; margin-bottom:8px;">🧹 端末キャッシュの一括クリーンアップ</div>
              <button onclick="cleanRemoteCache()">キャッシュ消去を実行</button>
            </div>
            <div class="url-box" style="margin-top:14px;">
              <div style="font-size:14px; font-weight:700; margin-bottom:8px;">🔐 遠隔からアプリの権限を切り替え (Grant / Revoke)</div>
              <div class="url-row">
                <input type="text" id="permPkgInput" placeholder="パッケージ名 (例: com.termux)">
                <input type="text" id="permNameInput" placeholder="権限名 (例: android.permission.CAMERA)">
                <button onclick="setRemotePerm(true)">許可 (Grant)</button>
                <button class="danger" onclick="setRemotePerm(false)">拒否 (Revoke)</button>
              </div>
            </div>
          </div>

          <div class="status-msg" id="statusMsg">待機中</div>
        </div>

        <script>
          const statusMsg = document.getElementById('statusMsg');
          function switchTab(name) {
            document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            document.getElementById('panel-' + name).classList.add('active');
            event.target.classList.add('active');
            if (name === 'apps') loadApps();
          }

          const dropzone = document.getElementById('dropzone');
          const fileInput = document.getElementById('fileInput');
          dropzone.addEventListener('click', () => fileInput.click());
          dropzone.addEventListener('dragover', e => { e.preventDefault(); dropzone.classList.add('dragover'); });
          dropzone.addEventListener('dragleave', () => dropzone.classList.remove('dragover'));
          dropzone.addEventListener('drop', e => {
            e.preventDefault();
            dropzone.classList.remove('dragover');
            if (e.dataTransfer.files.length > 0) uploadFile(e.dataTransfer.files[0]);
          });
          fileInput.addEventListener('change', () => {
            if (fileInput.files.length > 0) uploadFile(fileInput.files[0]);
          });

          function uploadFile(file) {
            statusMsg.textContent = 'アップロード中: ' + file.name + ' ...';
            const xhr = new XMLHttpRequest();
            xhr.open('POST', '/upload', true);
            xhr.setRequestHeader('X-Filename', encodeURIComponent(file.name));
            xhr.setRequestHeader('Content-Type', 'application/octet-stream');
            xhr.onload = () => {
              statusMsg.textContent = xhr.status === 200 ? '✅ 転送完了！端末側で解析画面を開きました。' : '❌ 転送失敗';
            };
            xhr.send(file);
          }

          function sendDirectUrl() {
            const url = document.getElementById('directUrlInput').value.trim();
            if (!url) return;
            statusMsg.textContent = '直リンクを送信中...';
            fetch('/install-url', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({url})})
              .then(r => r.json()).then(d => { statusMsg.textContent = d.message || '送信完了'; });
          }

          function loadApps() {
            const box = document.getElementById('appsContainer');
            box.innerHTML = '読み込み中...';
            fetch('/api/apps').then(r => r.json()).then(data => {
              if (data.status !== 'ok') {
                box.innerHTML = '<div style="color:#f87171;">' + (data.message || '権限が無効です') + '</div>';
                return;
              }
              box.innerHTML = '';
              data.apps.forEach(app => {
                const row = document.createElement('div');
                row.className = 'app-row';
                row.innerHTML = '<div>' +
                  '<div class="app-title">' + app.appLabel + ' <span style="font-size:11px;color:#38bdf8;">[' + app.category + ']</span></div>' +
                  '<div class="app-sub">' + app.customPackageName + ' • v' + app.versionName + ' • ' + app.sizeMB + 'MB</div>' +
                  '</div>' +
                  '<div style="display:flex;gap:6px;flex-wrap:wrap;">' +
                  '<button onclick="launchApp(\'' + app.packageName + '\')">起動</button>' +
                  '<button class="secondary" onclick="window.location=\'/api/apps/export?pkg=' + encodeURIComponent(app.packageName) + '\'">APK抽出</button>' +
                  '<button class="secondary" onclick="promptEditMeta(\'' + app.packageName + '\',\'' + app.appLabel.replace(/'/g,"") + '\',\'' + app.customPackageName + '\',\'' + app.versionName + '\',\'' + app.category + '\')">情報変更</button>' +
                  '<button class="danger" onclick="uninstallApp(\'' + app.packageName + '\')">削除</button>' +
                  '</div>';
                box.appendChild(row);
              });
            });
          }

          function launchApp(pkg) {
            fetch('/api/apps/launch', {method:'POST', body:JSON.stringify({packageName:pkg})})
              .then(r => r.json()).then(d => { statusMsg.textContent = d.message; });
          }

          function uninstallApp(pkg) {
            if (!confirm(pkg + ' をアンインストールしますか？')) return;
            fetch('/api/apps/uninstall', {method:'POST', body:JSON.stringify({packageName:pkg})})
              .then(r => r.json()).then(d => { statusMsg.textContent = d.message; loadApps(); });
          }

          function promptEditMeta(origPkg, label, customPkg, ver, cat) {
            const newLabel = prompt('新しいアプリ表示名:', label);
            if (newLabel === null) return;
            const newPkg = prompt('新しいバンドル名 (パッケージ名):', customPkg) || customPkg;
            const newVer = prompt('新しいバージョン表記:', ver) || ver;
            const newCat = prompt('新しいジャンル:', cat) || cat;
            fetch('/api/apps/metadata', {
              method:'POST',
              body:JSON.stringify({originalPackageName:origPkg, customAppLabel:newLabel, customPackageName:newPkg, customVersionName:newVer, customVersionCode:1, customCategory:newCat})
            }).then(r => r.json()).then(d => { statusMsg.textContent = d.message; loadApps(); });
          }

          function cleanRemoteCache() {
            fetch('/api/system/clean-cache', {method:'POST'}).then(r => r.json()).then(d => { statusMsg.textContent = d.message; });
          }

          function setRemotePerm(grant) {
            const pkg = document.getElementById('permPkgInput').value.trim();
            const perm = document.getElementById('permNameInput').value.trim();
            if (!pkg || !perm) return;
            fetch('/api/apps/permission', {method:'POST', body:JSON.stringify({packageName:pkg, permission:perm, grant:grant})})
              .then(r => r.json()).then(d => { statusMsg.textContent = d.message; });
          }
        </script>
      </body>
      </html>
    """.trimIndent()

    sendResponse(output, 200, "text/html; charset=UTF-8", html.toByteArray(Charsets.UTF_8))
  }

  private fun serveStatus(output: OutputStream) {
    val st = _status.value
    val json = JSONObject().apply {
      put("running", st.isRunning)
      put("port", st.port)
      put("uploads", st.uploadedFilesCount)
      put("allowRemoteAppManagement", st.allowRemoteAppManagement)
      put("allowRemotePermissionEdit", st.allowRemotePermissionEdit)
      put("allowRemoteUrlInstall", st.allowRemoteUrlInstall)
      put("developer", "Hikaproj")
    }
    sendResponse(output, 200, "application/json", json.toString().toByteArray(Charsets.UTF_8))
  }

  private fun sendCorsPreflight(output: OutputStream) {
    val header = "HTTP/1.1 204 No Content\r\n" +
      "Access-Control-Allow-Origin: *\r\n" +
      "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
      "Access-Control-Allow-Headers: Content-Type, X-Filename\r\n" +
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
      "Access-Control-Allow-Headers: Content-Type, X-Filename\r\n\r\n"
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
