// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/ui/infoScreen.kt
package com.gk.movie.Utils.Media3Play.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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

fun parseChineseNumber(chineseStr: String): Int {
    val map = mapOf('零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
    val units = mapOf('十' to 10, '百' to 100, '千' to 1000)
    var result = 0
    var temp = 0
    var valid = false
    for (char in chineseStr) {
        if (map.containsKey(char)) {
            temp = map[char]!!
            valid = true
        } else if (units.containsKey(char)) {
            val unit = units[char]!!
            if (temp == 0) temp = 1 
            result += temp * unit
            temp = 0
            valid = true
        }
    }
    result += temp
    return if (valid) result else -1
}

fun extractEpisodeNumbers(name: String): List<Int> {
    val arabic = Regex("\\d+").findAll(name).mapNotNull { it.value.toIntOrNull() }.toList()
    if (arabic.isNotEmpty()) return arabic
    
    val chinesePattern = Regex("[零一二三四五六七八九十百千两]+")
    return chinesePattern.findAll(name).map { parseChineseNumber(it.value) }.filter { it != -1 }.toList()
}

@Composable
fun InfoScreen(vodId: String, viewModel: InfoViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(vodId) {
        if (vodId.isNotEmpty()) {
            viewModel.fetchMovieDetail(vodId)
        }
    }

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
    val view = LocalView.current
    
    val displayMetrics = context.resources.displayMetrics
    val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
    val screenHeightDp = displayMetrics.heightPixels / displayMetrics.density
    val physicalSmallestWidthDp = kotlin.math.min(screenWidthDp, screenHeightDp)
    val isPhysicalTablet = physicalSmallestWidthDp >= 600

    val isExpandedScreen = configuration.screenWidthDp >= 840

    DisposableEffect(Unit) {
        onDispose {
            Media3Manager.release()
            if (!isPhysicalTablet) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isReversed by remember { mutableStateOf(false) }
    var selectedEpisodeUrl by remember { mutableStateOf<String?>(null) }
    var selectedEpisodeName by remember { mutableStateOf("") }
    
    var realVideoUrl by remember { mutableStateOf<String?>(null) }
    // 存储从 VideoSniffer 拿到的片头片尾秒数
    var headEndSec by remember { mutableIntStateOf(0) }
    var tailStartSec by remember { mutableIntStateOf(0) }
    
    var isPlaying by remember { mutableStateOf(false) }
    var isSniffing by remember { mutableStateOf(false) }

    var isFullscreen by remember { mutableStateOf(false) }
    
    var backPressedTime by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = isFullscreen || isPlaying) {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - backPressedTime > 2000) {
                android.widget.Toast.makeText(context, "再滑一次退出播放", android.widget.Toast.LENGTH_SHORT).show()
                backPressedTime = currentTime
            } else {
                activity?.finish()
            }
        }
    }

    LaunchedEffect(isFullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val isMultiWindow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode
        val shouldControlSystem = !isPhysicalTablet && !isMultiWindow

        if (shouldControlSystem) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            if (isFullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val handlePlayClick = {
        if (selectedEpisodeUrl == null) {
            val firstEp = movieInfo.playLists.firstOrNull()?.episodes?.firstOrNull()
            selectedEpisodeUrl = firstEp?.url
            selectedEpisodeName = firstEp?.name ?: "" 
        }
        if (selectedEpisodeUrl != null) {
            if (realVideoUrl == null) isSniffing = true 
            isPlaying = true
        }
    }
    
    val nextEpisode by remember(movieInfo.playLists, selectedTabIndex, selectedEpisodeUrl) {
        derivedStateOf {
            if (movieInfo.playLists.isEmpty() || selectedEpisodeUrl == null) return@derivedStateOf null
            val currentList = movieInfo.playLists.getOrNull(selectedTabIndex) ?: return@derivedStateOf null
            val episodes = currentList.episodes
            val currentIndex = episodes.indexOfFirst { it.url == selectedEpisodeUrl }
            if (currentIndex == -1) return@derivedStateOf null

            val nonEpisodicKeywords = listOf("国语", "粤语", "英语", "中字", "原版", "正片", "蓝光", "超清", "高清", "标清", "1080", "720", "4k", "4K", "TC", "TS", "BD", "HD")
            val episodicKeywords = listOf("集", "话", "期", "篇", "章", "季")
            
            val hasEpisodicKeyword = episodes.any { ep -> episodicKeywords.any { ep.name.contains(it) } }
            
            val isQualityOrLangList = episodes.size <= 6 && !hasEpisodicKeyword && episodes.all { ep ->
                nonEpisodicKeywords.any { ep.name.contains(it, ignoreCase = true) } || ep.name.contains("预告") || ep.name == "正片"
            }

            if (isQualityOrLangList) return@derivedStateOf null

            var isAscending = true
            if (episodes.isNotEmpty()) {
                val firstNums = extractEpisodeNumbers(episodes.first().name)
                val lastNums = extractEpisodeNumbers(episodes.last().name)
                
                for (i in 0 until kotlin.math.min(firstNums.size, lastNums.size)) {
                    if (firstNums[i] > lastNums[i]) {
                        isAscending = false
                        break
                    } else if (firstNums[i] < lastNums[i]) {
                        isAscending = true
                        break
                    }
                }
            }

            val nextIndex = if (isAscending) currentIndex + 1 else currentIndex - 1
            if (nextIndex in episodes.indices) episodes[nextIndex] else null
        }
    }

    // ★ 修复的核心：补上这行丢失的代码
    val hasNextEpisode = nextEpisode != null

    val handleNextEpisode: () -> Unit = {
        nextEpisode?.let { nextEp ->
            selectedEpisodeUrl = nextEp.url
            selectedEpisodeName = nextEp.name
            realVideoUrl = null 
            // 重置时间，防止上一集的片尾时间影响下一集
            headEndSec = 0
            tailStartSec = 0
            isSniffing = true 
            isPlaying = true
        }
    }

    LaunchedEffect(selectedEpisodeUrl, isPlaying) {
        if (isPlaying && selectedEpisodeUrl != null && realVideoUrl == null) {
            isSniffing = true
            val result = VideoSniffer.sniffVideoUrl(context, selectedEpisodeUrl!!)
            if (result != null) {
                realVideoUrl = result.playUrl 
                headEndSec = result.headEnd
                tailStartSec = result.tailStart
            } else {
                Log.e("Sniffer", "解析失败或未找到 m3u8")
            }
            isSniffing = false
        }
    }

    if (isFullscreen) {
        if (realVideoUrl != null && !isSniffing) {
            PlayViewsScreen(
                url = realVideoUrl!!,
                title = movieInfo.title,
                episodeName = selectedEpisodeName,
                coverUrl = movieInfo.coverUrl, 
                isMiniPlayer = false,
                isFullscreen = true,
                hasNextEpisode = hasNextEpisode,
                headEnd = headEndSec,
                tailStart = tailStartSec,
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
                    headEndSec = 0
                    tailStartSec = 0
                    isSniffing = true 
                    isPlaying = true 
                },
                modifier = Modifier.fillMaxSize().background(Color.Black)
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                SniffingLoadingView(modifier = Modifier.wrapContentSize())
            }
        }
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
                    if (isSniffing || realVideoUrl == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            SniffingLoadingView(modifier = Modifier.wrapContentSize())
                        }
                    } else if (realVideoUrl != null) {
                        PlayViewsScreen(
                            url = realVideoUrl!!,
                            title = movieInfo.title,
                            episodeName = selectedEpisodeName,
                            coverUrl = movieInfo.coverUrl,
                            isMiniPlayer = false,
                            isFullscreen = false,
                            hasNextEpisode = hasNextEpisode,
                            headEnd = headEndSec,
                            tailStart = tailStartSec,
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
                                headEndSec = 0
                                tailStartSec = 0
                                isSniffing = true 
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
                            headEndSec = 0
                            tailStartSec = 0
                            isSniffing = true 
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
                                if (isSniffing || realVideoUrl == null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        SniffingLoadingView(modifier = Modifier.wrapContentSize())
                                    }
                                } else if (realVideoUrl != null) {
                                    PlayViewsScreen(
                                        url = realVideoUrl!!,
                                        title = movieInfo.title,
                                        episodeName = selectedEpisodeName,
                                        coverUrl = movieInfo.coverUrl,
                                        isMiniPlayer = false,
                                        isFullscreen = false,
                                        hasNextEpisode = hasNextEpisode,
                                        headEnd = headEndSec,
                                        tailStart = tailStartSec,
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
                                            headEndSec = 0
                                            tailStartSec = 0
                                            isSniffing = true 
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
                                headEndSec = 0
                                tailStartSec = 0
                                isSniffing = true 
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
                        coverUrl = movieInfo.coverUrl,
                        isMiniPlayer = true,
                        isFullscreen = false,
                        hasNextEpisode = hasNextEpisode,
                        headEnd = headEndSec,
                        tailStart = tailStartSec,
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