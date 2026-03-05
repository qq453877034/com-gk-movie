// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/ui/playViewsScreen.kt
package com.gk.movie.Utils.Media3Play.ui

import android.view.LayoutInflater
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import com.gk.movie.R
import com.gk.movie.Utils.Media3Play.Util.Media3Manager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlayViewsScreen(
    url: String, 
    title: String,
    episodeName: String,
    isMiniPlayer: Boolean,
    isFullscreen: Boolean = false,
    hasNextEpisode: Boolean = false, // ★ 新增：判断是否有下一集
    onFullscreenToggle: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null, 
    playLists: List<PlayList> = emptyList(),
    selectedTabIndex: Int = 0,
    isReversed: Boolean = false,
    onTabSelected: ((Int) -> Unit)? = null,
    onReverseToggle: (() -> Unit)? = null,
    onEpisodeSelected: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember { Media3Manager.getInstance(context) }

    // ★ 智能尺寸适配：如果是手机竖屏(包含吸顶卡片)，大幅度缩小 UI 控件大小 ★
    val configuration = LocalConfiguration.current
    val isPhonePortrait = !isFullscreen && !isMiniPlayer && configuration.screenWidthDp < 840

    val topIconSize = if (isPhonePortrait) 20.dp else 24.dp
    val topTextSize = if (isPhonePortrait) 13.sp else 16.sp
    val timeTextSize = if (isPhonePortrait) 11.sp else 13.sp

    val centerIconSize = if (isMiniPlayer) 24.dp else if (isPhonePortrait) 32.dp else 48.dp
    val centerBgPadding = if (isMiniPlayer) 6.dp else if (isPhonePortrait) 10.dp else 16.dp

    val lockIconSize = if (isPhonePortrait) 18.dp else 24.dp
    val lockBgPadding = if (isPhonePortrait) 8.dp else 12.dp

    val bottomIconSize = if (isMiniPlayer) 16.dp else if (isPhonePortrait) 20.dp else 28.dp
    val bottomTextSize = if (isMiniPlayer) 8.sp else if (isPhonePortrait) 11.sp else 13.sp
    val bottomPadding = if (isMiniPlayer) 4.dp else if (isPhonePortrait) 8.dp else 16.dp
    val bottomSpaced = if (isMiniPlayer) 8.dp else if (isPhonePortrait) 12.dp else 20.dp

    var wasPlaying by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                wasPlaying = player.isPlaying 
                Media3Manager.pause()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                if (wasPlaying) Media3Manager.resume() 
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(url) {
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri != url) {
            Media3Manager.play(context, url)
        }
    }

    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) } 
    var showControls by remember { mutableStateOf(true) } 
    var isLocked by remember { mutableStateOf(false) } 
    var resolutionText by remember { mutableStateOf("解析中") }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    var systemTime by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            systemTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(10000L) 
        }
    }

    val speedOptions = listOf(
        "极速(X32)" to 32.0f, "X6" to 6.0f, "X3" to 3.0f, "X2" to 2.0f,
        "X1.5" to 1.5f, "X1.25" to 1.25f, "默认" to 1.0f,  "X0.5" to 0.5f
    )

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingState: Boolean) { isPlaying = isPlayingState }
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_TIMELINE_CHANGED)) {
                    val d = player.duration
                    duration = if (d == C.TIME_UNSET) 0L else d
                }
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val height = videoSize.height
                resolutionText = when {
                    height >= 2160 -> "4K"
                    height >= 1440 -> "2K"
                    height >= 1080 -> "高清"
                    height >= 720 -> "标清"
                    height > 0 -> "流畅"
                    else -> "解析中"
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(isPlaying, isSeeking) {
        while (isPlaying && !isSeeking) {
            currentPosition = player.currentPosition
            delay(500L) 
        }
    }

    LaunchedEffect(showControls, isPlaying, isLocked, showSpeedMenu, showMoreMenu, isSeeking) {
        if (showControls && isPlaying && !showSpeedMenu && !showMoreMenu && !isSeeking) {
            delay(4000L)
            showControls = false
        }
    }

    // ★ 安全区域合并：系统状态栏/导航条 + 物理挖孔刘海屏 ★
    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val playerView = LayoutInflater.from(ctx).inflate(R.layout.view_custom_player, null, false) as PlayerView
                playerView.player = player
                playerView
            },
            modifier = Modifier.matchParentSize()
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (showSpeedMenu || showMoreMenu) {
                        showSpeedMenu = false
                        showMoreMenu = false
                    } else {
                        showControls = !showControls
                    }
                }
        ) {
            // ==========================================
            // A. 顶部栏 
            // ==========================================
            AnimatedVisibility(
                visible = showControls && !isLocked && !isMiniPlayer,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .then(if (isFullscreen) Modifier.windowInsetsPadding(safeInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) else Modifier)
                        .padding(horizontal = 16.dp, vertical = bottomPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(topIconSize).clickable { onFullscreenToggle?.invoke() } 
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "$title $episodeName / $resolutionText",
                        color = Color.White,
                        fontSize = topTextSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Text(text = systemTime, color = Color.White, fontSize = timeTextSize, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Icon(imageVector = Icons.Default.Cast, contentDescription = "Cast", tint = Color.White, modifier = Modifier.size(topIconSize).clickable { /* TODO */ })

                    if (isFullscreen) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = Color.White,
                            modifier = Modifier.size(topIconSize).clickable { 
                                showMoreMenu = !showMoreMenu
                                showSpeedMenu = false 
                            }
                        )
                    }
                }
            }

            // ==========================================
            // B. 居中播放/暂停
            // ==========================================
            AnimatedVisibility(
                visible = showControls && !isLocked,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { if (isPlaying) Media3Manager.pause() else Media3Manager.resume() }
                        .padding(centerBgPadding)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(centerIconSize)
                    )
                }
            }

            // ==========================================
            // C. 左侧锁定屏幕
            // ==========================================
            AnimatedVisibility(
                visible = showControls && !isMiniPlayer,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it }),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Box(
                    modifier = Modifier
                        .then(if (isFullscreen) Modifier.windowInsetsPadding(safeInsets.only(WindowInsetsSides.Start)) else Modifier)
                        .padding(start = bottomSpaced)
                        .clip(CircleShape)
                        .background(if (isLocked) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.4f))
                        .clickable { isLocked = !isLocked }
                        .padding(lockBgPadding)
                ) {
                    Icon(imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(lockIconSize))
                }
            }

            // ==========================================
            // D. 底部控制栏 
            // ==========================================
            AnimatedVisibility(
                visible = showControls && !isLocked,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        // ★ 核心修复：全屏时避开小白条（导航栏），防止拖动条被遮挡 ★
                        .then(if (isFullscreen) Modifier.windowInsetsPadding(safeInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)) else Modifier)
                        .padding(horizontal = bottomSpaced, vertical = bottomPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ★ 智能显隐下一集按钮 ★
                    if (hasNextEpisode && !isMiniPlayer) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Episode",
                            tint = Color.White,
                            modifier = Modifier
                                .size(bottomIconSize)
                                .clickable { onNextEpisode?.invoke() }
                        )
                        Spacer(modifier = Modifier.width(bottomSpaced))
                    }

                    Text(text = formatTime(currentPosition), color = Color.White, fontSize = bottomTextSize, fontWeight = FontWeight.SemiBold)
                    
                    CustomVideoSlider(
                        progress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()) else 0f,
                        onProgressChange = { progress ->
                            isSeeking = true
                            currentPosition = (progress * duration).toLong()
                        },
                        onDragEnd = {
                            isSeeking = false
                            if (duration > 0) Media3Manager.seekTo(currentPosition)
                        },
                        thumbRadius = if (isMiniPlayer) 4.dp else 6.dp,
                        modifier = Modifier.weight(1f).padding(horizontal = bottomSpaced)
                    )

                    Text(text = formatTime(duration), color = Color.White.copy(alpha = 0.8f), fontSize = bottomTextSize, fontWeight = FontWeight.SemiBold)

                    if (!isMiniPlayer) {
                        Spacer(modifier = Modifier.width(bottomSpaced))
                        Text(text = resolutionText, color = Color.White, fontSize = bottomTextSize, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(bottomSpaced))
                        
                        Text(
                            text = speedOptions.find { it.second == playbackSpeed }?.first ?: "倍速", 
                            color = if (playbackSpeed == 1.0f) Color.White else MaterialTheme.colorScheme.primary, 
                            fontSize = bottomTextSize, 
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { 
                                showSpeedMenu = !showSpeedMenu
                                showMoreMenu = false 
                            }
                        )
                        
                        Spacer(modifier = Modifier.width(bottomSpaced))
                        
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(bottomIconSize).clickable { onFullscreenToggle?.invoke() }
                        )
                    }
                }
            }

            // ==========================================
            // E1. 侧边栏：倍速
            // ==========================================
            AnimatedVisibility(
                visible = showSpeedMenu && !isMiniPlayer,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(60.dp)
                        .background(Color.Black.copy(alpha = 0.85f))
                        .then(if (isFullscreen) Modifier.windowInsetsPadding(safeInsets.only(WindowInsetsSides.End)) else Modifier)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    speedOptions.forEach { (label, speedVal) ->
                        Text(
                            text = label,
                            color = if (playbackSpeed == speedVal) MaterialTheme.colorScheme.primary else Color.White,
                            fontWeight = if (playbackSpeed == speedVal) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playbackSpeed = speedVal
                                    Media3Manager.setPlaybackSpeed(speedVal)
                                    showSpeedMenu = false
                                }
                                .padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // ==========================================
            // E2. 侧边栏：全屏专享“更多/选集”面板
            // ==========================================
            AnimatedVisibility(
                visible = showMoreMenu && !isMiniPlayer && isFullscreen,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(360.dp) 
                        .background(Color.Black.copy(alpha = 0.85f))
                        .then(if (isFullscreen) Modifier.windowInsetsPadding(safeInsets.only(WindowInsetsSides.End + WindowInsetsSides.Top + WindowInsetsSides.Bottom)) else Modifier)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("选集", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("下载本集", color = Color.White, fontSize = 14.sp, modifier = Modifier.clickable { /* TODO */ })
                            Text("画质: $resolutionText", color = Color.White, fontSize = 14.sp, modifier = Modifier.clickable { /* TODO */ })
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    if (playLists.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SecondaryScrollableTabRow(
                                selectedTabIndex = selectedTabIndex,
                                modifier = Modifier.weight(1f),
                                edgePadding = 0.dp,
                                containerColor = Color.Transparent, 
                                divider = { },
                                indicator = {
                                    Box(
                                        modifier = Modifier
                                            .tabIndicatorOffset(selectedTabIndex)
                                            .padding(horizontal = 16.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            ) {
                                playLists.forEachIndexed { index, playList ->
                                    Box(
                                        modifier = Modifier
                                            .height(40.dp)
                                            .selectable(
                                                selected = selectedTabIndex == index,
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { onTabSelected?.invoke(index) }
                                            )
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = playList.sourceName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = if (isReversed) "倒序 ▼" else "正序 ▲",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.clickable { onReverseToggle?.invoke() }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        val episodes = playLists.getOrNull(selectedTabIndex)?.episodes ?: emptyList()
                        val displayEpisodes = if (isReversed) episodes.reversed() else episodes

                        // ★ 核心修复：使用 key(selectedTabIndex) 强制刷新网格，修复数量重复和错乱Bug ★
                        key(selectedTabIndex, isReversed) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(displayEpisodes) { ep ->
                                    val isSelected = episodeName == ep.name
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f))
                                            .border(
                                                width = if (isSelected) 1.dp else 0.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { onEpisodeSelected?.invoke(ep.url, ep.name) }
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ep.name,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomVideoSlider(
    progress: Float, 
    onProgressChange: (Float) -> Unit, 
    onDragEnd: () -> Unit, 
    thumbRadius: androidx.compose.ui.unit.Dp = 6.dp, 
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier = modifier
            .height(24.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> 
                        val w = if (size.width > 0) size.width.toFloat() else 1f
                        onProgressChange((offset.x / w).coerceIn(0f, 1f)) 
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onHorizontalDrag = { change, _ -> 
                        val w = if (size.width > 0) size.width.toFloat() else 1f
                        onProgressChange((change.position.x / w).coerceIn(0f, 1f)) 
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { offset -> 
                    val w = if (size.width > 0) size.width.toFloat() else 1f
                    onProgressChange((offset.x / w).coerceIn(0f, 1f)); 
                    onDragEnd() 
                })
            }
    ) {
        val sliderWidth = maxWidth
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.Center).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.fillMaxWidth(fraction = progress.coerceIn(0.001f, 1f)).height(2.dp).align(Alignment.CenterStart).background(primaryColor, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.offset(x = (sliderWidth * progress) - thumbRadius).align(Alignment.CenterStart).size(thumbRadius * 2).clip(CircleShape).background(primaryColor))
    }
}

fun formatTime(timeMs: Long): String {
    if (timeMs < 0) return "00:00"
    val totalSeconds = timeMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds) 
    else String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}