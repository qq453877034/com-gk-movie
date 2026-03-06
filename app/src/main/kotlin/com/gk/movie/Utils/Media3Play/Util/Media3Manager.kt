// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/Util/Media3Manager.kt
package com.gk.movie.Utils.Media3Play.Util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator

object Media3Manager {
    private const val TAG = "Media3Manager"
    private var exoPlayer: ExoPlayer? = null

    // 指纹与锁定状态
    private var mainVideoSignature: String? = null
    private var isMainLocked = false

    // 狂飙模式状态记录
    private var isAdMutedAndSpeeding = false
    private var userNormalSpeed = 1.0f
    private var userNormalVolume = 1.0f

    // 兜底巡检雷达
    private var checkHandler: Handler? = null
    private var checkRunnable: Runnable? = null

    fun getInstance(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            Log.d(TAG, "🚀 初始化 Media3Manager 播放器实例")
            
            // 1. 配置系统硬解优先
            val renderersFactory = DefaultRenderersFactory(context.applicationContext)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

            // 2. 配置轨道选择器
            val trackSelector = DefaultTrackSelector(context.applicationContext).apply {
                setParameters(buildUponParameters().setMaxVideoSizeSd())
            }

            // ★ 3. 极限缓冲控制器：为 128 倍速狂飙提供充足的本地内存弹药
            val loadControl = DefaultLoadControl.Builder()
                .setAllocator(DefaultAllocator(true, 64 * 1024))
                .setBufferDurationsMs(
                    10000,  // MinBuffer: 最小预加载 10 秒
                    120000, // MaxBuffer: 疯狂囤积 120 秒，确保广告早就在内存里了
                    500,    // 首次起步缓冲 0.5 秒
                    1000    // 卡顿后起步缓冲 1 秒
                )
                .setTargetBufferBytes(-1)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            exoPlayer = ExoPlayer.Builder(context.applicationContext)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl) 
                .build().apply {
                    playWhenReady = true
                    repeatMode = Player.REPEAT_MODE_OFF

                    addListener(object : Player.Listener {
                        // ★ 核心驱动：精准的硬件级画面尺寸改变回调
                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            if (videoSize.width > 0 && videoSize.height > 0) {
                                evaluateAdSkip(this@apply, videoSize.toString())
                            }
                        }
                    })
                }
        }
        return exoPlayer!!
    }

    /**
     * 核心逻辑：突破 8x 限制的 128 倍速无极变速法
     */
    private fun evaluateAdSkip(player: ExoPlayer, currentSignature: String) {
        // 1. 延迟锁定正片（1秒后锁定当前画面指纹）
        if (!isMainLocked && player.currentPosition > 1000L) {
            mainVideoSignature = currentSignature
            isMainLocked = true
            Log.e(TAG, "🎯 成功锁定正片指纹: $mainVideoSignature")
        }

        // 2. 动态拦截与刹车
        if (isMainLocked) {
            if (currentSignature != mainVideoSignature) {
                // 【进入广告区】
                if (!isAdMutedAndSpeeding) {
                    Log.e(TAG, "⚠️ 发现广告指纹！彻底关闭音频轨道，解除 8x 限制，开启 64x 狂飙！")
                    
                    // 记录用户当前习惯
                    userNormalSpeed = player.playbackParameters.speed
                    userNormalVolume = player.volume
                    isAdMutedAndSpeeding = true

                    player.volume = 0f
                    
                    // ★ 核心黑科技：直接在底层禁用音频轨道！
                    // 脱离 SonicAudioProcessor 的限制，让主时钟跟随全速飞奔的视频解码器
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .build()
                        
                    // 油门踩到底，64倍速吞噬广告
                    player.setPlaybackSpeed(64.0f) 
                }
            } else {
                // 【回到正片区】
                if (isAdMutedAndSpeeding) {
                    Log.e(TAG, "✅ 指纹恢复正常！硬件级精准刹车，恢复音频轨道与原速。")
                    isAdMutedAndSpeeding = false
                    
                    // ★ 刹车第一步：重新启用音频轨道
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .build()
                        
                    // 刹车第二步：恢复用户正常音量和倍速
                    player.volume = userNormalVolume
                    player.setPlaybackSpeed(userNormalSpeed)
                }
            }
        }
    }

    /**
     * 启动兜底巡检雷达：防漏扫
     */
    private fun startRadarLoop(player: ExoPlayer) {
        if (checkHandler == null) {
            checkHandler = Handler(Looper.getMainLooper())
        }
        checkRunnable?.let { checkHandler?.removeCallbacks(it) }
        
        checkRunnable = object : Runnable {
            override fun run() {
                if (player.isPlaying) {
                    val currentVideoSize = player.videoSize
                    if (currentVideoSize.width > 0 && currentVideoSize.height > 0) {
                        evaluateAdSkip(player, currentVideoSize.toString())
                    }
                }
                // 巡检雷达保持 0.5 秒，作为 onVideoSizeChanged 漏报时的兜底
                checkHandler?.postDelayed(this, 500L)
            }
        }
        checkHandler?.postDelayed(checkRunnable!!, 500L)
    }

    fun play(context: Context, url: String) {
        val player = getInstance(context)
        
        val currentMediaId = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentMediaId == url) {
            if (!player.isPlaying && player.playbackState != Player.STATE_ENDED) {
                player.play()
            }
            return
        }

        // 播放新视频，彻底清空所有状态
        mainVideoSignature = null
        isMainLocked = false
        isAdMutedAndSpeeding = false

        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()

        startRadarLoop(player)
    }

    fun pause() { exoPlayer?.pause() }
    fun resume() { exoPlayer?.play() }
    fun stop() { exoPlayer?.stop() }
    fun seekTo(positionMs: Long) { exoPlayer?.seekTo(positionMs) }
    
    // 如果用户在正常播放时手动调了倍速，需要更新我们的记忆，防止刹车时恢复错倍速
    fun setPlaybackSpeed(speed: Float) { 
        if (!isAdMutedAndSpeeding) {
            userNormalSpeed = speed
            exoPlayer?.setPlaybackSpeed(speed) 
        }
    }
    
    fun setRepeatMode(repeatMode: Int) { exoPlayer?.repeatMode = repeatMode }
    
    // 如果用户在正常播放时手动调了音量，需要更新我们的记忆
    fun setVolume(volume: Float) { 
        if (!isAdMutedAndSpeeding) {
            userNormalVolume = volume
            exoPlayer?.volume = volume 
        }
    }
    
    fun release() {
        checkRunnable?.let { checkHandler?.removeCallbacks(it) }
        exoPlayer?.release()
        exoPlayer = null
        mainVideoSignature = null
        isMainLocked = false
        isAdMutedAndSpeeding = false
    }
}