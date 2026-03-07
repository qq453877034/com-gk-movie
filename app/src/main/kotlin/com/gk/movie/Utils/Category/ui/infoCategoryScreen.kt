package com.gk.movie.Utils.Category.ui

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.gk.movie.Utils.Category.Util.CategoryMovie
import com.gk.movie.Utils.Category.Util.CategoryUiState
import com.gk.movie.Utils.Category.Util.InfoCategoryModel
import com.gk.movie.Utils.Category.Util.PageItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoCategoryScreen(
    viewModel: InfoCategoryModel = viewModel(),
    onMovieClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    val configuration = LocalConfiguration.current
    val isCompactScreen = configuration.screenWidthDp < 600

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            when (uiState) {
                is CategoryUiState.Success -> {
                    val title = (uiState as CategoryUiState.Success).data.pageTitle
                    Surface(
                        color = MaterialTheme.colorScheme.background, 
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding() 
                                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp), 
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isCompactScreen) {
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
                                horizontalArrangement = if (isCompactScreen) Arrangement.Start else Arrangement.End,
                                modifier = Modifier.weight(1f) 
                            ) {
                                // ★ 核心修改：小窗占满剩余空间，大屏固定 280dp 宽度不占满行
                                val searchBarModifier = if (isCompactScreen) Modifier.weight(1f) else Modifier.width(280.dp)
                                
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
                                            textStyle = TextStyle(
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(
                                                onSearch = { Log.d("CategoryScreen", "触发搜索: $searchQuery") }
                                            ),
                                            decorationBox = { innerTextField ->
                                                if (searchQuery.isEmpty()) {
                                                    Text(
                                                        text = "搜索热播影片...",
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                    )
                                                }
                                                innerTextField()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp)) 

                                IconButton(onClick = { /* TODO: 跳转历史 */ }) {
                                    Icon(Icons.Rounded.History, contentDescription = "历史记录", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { /* TODO: 跳转收藏 */ }) {
                                    Icon(Icons.Rounded.FavoriteBorder, contentDescription = "我的收藏", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
                is CategoryUiState.Loading -> {
                    // ★ 骨架屏同步修改，保证 Loading 时和真实数据宽度表现绝对一致
                    Surface(
                        color = MaterialTheme.colorScheme.background, 
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding() 
                                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isCompactScreen) {
                                Box(modifier = Modifier.width(100.dp).height(32.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = if (isCompactScreen) Arrangement.Start else Arrangement.End,
                                modifier = Modifier.weight(1f)
                            ) {
                                // ★ 同样应用判断逻辑
                                val searchBarModifier = if (isCompactScreen) Modifier.weight(1f) else Modifier.width(280.dp)
                                
                                Box(
                                    modifier = searchBarModifier
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(100.dp))
                                        .shimmerEffect()
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                repeat(2) {
                                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).shimmerEffect())
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        },
        bottomBar = {
            when (uiState) {
                is CategoryUiState.Success -> {
                    val data = (uiState as CategoryUiState.Success).data
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant, 
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PaginationBar(
                            pageItems = data.pageItems,
                            pageTips = data.pageTips,
                            onPageClick = { url -> viewModel.fetchCategoryData(url) },
                            modifier = Modifier.navigationBarsPadding() 
                        )
                    }
                }
                is CategoryUiState.Loading -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant, 
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(150.dp)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerEffect()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                if (isCompactScreen) {
                                    val compactWidths = listOf(56.dp, 70.dp, 70.dp, 56.dp)
                                    compactWidths.forEach { w ->
                                        Box(modifier = Modifier.width(w).height(36.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
                                    }
                                } else {
                                    val normalWidths = listOf(56.dp, 70.dp, 36.dp, 36.dp, 36.dp, 36.dp, 46.dp, 70.dp, 56.dp)
                                    normalWidths.forEach { w ->
                                        Box(modifier = Modifier.width(w).height(36.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    ) { innerPadding ->
        when (uiState) {
            is CategoryUiState.Loading -> {
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    CategorySkeletonScreen()
                }
            }
            is CategoryUiState.Error -> {
                val message = (uiState as CategoryUiState.Error).message
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = message, color = MaterialTheme.colorScheme.error)
                }
            }
            is CategoryUiState.Success -> {
                val data = (uiState as CategoryUiState.Success).data
                
                BoxWithConstraints(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    val minItemWidth = 130.dp
                    val rawColumns = (maxWidth / minItemWidth).toInt()
                    val columns = maxOf(1, if (rawColumns >= 4) rawColumns - 1 else rawColumns)

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp) 
                    ) {
                        item {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                data.filters.forEach { filterGroup ->
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        item {
                                            Text(
                                                text = filterGroup.groupName,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(end = 8.dp, top = 10.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        items(filterGroup.items) { item ->
                                            val isSelected = item.isSelected
                                            Surface(
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                modifier = Modifier
                                                    .height(36.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .clickable { viewModel.fetchCategoryData(item.url) }
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                                                    Text(text = item.name, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        stickyHeader {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(data.sortItems) { item ->
                                        val isSelected = item.isSelected
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null,
                                            modifier = Modifier
                                                .height(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { viewModel.fetchCategoryData(item.url) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                                Text(text = item.name, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        items(data.movies.chunked(columns)) { rowMovies ->
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

@Composable
fun PaginationBar(
    pageItems: List<PageItem>, 
    pageTips: String, 
    onPageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isCompactScreen = configuration.screenWidthDp < 600

    val displayPageItems = if (isCompactScreen) {
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
            items(displayPageItems) { item ->
                val containerColor = if (item.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                val contentColor = if (item.isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = containerColor,
                    contentColor = contentColor,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !item.isDisabled && !item.isActive) { onPageClick(item.url) }
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(text = item.title, fontSize = 14.sp, fontWeight = if (item.isActive) FontWeight.Bold else FontWeight.Normal)
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