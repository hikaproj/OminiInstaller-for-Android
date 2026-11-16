package com.example.remote

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import android.util.Log
import com.example.model.RemoteServerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

class RemoteInstallerServer(
  private val context: Context,
  private val onPackageReceived: (File) -> Unit,
  private val onDirectUrlReceived: ((String) -> Unit)? = null
) {

  companion object {
    private const val TAG = "RemoteInstallerServer"
    const val DEFAULT_PORT = 8888
  }

  private val _status = MutableStateFlow(RemoteServerStatus())
  val status: StateFlow<RemoteServerStatus> = _status.asStateFlow()

  private var serverJob: Job? = null
  private var serverSocket: ServerSocket? = null
  private var cfTunnelProcess: Process? = null
  private var cfTunnelJob: Job? = null
  private val serverScope = CoroutineScope(Dispatchers.IO)

  fun setPort(newPort: Int) {
    val validPort = newPort.coerceIn(1024, 65535)
    val wasRunning = _status.value.isRunning
    if (wasRunning) {
      stop()
      start(validPort)
    } else {
      _status.value = _status.value.copy(port = validPort)
    }
  }

  fun setCfTunnelToken(token: String) {
    _status.value = _status.value.copy(cfTunnelToken = token)
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
        addLog("PC遠隔サーバー起動: $url (バインド 0.0.0.0:$validPort)")
        if (allIps.size > 1) {
          addLog("利用可能な全IP: ${allIps.joinToString(", ") { "http://$it:$validPort" }}")
        }

        _status.value = _status.value.copy(
          isRunning = true,
          port = validPort,
          ipAddress = primaryIp,
          allIpAddresses = allIps,
          serverUrl = url
        )

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

  /**
   * Starts Cloudflare Tunnel (cloudflared) if available in the environment or Termux/system PATH,
   * or configures custom external tunnel endpoint.
   * Respects Apache 2.0 license by not bundling proprietary binaries without notice and executing external CLI cleanly.
   */
  fun startCloudflareTunnel(customPublicHost: String = "") {
    if (!_status.value.isRunning) {
      start(_status.value.port)
    }
    val port = _status.value.port
    val token = _status.value.cfTunnelToken.trim()

    if (customPublicHost.isNotBlank()) {
      val cleanUrl = if (customPublicHost.startsWith("http")) customPublicHost else "https://$customPublicHost"
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
      addLog("Cloudflare Tunnel (cloudflared) 接続を開始中... (ターゲット: http://127.0.0.1:$port)")

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
    val updated = (_status.value.logs + message).takeLast(35)
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
          method == "OPTIONS" -> {
            sendCorsPreflight(output)
          }
          method == "GET" && (path == "/" || path.startsWith("/index")) -> {
            serveHtml(output)
          }
          method == "GET" && path == "/status" -> {
            serveStatus(output)
          }
          method == "POST" && path == "/upload" -> {
            handleUpload(headers, input, output)
          }
          method == "POST" && path == "/install-url" -> {
            handleRemoteUrlInstall(headers, input, output)
          }
          else -> {
            sendResponse(output, 404, "text/plain; charset=UTF-8", "Not Found".toByteArray(Charsets.UTF_8))
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Client handle exception", e)
      }
    }
  }

  private fun handleRemoteUrlInstall(
    headers: Map<String, String>,
    input: InputStream,
    output: OutputStream
  ) {
    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
    if (contentLength <= 0 || contentLength > 65536) {
      sendResponse(output, 400, "application/json", """{"status":"error","message":"Invalid URL payload"}""".toByteArray())
      return
    }
    val buf = ByteArray(contentLength)
    var read = 0
    while (read < contentLength) {
      val r = input.read(buf, read, contentLength - read)
      if (r <= 0) break
      read += r
    }
    val bodyStr = String(buf, 0, read, Charsets.UTF_8).trim()
    val url = try {
      org.json.JSONObject(bodyStr).optString("url", bodyStr)
    } catch (_: Exception) {
      bodyStr
    }

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
      try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
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
      var read: Int
      while (input.read(buffer).also { read = it } != -1) {
        fos.write(buffer, 0, read)
      }
    }

    var filename = "package_${System.currentTimeMillis()}.apk"
    val headerBuffer = ByteArray(4096)
    var headerEnd = -1
    java.io.FileInputStream(tempFile).use { fis ->
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
      java.io.FileInputStream(tempFile).use { fis ->
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
    val port = _status.value.port

    val html = """
      <!DOCTYPE html>
      <html lang="ja">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>OmniInstaller - PC Remote Console (Hikaproj)</title>
        <style>
          * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
          body { background: #090d16; color: #f1f5f9; min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 24px; }
          .container { width: 100%; max-width: 680px; background: #111827; border: 1px solid #1f2937; border-radius: 22px; padding: 32px; box-shadow: 0 20px 50px rgba(0,0,0,0.6); }
          .header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
          .brand { display: flex; align-items: center; gap: 14px; }
          .icon { width: 48px; height: 48px; background: linear-gradient(135deg, #2563eb, #06b6d4); border-radius: 14px; display: flex; align-items: center; justify-content: center; font-size: 24px; }
          h1 { font-size: 21px; font-weight: 700; color: #fff; }
          .dev { font-size: 11px; color: #38bdf8; font-weight: 600; }
          .badge { display: inline-block; background: #064e3b; color: #34d399; font-size: 12px; padding: 5px 12px; border-radius: 999px; font-weight: 600; }
          .info-card { background: #182234; border-radius: 12px; padding: 14px 18px; margin-bottom: 20px; font-size: 13px; color: #94a3b8; display: flex; justify-content: space-between; flex-wrap: wrap; gap: 8px; }
          .dropzone { border: 2px dashed #3b82f6; background: rgba(37, 99, 235, 0.06); border-radius: 16px; padding: 40px 24px; text-align: center; cursor: pointer; transition: all 0.2s; }
          .dropzone:hover, .dropzone.dragover { background: rgba(37, 99, 235, 0.16); border-color: #38bdf8; }
          .drop-icon { font-size: 40px; margin-bottom: 10px; }
          .formats { font-size: 11px; color: #64748b; margin-top: 8px; line-height: 1.5; }
          .url-box { margin-top: 20px; background: #182234; border-radius: 14px; padding: 16px; }
          .url-row { display: flex; gap: 8px; margin-top: 8px; }
          input[type="text"] { flex: 1; background: #090d16; border: 1px solid #334155; color: #fff; padding: 10px 14px; border-radius: 10px; font-size: 13px; }
          button { background: #2563eb; color: #fff; border: none; padding: 10px 18px; border-radius: 10px; font-weight: 600; cursor: pointer; font-size: 13px; }
          button:hover { background: #1d4ed8; }
          .progress-bar-container { margin-top: 20px; display: none; }
          .progress-track { width: 100%; height: 8px; background: #334155; border-radius: 4px; overflow: hidden; margin-top: 8px; }
          .progress-fill { width: 0%; height: 100%; background: #38bdf8; transition: width 0.1s; }
          .status-msg { margin-top: 12px; font-size: 13px; color: #38bdf8; text-align: center; font-weight: 600; }
          #fileInput { display: none; }
        </style>
      </head>
      <body>
        <div class="container">
          <div class="header">
            <div class="brand">
              <div class="icon">🚀</div>
              <div>
                <h1>OmniInstaller PC Remote</h1>
                <div class="dev">Developed by Hikaproj</div>
              </div>
            </div>
            <span class="badge">● ONLINE (Port $port)</span>
          </div>
          <div class="info-card">
            <div>端末: <strong>$deviceModel</strong></div>
            <div>OS: <strong>$osVer</strong></div>
          </div>
          <div class="dropzone" id="dropzone">
            <div class="drop-icon">📦</div>
            <div style="font-size: 16px; font-weight: 600; margin-bottom: 4px;">パッケージをここにドラッグ＆ドロップ (複数ファイル対応)</div>
            <div style="font-size: 13px; color: #94a3b8;">またはクリックしてPC内のファイルを選択</div>
            <div class="formats">対応する25以上の形式: .apk / .apks / .xapk / .apkm / .aab / .zip / .omni / .hpjpkg / .tar / .tar.gz / .tgz / .apk.gz / .zapk / .sapk / .mapk / .bapk / .apk+ / .apex / .jar / .pack / .ab / .omnibak / .hpjbak / .bin</div>
          </div>
          <input type="file" id="fileInput" multiple>
          <div class="url-box">
            <div style="font-size: 13px; font-weight: 600; color: #e2e8f0;">🔗 APK / パッケージ直リンクを端末へ送信して直接インストール</div>
            <div class="url-row">
              <input type="text" id="directUrlInput" placeholder="https://example.com/app-release.apk (.xapk / .apks も可)">
              <button onclick="sendDirectUrl()">端末へ送信</button>
            </div>
          </div>
          <div class="progress-bar-container" id="progressContainer">
            <div style="display:flex; justify-content:space-between; font-size:12px; color:#94a3b8;">
              <span id="fileNameLabel">アップロード中...</span>
              <span id="percentLabel">0%</span>
            </div>
            <div class="progress-track"><div class="progress-fill" id="progressFill"></div></div>
            <div class="status-msg" id="statusMsg">デバイスへ転送中...</div>
          </div>
        </div>
        <script>
          const dropzone = document.getElementById('dropzone');
          const fileInput = document.getElementById('fileInput');
          const progressContainer = document.getElementById('progressContainer');
          const progressFill = document.getElementById('progressFill');
          const percentLabel = document.getElementById('percentLabel');
          const fileNameLabel = document.getElementById('fileNameLabel');
          const statusMsg = document.getElementById('statusMsg');

          dropzone.addEventListener('click', () => fileInput.click());
          dropzone.addEventListener('dragover', (e) => { e.preventDefault(); dropzone.classList.add('dragover'); });
          dropzone.addEventListener('dragleave', () => dropzone.classList.remove('dragover'));
          dropzone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropzone.classList.remove('dragover');
            if (e.dataTransfer.files.length > 0) uploadFile(e.dataTransfer.files[0]);
          });
          fileInput.addEventListener('change', () => {
            if (fileInput.files.length > 0) uploadFile(fileInput.files[0]);
          });

          function uploadFile(file) {
            progressContainer.style.display = 'block';
            fileNameLabel.textContent = file.name;
            statusMsg.textContent = 'PCからAndroid端末へ高速ストリーム転送中...';
            statusMsg.style.color = '#38bdf8';

            const xhr = new XMLHttpRequest();
            xhr.open('POST', '/upload', true);
            xhr.setRequestHeader('X-Filename', encodeURIComponent(file.name));
            xhr.setRequestHeader('Content-Type', 'application/octet-stream');

            xhr.upload.onprogress = (e) => {
              if (e.lengthComputable) {
                const percent = Math.round((e.loaded / e.total) * 100);
                progressFill.style.width = percent + '%';
                percentLabel.textContent = percent + '%';
              }
            };

            xhr.onload = () => {
              if (xhr.status === 200) {
                progressFill.style.width = '100%';
                percentLabel.textContent = '100%';
                statusMsg.textContent = '転送完了！Android端末側で解析・インストール画面を開きました。';
                statusMsg.style.color = '#34d399';
              } else {
                statusMsg.textContent = 'アップロード失敗 (HTTP ' + xhr.status + ')';
                statusMsg.style.color = '#f87171';
              }
            };

            xhr.onerror = () => {
              statusMsg.textContent = '通信エラーが発生しました。接続を確認してください。';
              statusMsg.style.color = '#f87171';
            };

            xhr.send(file);
          }

          function sendDirectUrl() {
            const url = document.getElementById('directUrlInput').value.trim();
            if (!url) return;
            progressContainer.style.display = 'block';
            fileNameLabel.textContent = url;
            statusMsg.textContent = '直リンクを端末へ送信中...';
            fetch('/install-url', {
              method: 'POST',
              headers: {'Content-Type': 'application/json'},
              body: JSON.stringify({url: url})
            }).then(r => r.json()).then(data => {
              statusMsg.textContent = '直リンク送信完了！端末側でダウンロードと導入を開始しました。';
              statusMsg.style.color = '#34d399';
            }).catch(err => {
              statusMsg.textContent = '送信エラー: ' + err;
              statusMsg.style.color = '#f87171';
            });
          }
        </script>
      </body>
      </html>
    """.trimIndent()

    sendResponse(output, 200, "text/html; charset=UTF-8", html.toByteArray(Charsets.UTF_8))
  }

  private fun serveStatus(output: OutputStream) {
    val json = """{"running":true,"port":${_status.value.port},"uploads":${_status.value.uploadedFilesCount},"developer":"Hikaproj"}"""
    sendResponse(output, 200, "application/json", json.toByteArray(Charsets.UTF_8))
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
      // 1. Check WifiManager first
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

      // 2. Enumerate NetworkInterfaces (prioritizing wlan0, eth0, ap0, rndis0)
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
