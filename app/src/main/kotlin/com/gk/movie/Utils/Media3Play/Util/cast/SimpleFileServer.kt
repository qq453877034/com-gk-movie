package com.gk.movie.Utils.Media3Play.Util.cast

import android.util.Log
import android.webkit.MimeTypeMap
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.net.ServerSocket
import java.net.Socket

object SimpleFileServer {
    private const val TAG = "SimpleFileServer"
    
    private var serverSocket: ServerSocket? = null
    
    @Volatile 
    private var currentFile: File? = null
    
    @Volatile 
    private var isRunning = false

    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(file: File): Int {
        stop() 
        currentFile = file
        isRunning = true

        try {
            val socket = ServerSocket(0)
            serverSocket = socket
            val actualPort = socket.localPort
            Log.d(TAG, "Local Server started on port $actualPort, serving: ${file.name}")

            serverScope.launch {
                try {
                    while (isRunning) {
                        val client = socket.accept() ?: break
                        launch { handleClient(client) }
                    }
                } catch (e: Exception) {
                    if (isRunning) Log.e(TAG, "Server error: ${e.message}")
                }
            }
            return actualPort
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server: ${e.message}")
            return -1
        }
    }

    fun stop() {
        isRunning = false
        currentFile = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Stop server error: ${e.message}")
        }
        serverSocket = null
        Log.d(TAG, "Local Server stopped")
    }

    private fun handleClient(socket: Socket) {
        socket.use { clientSocket ->
            try {
                val inputStream = clientSocket.getInputStream()
                val reader = inputStream.bufferedReader()
                
                val requestLine = reader.readLine() ?: return
                Log.d(TAG, "Request: $requestLine")

                var rangeStart: Long = 0
                
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break 
                    
                    if (line.startsWith("Range: bytes=", ignoreCase = true)) {
                        val range = line.substring(13).split("-")[0]
                        rangeStart = range.toLongOrNull() ?: 0
                    }
                }

                val file = currentFile
                if (file == null || !file.exists()) {
                    val out = clientSocket.getOutputStream()
                    out.write("HTTP/1.1 404 Not Found\r\n\r\n".toByteArray())
                    return
                }

                val fileLength = file.length()
                val outputStream = clientSocket.getOutputStream()
                val printWriter = java.io.PrintWriter(outputStream)

                val status = if (rangeStart > 0) "206 Partial Content" else "200 OK"
                printWriter.print("HTTP/1.1 $status\r\n")
                printWriter.print("Content-Type: ${getContentType(file.name)}\r\n")
                printWriter.print("Content-Length: ${fileLength - rangeStart}\r\n")
                printWriter.print("Content-Range: bytes $rangeStart-${fileLength - 1}/$fileLength\r\n")
                printWriter.print("Accept-Ranges: bytes\r\n")
                printWriter.print("Connection: close\r\n")
                printWriter.print("\r\n")
                printWriter.flush()

                FileInputStream(file).use { fis ->
                    if (rangeStart > 0) fis.skip(rangeStart)
                    
                    val buffer = ByteArray(64 * 1024) 
                    var bytesRead: Int
                    
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        if (!isRunning || currentFile == null) break
                        try {
                            outputStream.write(buffer, 0, bytesRead)
                        } catch (e: Exception) {
                            break 
                        }
                    }
                    outputStream.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client handle error: ${e.message}")
            }
        }
    }

    private fun getContentType(fileName: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(fileName) ?: fileName.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) 
            ?: "application/octet-stream"
    }
}