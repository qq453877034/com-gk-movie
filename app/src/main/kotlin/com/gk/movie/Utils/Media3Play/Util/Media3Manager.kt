// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/Util/Media3Manager.kt
package com.gk.movie.Utils.Media3Play.Util

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

object Media3Manager {
    private var exoPlayer: ExoPlayer? = null

    fun getInstance(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            // ★ 核心修复1：改为 ON，系统硬解画面优先，FFmpeg作冷门音频兜底，彻底告别有声音没画面
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
                }
        }
        return exoPlayer!!
    }

    fun play(context: Context, url: String) {
        val player = getInstance(context)
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
    }
}