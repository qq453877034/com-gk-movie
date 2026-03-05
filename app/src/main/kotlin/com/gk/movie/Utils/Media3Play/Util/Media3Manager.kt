// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/Util/Media3Manager.kt
package com.gk.movie.Utils.Media3Play.Util

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

object Media3Manager {
    private const val TAG = "Media3Manager"
    private var exoPlayer: ExoPlayer? = null

    // 广告智能跳过状态记录
    private var mainFormatFeature: String? = null
    private var isMainFormatLocked = false
    private var lastSkipTime = 0L

    fun getInstance(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            val renderersFactory = DefaultRenderersFactory(context.applicationContext)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

            val trackSelector = DefaultTrackSelector(context.applicationContext)
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setMaxVideoSizeSd() 
            )

            exoPlayer = ExoPlayer.Builder(context.applicationContext)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .build().apply {
                    playWhenReady = true
                    repeatMode = Player.REPEAT_MODE_OFF

                    addAnalyticsListener(object : AnalyticsListener {
                        override fun onDownstreamFormatChanged(
                            eventTime: AnalyticsListener.EventTime,
                            mediaLoadData: MediaLoadData
                        ) {
                            val format = mediaLoadData.trackFormat ?: return
                            val currentFeature = format.id ?: "${format.width}x${format.height}_${format.codecs}"
                            
                            if (!isMainFormatLocked && currentPosition > 15000L) {
                                mainFormatFeature = currentFeature
                                isMainFormatLocked = true
                                Log.d(TAG, "🎯 成功锁定正片特征: $mainFormatFeature")
                            }

                            if (isMainFormatLocked && currentFeature != mainFormatFeature) {
                                val now = System.currentTimeMillis()
                                if (now - lastSkipTime > 5000L) {
                                    Log.w(TAG, "⚠️ 检测到视频流突变！疑似镶嵌广告，自动快进 15 秒！")
                                    seekTo(currentPosition + 15000L)
                                    lastSkipTime = now
                                }
                            }
                        }
                    })
                }
        }
        return exoPlayer!!
    }

    fun play(context: Context, url: String) {
        val player = getInstance(context)
        
        // ★ 核心修复1：防止因屏幕旋转或切换小窗导致的重复加载，进度归零！
        val currentMediaId = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentMediaId == url) {
            Log.d(TAG, "📺 视频地址未变，保持当前播放进度，拒绝重置！")
            // 确保没有被意外暂停
            if (!player.isPlaying && player.playbackState != Player.STATE_ENDED) {
                player.play()
            }
            return
        }

        mainFormatFeature = null
        isMainFormatLocked = false
        lastSkipTime = 0L

        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
    }

    fun pause() { exoPlayer?.pause() }
    fun resume() { exoPlayer?.play() }
    fun stop() { exoPlayer?.stop() }
    fun seekTo(positionMs: Long) { exoPlayer?.seekTo(positionMs) }
    fun setPlaybackSpeed(speed: Float) { exoPlayer?.setPlaybackSpeed(speed) }
    fun setRepeatMode(repeatMode: Int) { exoPlayer?.repeatMode = repeatMode }
    fun setVolume(volume: Float) { exoPlayer?.volume = volume }
    
    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        mainFormatFeature = null
        isMainFormatLocked = false
    }
}