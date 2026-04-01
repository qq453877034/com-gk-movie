package com.gk.movie.Utils.Media3Play.Util.cast

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object DlnaManager {
    private const val TAG = "DlnaManager"
    
    private const val USER_AGENT = "Android/1.0 DLNADoc/1.50 Sec_Hlr/2.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS) 
        .build()

    enum class MediaType { VIDEO, AUDIO, IMAGE }

    suspend fun searchDevices(localIp: String): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<DlnaDevice>()
        var socket: DatagramSocket? = null
        try {
            val localAddress = InetAddress.getByName(localIp)
            socket = DatagramSocket(null)
            socket.bind(java.net.InetSocketAddress(localAddress, 0))
            socket.soTimeout = 3000

            val searchMsg = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 3\r\n" +
                    "ST: upnp:rootdevice\r\n" + 
                    "\r\n"

            val sendPacket = DatagramPacket(searchMsg.toByteArray(), searchMsg.length, InetAddress.getByName("239.255.255.250"), 1900)
            
            for (i in 0 until 3) {
                socket.send(sendPacket)
                delay(200)
            }

            val buffer = ByteArray(2048)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < 4000) {
                try {
                    val p = DatagramPacket(buffer, buffer.size)
                    socket.receive(p)
                    val response = String(p.data, 0, p.length)
                    val location = parseHeader(response, "LOCATION")
                    if (location != null) {
                        val device = parseDeviceXml(location)
                        if (device != null && devices.none { it.avTransportUrl == device.avTransportUrl }) {
                            devices.add(device)
                        }
                    }
                } catch (e: Exception) { /* Timeout expected */ }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error: ${e.message}")
        } finally {
            socket?.close()
        }
        return@withContext devices
    }

    suspend fun castLocalFile(device: DlnaDevice, file: File, type: MediaType, localIp: String) {
        val port = SimpleFileServer.start(file)
        if (port > 0) {
            val url = "http://$localIp:$port/stream/${System.currentTimeMillis()}/${file.name}"
            cast(device, url, file.name, type)
        } else {
            Log.e(TAG, "Failed to start local server, cannot cast.")
        }
    }

    suspend fun cast(device: DlnaDevice, url: String, title: String, type: MediaType = MediaType.VIDEO) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Casting URL: $url")
        val metaData = buildMetaData(title, url, type)

        val body = soapBody("SetAVTransportURI", "urn:schemas-upnp-org:service:AVTransport:1",
            "<CurrentURI>${escapeXml(url)}</CurrentURI><CurrentURIMetaData>$metaData</CurrentURIMetaData>")
        sendSoap(device.avTransportUrl, "urn:schemas-upnp-org:service:AVTransport:1", "SetAVTransportURI", body)

        play(device)
    }

    suspend fun play(device: DlnaDevice) = withContext(Dispatchers.IO) {
        val body = soapBody("Play", "urn:schemas-upnp-org:service:AVTransport:1", "<Speed>1</Speed>")
        sendSoap(device.avTransportUrl, "urn:schemas-upnp-org:service:AVTransport:1", "Play", body)
    }

    suspend fun stop(device: DlnaDevice) = withContext(Dispatchers.IO) {
        val body = soapBody("Stop", "urn:schemas-upnp-org:service:AVTransport:1", "")
        sendSoap(device.avTransportUrl, "urn:schemas-upnp-org:service:AVTransport:1", "Stop", body)
        SimpleFileServer.stop()
    }

    suspend fun seek(device: DlnaDevice, targetPosition: String) = withContext(Dispatchers.IO) {
        val body = soapBody(
            "Seek", "urn:schemas-upnp-org:service:AVTransport:1",
            "<Unit>REL_TIME</Unit><Target>$targetPosition</Target>"
        )
        sendSoap(device.avTransportUrl, "urn:schemas-upnp-org:service:AVTransport:1", "Seek", body)
    }

    suspend fun getTransportState(device: DlnaDevice): String? = withContext(Dispatchers.IO) {
        val body = soapBody("GetTransportInfo", "urn:schemas-upnp-org:service:AVTransport:1", "")
        val response = sendSoap(device.avTransportUrl, "urn:schemas-upnp-org:service:AVTransport:1", "GetTransportInfo", body)
        
        if (response.isNullOrEmpty()) return@withContext null
        try {
            val regex = Regex("<CurrentTransportState>\\s*(.*?)\\s*</CurrentTransportState>|<[\\w:]+CurrentTransportState>\\s*(.*?)\\s*</[\\w:]+CurrentTransportState>")
            val match = regex.find(response)
            return@withContext match?.groupValues?.findLast { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Parse transport state error: ${e.message}")
        }
        return@withContext null
    }

    suspend fun setVolume(device: DlnaDevice, volume: Int) = withContext(Dispatchers.IO) {
        val targetUrl = device.renderingControlUrl ?: return@withContext
        val body = soapBody("SetVolume", "urn:schemas-upnp-org:service:RenderingControl:1",
            "<Channel>Master</Channel><DesiredVolume>$volume</DesiredVolume>")
        sendSoap(targetUrl, "urn:schemas-upnp-org:service:RenderingControl:1", "SetVolume", body)
    }

    suspend fun getVolume(device: DlnaDevice): Int = withContext(Dispatchers.IO) {
        val targetUrl = device.renderingControlUrl ?: return@withContext -1
        val body = soapBody("GetVolume", "urn:schemas-upnp-org:service:RenderingControl:1", "<Channel>Master</Channel>")
        val response = sendSoap(targetUrl, "urn:schemas-upnp-org:service:RenderingControl:1", "GetVolume", body)

        if (response.isNullOrEmpty()) return@withContext -1

        try {
            val regex = Regex("<CurrentVolume>\\s*(\\d+)\\s*</CurrentVolume>|<[\\w:]+CurrentVolume>\\s*(\\d+)\\s*</[\\w:]+CurrentVolume>")
            val match = regex.find(response)
            val volStr = match?.groupValues?.findLast { it.isNotEmpty() && it.first().isDigit() }
            return@withContext volStr?.toInt() ?: -1
        } catch (e: Exception) {
            Log.e(TAG, "Parse volume error: ${e.message}")
        }
        return@withContext -1
    }

    private fun parseDeviceXml(url: String): DlnaDevice? {
        try {
            val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            val resp = client.newCall(req).execute()
            val xml = resp.body.string() //val xml = resp.body?.string() ?: ""

            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val name = doc.select("friendlyName").first()?.text() ?: "Unknown Device"
            val baseUri = url.substring(0, url.indexOf("/", 8))

            var avUrl: String? = null
            var renderUrl: String? = null

            doc.select("service").forEach { s ->
                val type = s.select("serviceType").text()
                val control = s.select("controlURL").text()
                val fullUrl = if (control.startsWith("http")) control else "$baseUri${if(control.startsWith("/")) "" else "/"}$control"

                if (type.contains("AVTransport")) avUrl = fullUrl
                else if (type.contains("RenderingControl")) renderUrl = fullUrl
            }

            if (avUrl != null) return DlnaDevice(name, url.substringAfter("//").substringBefore(":"), avUrl, renderUrl)
        } catch (e: Exception) { Log.e(TAG, "XML parse error: ${e.message}") }
        return null
    }

    private fun sendSoap(url: String, serviceType: String, action: String, body: String): String? {
        return try {
            val soap = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                    "<s:Body>$body</s:Body></s:Envelope>"

            val req = Request.Builder()
                .url(url)
                .header("SOAPACTION", "\"$serviceType#$action\"")
                .header("User-Agent", USER_AGENT)
                .post(soap.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            resp.body.string()
        } catch (e: Exception) {
            Log.e(TAG, "SOAP $action failed: ${e.message}")
            null
        }
    }

    private fun soapBody(action: String, serviceType: String, args: String): String {
        return "<u:$action xmlns:u=\"$serviceType\"><InstanceID>0</InstanceID>$args</u:$action>"
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun buildMetaData(title: String, url: String, type: MediaType): String {
        val upnpClass = when(type) {
            MediaType.VIDEO -> "object.item.videoItem"
            MediaType.AUDIO -> "object.item.audioItem"
            MediaType.IMAGE -> "object.item.imageItem"
        }
        
        // ★ 核心修复：根据 URL 动态判断 MIME 类型，防止电视因为类型不匹配而拒绝播放
        val protocol = when(type) {
            MediaType.VIDEO -> {
                when {
                    url.contains(".m3u8", ignoreCase = true) -> "http-get:*:application/vnd.apple.mpegurl:*"
                    url.contains(".avi", ignoreCase = true) -> "http-get:*:video/avi:*"
                    url.contains(".mkv", ignoreCase = true) -> "http-get:*:video/x-matroska:*"
                    else -> "http-get:*:video/mp4:*" // 默认 mp4
                }
            }
            MediaType.AUDIO -> "http-get:*:audio/mpeg:*"
            MediaType.IMAGE -> "http-get:*:image/jpeg:*"
        }

        val rawXml = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
            <item id="1" parentID="0" restricted="1">
            <dc:title>${escapeXml(title)}</dc:title>
            <upnp:class>$upnpClass</upnp:class>
            <res protocolInfo="$protocol">${escapeXml(url)}</res>
            </item>
            </DIDL-Lite>
        """.trimIndent()
        return escapeXml(rawXml)
    }

    private fun parseHeader(content: String, key: String): String? {
        return content.lines().find { it.uppercase().startsWith(key.uppercase()) }?.substringAfter(":")?.trim()
    }
}