// 文件路径: com/gk/movie/Utils/Media3Play/Util/cast/CastViewModel.kt
package com.gk.movie.Utils.Media3Play.Util.cast

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.NetworkInterface

class CastViewModel : ViewModel() {

    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _selectedDevice = MutableStateFlow<DlnaDevice?>(null)
    val selectedDevice: StateFlow<DlnaDevice?> = _selectedDevice.asStateFlow()

    private val _tvPlaybackEnded = MutableStateFlow(false)
    val tvPlaybackEnded: StateFlow<Boolean> = _tvPlaybackEnded.asStateFlow()
    private var pollingJob: Job? = null

    fun selectDevice(device: DlnaDevice) {
        _selectedDevice.value = device
    }

    fun scanDevices(context: Context) {
        if (_isScanning.value) return
        _isScanning.value = true
        _devices.value = emptyList()

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifiManager.createMulticastLock("DlnaScanLock")
        multicastLock.setReferenceCounted(true)

        viewModelScope.launch {
            try {
                multicastLock.acquire()
                val localIp = getLocalIpAddress()
                if (localIp.isEmpty()) return@launch

                val foundDevices = DlnaManager.searchDevices(localIp)
                _devices.value = foundDevices
                if (foundDevices.none { it.ip == _selectedDevice.value?.ip }) {
                    _selectedDevice.value = null
                }
            } catch (e: Exception) {
                Log.e("CastViewModel", "Scan failed: ${e.message}")
            } finally {
                if (multicastLock.isHeld) multicastLock.release()
                _isScanning.value = false
            }
        }
    }

    fun castUrlWithPosition(url: String, title: String, startPositionMs: Long, type: DlnaManager.MediaType = DlnaManager.MediaType.VIDEO) {
        val device = _selectedDevice.value ?: return
        _tvPlaybackEnded.value = false 

        viewModelScope.launch {
            Log.d("CastViewModel", "直接推送纯净 URL 给电视: $url")

            // 直接发送原始链接，不再做任何中间人伪装
            DlnaManager.cast(device, url, title, type)
            delay(1500) 
            
            if (startPositionMs > 0) {
                val targetTime = formatTime(startPositionMs)
                DlnaManager.seek(device, targetTime)
            }
            
            DlnaManager.play(device)
            startPollingTvState(device) 
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    private fun startPollingTvState(device: DlnaDevice) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            delay(5000) 
            var isPlayingDetected = false

            while (isActive) {
                val state = DlnaManager.getTransportState(device)
                if (state == "PLAYING") {
                    isPlayingDetected = true
                } else if (state == "STOPPED" && isPlayingDetected) {
                    _tvPlaybackEnded.value = true
                    break
                }
                delay(3000)
            }
        }
    }
    
    fun resetPlaybackEndedState() {
        _tvPlaybackEnded.value = false
    }

    fun castLocalUri(context: Context, uri: Uri, type: DlnaManager.MediaType) {
        val device = _selectedDevice.value ?: return
        viewModelScope.launch {
            val file = copyUriToFile(context, uri)
            if (file != null) {
                val localIp = getLocalIpAddress()
                DlnaManager.castLocalFile(device, file, type, localIp)
            }
        }
    }

    fun play() {
        val device = _selectedDevice.value ?: return
        viewModelScope.launch { DlnaManager.play(device) }
    }

    fun stop() {
        val device = _selectedDevice.value ?: return
        viewModelScope.launch { DlnaManager.stop(device) }
        pollingJob?.cancel()
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val inetAddress = addresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                        return inetAddress.hostAddress ?: ""
                    }
                }
            }
        } catch (e: Exception) {}
        return ""
    }

    private suspend fun copyUriToFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val mimeType = context.contentResolver.getType(uri)
            var extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            if (extension.isNullOrEmpty()) extension = "mp4"

            val tempFile = File(context.cacheDir, "cast_media_${System.currentTimeMillis()}.$extension")
            tempFile.outputStream().use { output -> inputStream.copyTo(output) }
            tempFile
        } catch (e: Exception) {
            null
        }
    }
}