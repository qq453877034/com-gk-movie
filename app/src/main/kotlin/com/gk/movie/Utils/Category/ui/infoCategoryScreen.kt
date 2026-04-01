// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Category/ui/infoCategoryScreen.kt
package com.gk.movie.Utils.Category.ui

import android.content.Intent
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.gk.movie.Utils.Category.Util.CategoryMovie
import com.gk.movie.Utils.Category.Util.CategoryUiState
import com.gk.movie.Utils.Category.Util.InfoCategoryModel
import com.gk.movie.Utils.Category.Util.PageItem
import kotlinx.coroutines.launch

enum class SidebarType {
    HISTORY, FAVORITES
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoCategoryScreen(
    viewModel: InfoCategoryModel = viewModel(),
    onMovieClick: (String) -> Unit 
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var activeSidebar by remember { mutableStateOf<SidebarType?>(null) }
    
    val configuration = LocalConfiguration.current
    val isCompactWindow = configuration.screenWidthDp < 600
    val isPhoneDevice = configuration.smallestScreenWidthDp < 600

    val scaleFactor = if (isPhoneDevice) 0.9f else 1f
    val currentDensity = LocalDensity.current
    val customDensity = Density(
        density = currentDensity.density * scaleFactor,
        fontScale = currentDensity.fontScale * scaleFactor
    )
    
    val context = LocalContext.current

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var cachedSuccessState by remember { mutableStateOf<CategoryUiState.Success?>(null) }
    var currentPageStr by remember { mutableStateOf("1") }
    
    var pendingFilterUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState) {
        if (uiState is CategoryUiState.Success) {
            cachedSuccessState = uiState as CategoryUiState.Success
            val data = (uiState as CategoryUiState.Success).data
            data.pageItems.find { it.isActive }?.pageStr?.let { currentPageStr = it }
            pendingFilterUrl = null 
        }
    }

    val displayState = (uiState as? CategoryUiState.Success) ?: cachedSuccessState
    val isLoading = uiState is CategoryUiState.Loading
    val isInitialLoading = isLoading && cachedSuccessState == null

