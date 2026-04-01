// 文件路径: com/gk/movie/Utils/Media3Play/Util/cast/CastProxyServer.kt
package com.gk.movie.Utils.Media3Play.Util.cast

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object CastProxyServer {
    private const val TAG = "CastProxyServer"
    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var localIpAddress = ""

    fun start(localIp: String): Int {
        if (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
            return serverSocket!!.localPort
        }
        stop()
        localIpAddress = localIp
        isRunning = true
        try {
            val socket = ServerSocket(0)
            serverSocket = socket
            val port = socket.localPort
            Log.d(TAG, "本地代理服务器已启动，端口: $port")
            serverScope.launch {
                while (isRunning) {
                    try {
                        val clientSocket = socket.accept() ?: break
                        launch(Dispatchers.IO) { handleClient(clientSocket, port) }
                    } catch (e: Exception) {
                        if (isRunning) Log.e(TAG, "Accept error: ${e.message}")
                    }
                }
            }
            return port
        } catch (e: Exception) {
            Log.e(TAG, "本地代理启动失败: ${e.message}")
            return -1
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        Log.d(TAG, "本地代理服务器已关闭")
    }

    private fun handleClient(clientSocket: Socket, port: Int) {
        clientSocket.use { socket ->
            try {
                val reader = socket.getInputStream().bufferedReader()
                val requestLine = reader.readLine() ?: return
                
                Log.i(TAG, "📺 收到电视请求: $requestLine")
                
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1]

                val tvHeaders = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(":")
                    if (colon > 0) {
                        tvHeaders[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                    }
                }

                if (!path.startsWith("/proxy/")) {
                    send404(socket.getOutputStream())
                    return
                }

                val pathSegments = path.split("/")
                if (pathSegments.size < 4) {
                    send404(socket.getOutputStream())
                    return
                }

                val hexString = pathSegments[2]
                val targetUrl = hexString.fromHex()

                if (!targetUrl.startsWith("http")) {
                    send404(socket.getOutputStream())
                    return
                }

                proxyRequest(method, tvHeaders, targetUrl, socket.getOutputStream(), port)

            } catch (e: Exception) {
                Log.e(TAG, "处理电视请求外层异常: ${e.message}")
            }
        }
    }

    private fun proxyRequest(method: String, tvHeaders: Map<String, String>, targetUrl: String, out: OutputStream, port: Int) {
        try {
            Log.i(TAG, "🌐 代理正在抓取真实地址: $targetUrl")
            
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "*/*")

            tvHeaders["range"]?.let { requestBuilder.header("Range", it) }

            if (method.equals("HEAD", ignoreCase = true)) {
                requestBuilder.head()
            }

            val response = client.newCall(requestBuilder.build()).execute()
            
            Log.i(TAG, "📥 真实服务器返回状态码: ${response.code}")

            if (!response.isSuccessful) {
                Log.e(TAG, "❌ 抓取失败！服务器拒绝提供视频，可能Token过期或UA被封")
                send404(out)
                return
            }

            val contentType = response.header("Content-Type", "") ?: ""
            val isM3u8 = contentType.contains("mpegurl", ignoreCase = true) || 
                         contentType.contains("m3u8", ignoreCase = true) ||
                         targetUrl.contains(".m3u8", ignoreCase = true)

            val writer = java.io.PrintWriter(out)
            val code = if (response.code == 206) "206 Partial Content" else "200 OK"
            writer.print("HTTP/1.1 $code\r\n")

            if (isM3u8) {
                val bodyString = if (method.equals("HEAD", ignoreCase = true)) "" else response.body.string()
                val rewritten = if (bodyString.isNotEmpty()) rewriteM3u8(bodyString, targetUrl, port) else ""
                val bytes = rewritten.toByteArray(Charsets.UTF_8)
                
                writer.print("Content-Type: application/x-mpegURL\r\n")
                if (bytes.isNotEmpty()) {
                    writer.print("Content-Length: ${bytes.size}\r\n")
                }
                writer.print("Connection: close\r\n")
                writer.print("Access-Control-Allow-Origin: *\r\n")
                writer.print("\r\n")
                writer.flush()

                if (!method.equals("HEAD", ignoreCase = true) && bytes.isNotEmpty()) {
                    try {
                        out.write(bytes)
                        out.flush()
                    } catch (e: Exception) {
                        Log.d(TAG, "电视读取 m3u8 后断开 (正常现象)")
                    }
                }
            } else {
                writer.print("Content-Type: ${if(contentType.isEmpty()) "video/mp2t" else contentType}\r\n")
                val contentLength = response.body.contentLength()
                if (contentLength > 0) {
                    writer.print("Content-Length: $contentLength\r\n")
                }
                response.header("Content-Range")?.let { writer.print("Content-Range: $it\r\n") }
                
                writer.print("contentFeatures.dlna.org: 10000000000000000000000000000000\r\n")
                writer.print("transferMode.dlna.org: Streaming\r\n")
                writer.print("Connection: keep-alive\r\n")
                writer.print("Access-Control-Allow-Origin: *\r\n")
                writer.print("\r\n")
                writer.flush()

                if (!method.equals("HEAD", ignoreCase = true)) {
                    try {
                        response.body.byteStream().use { input ->
                            input.copyTo(out)
                        }
                        out.flush()
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "数据中转失败: ${e.message}")
        }
    }

    private fun rewriteM3u8(m3u8Content: String, baseUrlStr: String, port: Int): String {
        val lines = m3u8Content.split("\n")
        val sb = StringBuilder()
        val baseUrl = URL(baseUrlStr)

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            if (trimmed.startsWith("#")) {
                if (trimmed.startsWith("#EXT-X-KEY:") && trimmed.contains("URI=\"")) {
                    val beforeUri = trimmed.substringBefore("URI=\"") + "URI=\""
                    val uriStr = trimmed.substringAfter("URI=\"").substringBefore("\"")
                    val afterUri = trimmed.substringAfter("URI=\"").substringAfter("\"")
                    val absoluteKeyUrl = if (uriStr.startsWith("http")) uriStr else URL(baseUrl, uriStr).toString()
                    val hexKeyUrl = absoluteKeyUrl.toHex()
                    sb.append(beforeUri).append("http://$localIpAddress:$port/proxy/$hexKeyUrl/media.key").append("\"").append(afterUri).append("\n")
                } else {
                    sb.append(trimmed).append("\n")
                }
            } else {
                val absoluteUrl = if (trimmed.startsWith("http")) trimmed else URL(baseUrl, trimmed).toString()
                val hexUrl = absoluteUrl.toHex()
                val fakeExt = if (trimmed.contains(".m3u8", ignoreCase = true)) "video.m3u8" else "segment.ts"
                
                sb.append("http://$localIpAddress:$port/proxy/$hexUrl/$fakeExt").append("\n")
            }
        }
        return sb.toString()
    }

    private fun send404(out: OutputStream) {
        try {
            val writer = java.io.PrintWriter(out)
            writer.print("HTTP/1.1 404 Not Found\r\n\r\n")
            writer.flush()
        } catch (e: Exception) {}
    }

    private fun String.toHex(): String {
        val bytes = this.toByteArray(Charsets.UTF_8)
        val sb = java.lang.StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun String.fromHex(): String {
        val len = this.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
        }
        return String(data, Charsets.UTF_8)
    }
}