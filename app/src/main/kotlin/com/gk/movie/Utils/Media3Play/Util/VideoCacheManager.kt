// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/Util/VideoCacheManager.kt
package com.gk.movie.Utils.Media3Play.Util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.gk.movie.Utils.okhttpUtils.OkhttpManager
import java.io.File
import java.util.concurrent.Executor

@UnstableApi
object VideoCacheManager {
    private var cache: SimpleCache? = null
    private var downloadManager: DownloadManager? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (cache == null) {
            val cacheDir = File(context.getExternalFilesDir(null), "media3_cache")
            // 设置最大缓存容量 2GB
            val cacheSize = 20480L * 1024L * 1024L
            val evictor = LeastRecentlyUsedCacheEvictor(cacheSize)
            databaseProvider = StandaloneDatabaseProvider(context)
            cache = SimpleCache(cacheDir, evictor, databaseProvider!!)
        }
        return cache!!
    }

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        if (downloadManager == null) {
            val httpDataSourceFactory = OkHttpDataSource.Factory(OkhttpManager.client)
            
            // ★ 修复核心：包一层 DefaultDataSource.Factory，让它能读取 file:// 本地协议的 M3U8 文本
            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
            
            downloadManager = DownloadManager(
                context,
                databaseProvider ?: StandaloneDatabaseProvider(context).also { databaseProvider = it },
                getCache(context),
                dataSourceFactory, // 使用支持本地和网络的混合工厂
                Executor { it.run() }
            ).apply {
                // 最大同时下载数
                maxParallelDownloads = 5
            }
        }
        return downloadManager!!
    }

    // ★ 修复核心：分离 videoId (用于标识任务状态) 和 downloadUri (真实去读取的M3U8源)
    fun startDownload(context: Context, videoId: String, downloadUri: Uri, title: String) {
        try {
            val manager = getDownloadManager(context)
            
            // 如果该视频已经 100% 下载完成，直接跳过任务添加，防止 0B 闪烁
            val existingDownload = manager.downloadIndex.getDownload(videoId)
            if (existingDownload != null && existingDownload.state == Download.STATE_COMPLETED) {
                Log.d("VideoCacheManager", "✅ 视频 [${title}] 已在本地完全离线，跳过重复激活，防止 0B 闪烁！")
                return
            }

            Log.d("VideoCacheManager", "⬇️ 开始后台缓存视频: $title -> $downloadUri")
            
            // 使用原 url 作为唯一 ID，但传给 DownloadManager 去读取的是净化后的 Uri
            val downloadRequest = DownloadRequest.Builder(videoId, downloadUri)
                .setData(title.toByteArray(Charsets.UTF_8))
                .build()
            
            DownloadService.sendAddDownload(
                context,
                VideoDownloadService::class.java,
                downloadRequest,
                false 
            )

            // 单点解除刹车：强制清零它的暂停理由，让它全速跑起来
            DownloadService.sendSetStopReason(
                context,
                VideoDownloadService::class.java,
                videoId, 
                Download.STOP_REASON_NONE, 
                false
            )
            
        } catch (e: Exception) {
            Log.e("VideoCacheManager", "下载启动失败: ${e.message}")
        }
    }

    // 全局急刹车引擎
    fun pauseAllDownloads(context: Context) {
        try {
            Log.d("VideoCacheManager", "🛑 触发全局急刹车，暂停所有后台偷跑任务...")
            DownloadService.sendSetStopReason(
                context,
                VideoDownloadService::class.java,
                null, // 传 null 表示对队列中所有的任务生效
                1,    // 1 表示设置暂停理由
                false
            )
        } catch (e: Exception) {
            Log.e("VideoCacheManager", "发送暂停指令失败: ${e.message}")
        }
    }

    @Synchronized
    fun release() {
        downloadManager?.release()
        downloadManager = null
        cache?.release()
        cache = null
        databaseProvider = null
    }
}