    CompositionLocalProvider(LocalDensity provides customDensity) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (displayState != null) {
                    val title = displayState.data.pageTitle
                    Surface(
                        color = MaterialTheme.colorScheme.background, 
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding() 
                                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp), 
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isCompactWindow) {
                                Text(
                                    text = "${title}频道",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp,
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
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = "Search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
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
                                                    val intent = Intent(context, com.gk.movie.Utils.Search.Ui.SearchActivity::class.java)
                                                    intent.putExtra("keyword", searchQuery)
                                                    context.startActivity(intent)
                                                }
                                            }),
                                            decorationBox = { innerTextField ->
                                                if (searchQuery.isEmpty()) {
                                                    Text(text = "搜索热播影片...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                                }
                                                innerTextField()
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (searchQuery.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Clear",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .clickable { searchQuery = "" }
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp)) 
                                IconButton(onClick = { activeSidebar = if (activeSidebar == SidebarType.HISTORY) null else SidebarType.HISTORY }) { 
                                    Icon(Icons.Rounded.History, contentDescription = "历史记录", tint = MaterialTheme.colorScheme.onSurface) 
                                }
                                IconButton(onClick = { activeSidebar = if (activeSidebar == SidebarType.FAVORITES) null else SidebarType.FAVORITES }) { 
                                    Icon(Icons.Rounded.FavoriteBorder, contentDescription = "我的收藏", tint = MaterialTheme.colorScheme.onSurface) 
                                }
                            }
                        }
                    }
                } else if (isInitialLoading) {
                    Surface(
                        color = MaterialTheme.colorScheme.background, 
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding() 
                                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isCompactWindow) {
                                Box(modifier = Modifier.width(120.dp).height(36.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = if (isCompactWindow) Arrangement.Start else Arrangement.End,
                                modifier = Modifier.weight(1f)
                            ) {
                                val searchBarModifier = if (isCompactWindow) Modifier.weight(1f) else Modifier.width(300.dp)
                                Box(modifier = searchBarModifier.height(40.dp).clip(RoundedCornerShape(100.dp)).shimmerEffect())
                                Spacer(modifier = Modifier.width(8.dp))
                                repeat(2) {
                                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).shimmerEffect())
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
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
                            pendingPageStr = currentPageStr,
                            onPageClick = { pageStr -> 
                                currentPageStr = pageStr
                                viewModel.loadPage(pageStr.toInt()) 
                                coroutineScope.launch { 
                                    try { listState.scrollToItem(0) } catch (e: Exception) { } 
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
                when {
                    isInitialLoading -> CategorySkeletonScreen(listState = listState)
                    uiState is CategoryUiState.Error && displayState == null -> {
                        val message = (uiState as CategoryUiState.Error).message
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    displayState != null -> {
                        val data = displayState.data
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val minItemWidth = 130.dp
                            val rawColumns = (maxWidth / minItemWidth).toInt()
                            val columns = maxOf(1, if (rawColumns >= 4) rawColumns - 3 else rawColumns)
                            val chunkedMovies = remember(data.movies, columns) { data.movies.chunked(columns) }

                            val regularFilters = data.filters.filter { it.groupName != "排序" }
                            val stickyFilters = data.filters.filter { it.groupName == "排序" }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState, 
                                contentPadding = PaddingValues(bottom = 16.dp) 
                            ) {
                                if (regularFilters.isNotEmpty()) {
                                    item(key = "RegularFiltersContainer") {
                                        Surface(
                                            color = MaterialTheme.colorScheme.background,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column {
                                                regularFilters.forEach { filterGroup ->
                                                    LazyRow(
                                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                                                    ) {
                                                        items(items = filterGroup.items) { item ->
                                                            val isSelected = if (pendingFilterUrl != null) {
                                                                item.targetId == pendingFilterUrl || 
                                                                (item.isSelected && filterGroup.items.none { it.targetId == pendingFilterUrl })
                                                            } else {
                                                                item.isSelected
                                                            }

                                                            Surface(
                                                                shape = RoundedCornerShape(16.dp),
                                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                                modifier = Modifier
                                                                    .height(34.dp)
                                                                    .clip(RoundedCornerShape(16.dp))
                                                                    .clickable { 
                                                                        pendingFilterUrl = item.targetId
                                                                        currentPageStr = "1"
                                                                        viewModel.updateFilter(item.targetId) 
                                                                        coroutineScope.launch { 
                                                                            try { listState.animateScrollToItem(0) } catch (e: Exception) {} 
                                                                        }
                                                                    }
                                                            ) {
                                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                                                    Text(text = item.name, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (stickyFilters.isNotEmpty()) {
                                    stickyHeader(key = "StickyFiltersContainer") {
                                        Surface(
                                            color = MaterialTheme.colorScheme.background,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                        ) {
                                            Column {
                                                stickyFilters.forEach { filterGroup ->
                                                    LazyRow(
                                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                                                    ) {
                                                        items(items = filterGroup.items) { item ->
                                                            val isSelected = if (pendingFilterUrl != null) {
                                                                item.targetId == pendingFilterUrl || 
                                                                (item.isSelected && filterGroup.items.none { it.targetId == pendingFilterUrl })
                                                            } else {
                                                                item.isSelected
                                                            }
                                                            
                                                            Surface(
                                                                shape = RoundedCornerShape(16.dp),
                                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                                modifier = Modifier
                                                                    .height(34.dp)
                                                                    .clip(RoundedCornerShape(16.dp))
                                                                    .clickable { 
                                                                        pendingFilterUrl = item.targetId
                                                                        currentPageStr = "1"
                                                                        viewModel.updateFilter(item.targetId) 
                                                                        coroutineScope.launch { 
                                                                            try { listState.animateScrollToItem(0) } catch (e: Exception) {} 
                                                                        }
                                                                    }
                                                            ) {
                                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                                                    Text(text = item.name, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    item { Spacer(modifier = Modifier.height(8.dp)) }
                                }

                                if (uiState is CategoryUiState.Error) {
                                    item(key = "ErrorBottom", contentType = "Error") {
                                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                                            Text(text = (uiState as CategoryUiState.Error).message, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                } else if (isLoading) {
                                    items(count = 4) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            repeat(columns) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(6.dp)).shimmerEffect())
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Box(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    items(items = chunkedMovies) { rowMovies ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            for (movie in rowMovies) {
                                                Box(modifier = Modifier.weight(1f)) {
                                                    MovieItemCard(movie = movie, onClick = { onMovieClick(movie.vodId.toString()) })
                                                }
                                            }
                                            repeat(columns - rowMovies.size) { Spacer(modifier = Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (activeSidebar != null) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { activeSidebar = null })
                }

                AnimatedVisibility(
                    visible = activeSidebar != null,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    activeSidebar?.let { type -> SidebarPanel(type = type, isCompactWindow = isCompactWindow, onClose = { activeSidebar = null }) }
                }
            } 
        }
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

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MovieItemCard(movie: CategoryMovie, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)) {
                
                // 🚀 完全信任 GlideModuleUtil，恢复最纯净的请求代码
                GlideImage(
                    model = movie.coverUrl,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                if (movie.score.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd).padding(4.dp)
                            .clip(RoundedCornerShape(4.dp)).background(Color(0xAA000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = movie.score, color = Color(0xFFFF9800), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (movie.remark.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter).fillMaxWidth()
                            .background(Color(0x88000000)).padding(vertical = 4.dp, horizontal = 6.dp)
                    ) {
                        Text(text = movie.remark, color = Color.White, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = movie.title, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 14.sp, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun Modifier.shimmerEffect(): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    return this.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategorySkeletonScreen(listState: LazyListState) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val minItemWidth = 130.dp
        val rawColumns = (maxWidth / minItemWidth).toInt()
        val columns = maxOf(1, if (rawColumns >= 4) rawColumns - 1 else rawColumns)
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState, 
            userScrollEnabled = false, 
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            repeat(4) { rowIndex ->
                item(key = "Skeleton_FilterRow_$rowIndex", contentType = "FilterRow") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(
                            top = if (rowIndex == 0) 12.dp else 4.dp,
                            bottom = 4.dp
                        ),
                        userScrollEnabled = false
                    ) {
                        item(key = "Skeleton_Title_$rowIndex", contentType = "FilterTitle") { 
                            Box(modifier = Modifier.padding(end = 8.dp, top = 10.dp).width(32.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect()) 
                        }
                        items(
                            count = 18,
                            key = { colIndex -> "Skeleton_FilterItem_${rowIndex}_$colIndex" },
                            contentType = { "FilterItem" }
                        ) { 
                            Box(modifier = Modifier.width(60.dp).height(36.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect()) 
                        }
                    }
                }
            }
            
            stickyHeader(key = "Skeleton_StickySortHeader", contentType = "SortHeader") {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        userScrollEnabled = false
                    ) {
                        items(
                            count = 4,
                            key = { index -> "Skeleton_SortItem_$index" },
                            contentType = { "SortItem" }
                        ) {
                            Box(modifier = Modifier.width(70.dp).height(32.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
                        }
                    }
                }
            }

            items(
                count = 6,
                key = { index -> "Skeleton_MovieRow_$index" },
                contentType = { "LoadingMovieRow" }
            ) { 
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(columns) {
                        Card(
                            modifier = Modifier.weight(1f),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Column {
                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(6.dp)).shimmerEffect())
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}