package com.example.remote

import android.content.Context
import android.os.Build
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
import kotlinx.coroutines.withContext
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
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

class RemoteInstallerServer(
  private val context: Context,
  private val onPackageReceived: (File) -> Unit
) {

  companion object {
    private const val TAG = "RemoteInstallerServer"
    const val DEFAULT_PORT = 8888
  }

  private val _status = MutableStateFlow(RemoteServerStatus())
  val status: StateFlow<RemoteServerStatus> = _status.asStateFlow()

  private var serverJob: Job? = null
  private var serverSocket: ServerSocket? = null
  private val serverScope = CoroutineScope(Dispatchers.IO)

  fun start(port: Int = DEFAULT_PORT) {
    if (serverJob?.isActive == true) return

    val ip = getLocalIpAddress() ?: "127.0.0.1"
    serverJob = serverScope.launch {
      try {
        serverSocket = ServerSocket(port)
        val url = "http://$ip:$port"
        addLog("PC遠隔サーバー起動: $url")
        _status.value = _status.value.copy(
          isRunning = true,
          port = port,
          ipAddress = ip,
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
        addLog("サーバーエラー: ${e.message}")
        stop()
      }
    }
  }

  fun stop() {
    try {
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

  private fun addLog(message: String) {
    val updated = (_status.value.logs + message).takeLast(25)
    _status.value = _status.value.copy(logs = updated)
  }

  private fun handleClient(socket: Socket) {
    socket.use { s ->
      try {
        val input = BufferedInputStream(s.getInputStream())
        val output = BufferedOutputStream(s.getOutputStream())

        // Read request line
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val path = parts[1]

        // Read headers
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
          method == "GET" && (path == "/" || path.startsWith("/index")) -> {
            serveHtml(output)
          }
          method == "GET" && path == "/status" -> {
            serveStatus(output)
          }
          method == "POST" && path == "/upload" -> {
            handleUpload(headers, input, output)
          }
          else -> {
            sendResponse(output, 404, "text/plain", "Not Found".toByteArray())
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Client handle exception", e)
      }
    }
  }

  private fun handleUpload(
    headers: Map<String, String>,
    input: InputStream,
    output: OutputStream
  ) {
    val contentType = headers["content-type"] ?: ""
    val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L

    addLog("PCからのアップロードを受信中... (サイズ: ${contentLength / 1024 / 1024}MB)")

    val uploadDir = File(context.cacheDir, "remote_uploads")
    if (!uploadDir.exists()) uploadDir.mkdirs()

    val rawFilename = headers["x-filename"]?.let {
      try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
    }

    val savedFile: File

    if (!rawFilename.isNullOrBlank()) {
      val safeName = rawFilename.replace("/", "_").replace("\\", "_")
      savedFile = File(uploadDir, safeName)
      addLog("PCからのアップロードを受信中: $safeName (${contentLength / 1024 / 1024}MB)")
      FileOutputStream(savedFile).use { fos ->
        val buffer = ByteArray(65536)
        var bytesRead = 0
        var totalRead = 0L
        while (totalRead < contentLength && input.read(buffer).also { bytesRead = it } != -1) {
          fos.write(buffer, 0, bytesRead)
          totalRead += bytesRead
        }
      }
    } else {
      val boundary = contentType.substringAfter("boundary=", "").trim()
      if (boundary.isNotBlank()) {
        savedFile = parseMultipartAndSave(boundary, input, uploadDir)
      } else {
        // Direct binary stream fallback
        savedFile = File(uploadDir, "remote_package_${System.currentTimeMillis()}.apk")
        FileOutputStream(savedFile).use { fos ->
          val buffer = ByteArray(65536)
          var bytesRead = 0
          var totalRead = 0L
          while (totalRead < contentLength && input.read(buffer).also { bytesRead = it } != -1) {
            fos.write(buffer, 0, bytesRead)
            totalRead += bytesRead
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

      val responseJson = """{"status":"ok","message":"Package received successfully! Installing on device..."}"""
      sendResponse(output, 200, "application/json", responseJson.toByteArray(Charsets.UTF_8))
    } else {
      sendResponse(output, 400, "application/json", """{"status":"error","message":"No file uploaded"}""".toByteArray())
    }
  }

  private fun parseMultipartAndSave(boundary: String, input: InputStream, uploadDir: File): File {
    val tempFile = File(uploadDir, "upload_${System.currentTimeMillis()}.tmp")
    FileOutputStream(tempFile).use { fos ->
      val buffer = ByteArray(65536)
      var read = 0
      while (input.read(buffer).also { read = it } != -1) {
        fos.write(buffer, 0, read)
      }
    }

    var filename = "package_${System.currentTimeMillis()}.apk"
    // Inspect only first 4KB to find filename and header boundary
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

    val html = """
      <!DOCTYPE html>
      <html lang="ja">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>OmniInstaller - PC Remote Installer</title>
        <style>
          * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
          body { background: #0b132b; color: #f1f5f9; min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 24px; }
          .container { width: 100%; max-width: 620px; background: #1c2541; border: 1px solid #334155; border-radius: 20px; padding: 32px; box-shadow: 0 20px 50px rgba(0,0,0,0.5); }
          .header { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
          .icon { width: 48px; height: 48px; background: #2563eb; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 24px; }
          h1 { font-size: 22px; font-weight: 700; color: #fff; }
          .badge { display: inline-block; background: #064e3b; color: #34d399; font-size: 12px; padding: 4px 10px; border-radius: 999px; margin-top: 4px; font-weight: 600; }
          .info-card { background: #0f172a; border-radius: 12px; padding: 14px 18px; margin-bottom: 24px; font-size: 13px; color: #94a3b8; display: flex; justify-content: space-between; }
          .dropzone { border: 2px dashed #3b82f6; background: rgba(37, 99, 235, 0.05); border-radius: 16px; padding: 48px 24px; text-align: center; cursor: pointer; transition: all 0.2s; }
          .dropzone:hover, .dropzone.dragover { background: rgba(37, 99, 235, 0.15); border-color: #60a5fa; }
          .drop-icon { font-size: 40px; margin-bottom: 12px; }
          .formats { font-size: 12px; color: #64748b; margin-top: 8px; }
          .progress-bar-container { margin-top: 24px; display: none; }
          .progress-track { width: 100%; height: 8px; background: #334155; border-radius: 4px; overflow: hidden; margin-top: 8px; }
          .progress-fill { width: 0%; height: 100%; background: #38bdf8; transition: width 0.1s; }
          .status-msg { margin-top: 12px; font-size: 14px; color: #38bdf8; text-align: center; font-weight: 600; }
          #fileInput { display: none; }
        </style>
      </head>
      <body>
        <div class="container">
          <div class="header">
            <div class="icon">🚀</div>
            <div>
              <h1>OmniInstaller PC Remote</h1>
              <span class="badge">● 接続待機中 (ポート 8888)</span>
            </div>
          </div>
          <div class="info-card">
            <div>デバイス: <strong>$deviceModel</strong></div>
            <div>OS: <strong>$osVer</strong></div>
          </div>
          <div class="dropzone" id="dropzone">
            <div class="drop-icon">📦</div>
            <div style="font-size: 16px; font-weight: 600; margin-bottom: 4px;">ここにAPKファイルをドラッグ＆ドロップ</div>
            <div style="font-size: 13px; color: #94a3b8;">またはクリックしてPC上のファイルを選択</div>
            <div class="formats">対応形式: .apk / .xapk / .apks / .apkm / .zip</div>
          </div>
          <input type="file" id="fileInput" accept=".apk,.xapk,.apks,.apkm,.zip">
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
            statusMsg.textContent = 'PCからAndroid端末へ転送中...';
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
                statusMsg.textContent = '転送完了！端末側でインストール処理を開始しました。';
                statusMsg.style.color = '#34d399';
              } else {
                statusMsg.textContent = 'アップロードに失敗しました (HTTP ' + xhr.status + ')';
                statusMsg.style.color = '#f87171';
              }
            };

            xhr.onerror = () => {
              statusMsg.textContent = '通信エラーが発生しました。接続を確認してください。';
              statusMsg.style.color = '#f87171';
            };

            xhr.send(file);
          }
        </script>
      </body>
      </html>
    """.trimIndent()

    sendResponse(output, 200, "text/html; charset=UTF-8", html.toByteArray(Charsets.UTF_8))
  }

  private fun serveStatus(output: OutputStream) {
    val json = """{"running":true,"port":${_status.value.port},"uploads":${_status.value.uploadedFilesCount}}"""
    sendResponse(output, 200, "application/json", json.toByteArray(Charsets.UTF_8))
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
      "Access-Control-Allow-Origin: *\r\n\r\n"
    output.write(header.toByteArray(Charsets.UTF_8))
    output.write(data)
    output.flush()
  }

  private fun readLine(input: InputStream): String? {
    val baos = ByteArrayOutputStream()
    var prev = -1
    while (true) {
      val b = input.read()
      if (b == -1) {
        if (baos.size() == 0) return null
        break
      }
      if (b == '\n'.code && prev == '\r'.code) {
        val bytes = baos.toByteArray()
        return String(bytes, 0, bytes.size - 1, Charsets.UTF_8)
      }
      baos.write(b)
      prev = b
    }
    return baos.toString(Charsets.UTF_8.name())
  }

  fun getLocalIpAddress(): String? {
    try {
      val interfaces = NetworkInterface.getNetworkInterfaces()
      while (interfaces.hasMoreElements()) {
        val intf = interfaces.nextElement()
        if (intf.isLoopback || !intf.isUp) continue
        val addresses = intf.inetAddresses
        while (addresses.hasMoreElements()) {
          val addr = addresses.nextElement()
          if (!addr.isLoopbackAddress && addr is Inet4Address) {
            val host = addr.hostAddress
            if (!host.isNullOrBlank() && !host.startsWith("127.")) {
              return host
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error getting IP", e)
    }
    return null
  }
}
