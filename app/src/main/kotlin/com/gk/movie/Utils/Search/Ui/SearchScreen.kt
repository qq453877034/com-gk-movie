// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Search/Ui/SearchScreen.kt
package com.gk.movie.Utils.Search.Ui

import android.content.Intent
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.gk.movie.Utils.Category.Util.PageItem
import com.gk.movie.Utils.Search.Util.SearchMovie
import com.gk.movie.Utils.Search.Util.SearchUiState
import com.gk.movie.Utils.Search.Util.SearchViewModel
import kotlinx.coroutines.launch

enum class SidebarType { HISTORY, FAVORITES }

@Composable
fun SearchScreen(
    initialKeyword: String = "",
    viewModel: SearchViewModel = viewModel(),
    onBackClick: () -> Unit,
    onMovieClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val engines by viewModel.engines.collectAsState()
    val selectedEngineId by viewModel.selectedEngineId.collectAsState()
    
    var searchQuery by remember { mutableStateOf(initialKeyword) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    val context = LocalContext.current
    val appName = remember { context.applicationInfo.loadLabel(context.packageManager).toString() }
    
    // ==========================================
    // ★ 核心适配重构：使用官方 Material 3 Adaptive 库
    // ==========================================
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val windowWidthClass = windowSizeClass.windowWidthSizeClass
    
    // 判断宽度是否为紧凑型 (Compact)。手机竖屏通常是 Compact，而平板或横屏是 Medium 或 Expanded
    val useVerticalLayout = windowWidthClass == WindowWidthSizeClass.COMPACT
    val isCompactWindow = useVerticalLayout
    
    // 窄屏/竖屏时使用 3 列，横屏/宽屏使用 6 列
    val gridColumns = if (useVerticalLayout) 3 else 6
    // ==========================================

    var activeSidebar by remember { mutableStateOf<SidebarType?>(null) }
    var cachedSuccessState by remember { mutableStateOf<SearchUiState.Success?>(null) }
    
    LaunchedEffect(uiState) {
        if (uiState is SearchUiState.Success) {
            cachedSuccessState = uiState as SearchUiState.Success
        } else if (uiState is SearchUiState.Error) {
            val errorMsg = (uiState as SearchUiState.Error).message
            android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    
    val displayState = (uiState as? SearchUiState.Success) ?: cachedSuccessState
    val isLoading = uiState is SearchUiState.Loading

    LaunchedEffect(initialKeyword) {
        if (initialKeyword.isNotBlank() && viewModel.currentKeyword.isBlank()) {
            viewModel.search(initialKeyword)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 12.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    
                    if (!isCompactWindow) {
                        Text(
                            text = appName,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isCompactWindow) Arrangement.Start else Arrangement.End,
                        modifier = Modifier.weight(1f) 
                    ) {
                        val searchBarModifier = if (isCompactWindow) Modifier.weight(1f) else Modifier.width(300.dp)
                        Box(
                            modifier = searchBarModifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Icon(Icons.Rounded.Search, "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { 
                                        if (searchQuery.isNotBlank()) {
                                            viewModel.search(searchQuery)
                                        }
                                    }),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxWidth()) {
                                            if (searchQuery.isEmpty()) {
                                                Text("搜索想看的影视...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                            }
                                            innerTextField()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        Icons.Rounded.Close, "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp).clip(CircleShape).clickable { searchQuery = "" }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { activeSidebar = if (activeSidebar == SidebarType.HISTORY) null else SidebarType.HISTORY }) {
                            Icon(Icons.Rounded.History, contentDescription = "历史", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { activeSidebar = if (activeSidebar == SidebarType.FAVORITES) null else SidebarType.FAVORITES }) {
                            Icon(Icons.Rounded.FavoriteBorder, contentDescription = "收藏", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (displayState != null) {
                val data = displayState.data
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant, 
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PaginationBar(
                        pageItems = data.pageItems,
                        pageTips = data.pageTips,
                        pendingPageStr = viewModel.currentPage.toString(),
                        onPageClick = { pageStr -> 
                            val targetPage = pageStr.toIntOrNull()
                            if (targetPage != null) {
                                viewModel.search(viewModel.currentKeyword, targetPage) 
                                coroutineScope.launch { try { listState.scrollToItem(0) } catch (e: Exception) { } }
                            }
                        },
                        modifier = Modifier.navigationBarsPadding(),
                        isCompactWindow = isCompactWindow,
                        isLoading = isLoading
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            
            // ★ 利用 Adaptive 判断驱动布局渲染
            if (useVerticalLayout) {
                // 【窄屏/垂直模式 (Compact)】：上方是可滑动 Tab，下方是网格内容
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        items(engines) { engine ->
                            val isSelected = engine.id == selectedEngineId
                            AnimatedSearchEngineChip(
                                engineName = engine.name,
                                engineCount = engine.count,
                                isSelected = isSelected,
                                onClick = { viewModel.selectEngine(engine.id) }
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        SearchContentArea(uiState, displayState, isLoading, listState, gridColumns, onMovieClick)
                    }
                }
            } else {
                // 【宽屏/水平模式 (Medium & Expanded)】：左边是 20% 的侧边栏，右边是 80% 的网格内容
                Row(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.weight(0.2f).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(engines) { engine ->
                                val isSelected = engine.id == selectedEngineId
                                AnimatedSearchEngineCard(
                                    engineName = engine.name,
                                    engineCount = engine.count,
                                    isSelected = isSelected,
                                    onClick = { viewModel.selectEngine(engine.id) }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(0.8f).fillMaxHeight()) {
                        SearchContentArea(uiState, displayState, isLoading, listState, gridColumns, onMovieClick)
                    }
                }
            }

            // 右侧抽屉阴影与面板
            if (activeSidebar != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { activeSidebar = null }
                )
            }

            AnimatedVisibility(
                visible = activeSidebar != null,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                activeSidebar?.let { type -> 
                    SidebarPanel(type = type, isCompactWindow = isCompactWindow, onClose = { activeSidebar = null }) 
                }
            }
        }
    }
}

// =====================================
// 以下为拆分出的独立 Compose 组件
// =====================================

@Composable
fun SearchContentArea(
    uiState: SearchUiState,
    displayState: SearchUiState.Success?,
    isLoading: Boolean,
    listState: LazyListState,
    columns: Int,
    onMovieClick: (String) -> Unit
) {
    when {
        uiState is SearchUiState.Idle -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("请输入关键字进行搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        isLoading && displayState == null -> {
            SearchSkeletonScreen(columns = columns)
        }
        uiState is SearchUiState.Error && displayState == null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = (uiState as SearchUiState.Error).message, color = MaterialTheme.colorScheme.error)
            }
        }
        displayState != null -> {
            val data = displayState.data
            val chunkedMovies = remember(data.movies, columns) { data.movies.chunked(columns) }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp)
            ) {
                items(items = chunkedMovies) { rowMovies ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (movie in rowMovies) {
                            Box(modifier = Modifier.weight(1f)) {
                                SearchMovieItemCard(movie = movie, onClick = { onMovieClick(movie.vodId.toString()) })
                            }
                        }
                        repeat(columns - rowMovies.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedSearchEngineChip(
    engineName: String,
    engineCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, 
        label = "bgColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "contentColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f, 
        label = "scale"
    )

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.LocationOn,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$engineName $engineCount", 
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, 
            color = contentColor, 
            fontSize = 13.sp
        )
    }
}

@Composable
fun AnimatedSearchEngineCard(
    engineName: String,
    engineCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent, 
        label = "bgColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), 
        label = "borderColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.03f else 1f, 
        label = "scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$engineName $engineCount 条", 
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold, 
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, 
            maxLines = 2, 
            overflow = TextOverflow.Ellipsis, 
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SidebarPanel(type: SidebarType, isCompactWindow: Boolean, onClose: () -> Unit) {
    val title = if (type == SidebarType.HISTORY) "历史记录" else "我的收藏"
    val modifier = if (isCompactWindow) {
        Modifier.fillMaxWidth(0.65f).fillMaxHeight(0.6f).padding(end = 16.dp, top = 8.dp)
    } else {
        Modifier.width(400.dp).fillMaxHeight().padding(end = 20.dp, top = 8.dp, bottom = 16.dp) 
    }

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "暂无${title}数据...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun PaginationBar(
    pageItems: List<PageItem>, 
    pageTips: String, 
    onPageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isCompactWindow: Boolean,
    isLoading: Boolean,
    pendingPageStr: String? = null 
) {
    val displayPageItems = if (isCompactWindow) {
        pageItems.filter { item -> item.title.toIntOrNull() != null || item.title.contains("上一页") || item.title.contains("下一页") }
    } else { pageItems }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (pageTips.isNotEmpty()) {
            Text(text = pageTips, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            itemsIndexed(items = displayPageItems) { _, item ->
                val isActive = item.pageStr == pendingPageStr
                val containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                val contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = containerColor,
                    contentColor = contentColor,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.height(36.dp).clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !item.isDisabled && !isActive && !isLoading) { onPageClick(item.pageStr) }
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(text = item.title, fontSize = 14.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
fun Modifier.shimmerEffect(): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "shimmer_alpha"
    )
    return this.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
}

@Composable
fun SearchSkeletonScreen(columns: Int) {
    LazyColumn(userScrollEnabled = false, contentPadding = PaddingValues(bottom = 16.dp, top = 8.dp)) {
        items(6) { 
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(columns) {
                    Column(modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(6.dp)).shimmerEffect())
                        Spacer(Modifier.height(6.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SearchMovieItemCard(movie: SearchMovie, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)) {
                GlideImage(model = movie.coverUrl, contentDescription = movie.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (movie.score.isNotEmpty()) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xAA000000)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(text = movie.score, color = Color(0xFFFF9800), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (movie.remark.isNotEmpty()) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x88000000)).padding(vertical = 4.dp, horizontal = 6.dp)) {
                        Text(text = movie.remark, color = Color.White, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
            
            Column(
                modifier = Modifier.padding(8.dp).height(50.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = movie.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Text(
                    text = movie.subTitle.ifEmpty { " " }, 
                    fontSize = 11.sp, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}