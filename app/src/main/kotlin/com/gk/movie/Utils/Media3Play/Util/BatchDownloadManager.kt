// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/Util/BatchDownloadManager.kt
package com.gk.movie.Utils.Media3Play.Util

import android.net.Uri
import android.util.Log
import com.gk.movie.Utils.okhttpUtils.OkhttpManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BatchTask(
    val videoTitle: String,
    val episodeName: String,
    val sourceUrl: String
)

object BatchDownloadManager {
    private const val TAG = "BatchDownloadManager"

    private val taskQueue = Channel<BatchTask>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _pendingTaskCount = MutableStateFlow(0)
    val pendingTaskCount = _pendingTaskCount.asStateFlow()

    private val _inQueueTasks = MutableStateFlow<Set<String>>(emptySet())
    val inQueueTasks = _inQueueTasks.asStateFlow()

    init {
        startWorker()
    }

    private fun startWorker() {
        scope.launch {
            for (task in taskQueue) {
                try {
                    _pendingTaskCount.value = _pendingTaskCount.value.coerceAtLeast(1) - 1
                    Log.d(TAG, "⏳ 正在极速提取 [${task.videoTitle} - ${task.episodeName}] 的真实下载地址...")

                    // ★ 核心修复：接收 SniffResult 数据包裹
                    val sniffResult = VideoSniffer.sniffVideoUrl(OkhttpManager.appContext!!, task.sourceUrl)

                    // ★ 核心修复：判空并提取包裹里的 playUrl
                    if (sniffResult != null && sniffResult.playUrl.isNotEmpty()) {
                        val realUrl = sniffResult.playUrl // 拿到纯净的 URL 字符串
                        
                        Log.d(TAG, "✅ 提取成功，光速推入 ExoPlayer 下载引擎: $realUrl")
                        VideoCacheManager.startDownload(
                            context = OkhttpManager.appContext!!,
                            videoId = realUrl,
                            downloadUri = Uri.parse(realUrl), // 传入网络原始地址
                            title = "${task.videoTitle} - ${task.episodeName}"
                        )
                    } else {
                        Log.e(TAG, "❌ 提取失败: [${task.episodeName}]")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "批量任务执行异常", e)
                } finally {
                    val taskKey = "${task.videoTitle}_${task.episodeName}"
                    _inQueueTasks.value = _inQueueTasks.value - taskKey
                }

                // ==========================================
                // ★ 性能释放：移除了以前长达 15 秒的笨重休眠！
                // 改为 1500 毫秒的微间隔，既能防止被服务器当作恶意 DDoS 封IP，
                // 又能让用户体验到一秒加多集的丝滑快感！
                // ==========================================
                Log.d(TAG, "⚡ 触发基础防刷接口保护，微调休眠 1500 毫秒...")
                delay(1500L) 
            }
        }
    }

    // ✅ 修复：移除了 limit 限制，让选中的所有任务都能成功加入队列
    fun enqueueBatch(videoTitle: String, episodes: List<Pair<String, String>>) {
        scope.launch {
            val newQueueKeys = mutableSetOf<String>()
            
            for (ep in episodes) {
                val task = BatchTask(videoTitle, ep.first, ep.second)
                taskQueue.send(task)
                _pendingTaskCount.value += 1
                newQueueKeys.add("${videoTitle}_${ep.first}")
            }
            
            _inQueueTasks.value = _inQueueTasks.value + newQueueKeys
            Log.d(TAG, "📦 已将 ${episodes.size} 个任务加入延迟嗅探队列。")
        }
    }
    
    fun clearQueue() {
        var task = taskQueue.tryReceive().getOrNull()
        while (task != null) {
            task = taskQueue.tryReceive().getOrNull()
        }
        _pendingTaskCount.value = 0
        _inQueueTasks.value = emptySet()
    }
}