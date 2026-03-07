// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/Util/Media3Manager.kt
package com.gk.movie.Utils.Media3Play.Util

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.gk.movie.Utils.okhttpUtils.OkhttpManager
import java.io.File
import java.net.URI
import kotlin.concurrent.thread

/**
 * 视频播放全局管理器 (单例)
 * 负责管理底层 ExoPlayer 的生命周期、防盗链与 Cookie 共享。
 * 当前状态：M3U8 终极降维打击版（修复了头部丢失和尾部 ENDLIST 丢失导致的直播流倒退 Bug）。
 */
object Media3Manager {
    private const val TAG = "Media3Manager"
    
    private var exoPlayer: ExoPlayer? = null

    // ==========================================
    // 1. 初始化与核心构建区
    // ==========================================
    
    fun getInstance(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            Log.d(TAG, "🚀 初始化 Media3Manager 播放器实例")
            
            val renderersFactory = DefaultRenderersFactory(context.applicationContext)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

            val trackSelector = DefaultTrackSelector(context.applicationContext).apply {
                setParameters(buildUponParameters().setMaxVideoSizeSd())
            }

            val loadControl = DefaultLoadControl.Builder()
                .setAllocator(DefaultAllocator(true, 64 * 1024))
                .setBufferDurationsMs(10000, 120000, 500, 1000)
                .setTargetBufferBytes(-1)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(OkhttpManager.USER_AGENT)
                .setDefaultRequestProperties(
                    mapOf(
                        "Referer" to OkhttpManager.REFERER,
                        "Origin" to "https://www.jkan.app" 
                    )
                )

            val resolvingDataSourceFactory = ResolvingDataSource.Factory(httpDataSourceFactory) { dataSpec ->
                val cookie = CookieManager.getInstance().getCookie(dataSpec.uri.toString())
                if (!cookie.isNullOrEmpty()) {
                    dataSpec.withAdditionalHeaders(mapOf("Cookie" to cookie))
                } else {
                    dataSpec
                }
            }

            val dataSourceFactory = DefaultDataSource.Factory(context.applicationContext, resolvingDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(context.applicationContext)
                .setDataSourceFactory(dataSourceFactory)

            exoPlayer = ExoPlayer.Builder(context.applicationContext)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl) 
                .setMediaSourceFactory(mediaSourceFactory) 
                .build().apply {
                    playWhenReady = true 
                    repeatMode = Player.REPEAT_MODE_OFF
                }
        }
        return exoPlayer!!
    }

    // ==========================================
    // 2. 基础播放控制区 (含 M3U8 手术切除引擎)
    // ==========================================
    
    fun play(context: Context, url: String) {
        val player = getInstance(context)
        
        if (player.isPlaying && player.playbackState != Player.STATE_ENDED) {
            return
        }

        thread {
            try {
                Log.d(TAG, "🔍 正在抓取 M3U8 并准备实施广告剔除手术...")
                
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(OkhttpManager.USER_AGENT)
                    .setDefaultRequestProperties(
                        mapOf(
                            "Referer" to OkhttpManager.REFERER,
                            "Origin" to "https://www.jkan.app" 
                        )
                    )
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(15000)

                val resolvingDataSourceFactory = ResolvingDataSource.Factory(httpDataSourceFactory) { dataSpec ->
                    val cookie = CookieManager.getInstance().getCookie(dataSpec.uri.toString())
                    if (!cookie.isNullOrEmpty()) {
                        dataSpec.withAdditionalHeaders(mapOf("Cookie" to cookie))
                    } else {
                        dataSpec
                    }
                }
                
                val dataSource = DefaultDataSource.Factory(context.applicationContext, resolvingDataSourceFactory).createDataSource()
                
                val dataSpec = DataSpec.Builder().setUri(Uri.parse(url)).build()
                val inputStream = DataSourceInputStream(dataSource, dataSpec)
                var m3u8Text = inputStream.bufferedReader().readText()
                inputStream.close()
                
                var finalUrl = url
                
                if (m3u8Text.contains("#EXT-X-STREAM-INF")) {
                    val lines = m3u8Text.lines()
                    var nestedUrlPath = ""
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            nestedUrlPath = trimmed
                            break
                        }
                    }
                    if (nestedUrlPath.isNotEmpty()) {
                        finalUrl = URI(url).resolve(nestedUrlPath).toString()
                        val nestedDataSpec = DataSpec.Builder().setUri(Uri.parse(finalUrl)).build()
                        val nestedInputStream = DataSourceInputStream(dataSource, nestedDataSpec)
                        m3u8Text = nestedInputStream.bufferedReader().readText()
                        nestedInputStream.close()
                    }
                }

                // ==========================================
                // 核心手术区：安全拆解与广告剔除
                // ==========================================
                val baseUri = URI(finalUrl)
                
                // 🚀 第 1 步：安全提取头部信息 (Header)，防止误删导致播放器无法解析
                val lines = m3u8Text.lines()
                val headerLines = mutableListOf<String>()
                for (line in lines) {
                    val t = line.trim()
                    // 遇到第一个切片或断层标志时，头部结束
                    if (t.startsWith("#EXTINF") || t.startsWith("#EXT-X-DISCONTINUITY")) {
                        break
                    }
                    if (t.isNotBlank() && !t.startsWith("#EXT-X-ENDLIST")) {
                        headerLines.add(t)
                    }
                }
                var header = headerLines.joinToString("\n")
                if (!header.contains("#EXTM3U")) {
                    header = "#EXTM3U\n" + header
                }

                // 🚀 第 2 步：按断层拆分区块并分析
                val blocks = m3u8Text.split("#EXT-X-DISCONTINUITY")
                
                var blocksWith30Fps = 0
                var blocksWithout30Fps = 0
                for (block in blocks) {
                    if (block.contains("#EXTINF")) {
                        if (block.contains("333333") || block.contains("666667")) {
                            blocksWith30Fps++
                        } else {
                            blocksWithout30Fps++
                        }
                    }
                }
                
                val isMovie30Fps = blocksWith30Fps > blocksWithout30Fps
                val cleanBlocks = mutableListOf<String>()
                var removedAdsCount = 0
                var totalAdSeconds = 0.0 
                
                for (block in blocks) {
                    if (block.isBlank()) continue
                    
                    // 剥离当前区块中可能残留的头部或尾部垃圾，提取纯净的切片信息
                    val blockLines = block.lines()
                    val actualBlockLines = mutableListOf<String>()
                    var startCollecting = false
                    for (line in blockLines) {
                        val t = line.trim()
                        if (t.startsWith("#EXTINF")) {
                            startCollecting = true
                        }
                        // 抛弃旧的 ENDLIST
                        if (startCollecting && !t.startsWith("#EXT-X-ENDLIST")) {
                            if (t.isNotBlank() && !t.startsWith("#") && !t.startsWith("http")) {
                                actualBlockLines.add(baseUri.resolve(t).toString())
                            } else {
                                actualBlockLines.add(t)
                            }
                        }
                    }
                    
                    val actualBlockText = actualBlockLines.joinToString("\n").trim()
                    if (actualBlockText.isBlank()) continue
                    
                    val extinfCount = actualBlockText.split("#EXTINF").size - 1
                    var isAd = false
                    
                    var blockDuration = 0.0
                    val extinfRegex = "#EXTINF:([0-9.]+)".toRegex()
                    val matches = extinfRegex.findAll(actualBlockText)
                    for (match in matches) {
                        blockDuration += match.groupValues[1].toDoubleOrNull() ?: 0.0
                    }
                    
                    if (extinfCount > 0) {
                        val has30Fps = actualBlockText.contains("333333") || actualBlockText.contains("666667")
                        if (isMovie30Fps && !has30Fps) {
                            isAd = true
                        } else if (!isMovie30Fps && has30Fps) {
                            isAd = true
                        }
                    }
                    
                    if (isAd && blockDuration > 90.0) {
                        Log.w(TAG, "🛡️ 触发保护机制：发现疑似区块，但时长高达 ${String.format("%.1f", blockDuration)} 秒！判定为正片，予以保留！")
                        isAd = false
                    }
                    
                    if (isAd) {
                        Log.e(TAG, "🔪 【手术刀】切除广告！包含 $extinfCount 个片段，该广告时长为: ${String.format("%.1f", blockDuration)} 秒")
                        totalAdSeconds += blockDuration
                        removedAdsCount++
                        continue 
                    }
                    
                    cleanBlocks.add(actualBlockText)
                }
                
                // 🚀 第 3 步：完美重组 (强制挂载 ENDLIST 剧终标签，彻底断绝直播流判定)
                val cleanedM3u8Text = header + "\n" + cleanBlocks.joinToString("\n#EXT-X-DISCONTINUITY\n") + "\n#EXT-X-ENDLIST\n"
                
                Log.e(TAG, "✅ 广告清理完毕！共切除 $removedAdsCount 个广告区块，总计剔除广告时长: ${String.format("%.1f", totalAdSeconds)} 秒！")
                
                val localM3u8File = File(context.cacheDir, "pure_movie.m3u8")
                localM3u8File.writeText(cleanedM3u8Text)
                
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.fromFile(localM3u8File))
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
                
                Handler(Looper.getMainLooper()).post {
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ M3U8 手术失败: ${e.message}。启动兜底原路播放...")
                Handler(Looper.getMainLooper()).post {
                    player.setMediaItem(MediaItem.fromUri(url))
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    fun prepare() { exoPlayer?.prepare() }
    fun play() { exoPlayer?.play() }
    fun pause() { exoPlayer?.pause() }
    fun resume() { exoPlayer?.play() }
    fun stop() { exoPlayer?.stop() }
    
    fun seekTo(positionMs: Long) { exoPlayer?.seekTo(positionMs) }
    fun seekForward() { exoPlayer?.seekForward() }
    fun seekBack() { exoPlayer?.seekBack() }

    // ==========================================
    // 3. 状态与信息获取区
    // ==========================================
    
    val isPlaying: Boolean get() = exoPlayer?.isPlaying ?: false
    val playbackState: Int get() = exoPlayer?.playbackState ?: Player.STATE_IDLE
    val currentPosition: Long get() = exoPlayer?.currentPosition ?: 0L
    val duration: Long get() = exoPlayer?.duration ?: C.TIME_UNSET
    val bufferedPosition: Long get() = exoPlayer?.bufferedPosition ?: 0L
    val bufferedPercentage: Int get() = exoPlayer?.bufferedPercentage ?: 0
    val videoSize: VideoSize get() = exoPlayer?.videoSize ?: VideoSize.UNKNOWN
    
    val currentSpeed: Float get() = exoPlayer?.playbackParameters?.speed ?: 1.0f
    val currentVolume: Float get() = exoPlayer?.volume ?: 1.0f

    // ==========================================
    // 4. 播放列表控制区
    // ==========================================
    
    fun addMediaItem(mediaItem: MediaItem) { exoPlayer?.addMediaItem(mediaItem) }
    fun setMediaItems(mediaItems: List<MediaItem>) { exoPlayer?.setMediaItems(mediaItems) }
    fun clearMediaItems() { exoPlayer?.clearMediaItems() }
    
    fun hasNextMediaItem(): Boolean = exoPlayer?.hasNextMediaItem() ?: false
    fun seekToNextMediaItem() { exoPlayer?.seekToNextMediaItem() }
    
    fun hasPreviousMediaItem(): Boolean = exoPlayer?.hasPreviousMediaItem() ?: false
    fun seekToPreviousMediaItem() { exoPlayer?.seekToPreviousMediaItem() }
    
    val currentMediaItem: MediaItem? get() = exoPlayer?.currentMediaItem
    val mediaItemCount: Int get() = exoPlayer?.mediaItemCount ?: 0
    val currentMediaItemIndex: Int get() = exoPlayer?.currentMediaItemIndex ?: 0

    // ==========================================
    // 5. 偏好与轨道设置区
    // ==========================================
    
    fun setPlaybackSpeed(speed: Float) { exoPlayer?.setPlaybackSpeed(speed) }
    fun setVolume(volume: Float) { exoPlayer?.volume = volume }
    fun setRepeatMode(repeatMode: Int) { exoPlayer?.repeatMode = repeatMode }
    val repeatMode: Int get() = exoPlayer?.repeatMode ?: Player.REPEAT_MODE_OFF

    fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) { exoPlayer?.shuffleModeEnabled = shuffleModeEnabled }
    val shuffleModeEnabled: Boolean get() = exoPlayer?.shuffleModeEnabled ?: false

    var trackSelectionParameters: TrackSelectionParameters?
        get() = exoPlayer?.trackSelectionParameters
        set(value) { value?.let { exoPlayer?.trackSelectionParameters = it } }

    // ==========================================
    // 6. 监听器管理区
    // ==========================================
    
    fun addListener(listener: Player.Listener) { exoPlayer?.addListener(listener) }
    fun removeListener(listener: Player.Listener) { exoPlayer?.removeListener(listener) }

    // ==========================================
    // 7. 资源释放区
    // ==========================================
    
    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}