// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/Util/VideoDownloadService.kt
package com.gk.movie.Utils.Media3Play.Util

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import com.gk.movie.R

@UnstableApi
class VideoDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name, 
    0 
) {
    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 1
    }

    // ==========================================
    // ★ 核心修复：把 PendingIntent 提升为全局缓存变量
    // 避免在频繁更新进度条时无限创建新对象引发 OOM 闪退！
    // ==========================================
    private var cachedPendingIntent: PendingIntent? = null

    override fun getDownloadManager(): DownloadManager {
        return VideoCacheManager.getDownloadManager(this)
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {

        // 1. 如果缓存为空，才去向系统索要跳转 Intent（仅执行一次）
        if (cachedPendingIntent == null) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            
            // 兼容高版本 Android 的安全性要求
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            if (intent != null) {
                cachedPendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
            }
        }

        // 2. 解析下载进度与文本
        var progress = 0
        var isIndeterminate = true
        var title = "视频缓存服务"
        var contentText = "正在准备下载..."

        if (downloads.isNotEmpty()) {
            val activeDownload = downloads[0]
            val request = activeDownload.request
            title = String(request.data, Charsets.UTF_8)

            val isDownloading = activeDownload.state == Download.STATE_DOWNLOADING
            val percent = activeDownload.percentDownloaded

            if (percent != C.PERCENTAGE_UNSET.toFloat() && percent >= 0f) {
                progress = percent.toInt()
                isIndeterminate = false
                
                contentText = if (isDownloading) {
                    "已下载: $progress%"
                } else if (activeDownload.state == Download.STATE_COMPLETED) {
                    progress = 100 
                    "下载完成"      
                } else {
                    "等待中/已暂停"
                }
            } else {
                contentText = "正在解析资源..."
            }

            if (downloads.size > 1) {
                contentText += " (共 ${downloads.size} 个任务)"
            }
        }

        // 3. 构建通知，复用 cachedPendingIntent
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download) 
            .setContentTitle(title)       
            .setContentText(contentText)  
            .setOngoing(true)             
            .setOnlyAlertOnce(true)       
            .setProgress(100, progress, isIndeterminate) 

        // 直接使用缓存的 Intent，彻底消除每秒引发的 GC 内存抖动！
        cachedPendingIntent?.let {
            builder.setContentIntent(it) 
        }

        return builder.build()
    }

    override fun getScheduler(): androidx.media3.exoplayer.scheduler.Scheduler? {
        return null
    }
}