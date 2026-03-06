// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/Util/Media3Manager.kt
package com.gk.movie.Utils.Media3Play.Util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.VideoSize
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

/**
 * 视频播放全局管理器 (单例)
 * 负责管理底层 ExoPlayer 的生命周期、防盗链与 Cookie 共享、以及特色的“画面尺寸识别去广告”引擎。
 */
object Media3Manager {
    private const val TAG = "Media3Manager"
    
    // 底层真正的播放器实例
    private var exoPlayer: ExoPlayer? = null

    // ==========================================
    // 广告狂飙与指纹锁定状态记录区
    // ==========================================
    
    // 记录正片的画面尺寸（例如：1920x1080），作为正片的唯一“指纹”
    private var mainVideoSignature: String? = null
    // 是否已经成功锁定了正片的指纹
    private var isMainLocked = false
    
    // 当前是否正处于“静音+64倍速”跳过广告的状态
    private var isAdMutedAndSpeeding = false
    // 记录用户在看正片时的播放倍速（如 1.0x, 1.5x），用于广告结束后恢复
    private var userNormalSpeed = 1.0f
    // 记录用户在看正片时的系统音量，用于广告结束后恢复
    private var userNormalVolume = 1.0f

    // 兜底巡检雷达使用的 Handler 和 Runnable（防止监听器漏报）
    private var checkHandler: Handler? = null
    private var checkRunnable: Runnable? = null

    // ==========================================
    // 1. 初始化与核心构建区
    // ==========================================
    
