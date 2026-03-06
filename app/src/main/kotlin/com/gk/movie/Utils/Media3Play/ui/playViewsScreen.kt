// 文件路径: com/gk/movie/Utils/Media3Play/ui/playViewsScreen.kt
package com.gk.movie.Utils.Media3Play.ui

import android.content.Context
import android.media.AudioManager
import android.net.TrafficStats 
import android.os.BatteryManager
import android.os.Process 
import android.util.Log
import android.view.LayoutInflater
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.gk.movie.R
import com.gk.movie.Utils.Media3Play.Util.Media3Manager
import com.gk.movie.Utils.Media3Play.Util.cast.CastDeviceDialog
import com.gk.movie.Utils.Media3Play.Util.cast.CastViewModel
import com.gk.movie.Utils.Media3Play.Util.cast.NextEpisodeDialog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DragMode { NONE, SEEK, BRIGHTNESS, VOLUME }

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun PlayViewsScreen(
    url: String, 
    title: String,
    episodeName: String,
    coverUrl: String = "", 
    isMiniPlayer: Boolean,
    isFullscreen: Boolean = false,
    hasNextEpisode: Boolean = false, 
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
    remember { Media3Manager.getInstance(context) }
    
    val castViewModel: CastViewModel = viewModel()
    var showCastDialog by remember { mutableStateOf(false) }
    var showNextEpisodePrompt by remember { mutableStateOf(false) }
    val tvPlaybackEnded by castViewModel.tvPlaybackEnded.collectAsState()

    val displayMetrics = context.resources.displayMetrics
    val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
    val screenHeightDp = displayMetrics.heightPixels / displayMetrics.density
    val physicalSmallestWidthDp = kotlin.math.min(screenWidthDp, screenHeightDp)
    val isPhysicalPhone = physicalSmallestWidthDp < 600
    val isPhysicalTablet = !isPhysicalPhone

    val isPhoneFullscreen = isPhysicalPhone && isFullscreen
    val isTabletFullscreen = isPhysicalTablet && isFullscreen

    val centerIconSize = if (isMiniPlayer) 24.dp else if (isTabletFullscreen) 54.dp else if (isPhoneFullscreen) 36.dp else 32.dp
    val centerBgPadding = if (isMiniPlayer) 6.dp else if (isTabletFullscreen) 20.dp else if (isPhoneFullscreen) 12.dp else 10.dp
    val lockIconSize = if (isTabletFullscreen) 24.dp else if (isFullscreen) 20.dp else 18.dp
    val lockBgPadding = if (isFullscreen) 10.dp else 8.dp
    
    val bottomPadding = if (isMiniPlayer) 4.dp else if (isFullscreen) 16.dp else 8.dp
    val bottomSpaced = if (isMiniPlayer) 8.dp else if (isTabletFullscreen) 32.dp else if (isPhoneFullscreen) 20.dp else 12.dp

    val topIconSize = if (isFullscreen) (if (isPhysicalTablet) 36.dp else 28.dp) else 22.dp
    val topTextSize = if (isFullscreen) (if (isPhysicalTablet) 22.sp else 18.sp) else 15.sp
    val timeTextSize = if (isFullscreen) (if (isPhysicalTablet) 18.sp else 14.sp) else 12.sp

    val bottomIconSize = if (isMiniPlayer) 16.dp else if (isFullscreen) (if (isPhysicalTablet) 36.dp else 28.dp) else 22.dp
    val bottomTextSize = if (isMiniPlayer) 8.sp else if (isFullscreen) (if (isPhysicalTablet) 18.sp else 14.sp) else 12.sp

    var wasPlaying by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                wasPlaying = Media3Manager.isPlaying 
                Media3Manager.pause()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                if (wasPlaying) Media3Manager.resume() 
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isPlaying by remember { mutableStateOf(Media3Manager.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(Media3Manager.currentPosition) }
    var duration by remember { mutableLongStateOf(if (Media3Manager.duration == C.TIME_UNSET) 0L else Media3Manager.duration) }
    var bufferedPercentage by remember { mutableIntStateOf(Media3Manager.bufferedPercentage) }
    var playbackState by remember { mutableIntStateOf(Media3Manager.playbackState) }
    
    var isSeeking by remember { mutableStateOf(false) } 
    var showControls by remember { mutableStateOf(true) } 
    var isLocked by remember { mutableStateOf(false) } 
    var resolutionText by remember { mutableStateOf("解析中") }
    var playbackSpeed by remember { mutableFloatStateOf(Media3Manager.currentSpeed) }
    
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    var isLongPressSpeedUp by remember { mutableStateOf(false) }
    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var dragSeekPosition by remember { mutableLongStateOf(0L) }
    var dragBrightnessValue by remember { mutableFloatStateOf(0f) }
    var dragVolumeValue by remember { mutableFloatStateOf(0f) }

    var batteryLevel by remember { mutableIntStateOf(100) }
    LaunchedEffect(Unit) {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        while (true) {
            batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            delay(60000L) 
        }
    }

    LaunchedEffect(url) {
        val currentUri = Media3Manager.currentMediaItem?.localConfiguration?.uri?.toString()
        if (currentUri != url) {
            Media3Manager.play(context, url)
        }
    }

    LaunchedEffect(tvPlaybackEnded) {
        if (tvPlaybackEnded && hasNextEpisode) {
            showNextEpisodePrompt = true
            castViewModel.resetPlaybackEndedState()
        }
    }

    var systemTime by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            systemTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(10000L) 
        }
    }

    var netSpeedText by remember { mutableStateOf("0 KB/s") }
    LaunchedEffect(playbackState) {
        if (playbackState == Player.STATE_BUFFERING) {
            var lastBytes = TrafficStats.getUidRxBytes(Process.myUid())
            var lastTime = System.currentTimeMillis()
            while (true) {
                delay(1000)
                val currentBytes = TrafficStats.getUidRxBytes(Process.myUid())
                val currentTime = System.currentTimeMillis()
                val bytesDiff = currentBytes - lastBytes
                val timeDiff = currentTime - lastTime

                if (timeDiff > 0) {
                    val speedBps = (bytesDiff * 1000) / timeDiff
                    netSpeedText = if (speedBps < 1024) {
                        "$speedBps B/s"
                    } else if (speedBps < 1024 * 1024) {
                        String.format(Locale.getDefault(), "%.1f KB/s", speedBps / 1024f)
                    } else {
                        String.format(Locale.getDefault(), "%.1f MB/s", speedBps / (1024f * 1024f))
                    }
                }

                lastBytes = currentBytes
                lastTime = currentTime
            }
        }
    }

    val speedOptions = listOf(
        "X8" to 8.0f, "X6" to 6.0f, "X4" to 4.0f, "X2" to 2.0f,
        "X1.5" to 1.5f, "X1.25" to 1.25f, "默认" to 1.0f,  "X0.5" to 0.5f
    )

    DisposableEffect(Unit) {
        isPlaying = Media3Manager.isPlaying
        val d = Media3Manager.duration
        duration = if (d == C.TIME_UNSET) 0L else d
        currentPosition = Media3Manager.currentPosition
        playbackSpeed = Media3Manager.currentSpeed
        bufferedPercentage = Media3Manager.bufferedPercentage
        playbackState = Media3Manager.playbackState

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingState: Boolean) { 
                isPlaying = isPlayingState 
            }
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_TIMELINE_CHANGED)) {
                    val currentDur = Media3Manager.duration
                    duration = if (currentDur == C.TIME_UNSET) 0L else currentDur
                    playbackState = Media3Manager.playbackState
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
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                playbackSpeed = Media3Manager.currentSpeed
            }
        }
        Media3Manager.addListener(listener)
        onDispose { Media3Manager.removeListener(listener) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (isPlaying && !isSeeking && dragMode != DragMode.SEEK) {
                currentPosition = Media3Manager.currentPosition
            }
            bufferedPercentage = Media3Manager.bufferedPercentage
            delay(500L) 
        }
    }

    LaunchedEffect(showControls, isPlaying, isLocked, showSpeedMenu, showMoreMenu, isSeeking, dragMode, isLongPressSpeedUp) {
        if (showControls && isPlaying && !showSpeedMenu && !showMoreMenu && !isSeeking && dragMode == DragMode.NONE && !isLongPressSpeedUp) {
            delay(4500L)
            showControls = false
        }
    }

    val safeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val playerView = LayoutInflater.from(ctx).inflate(R.layout.view_custom_player, null, false) as PlayerView
                playerView.player = Media3Manager.getInstance(ctx)
                playerView.keepScreenOn = true 
                playerView
            },
            update = { view ->
                view.keepScreenOn = isPlaying 
            },
            modifier = Modifier.matchParentSize()
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (!isLocked && !isMiniPlayer) {
                                if (isPlaying) Media3Manager.pause() else Media3Manager.resume()
                                showControls = true
                            }
                        },
                        onTap = {
                            if (showSpeedMenu || showMoreMenu) {
                                showSpeedMenu = false
                                showMoreMenu = false
                            } else {
                                showControls = !showControls
                            }
                        },
                        onLongPress = {
                            if (!isLocked && !isMiniPlayer && isPlaying) {
                                Media3Manager.setPlaybackSpeed(3.0f)
                                isLongPressSpeedUp = true
                            }
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (isLongPressSpeedUp) {
                                Media3Manager.setPlaybackSpeed(1.0f)
                                isLongPressSpeedUp = false
                            }
                        }
                    )
                }
                .then(
                    if (isMiniPlayer) Modifier else Modifier.pointerInput(Unit) {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                        detectDragGestures(
                            onDragStart = { 
                                dragMode = DragMode.NONE 
                            },
                            onDragEnd = {
                                if (dragMode == DragMode.SEEK) {
                                    Media3Manager.seekTo(dragSeekPosition)
                                }
                                dragMode = DragMode.NONE
                            },
                            onDragCancel = {
                                dragMode = DragMode.NONE
                            },
                            onDrag = { change, dragAmount ->
                                if (isLocked) return@detectDragGestures
                                change.consume()
                                
                                if (dragMode == DragMode.NONE) {
                                    if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y) + 2f) {
                                        dragMode = DragMode.SEEK
                                        dragSeekPosition = currentPosition
                                    } else if (kotlin.math.abs(dragAmount.y) > 2f) {
                                        if (change.position.x < size.width / 2) {
                                            dragMode = DragMode.BRIGHTNESS
                                            val activity = context.findActivity()
                                            dragBrightnessValue = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f
                                        } else {
                                            dragMode = DragMode.VOLUME
                                            dragVolumeValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                        }
                                    }
                                }

                                when (dragMode) {
                                    DragMode.SEEK -> {
                                        val seekDelta = (dragAmount.x / size.width) * 300000L
                                        dragSeekPosition = (dragSeekPosition + seekDelta).toLong().coerceIn(0L, duration)
                                    }
                                    DragMode.BRIGHTNESS -> {
                                        val delta = -(dragAmount.y / size.height) * 1.2f
                                        dragBrightnessValue = (dragBrightnessValue + delta).coerceIn(0f, 1f)
                                        val activity = context.findActivity()
                                        val attr = activity?.window?.attributes
                                        attr?.screenBrightness = dragBrightnessValue
                                        activity?.window?.attributes = attr
                                    }
                                    DragMode.VOLUME -> {
                                        val delta = -(dragAmount.y / size.height) * maxVolume * 1.2f
                                        dragVolumeValue = (dragVolumeValue + delta).coerceIn(0f, maxVolume.toFloat())
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, dragVolumeValue.toInt(), 0)
                                    }
                                    else -> {}
                                }
                            }
                        )
                    }
                )
        ) {
            AnimatedVisibility(
                visible = isLongPressSpeedUp,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = if (isFullscreen) 60.dp else 40.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("3X 倍速播放中", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            AnimatedVisibility(
                visible = dragMode != DragMode.NONE,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (dragMode) {
                        DragMode.SEEK -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isFullscreen && coverUrl.isNotEmpty()) {
                                    GlideImage(
                                        model = coverUrl,
                                        contentDescription = "Thumbnail",
                                        modifier = Modifier.width(160.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.height(12.dp))
                                }
                                Text(
                                    text = "${formatTime(dragSeekPosition)} / ${formatTime(duration)}",
                                    color = Color.White,
                                    fontSize = if (isFullscreen) 18.sp else 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        DragMode.BRIGHTNESS -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("${(dragBrightnessValue * 100).toInt()}%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        DragMode.VOLUME -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))
                                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                Text("${((dragVolumeValue / maxVol) * 100).toInt()}%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {}
                    }
                }
            }

            AnimatedVisibility(
                visible = playbackState == Player.STATE_BUFFERING && !isMiniPlayer,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0f))
                        .padding(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary, 
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("正在拼命缓冲...", color = Color.White, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(netSpeedText, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ================== Top 栏 ==================
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
                        .padding(
                            start = 10.dp, 
                            end = if (isFullscreen) 20.dp else 10.dp, 
                            top = bottomPadding, 
                            bottom = if (isFullscreen) 5.dp else 10.dp, 
                        ),
                    verticalAlignment = Alignment.Bottom 
                ) {
                    // ★ 修复：只在全屏时才显示返回图标
                    if (isFullscreen) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(topIconSize).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFullscreenToggle?.invoke() } 
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    
                    Text(
                        text = "$title $episodeName[$resolutionText]",
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
                    
                    Icon(
                        imageVector = Icons.Default.Cast, 
                        contentDescription = "Cast", 
                        tint = Color.White, 
                        modifier = Modifier.size(topIconSize).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { 
                            if (isPlaying) {
                                Media3Manager.pause()
                            }
                            showCastDialog = true 
                        }
                    )
                    
                    if (isFullscreen) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = Color.White,
                            modifier = Modifier.size(topIconSize).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { 
                                showMoreMenu = !showMoreMenu
                                showSpeedMenu = false 
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                    val batteryWidth = if (isFullscreen) 28.dp else 22.dp
                    val batteryHeight = if (isFullscreen) 14.dp else 11.dp
                    val batteryFontSize = if (isFullscreen) 10.sp else 8.sp

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                        Box(
                            modifier = Modifier
                                .size(width = batteryWidth, height = batteryHeight)
                                .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                                .padding(1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(batteryLevel / 100f)
                                    .fillMaxHeight()
                                    .align(Alignment.CenterStart)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(if (batteryLevel <= 20) Color.Red else Color.White)
                            )
                            Text(
                                text = "$batteryLevel",
                                color = if (batteryLevel > 40 && batteryLevel > 20) Color.Black else Color.White,
                                fontSize = batteryFontSize,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(width = 2.dp, height = batteryHeight * 0.4f)
                                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showControls && !isLocked && dragMode == DragMode.NONE && playbackState != Player.STATE_BUFFERING, 
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (isPlaying) Media3Manager.pause() else Media3Manager.resume() }
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

            AnimatedVisibility(
                visible = showControls && !isMiniPlayer && dragMode == DragMode.NONE,
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
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { isLocked = !isLocked }
                        .padding(lockBgPadding)
                ) {
                    Icon(imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(lockIconSize))
                }
            }

            // ================== Bottom 栏 ==================
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
                        .padding(
                            start = if (isFullscreen) 15.dp else 10.dp, 
                            end = if (isFullscreen) 15.dp else 10.dp, 
                            top = if (isFullscreen) 5.dp else 10.dp, 
                            bottom = bottomPadding
                        ),
                    verticalAlignment = Alignment.Top 
                ) {
                    if (hasNextEpisode && !isMiniPlayer) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Episode",
                            tint = Color.White,
                            modifier = Modifier
                                .size(bottomIconSize)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onNextEpisode?.invoke() }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    val displayPosition = if (dragMode == DragMode.SEEK) dragSeekPosition else currentPosition
                    Text(text = formatTime(displayPosition), color = Color.White, fontSize = bottomTextSize, fontWeight = FontWeight.SemiBold)
                    
                    CustomVideoSlider(
                        progress = if (duration > 0) (displayPosition.toFloat() / duration.toFloat()) else 0f,
                        bufferedProgress = bufferedPercentage / 100f, 
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
                            modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { 
                                showSpeedMenu = !showSpeedMenu
                                showMoreMenu = false 
                            }
                        )
                        
                        Spacer(modifier = Modifier.width(10.dp))
                        
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(bottomIconSize).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFullscreenToggle?.invoke() }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showSpeedMenu && !isMiniPlayer,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(70.dp)
                        .background(Color.Black.copy(alpha = 0.85f))
                        .then(if (isFullscreen) Modifier.windowInsetsPadding(safeInsets.only(WindowInsetsSides.End)) else Modifier)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
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
                                    Media3Manager.setPlaybackSpeed(speedVal)
                                    showSpeedMenu = false
                                }
                                .padding(vertical = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showMoreMenu && !isMiniPlayer && isFullscreen,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                val episodes = playLists.getOrNull(selectedTabIndex)?.episodes ?: emptyList()
                val displayEpisodes = if (isReversed) episodes.reversed() else episodes
                val gridState = rememberLazyGridState()
                
                LaunchedEffect(showMoreMenu, selectedTabIndex, isReversed) {
                    if (showMoreMenu) {
                        val selectedIndex = displayEpisodes.indexOfFirst { it.name == episodeName }
                        if (selectedIndex >= 0) {
                            gridState.scrollToItem(selectedIndex)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(if (isPhysicalPhone) 300.dp else 400.dp) 
                        .background(Color.Black.copy(alpha = 0.85f))
                        .then(if (isFullscreen) Modifier.windowInsetsPadding(safeInsets.only(WindowInsetsSides.End)) else Modifier)
                        .padding(start = 16.dp, end = 24.dp, bottom = 10.dp, top = 32.dp)
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
                                            .height(if (isPhysicalPhone) 32.dp else 40.dp)
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
                                            text = "${playList.sourceName}(${playList.episodes.size})",
                                            fontSize = if (isPhysicalPhone) 11.sp else 14.sp,
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
                                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onReverseToggle?.invoke() }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        key(selectedTabIndex, isReversed) {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(minSize = if (isPhysicalPhone) 65.dp else 80.dp),
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
                                            .padding(vertical = if (isPhysicalPhone) 6.dp else 10.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ep.name,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                            fontSize = if (isPhysicalPhone) 10.sp else 12.sp,
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

            if (showCastDialog) {
                CastDeviceDialog(
                    viewModel = castViewModel,
                    onDismiss = { showCastDialog = false },
                    onDeviceClick = { device ->
                        castViewModel.selectDevice(device)
                        Log.e("CAST_DEBUG", "推送给电视的 URL: $url")
                        castViewModel.castUrlWithPosition(
                            url = url,
                            title = "$title - $episodeName",
                            startPositionMs = currentPosition
                        )
                        showCastDialog = false
                    }
                )
            }

            if (showNextEpisodePrompt) {
                NextEpisodeDialog(
                    onDismiss = { showNextEpisodePrompt = false },
                    onPlayNext = {
                        showNextEpisodePrompt = false
                        onNextEpisode?.invoke()
                    }
                )
            }
        }
    }
}

@Composable
fun CustomVideoSlider(
    progress: Float, 
    bufferedProgress: Float,
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
        Box(modifier = Modifier.fillMaxWidth(fraction = bufferedProgress.coerceIn(0.001f, 1f)).height(2.dp).align(Alignment.CenterStart).background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(1.dp)))
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