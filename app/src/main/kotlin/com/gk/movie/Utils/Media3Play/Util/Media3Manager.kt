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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.extractor.DefaultExtractorsFactory
import com.gk.movie.Utils.okhttpUtils.OkhttpManager
import okhttp3.Request
import java.io.File
import java.net.URI
import kotlin.concurrent.thread

// ✅ 导入 UniFFI 生成的顶层函数
import uniffi.m3u8_parser.cleanM3u8Ad

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object Media3Manager {
    private const val TAG = "Media3Manager"
    
    private var exoPlayer: ExoPlayer? = null
    private var currentPlayingUrl: String? = null
    private var appContext: Context? = null

    private fun savePosition() {
        if (currentPlayingUrl != null && exoPlayer != null && appContext != null) {
            val pos = exoPlayer!!.currentPosition
            if (pos > 0) {
                appContext!!.getSharedPreferences("video_positions", Context.MODE_PRIVATE)
                    .edit().putLong(currentPlayingUrl, pos).apply()
                Log.d(TAG, "💾 保存播放进度: $currentPlayingUrl -> $pos ms")
            }
        }
    }

    fun getInstance(context: Context): ExoPlayer {
        appContext = context.applicationContext 
        
        if (exoPlayer == null) {
            Log.d(TAG, "🚀 初始化 Media3Manager 播放器实例")
            
            val renderersFactory = DefaultRenderersFactory(context.applicationContext)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true) // ✅ 允许解码器在异常时自动降级，减少卡死

            val trackSelector = DefaultTrackSelector(context.applicationContext).apply {
                setParameters(buildUponParameters().setMaxVideoSizeSd())
            }

            // ==========================================
            // ★ 修复点 1：优化缓冲策略，优先保证播放时长，防卡顿
            // ==========================================
            val loadControl = DefaultLoadControl.Builder()
                .setAllocator(DefaultAllocator(true, 64 * 1024))
                .setBufferDurationsMs(32000, 60000, 2500, 5000) // ✅ 稍微增加卡顿后的重缓冲要求(2500->5000)
                .setTargetBufferBytes(androidx.media3.common.C.LENGTH_UNSET) // ✅ 移除死板的大小限制
                .setPrioritizeTimeOverSizeThresholds(true) // ✅ 改为 true：优先保证缓冲足够的时间(秒)而不是文件体积
                .build()

            val httpDataSourceFactory = OkHttpDataSource.Factory(OkhttpManager.client)

            val resolvingDataSourceFactory = ResolvingDataSource.Factory(httpDataSourceFactory) { dataSpec ->
                val cookie = CookieManager.getInstance().getCookie(dataSpec.uri.toString())
                if (!cookie.isNullOrEmpty()) {
                    dataSpec.withAdditionalHeaders(mapOf("Cookie" to cookie))
                } else {
                    dataSpec
                }
            }

            // ==========================================
            // ★ 修复点 2：移除导致播放线程死锁的 FLAG_BLOCK_ON_CACHE
            // ==========================================
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(VideoCacheManager.getCache(context.applicationContext)) 
                .setUpstreamDataSourceFactory(resolvingDataSourceFactory)         
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR) // ✅ 移除了 FLAG_BLOCK_ON_CACHE，彻底解决画面定住的问题

            val dataSourceFactory = DefaultDataSource.Factory(context.applicationContext, cacheDataSourceFactory)
            
            // ==========================================
            // ★ 修复点 3：将配置好的 extractorsFactory 真正注入到解析器中
            // ==========================================
            val extractorsFactory = DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
            
            val mediaSourceFactory = DefaultMediaSourceFactory(context.applicationContext, extractorsFactory) // ✅ 成功注入！
                .setDataSourceFactory(dataSourceFactory)
                // ❌ 删除了 setLiveTargetOffsetMs(5000)，解决点播视频卡顿后时间戳错乱、强行回跳的问题

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

    fun play(context: Context, url: String, videoTitle: String = "正在缓存的视频") {
        if (OkhttpManager.isBypassFailed()) {
            Log.e(TAG, "🛑 播放强行拦截：当前强制被困于 VPN 隧道中，拒绝加载任何视频流媒体！")
            return
        }

        val player = getInstance(context)
        
        if (currentPlayingUrl != null && currentPlayingUrl != url) {
            savePosition()
        }

        if (currentPlayingUrl == url) {
            if (player.playbackState == Player.STATE_IDLE || player.playerError != null) {
                player.prepare()
                val savedPos = context.getSharedPreferences("video_positions", Context.MODE_PRIVATE).getLong(url, 0L)
                if (savedPos > 0) player.seekTo(savedPos)
            }
            player.play()
            return
        }

        VideoCacheManager.pauseAllDownloads(context.applicationContext)
        currentPlayingUrl = url

        val savedPos = context.getSharedPreferences("video_positions", Context.MODE_PRIVATE).getLong(url, 0L)

        if (!url.contains(".m3u8", ignoreCase = true)) {
            Log.d(TAG, "⚡ 检测到非 M3U8 格式，直接播放！")
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            if (savedPos > 0) player.seekTo(savedPos)
            player.play()
            VideoCacheManager.startDownload(context.applicationContext, url, Uri.parse(url), videoTitle)
            return
        }

        val safeFileName = "pure_movie_${url.hashCode()}.m3u8"
        val localM3u8File = File(context.cacheDir, safeFileName)

        if (localM3u8File.exists() && localM3u8File.length() > 0) {
            Log.d(TAG, "⚡ 发现已缓存的 M3U8，实现秒开！")
            val localUri = Uri.fromFile(localM3u8File)
            val mediaItem = MediaItem.Builder().setUri(localUri).setMimeType(MimeTypes.APPLICATION_M3U8).build()
            player.setMediaItem(mediaItem)
            player.prepare()
            if (savedPos > 0) player.seekTo(savedPos)
            player.play()
            
            // ★ 传入本地文件的 URI 作为下载源
            VideoCacheManager.startDownload(context.applicationContext, url, localUri, videoTitle)
            return
        }

        thread {
            try {
                Log.d(TAG, "🔍 正在抓取 M3U8 (物理直连) 并交由 Rust 引擎实施广告剔除手术...")
                var finalUrl = url
                var request = Request.Builder().url(finalUrl).build()
                
                var m3u8Text = OkhttpManager.client.newCall(request).execute().use { response ->
                    val body = response.body
                    if (body.contentLength() > 15 * 1024 * 1024) throw Exception("流文件超过5MB，拒绝读入内存！")
                    body.string()
                }
                
                if (m3u8Text.isBlank() || !m3u8Text.contains("#EXTM3U")) throw Exception("非标准 M3U8")

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
                        request = Request.Builder().url(finalUrl).build()
                        m3u8Text = OkhttpManager.client.newCall(request).execute().use { response ->
                            val body = response.body
                            if (body.contentLength() > 15 * 1024 * 1024) throw Exception("嵌套流超过5MB，拒绝读入内存！")
                            body.string()
                        }
                    }
                }

                val startTime = System.currentTimeMillis()
                val cleanedM3u8Text = cleanM3u8Ad(m3u8Text, finalUrl)
                val costTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "⚡ Rust 引擎剔除完毕！耗时: ${costTime}ms (清理前: ${m3u8Text.length} 字符 -> 清理后: ${cleanedM3u8Text.length} 字符)")
                
                localM3u8File.writeText(cleanedM3u8Text)
                
                val localUri = Uri.fromFile(localM3u8File)
                val mediaItem = MediaItem.Builder().setUri(localUri).setMimeType(MimeTypes.APPLICATION_M3U8).build()
                
                Handler(Looper.getMainLooper()).post {
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    if (savedPos > 0) player.seekTo(savedPos)
                    player.play()
                    
                    // ★ 传入本地洗白文件的 URI 作为下载源！完美避开原始广告流！
                    VideoCacheManager.startDownload(context.applicationContext, url, localUri, videoTitle)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ M3U8 抓取或 Rust 手术失败: ${e.message}。启动兜底原路播放...")
                Handler(Looper.getMainLooper()).post {
                    player.setMediaItem(MediaItem.fromUri(url))
                    player.prepare()
                    if (savedPos > 0) player.seekTo(savedPos)
                    player.play()
                    
                    // 兜底原路播放因为是播放原始流，没有修改，可以安全加入后台缓存
                    VideoCacheManager.startDownload(context.applicationContext, url, Uri.parse(url), videoTitle)
                }
            }
        }
    }

    fun prepare() { exoPlayer?.prepare() }
    fun pause() { exoPlayer?.pause() }
    fun resume() { exoPlayer?.play() }
    
    fun stop() { 
        savePosition()
        appContext?.let { VideoCacheManager.pauseAllDownloads(it) }
        exoPlayer?.stop() 
        currentPlayingUrl = null
    }
    
    fun seekTo(positionMs: Long) { exoPlayer?.seekTo(positionMs) }
    fun seekForward() { exoPlayer?.seekForward() }
    fun seekBack() { exoPlayer?.seekBack() }

    val isPlaying: Boolean get() = exoPlayer?.isPlaying ?: false
    val playbackState: Int get() = exoPlayer?.playbackState ?: Player.STATE_IDLE
    val currentPosition: Long get() = exoPlayer?.currentPosition ?: 0L
    val duration: Long get() = exoPlayer?.duration ?: C.TIME_UNSET
    val bufferedPosition: Long get() = exoPlayer?.bufferedPosition ?: 0L
    val bufferedPercentage: Int get() = exoPlayer?.bufferedPercentage ?: 0
    val videoSize: VideoSize get() = exoPlayer?.videoSize ?: VideoSize.UNKNOWN
    
    val currentSpeed: Float get() = exoPlayer?.playbackParameters?.speed ?: 1.0f
    val currentVolume: Float get() = exoPlayer?.volume ?: 1.0f

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

    fun setPlaybackSpeed(speed: Float) { exoPlayer?.setPlaybackSpeed(speed) }
    fun setVolume(volume: Float) { exoPlayer?.volume = volume }
    fun setRepeatMode(repeatMode: Int) { exoPlayer?.repeatMode = repeatMode }
    val repeatMode: Int get() = exoPlayer?.repeatMode ?: Player.REPEAT_MODE_OFF

    fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) { exoPlayer?.shuffleModeEnabled = shuffleModeEnabled }
    val shuffleModeEnabled: Boolean get() = exoPlayer?.shuffleModeEnabled ?: false

    var trackSelectionParameters: TrackSelectionParameters?
        get() = exoPlayer?.trackSelectionParameters
        set(value) { value?.let { exoPlayer?.trackSelectionParameters = it } }

    fun addListener(listener: Player.Listener) { exoPlayer?.addListener(listener) }
    fun removeListener(listener: Player.Listener) { exoPlayer?.removeListener(listener) }

    fun release() {
        savePosition()
        appContext?.let { VideoCacheManager.pauseAllDownloads(it) }
        exoPlayer?.release()
        exoPlayer = null
        currentPlayingUrl = null
    }
}