    /**
     * 获取或初始化 ExoPlayer 实例
     * 包含了防盗链头、动态 Cookie 注入、极限缓冲控制和广告检测监听器。
     */
    fun getInstance(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            Log.d(TAG, "🚀 初始化 Media3Manager 播放器实例")
            
            // 1. 配置渲染器：优先使用硬件解码，提升播放性能并降低功耗
            val renderersFactory = DefaultRenderersFactory(context.applicationContext)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

            // 2. 轨道选择器：限制最高分辨率等策略（目前配置为自动选择）
            val trackSelector = DefaultTrackSelector(context.applicationContext).apply {
                setParameters(buildUponParameters().setMaxVideoSizeSd())
            }

            // 3. 极限缓冲控制器：为 64/128 倍速狂飙提供充足的本地内存弹药
            val loadControl = DefaultLoadControl.Builder()
                .setAllocator(DefaultAllocator(true, 64 * 1024))
                .setBufferDurationsMs(
                    10000,  // 最小预加载 10 秒
                    120000, // 最大预加载 120 秒（疯狂囤积数据，确保快进时不卡顿）
                    500,    // 首次起步缓冲 0.5 秒即可播放
                    1000    // 卡顿后起步缓冲 1 秒恢复播放
                )
                .setTargetBufferBytes(-1)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            // 4. 基础网络下载器：配置全局统一的伪装信息 (防盗链/防封杀)
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(OkhttpManager.USER_AGENT)
                .setDefaultRequestProperties(
                    mapOf(
                        "Referer" to OkhttpManager.REFERER,
                        "Origin" to "https://www.jkan.app" // 伪装请求来源
                    )
                )

            // 5. 动态解析器 (核心)：每次拉取 m3u8 或 ts 切片前，实时从 WebView 共享池获取最新 Cookie
            val resolvingDataSourceFactory = ResolvingDataSource.Factory(httpDataSourceFactory) { dataSpec ->
                val cookie = CookieManager.getInstance().getCookie(dataSpec.uri.toString())
                if (!cookie.isNullOrEmpty()) {
                    dataSpec.withAdditionalHeaders(mapOf("Cookie" to cookie))
                } else {
                    dataSpec
                }
            }

            // 构建最终的数据源和媒体源
            val dataSourceFactory = DefaultDataSource.Factory(context.applicationContext, resolvingDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(context.applicationContext)
                .setDataSourceFactory(dataSourceFactory)

            // 正式实例化 ExoPlayer
            exoPlayer = ExoPlayer.Builder(context.applicationContext)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl) 
                .setMediaSourceFactory(mediaSourceFactory) 
                .build().apply {
                    playWhenReady = true // 准备好后自动播放
                    repeatMode = Player.REPEAT_MODE_OFF // 默认不循环

                    // 注册底层状态监听器
                    addListener(object : Player.Listener {
                        // 触发点：视频画面尺寸发生改变时（核心驱动）
                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            if (videoSize.width > 0 && videoSize.height > 0) {
                                evaluateAdSkip(this@apply, videoSize.toString())
                            }
                        }

                        // 触发点：自动播放下一集，或切换媒体源时
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            super.onMediaItemTransition(mediaItem, reason)
                            // 切换集数时，必须清空上一集的广告锁和指纹
                            resetAdStates()
                        }
                    })
                }
        }
        return exoPlayer!!
    }

    // ==========================================
    // 2. 广告狂飙核心引擎区
    // ==========================================
    
    /**
     * 广告识别与处理逻辑
     * 原理：影视站贴片广告的分辨率通常与正片不一致。通过比对画面尺寸特征(指纹)来区分正片和广告。
     * @param player 当前播放器实例
     * @param currentSignature 当前的画面尺寸字符串 (例："1920x1080")
     */
    private fun evaluateAdSkip(player: ExoPlayer, currentSignature: String) {
        // 第一步：开播 1 秒后，认为当前正在播放的就是“正片”，记录其指纹并加锁
        if (!isMainLocked && player.currentPosition > 1000L) {
            mainVideoSignature = currentSignature
            isMainLocked = true
            Log.e(TAG, "🎯 成功锁定正片指纹: $mainVideoSignature")
        }

        // 第二步：一旦正片指纹被锁定，开始实时对比
        if (isMainLocked) {
            if (currentSignature != mainVideoSignature) {
                // 【进入广告区】：画面尺寸与正片不符！
                if (!isAdMutedAndSpeeding) {
                    Log.e(TAG, "⚠️ 发现广告指纹！彻底关闭音频轨道，解除 8x 限制，开启 64x 狂飙！")
                    
                    // 1. 记忆用户当前的倍速和音量
                    userNormalSpeed = player.playbackParameters.speed
                    userNormalVolume = player.volume
                    isAdMutedAndSpeeding = true

                    // 2. 表面静音
                    player.volume = 0f
                    
                    // 3. 底层黑科技：禁用音频轨道！
                    // 因为带声音快进受限于音频处理器的极限制（通常最高8倍），禁用音频后，视频解码器可无视声音束缚全速运转
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .build()
                        
                    // 4. 油门踩到底，64倍速吞噬广告片段
                    player.setPlaybackSpeed(64.0f) 
                }
            } else {
                // 【回到正片区】：画面尺寸恢复为我们记录的指纹
                if (isAdMutedAndSpeeding) {
                    Log.e(TAG, "✅ 指纹恢复正常！硬件级精准刹车，恢复音频轨道与原速。")
                    isAdMutedAndSpeeding = false
                    
                    // 1. 重新启用音频轨道
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .build()
                        
                    // 2. 恢复用户的习惯音量和倍速
                    player.volume = userNormalVolume
                    player.setPlaybackSpeed(userNormalSpeed)
                }
            }
        }
    }

    /**
     * 启动兜底巡检雷达 (防止 onVideoSizeChanged 偶发性漏报)
     * 每隔 0.5 秒主动去抓取一次当前的视频尺寸进行校验。
     */
    private fun startRadarLoop(player: ExoPlayer) {
        if (checkHandler == null) {
            checkHandler = Handler(Looper.getMainLooper())
        }
        // 清理上一个循环，防止多重触发
        checkRunnable?.let { checkHandler?.removeCallbacks(it) }
        
        checkRunnable = object : Runnable {
            override fun run() {
                if (player.isPlaying) {
                    val currentVideoSize = player.videoSize
                    if (currentVideoSize.width > 0 && currentVideoSize.height > 0) {
                        evaluateAdSkip(player, currentVideoSize.toString())
                    }
                }
                // 循环执行，间隔 500 毫秒
                checkHandler?.postDelayed(this, 500L)
            }
        }
        checkHandler?.postDelayed(checkRunnable!!, 500L)
    }

    /**
     * 清空广告追踪状态 (用于切换集数或停止播放时)
     */
    private fun resetAdStates() {
        mainVideoSignature = null
        isMainLocked = false
        isAdMutedAndSpeeding = false
    }

    // ==========================================
    // 3. 基础播放控制区 (Play/Pause/Seek)
    // ==========================================
    
    /**
     * 播放指定的 URL
     */
    fun play(context: Context, url: String) {
        val player = getInstance(context)
        val currentMediaId = player.currentMediaItem?.localConfiguration?.uri?.toString()
        
        // 如果发现点的是同一个链接，且没有在播放，直接恢复播放即可，不用重新加载
        if (currentMediaId == url) {
            if (!player.isPlaying && player.playbackState != Player.STATE_ENDED) {
                player.play()
            }
            return
        }

        // 新视频，彻底清空上一集的指纹和拦截状态
        resetAdStates()
        
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
        
        // 启动防漏报雷达
        startRadarLoop(player)
    }

    fun prepare() { exoPlayer?.prepare() }
    fun play() { exoPlayer?.play() }
    fun pause() { exoPlayer?.pause() }
    fun resume() { exoPlayer?.play() }
    fun stop() { exoPlayer?.stop() }
    
    /** 跳转到指定的时间位置 (毫秒) */
    fun seekTo(positionMs: Long) { exoPlayer?.seekTo(positionMs) }
    /** 默认快进 (系统默认跳跃时长) */
    fun seekForward() { exoPlayer?.seekForward() }
    /** 默认快退 (系统默认跳跃时长) */
    fun seekBack() { exoPlayer?.seekBack() }

    // ==========================================
    // 4. 状态与信息获取区 (Getters)
    // ==========================================
    
    val isPlaying: Boolean get() = exoPlayer?.isPlaying ?: false
    val playbackState: Int get() = exoPlayer?.playbackState ?: Player.STATE_IDLE
    val currentPosition: Long get() = exoPlayer?.currentPosition ?: 0L
    val duration: Long get() = exoPlayer?.duration ?: C.TIME_UNSET
    val bufferedPosition: Long get() = exoPlayer?.bufferedPosition ?: 0L
    val bufferedPercentage: Int get() = exoPlayer?.bufferedPercentage ?: 0
    val videoSize: VideoSize get() = exoPlayer?.videoSize ?: VideoSize.UNKNOWN
    
    /** * 获取当前倍速。
     * ★ 特殊处理：如果在广告狂飙期间(64x)，欺骗 UI 返回正常的倍速，防止播放器界面上的倍速按钮瞬间乱跳。
     */
    val currentSpeed: Float get() = if (isAdMutedAndSpeeding) userNormalSpeed else (exoPlayer?.playbackParameters?.speed ?: 1.0f)
    
    /** * 获取当前音量。
     * ★ 特殊处理：如果在广告静音期间，欺骗 UI 返回正常音量。
     */
    val currentVolume: Float get() = if (isAdMutedAndSpeeding) userNormalVolume else (exoPlayer?.volume ?: 1.0f)

    // ==========================================
    // 5. 播放列表控制区 (Playlists)
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
    // 6. 偏好与轨道设置区 (Settings)
    // ==========================================
    
    /**
     * 设置播放倍速
     */
    fun setPlaybackSpeed(speed: Float) { 
        if (!isAdMutedAndSpeeding) {
            userNormalSpeed = speed
            exoPlayer?.setPlaybackSpeed(speed) 
        } else {
            // 如果正好在播广告，只默默记住用户的选择，等正片回来再应用
            userNormalSpeed = speed 
        }
    }
    
    /**
     * 设置播放音量 (0.0f ~ 1.0f)
     */
    fun setVolume(volume: Float) { 
        if (!isAdMutedAndSpeeding) {
            userNormalVolume = volume
            exoPlayer?.volume = volume 
        } else {
            userNormalVolume = volume
        }
    }

    /** 循环模式 (单曲循环、列表循环等) */
    fun setRepeatMode(repeatMode: Int) { exoPlayer?.repeatMode = repeatMode }
    val repeatMode: Int get() = exoPlayer?.repeatMode ?: Player.REPEAT_MODE_OFF

    /** 随机播放模式 */
    fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) { exoPlayer?.shuffleModeEnabled = shuffleModeEnabled }
    val shuffleModeEnabled: Boolean get() = exoPlayer?.shuffleModeEnabled ?: false

    /** * 获取和设置轨道参数（非常重要，用于后期做"切换清晰度"、"切换音轨/字幕"功能）
     */
    var trackSelectionParameters: TrackSelectionParameters?
        get() = exoPlayer?.trackSelectionParameters
        set(value) { value?.let { exoPlayer?.trackSelectionParameters = it } }

    // ==========================================
    // 7. 监听器管理区 (Listeners)
    // ==========================================
    
    /** 外部 UI 注册状态监听器 */
    fun addListener(listener: Player.Listener) {
        exoPlayer?.addListener(listener)
    }

    /** 外部 UI 注销状态监听器 */
    fun removeListener(listener: Player.Listener) {
        exoPlayer?.removeListener(listener)
    }

    // ==========================================
    // 8. 资源释放区 (Lifecycle)
    // ==========================================
    
    /**
     * 彻底释放播放器及相关资源。
     * 当离开播放页面或应用退到后台不需要播放时，必须调用此方法释放内存和硬解资源。
     */
    fun release() {
        checkRunnable?.let { checkHandler?.removeCallbacks(it) }
        checkHandler = null
        checkRunnable = null
        
        exoPlayer?.release()
        exoPlayer = null
        
        resetAdStates()
    }
}