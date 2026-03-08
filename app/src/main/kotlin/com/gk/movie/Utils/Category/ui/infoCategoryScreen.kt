package com.gk.movie.Utils.Category.ui

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoCategoryScreen(
    viewModel: InfoCategoryModel = viewModel(),
    onMovieClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    val configuration = LocalConfiguration.current
    val isCompactWindow = configuration.screenWidthDp < 600
    val isPhoneDevice = configuration.smallestScreenWidthDp < 600

    val scaleFactor = if (isPhoneDevice) 0.8f else 1f
    val currentDensity = LocalDensity.current
    val customDensity = Density(
        density = currentDensity.density * scaleFactor,
        fontScale = currentDensity.fontScale * scaleFactor
    )

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var cachedSuccessState by remember { mutableStateOf<CategoryUiState.Success?>(null) }
    var pendingSelections by remember { mutableStateOf(mapOf<String, String>()) }

    LaunchedEffect(uiState) {
        if (uiState is CategoryUiState.Success) {
            cachedSuccessState = uiState as CategoryUiState.Success
            pendingSelections = emptyMap()
        } else if (uiState is CategoryUiState.Error) {
            pendingSelections = emptyMap()
        }
    }

    val displayState = (uiState as? CategoryUiState.Success) ?: cachedSuccessState
    val isLoading = uiState is CategoryUiState.Loading
    val isInitialLoading = isLoading && cachedSuccessState == null
    
    // ★ 智能计算吸顶栏的索引位置 (打平分类列表后，吸顶栏的索引等于分类的行数)
    val stickyHeaderIndex = displayState?.data?.filters?.size ?: 4

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
                                    text = "${title}分类",
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
                                val searchBarModifier = if (isCompactWindow) Modifier.weight(1f) else Modifier.width(280.dp)
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
                                            keyboardActions = KeyboardActions(onSearch = { Log.d("CategoryScreen", "触发搜索: $searchQuery") }),
                                            decorationBox = { innerTextField ->
                                                if (searchQuery.isEmpty()) {
                                                    Text(text = "搜索热播影片...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                                }
                                                innerTextField()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp)) 
                                IconButton(onClick = { /* TODO */ }) { Icon(Icons.Rounded.History, contentDescription = "历史记录", tint = MaterialTheme.colorScheme.onSurface) }
                                IconButton(onClick = { /* TODO */ }) { Icon(Icons.Rounded.FavoriteBorder, contentDescription = "我的收藏", tint = MaterialTheme.colorScheme.onSurface) }
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
                                val searchBarModifier = if (isCompactWindow) Modifier.weight(1f) else Modifier.width(280.dp)
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
                            pendingPageUrl = pendingSelections["PAGE_GROUP"],
                            onPageClick = { url -> 
                                pendingSelections = pendingSelections + ("PAGE_GROUP" to url)
                                viewModel.fetchCategoryData(url) 
                                coroutineScope.launch {
                                    // ★ 使用智能计算出的吸顶索引
                                    if (listState.firstVisibleItemIndex >= stickyHeaderIndex) {
                                        listState.scrollToItem(stickyHeaderIndex) 
                                    }
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
            when {
                isInitialLoading -> {
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        CategorySkeletonScreen(listState = listState)
                    }
                }
                uiState is CategoryUiState.Error && displayState == null -> {
                    val message = (uiState as CategoryUiState.Error).message
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = message, color = MaterialTheme.colorScheme.error)
                    }
                }
                displayState != null -> {
                    val data = displayState.data
                    
                    BoxWithConstraints(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        val minItemWidth = 130.dp
                        val rawColumns = (maxWidth / minItemWidth).toInt()
                        val columns = maxOf(1, if (rawColumns >= 4) rawColumns - 1 else rawColumns)

                        val chunkedMovies = remember(data.movies, columns) {
                            data.movies.chunked(columns)
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState, 
                            contentPadding = PaddingValues(bottom = 16.dp) 
                        ) {
                            // ★ 终极性能优化：彻底打平 FilterGroup！
                            // 不再用一个 item 包裹全部，而是将每一行分类作为一个独立的 item，极大减轻滑动下拉时的瞬间渲染压力
                            data.filters.forEachIndexed { index, filterGroup ->
                                item(key = "FilterRow_${filterGroup.groupName}", contentType = "FilterRow") {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        // 完美复原此前的边距视觉：第一行顶部 12dp，其他行顶部 4dp
                                        modifier = Modifier.padding(
                                            top = if (index == 0) 12.dp else 4.dp,
                                            bottom = 4.dp
                                        )
                                    ) {
                                        item(key = "Title_${filterGroup.groupName}", contentType = "FilterTitle") {
                                            Text(
                                                text = filterGroup.groupName,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(end = 8.dp, top = 10.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        items(
                                            items = filterGroup.items,
                                            key = { item -> "FilterItem_${filterGroup.groupName}_${item.name}" },
                                            contentType = { "FilterItem" }
                                        ) { item ->
                                            val isSelected = if (pendingSelections.containsKey(filterGroup.groupName)) {
                                                item.url == pendingSelections[filterGroup.groupName]
                                            } else {
                                                item.isSelected
                                            }
                                            
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                modifier = Modifier
                                                    .height(36.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .clickable { 
                                                        pendingSelections = pendingSelections + (filterGroup.groupName to item.url)
                                                        viewModel.fetchCategoryData(item.url) 
                                                        coroutineScope.launch { listState.scrollToItem(0) }
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                                                    Text(text = item.name, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 动态索引跟随的吸顶栏
                            stickyHeader(key = "StickySortHeader", contentType = "SortHeader") {
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(
                                            items = data.sortItems,
                                            key = { item -> "SortItem_${item.name}" },
                                            contentType = { "SortItem" }
                                        ) { item ->
                                            val isSelected = if (pendingSelections.containsKey("SORT_GROUP")) {
                                                item.url == pendingSelections["SORT_GROUP"]
                                            } else {
                                                item.isSelected
                                            }
                                            
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
                                                modifier = Modifier
                                                    .height(32.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { 
                                                        pendingSelections = pendingSelections + ("SORT_GROUP" to item.url)
                                                        viewModel.fetchCategoryData(item.url) 
                                                        coroutineScope.launch {
                                                            if (listState.firstVisibleItemIndex >= stickyHeaderIndex) {
                                                                listState.scrollToItem(stickyHeaderIndex)
                                                            }
                                                        }
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                                    Text(text = item.name, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (uiState is CategoryUiState.Error) {
                                item(key = "ErrorBottom", contentType = "Error") {
                                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                                        Text(text = (uiState as CategoryUiState.Error).message, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            } else if (isLoading) {
                                items(
                                    count = 6,
                                    key = { index -> "LoadingRow_$index" },
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
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                            ) {
                                                Column {
                                                    Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).shimmerEffect())
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = chunkedMovies,
                                    key = { rowMovies -> "RealMovieRow_${rowMovies.firstOrNull()?.detailUrl.hashCode()}" },
                                    contentType = { "RealMovieRow" }
                                ) { rowMovies ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        for (movie in rowMovies) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                MovieItemCard(movie = movie, onClick = { onMovieClick(movie.detailUrl) })
                                            }
                                        }
                                        val emptySpaces = columns - rowMovies.size
                                        repeat(emptySpaces) {
                                            Spacer(modifier = Modifier.weight(1f))
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
}

@Composable
fun PaginationBar(
    pageItems: List<PageItem>, 
    pageTips: String, 
    onPageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isCompactWindow: Boolean,
    isLoading: Boolean,
    pendingPageUrl: String? = null 
) {
    val displayPageItems = if (isCompactWindow) {
        pageItems.filter { item ->
            val isNumberOrDots = item.title.toIntOrNull() != null || item.title.contains("...")
            !isNumberOrDots
        }
    } else {
        pageItems
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (pageTips.isNotEmpty()) {
            Text(text = pageTips, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp), 
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            // ★ 修复闪退：使用 itemsIndexed 并将 index 编入 key 之中！
            itemsIndexed(
                items = displayPageItems,
                key = { index, item -> "PageBtn_${index}_${item.title}_${item.url}" } 
            ) { index, item ->
                
                val isActive = if (pendingPageUrl != null && pendingPageUrl.isNotEmpty()) {
                    item.url == pendingPageUrl
                } else {
                    item.isActive
                }
                
                val containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                val contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = containerColor,
                    contentColor = contentColor,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !item.isDisabled && !isActive && !isLoading) { onPageClick(item.url) }
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)) {
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