// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/Util/Media3Manager.kt
package com.gk.movie.Utils.Media3Play.Util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
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

    // 广告智能跳过状态记录
    private var mainVideoSignature: String? = null
    private var isMainLocked = false
    private var lastSkipTime = 0L

    private var checkHandler: Handler? = null
    private var checkRunnable: Runnable? = null

    fun getInstance(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            Log.d(TAG, "🚀 初始化 Media3Manager 播放器实例")
            
            // 1. 渲染器配置
            val renderersFactory = DefaultRenderersFactory(context.applicationContext)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

            // 2. 轨道选择器
            val trackSelector = DefaultTrackSelector(context.applicationContext).apply {
                setParameters(buildUponParameters().setMaxVideoSizeSd())
            }

            // ★ 3. 核心黑科技：注入极限缓冲控制器 (LoadControl)
            val loadControl = DefaultLoadControl.Builder()
                .setAllocator(DefaultAllocator(true, 64 * 1024)) // 64KB 标准切片分配器
                .setBufferDurationsMs(
                    30000,  // MinBuffer: 最小预加载 30 秒数据
                    120000, // MaxBuffer: 最大预加载 120 秒数据（疯狂囤积，让跳跃都在本地内存中发生）
                    500,    // BufferForPlayback: 首次播放只要 0.5 秒数据就立刻起步（秒开）
                    1000    // BufferForPlaybackAfterRebuffer: 快进/卡顿后只要 1 秒数据就立刻恢复画面（极限闪现）
                )
                .setTargetBufferBytes(-1)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            exoPlayer = ExoPlayer.Builder(context.applicationContext)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl) // 将我们的黑科技注入内核
                .build().apply {
                    playWhenReady = true
                    repeatMode = Player.REPEAT_MODE_OFF

                    addListener(object : Player.Listener {
                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            if (videoSize.width > 0 && videoSize.height > 0) {
                                evaluateAdSkip(this@apply)
                            }
                        }
                    })
                }
        }
        return exoPlayer!!
    }

    private fun evaluateAdSkip(player: ExoPlayer) {
        val currentVideoSize = player.videoSize
        if (currentVideoSize.width <= 0 || currentVideoSize.height <= 0) return

        val currentSignature = currentVideoSize.toString()

        if (!isMainLocked && player.currentPosition > 1000L) {
            mainVideoSignature = currentSignature
            isMainLocked = true
            Log.e(TAG, "🎯 成功锁定正片指纹: $mainVideoSignature")
        }

        if (isMainLocked && currentSignature != mainVideoSignature) {
            val now = System.currentTimeMillis()
            // 防抖冷却时间缩短至 1.1 秒！因为我们的缓冲起步只要 1 秒！
            if (now - lastSkipTime > 1100L) {
                Log.e(TAG, "⚠️ 发现异常指纹: $currentSignature ！快进 15 秒！")
                player.seekTo(player.currentPosition + 15000L)
                lastSkipTime = now
            }
        }
    }

    private fun startRadarLoop(player: ExoPlayer) {
        if (checkHandler == null) {
            checkHandler = Handler(Looper.getMainLooper())
        }
        checkRunnable?.let { checkHandler?.removeCallbacks(it) }
        
        checkRunnable = object : Runnable {
            override fun run() {
                if (player.isPlaying) {
                    evaluateAdSkip(player)
                }
                checkHandler?.postDelayed(this, 1000L)
            }
        }
        checkHandler?.postDelayed(checkRunnable!!, 1000L)
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

        mainVideoSignature = null
        isMainLocked = false
        lastSkipTime = 0L

        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()

        startRadarLoop(player)
    }

    fun pause() { exoPlayer?.pause() }
    fun resume() { exoPlayer?.play() }
    fun stop() { exoPlayer?.stop() }
    fun seekTo(positionMs: Long) { exoPlayer?.seekTo(positionMs) }
    fun setPlaybackSpeed(speed: Float) { exoPlayer?.setPlaybackSpeed(speed) }
    fun setRepeatMode(repeatMode: Int) { exoPlayer?.repeatMode = repeatMode }
    fun setVolume(volume: Float) { exoPlayer?.volume = volume }
    
    fun release() {
        checkRunnable?.let { checkHandler?.removeCallbacks(it) }
        exoPlayer?.release()
        exoPlayer = null
        mainVideoSignature = null
        isMainLocked = false
    }
}