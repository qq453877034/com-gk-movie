// 文件路径: app/src/main/kotlin/com/gk/movie/TabItemsPages.kt
package com.gk.movie

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.gk.movie.Utils.Media3Play.ui.infoActivity
import kotlinx.coroutines.delay

@Composable
fun TabItemsPages(
    url: String,
    viewModel: MainViewModel,
    isPortrait: Boolean
) {
    LaunchedEffect(url) {
        viewModel.fetchTabData(url)
    }

    val tabStates by viewModel.tabStates.collectAsState()
    val uiState = tabStates[url] ?: HomeUiState.Loading

    CategoryFeedLayout(uiState = uiState, isPortrait = isPortrait, onRetry = { viewModel.fetchTabData(url) })
}

@Composable
fun CategoryFeedLayout(uiState: HomeUiState, isPortrait: Boolean, onRetry: () -> Unit) {
    val isLoading = uiState is HomeUiState.Loading

    val banners = if (isLoading) List(3) { CmsVod(vod_name = "加载中...") }
    else (uiState as? HomeUiState.Success)?.banners ?: emptyList()

    val sections = if (isLoading) {
        listOf(
            HomeSection("动作片", "", List(6) { CmsVod(vod_name = "加载中...") }),
            HomeSection("喜剧片", "", List(6) { CmsVod(vod_name = "加载中...") })
        )
    } else {
        (uiState as? HomeUiState.Success)?.sections ?: emptyList()
    }

    if (uiState is HomeUiState.Error) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = uiState.message, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("重新加载") }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        if (banners.isNotEmpty()) {
            item { FullWidthCarousel(carouselList = banners, isLoading = isLoading) }
        }

        items(sections) { section ->
            CategoryGridSection(section = section, isLoading = isLoading, isPortrait = isPortrait)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun FullWidthCarousel(carouselList: List<CmsVod>, isLoading: Boolean) {
    val context = LocalContext.current
    val pageCount = carouselList.size
    val virtualCount = if (isLoading) pageCount else Int.MAX_VALUE
    val initialPage = if (isLoading) 0 else (virtualCount / 2) - ((virtualCount / 2) % pageCount)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { virtualCount })

    if (!isLoading) {
        LaunchedEffect(pagerState) {
            while (true) {
                delay(4500)
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    // 无左右边距，高度固定为宽度/1.6，打造极致巨幕效果！
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(21f/9f).padding(bottom = 16.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val realIndex = page % pageCount
            val movie = carouselList[realIndex]

            Box(
                modifier = Modifier.fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (!isLoading) {
                            val intent = Intent(context, infoActivity::class.java).apply {
                                putExtra("vodId", movie.vod_id.toString())
                            }
                            startActivityWithFade(context, intent)
                        }
                    }
            ) {
                if (!isLoading) {
                    val targetCoverUrl = movie.vod_pic_slide.takeIf { it.isNotEmpty() } ?: movie.vod_pic
                    GlideImage(
                        model = targetCoverUrl,
                        contentDescription = movie.vod_name,
                        contentScale = ContentScale.Crop, 
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.65f)
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))))
                    )

                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 24.dp)
                    ) {
                        Text(
                            text = movie.vod_name,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        val subText = listOf(movie.type_name, movie.vod_remarks, movie.vod_version).filter { it.isNotEmpty() }.joinToString(" • ")
                        if (subText.isNotEmpty()) {
                            Text(
                                text = subText,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().shimmerPlaceholder(true))
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(pageCount) { iteration ->
                val isActive = (pagerState.currentPage % pageCount) == iteration
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(if (isActive) 18.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Composable
fun CategoryGridSection(section: HomeSection, isLoading: Boolean, isPortrait: Boolean) {
    val columns = if (isPortrait) 3 else 6 
    val chunkedMovies = section.movies.chunked(columns)

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.title,
                color = if (isLoading) Color.Transparent else MaterialTheme.colorScheme.onBackground,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.shimmerPlaceholder(isLoading)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (rowMovies in chunkedMovies) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (movie in rowMovies) {
                        Box(modifier = Modifier.weight(1f)) {
                            HomeMovieItem(movie = movie, isLoading = isLoading, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    repeat(columns - rowMovies.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}