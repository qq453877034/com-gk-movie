// 文件路径: com/gk/movie/Utils/Media3Play/ui/infoScreen.kt
package com.gk.movie.Utils.Media3Play.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gk.movie.Utils.Media3Play.Util.Media3Manager
import com.gk.movie.Utils.Media3Play.Util.VideoSniffer
import kotlin.math.roundToInt

fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

@Composable
fun InfoScreen(viewModel: InfoViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (val state = uiState) {
            is UiState.Loading -> {
                MovieContentSkeleton()
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                MovieContent(movieInfo = state.data)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieContent(movieInfo: MovieInfo) {
    val context = LocalContext.current
    val activity = context.findActivity() 
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 840

    DisposableEffect(Unit) {
        onDispose {
            Media3Manager.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isReversed by remember { mutableStateOf(false) }
    var selectedEpisodeUrl by remember { mutableStateOf<String?>(null) }
    var selectedEpisodeName by remember { mutableStateOf("") }
    var realVideoUrl by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isSniffing by remember { mutableStateOf(false) }

    var isFullscreen by remember { mutableStateOf(false) }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            if (!isExpandedScreen) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val handlePlayClick = {
        if (selectedEpisodeUrl == null) {
            val firstEp = movieInfo.playLists.firstOrNull()?.episodes?.firstOrNull()
            selectedEpisodeUrl = firstEp?.url
            selectedEpisodeName = firstEp?.name ?: "" 
        }
        if (selectedEpisodeUrl != null) {
            isPlaying = true
        }
    }
    
    val hasNextEpisode by remember(movieInfo.playLists, selectedTabIndex, selectedEpisodeUrl) {
        derivedStateOf {
            if (movieInfo.playLists.isEmpty() || selectedEpisodeUrl == null) return@derivedStateOf false
            val currentList = movieInfo.playLists.getOrNull(selectedTabIndex) ?: return@derivedStateOf false
            val currentIndex = currentList.episodes.indexOfFirst { it.url == selectedEpisodeUrl }
            currentIndex != -1 && currentIndex < currentList.episodes.size - 1
        }
    }

    val handleNextEpisode = {
        if (movieInfo.playLists.isNotEmpty() && selectedEpisodeUrl != null) {
            val currentList = movieInfo.playLists.getOrNull(selectedTabIndex)
            if (currentList != null) {
                val currentIndex = currentList.episodes.indexOfFirst { it.url == selectedEpisodeUrl }
                if (currentIndex != -1 && currentIndex < currentList.episodes.size - 1) {
                    val nextEp = currentList.episodes[currentIndex + 1]
                    selectedEpisodeUrl = nextEp.url
                    selectedEpisodeName = nextEp.name
                    realVideoUrl = null 
                    isPlaying = true
                }
            }
        }
    }

    LaunchedEffect(selectedEpisodeUrl, isPlaying) {
        if (isPlaying && selectedEpisodeUrl != null && realVideoUrl == null) {
            isSniffing = true
            val m3u8 = VideoSniffer.sniff(context, selectedEpisodeUrl!!)
            if (m3u8 != null) {
                realVideoUrl = m3u8 
            } else {
                Log.e("Sniffer", "解析失败或未找到 m3u8")
            }
            isSniffing = false
        }
    }

    if (isFullscreen && realVideoUrl != null) {
        PlayViewsScreen(
            url = realVideoUrl!!,
            title = movieInfo.title,
            episodeName = selectedEpisodeName,
            isMiniPlayer = false,
            isFullscreen = true,
            hasNextEpisode = hasNextEpisode,
            onFullscreenToggle = { isFullscreen = false }, 
            onNextEpisode = handleNextEpisode, 
            playLists = movieInfo.playLists,   
            selectedTabIndex = selectedTabIndex,
            isReversed = isReversed,
            onTabSelected = { selectedTabIndex = it; isReversed = false },
            onReverseToggle = { isReversed = !isReversed },
            onEpisodeSelected = { url, name -> 
                selectedEpisodeUrl = url
                selectedEpisodeName = name
                realVideoUrl = null
                isPlaying = true 
            },
            modifier = Modifier.fillMaxSize().background(Color.Black)
        )
        return 
    }

    if (isExpandedScreen) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, top = 60.dp, bottom = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .verticalScroll(rememberScrollState())
            ) {
                if (isPlaying && selectedEpisodeUrl != null) {
                    if (isSniffing) {
                        SniffingLoadingView(modifier = Modifier.fillMaxWidth())
                    } else if (realVideoUrl != null) {
                        PlayViewsScreen(
                            url = realVideoUrl!!,
                            title = movieInfo.title,
                            episodeName = selectedEpisodeName,
                            isMiniPlayer = false,
                            isFullscreen = false,
                            hasNextEpisode = hasNextEpisode,
                            onFullscreenToggle = { isFullscreen = true }, 
                            onNextEpisode = handleNextEpisode,
                            playLists = movieInfo.playLists,
                            selectedTabIndex = selectedTabIndex,
                            isReversed = isReversed,
                            onTabSelected = { selectedTabIndex = it; isReversed = false },
                            onReverseToggle = { isReversed = !isReversed },
                            onEpisodeSelected = { url, name -> 
                                selectedEpisodeUrl = url
                                selectedEpisodeName = name
                                realVideoUrl = null
                                isPlaying = true 
                            },
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                        )
                    }
                } else {
                    MovieHeaderInfo(movieInfo, isExpandedScreen, handlePlayClick)
                }

                Spacer(modifier = Modifier.height(30.dp))
                MovieDescriptionSection(movieInfo, isExpandedScreen)
            }

            Column(modifier = Modifier.weight(0.4f)) {
                if (isPlaying && selectedEpisodeUrl != null) {
                    MovieHeaderInfo(movieInfo, isExpandedScreen = false, handlePlayClick)
                    Spacer(modifier = Modifier.height(20.dp))
                }

                if (movieInfo.playLists.isNotEmpty()) {
                    PlayListHeader(
                        playLists = movieInfo.playLists,
                        selectedTabIndex = selectedTabIndex,
                        isReversed = isReversed,
                        onTabSelected = { selectedTabIndex = it; isReversed = false },
                        onReverseToggle = { isReversed = !isReversed },
                        isExpandedScreen = isExpandedScreen,
                        isStuck = false 
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PlayListGrid(
                        playLists = movieInfo.playLists,
                        selectedTabIndex = selectedTabIndex,
                        isReversed = isReversed,
                        selectedEpisodeUrl = selectedEpisodeUrl,
                        onEpisodeSelected = { url, name -> 
                            selectedEpisodeUrl = url
                            selectedEpisodeName = name
                            realVideoUrl = null
                            isPlaying = true 
                        },
                        isExpandedScreen = isExpandedScreen,
                        modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()) 
                    )
                }
            }
        }
    } else {
        val listState = rememberLazyListState()
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        
        val isStuck by remember { 
            derivedStateOf { 
                if (listState.firstVisibleItemIndex == 0) {
                    val firstItemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                    listState.firstVisibleItemScrollOffset > (firstItemSize / 1.1f)
                } else {
                    listState.firstVisibleItemIndex >= 1
                }
            } 
        }

        val isMiniPlayer by remember {
            derivedStateOf {
                if (!isPlaying) return@derivedStateOf false
                if (listState.firstVisibleItemIndex > 0) return@derivedStateOf true
                val firstItemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                if (firstItemSize == 0) return@derivedStateOf false
                listState.firstVisibleItemScrollOffset > (firstItemSize / 1.5f)
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val maxWidthPx = with(density) { maxWidth.toPx() }
            val maxHeightPx = with(density) { maxHeight.toPx() }
            val playerWidthPx = with(density) { 220.dp.toPx() }
            val playerHeightPx = playerWidthPx * (9f / 16f) 
            val rightPaddingPx = with(density) { 16.dp.toPx() }
            val bottomPaddingPx = with(density) { 90.dp.toPx() }

            val minOffsetX = -(maxWidthPx - playerWidthPx - rightPaddingPx)
            val maxOffsetX = rightPaddingPx 
            val minOffsetY = -(maxHeightPx - playerHeightPx - bottomPaddingPx) 
            val maxOffsetY = bottomPaddingPx 

            val clampedOffsetX = offsetX.coerceIn(minOffsetX, maxOffsetX)
            val clampedOffsetY = offsetY.coerceIn(minOffsetY, maxOffsetY)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 10.dp)
            ) {
                item {
                    Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 45.dp)) {
                        
                        if (isPlaying && selectedEpisodeUrl != null) {
                            if (isMiniPlayer) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color.Black)
                                )
                            } else {
                                if (isSniffing) {
                                    SniffingLoadingView(modifier = Modifier.fillMaxWidth())
                                } else if (realVideoUrl != null) {
                                    PlayViewsScreen(
                                        url = realVideoUrl!!,
                                        title = movieInfo.title,
                                        episodeName = selectedEpisodeName,
                                        isMiniPlayer = false,
                                        isFullscreen = false,
                                        hasNextEpisode = hasNextEpisode,
                                        onFullscreenToggle = { isFullscreen = true }, 
                                        onNextEpisode = handleNextEpisode,
                                        playLists = movieInfo.playLists,
                                        selectedTabIndex = selectedTabIndex,
                                        isReversed = isReversed,
                                        onTabSelected = { selectedTabIndex = it; isReversed = false },
                                        onReverseToggle = { isReversed = !isReversed },
                                        onEpisodeSelected = { url, name -> 
                                            selectedEpisodeUrl = url
                                            selectedEpisodeName = name
                                            realVideoUrl = null
                                            isPlaying = true 
                                        },
                                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                        } else {
                            MovieHeaderInfo(movieInfo, isExpandedScreen, handlePlayClick)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        MovieDescriptionSection(movieInfo, isExpandedScreen)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                if (movieInfo.playLists.isNotEmpty()) {
                    stickyHeader {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background) 
                                .padding(horizontal = 10.dp)
                                .padding(bottom = 12.dp, top = 8.dp)
                        ) {
                            PlayListHeader(
                                playLists = movieInfo.playLists,
                                selectedTabIndex = selectedTabIndex,
                                isReversed = isReversed,
                                onTabSelected = { selectedTabIndex = it; isReversed = false },
                                onReverseToggle = { isReversed = !isReversed },
                                isExpandedScreen = isExpandedScreen,
                                isStuck = isStuck
                            )
                        }
                    }

                    item {
                        PlayListGrid(
                            playLists = movieInfo.playLists,
                            selectedTabIndex = selectedTabIndex,
                            isReversed = isReversed,
                            selectedEpisodeUrl = selectedEpisodeUrl,
                            onEpisodeSelected = { url, name -> 
                                selectedEpisodeUrl = url
                                selectedEpisodeName = name
                                realVideoUrl = null
                                isPlaying = true 
                            },
                            isExpandedScreen = isExpandedScreen,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isMiniPlayer && realVideoUrl != null && !isSniffing,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset { IntOffset(clampedOffsetX.roundToInt(), clampedOffsetY.roundToInt()) }
                    .padding(end = 16.dp, bottom = 90.dp) 
            ) {
                realVideoUrl?.let { url ->
                    PlayViewsScreen(
                        url = url,
                        title = movieInfo.title,
                        episodeName = selectedEpisodeName,
                        isMiniPlayer = true,
                        isFullscreen = false,
                        hasNextEpisode = hasNextEpisode,
                        onNextEpisode = handleNextEpisode,
                        modifier = Modifier
                            .width(220.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume() 
                                    offsetX = (offsetX + dragAmount.x).coerceIn(minOffsetX, maxOffsetX)
                                    offsetY = (offsetY + dragAmount.y).coerceIn(minOffsetY, maxOffsetY)
                                }
                            }
                    )
                }
            }
        }
    }
}