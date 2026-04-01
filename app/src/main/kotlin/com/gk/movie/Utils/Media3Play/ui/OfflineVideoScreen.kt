// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/ui/OfflineVideoScreen.kt
package com.gk.movie.Utils.Media3Play.ui

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import com.gk.movie.Utils.Media3Play.Util.Media3Manager
import com.gk.movie.Utils.Media3Play.Util.VideoCacheManager
import com.gk.movie.Utils.Media3Play.Util.VideoDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DownloadItemInfo(
    val id: String,
    val title: String,
    val episodeName: String, 
    val state: Int,
    val percent: Float,
    val downloadedBytes: Long,
    val totalBytes: Long
)

data class VideoDownloadGroup(
    val videoTitle: String,
    val episodes: List<DownloadItemInfo>
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class OfflineVideoViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    
    private val _groupedDownloads = MutableStateFlow<List<VideoDownloadGroup>>(emptyList())
    val groupedDownloads = _groupedDownloads.asStateFlow()

    private var pollingJob: Job? = null

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            val downloadManager = VideoCacheManager.getDownloadManager(context)
            while (true) {
                val cursor = downloadManager.downloadIndex.getDownloads()
                val rawList = mutableListOf<DownloadItemInfo>()
                while (cursor.moveToNext()) {
                    val download = cursor.download
                    val fullTitle = String(download.request.data, Charsets.UTF_8)
                    val videoTitle = fullTitle.substringBeforeLast(" - ", fullTitle)
                    val episodeName = fullTitle.substringAfterLast(" - ", "默认集")
                    
                    rawList.add(
                        DownloadItemInfo(
                            id = download.request.id,
                            title = videoTitle,
                            episodeName = episodeName,
                            state = download.state,
                            percent = if (download.percentDownloaded == C.PERCENTAGE_UNSET.toFloat()) 0f else download.percentDownloaded,
                            downloadedBytes = download.bytesDownloaded,
                            totalBytes = download.contentLength
                        )
                    )
                }
                cursor.close()
                
                val groups = rawList.groupBy { it.title }.map { 
                    VideoDownloadGroup(videoTitle = it.key, episodes = it.value) 
                }
                _groupedDownloads.value = groups
                
                delay(1000)
            }
        }
    }

    fun stopPolling() { pollingJob?.cancel() }

    fun pauseDownload(id: String) { DownloadService.sendSetStopReason(context, VideoDownloadService::class.java, id, 1, false) }
    fun resumeDownload(id: String) { DownloadService.sendSetStopReason(context, VideoDownloadService::class.java, id, Download.STOP_REASON_NONE, false) }
    fun deleteDownload(id: String) { DownloadService.sendRemoveDownload(context, VideoDownloadService::class.java, id, false) }
    
    fun pauseGroup(group: VideoDownloadGroup) { group.episodes.forEach { pauseDownload(it.id) } }
    fun resumeGroup(group: VideoDownloadGroup) { group.episodes.forEach { resumeDownload(it.id) } }
    fun deleteGroup(group: VideoDownloadGroup) { group.episodes.forEach { deleteDownload(it.id) } }

    fun pauseAll() { DownloadService.sendSetStopReason(context, VideoDownloadService::class.java, null, 1, false) }
    fun resumeAll() { DownloadService.sendSetStopReason(context, VideoDownloadService::class.java, null, Download.STOP_REASON_NONE, false) }
    fun deleteAll() { DownloadService.sendRemoveAllDownloads(context, VideoDownloadService::class.java, false) }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun OfflineVideoScreen(viewModel: OfflineVideoViewModel = viewModel()) {
    val groupedDownloads by viewModel.groupedDownloads.collectAsState()
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }
    
    var playingItem by remember { mutableStateOf<DownloadItemInfo?>(null) }
    // ★ 默认非全屏播放
    var isFullScreenMode by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }

    // ★ 离线播放专用弹窗
    if (playingItem != null) {
        Dialog(
            onDismissRequest = { 
                Media3Manager.stop() // 实体按键返回时彻底切断声音与后台服务
                playingItem = null 
                isFullScreenMode = false
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false, 
                decorFitsSystemWindows = false
            )
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                PlayViewsScreen(
                    url = playingItem!!.id,
                    title = playingItem!!.title,
                    episodeName = playingItem!!.episodeName,
                    isMiniPlayer = false,
                    isFullscreen = isFullScreenMode, 
                    isOffline = true, // 隐去多余按钮
                    onFullscreenToggle = { isFullScreenMode = !isFullScreenMode },
                    onBack = { 
                        Media3Manager.stop() // 面板返回键彻底切断声音与后台服务
                        playingItem = null 
                        isFullScreenMode = false
                    },
                    // ★ 核心修复：全屏则铺满，非全屏则限制 16:9 画幅，绝对不会再次变 0x0 塌陷！
                    modifier = if (isFullScreenMode) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f/9f)
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (groupedDownloads.isNotEmpty()) {
            val totalEpisodes = groupedDownloads.sumOf { it.episodes.size }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "离线任务 ($totalEpisodes 个文件)",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val hasDownloading = groupedDownloads.any { group -> group.episodes.any { it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED } }
                    
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { if (hasDownloading) viewModel.pauseAll() else viewModel.resumeAll() }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (hasDownloading) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (hasDownloading) "全部暂停" else "全部开始", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { viewModel.deleteAll() }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(groupedDownloads, key = { it.videoTitle }) { group ->
                    val isExpanded = expandedGroups.contains(group.videoTitle)
                    VideoGroupCard(
                        group = group,
                        isExpanded = isExpanded,
                        onExpandToggle = {
                            expandedGroups = if (isExpanded) expandedGroups - group.videoTitle else expandedGroups + group.videoTitle
                        },
                        onPlayClick = { item -> playingItem = item },
                        viewModel = viewModel
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.OfflinePin, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("暂无离线视频", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("播放视频时会自动在后台边下边播", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), fontSize = 13.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoGroupCard(
    group: VideoDownloadGroup,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onPlayClick: (DownloadItemInfo) -> Unit,
    viewModel: OfflineVideoViewModel
) {
    val completedCount = group.episodes.count { it.state == Download.STATE_COMPLETED }
    val isAllCompleted = completedCount == group.episodes.size
    
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onExpandToggle() },
                            onLongClick = { showMenu = true } 
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.videoTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isAllCompleted) "已全部下载完成" else "已下载 $completedCount / ${group.episodes.size}",
                                fontSize = 12.sp,
                                color = if (isAllCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("全部继续", fontWeight = FontWeight.Bold) },
                        onClick = { viewModel.resumeGroup(group); showMenu = false },
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("全部暂停", fontWeight = FontWeight.Bold) },
                        onClick = { viewModel.pauseGroup(group); showMenu = false },
                        leadingIcon = { Icon(Icons.Rounded.Pause, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("一键删除该剧集", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                        onClick = { viewModel.deleteGroup(group); showMenu = false },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    group.episodes.forEach { episodeItem ->
                        DownloadItemRow(item = episodeItem, onPlayClick = { onPlayClick(episodeItem) }, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun DownloadItemRow(item: DownloadItemInfo, onPlayClick: () -> Unit, viewModel: OfflineVideoViewModel) {
    val isCompleted = item.state == Download.STATE_COMPLETED
    val isDownloading = item.state == Download.STATE_DOWNLOADING || item.state == Download.STATE_QUEUED
    
    val stateText = when (item.state) {
        Download.STATE_COMPLETED -> "已完成"
        Download.STATE_DOWNLOADING -> "下载中 (${item.percent.toInt()}%)"
        Download.STATE_QUEUED -> "排队中"
        Download.STATE_STOPPED -> "已暂停"
        Download.STATE_FAILED -> "失败"
        else -> "解析中"
    }

    val stateColor = when (item.state) {
        Download.STATE_COMPLETED -> Color(0xFF4CAF50) 
        Download.STATE_DOWNLOADING -> MaterialTheme.colorScheme.primary
        Download.STATE_FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    // ★ 核心修复：当 contentLength 返回 -1(未知) 时，使用准确的已下载字节数
    val finalSizeStr = if (item.totalBytes > 0 && item.totalBytes != C.LENGTH_UNSET.toLong()) {
        formatBytes(item.totalBytes)
    } else {
        formatBytes(item.downloadedBytes)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.episodeName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stateText, color = stateColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                if (!isCompleted) {
                    Text(
                        text = "${formatBytes(item.downloadedBytes)} / $finalSizeStr",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                } else {
                    Text(finalSizeStr, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }

            if (!isCompleted) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (item.totalBytes <= 0) 0f else item.percent / 100f },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.5.dp)),
                    color = stateColor,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isCompleted) {
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play Offline Video",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = { if (isDownloading) viewModel.pauseDownload(item.id) else viewModel.resumeDownload(item.id) },
                    modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isDownloading) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            IconButton(
                onClick = { viewModel.deleteDownload(item.id) },
                modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}