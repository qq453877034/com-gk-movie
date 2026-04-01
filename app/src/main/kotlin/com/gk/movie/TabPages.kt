// 文件路径: app/src/main/kotlin/com/gk/movie/TabPages.kt
package com.gk.movie

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.gk.movie.Utils.Category.ui.InfoCategoryActivity
import com.gk.movie.Utils.Media3Play.ui.infoActivity
import com.gk.movie.Utils.Media3Play.ui.OfflineVideoScreen
import kotlinx.coroutines.delay

fun startActivityWithFade(context: Context, intent: Intent) {
    val options = ActivityOptions.makeCustomAnimation(
        context,
        android.R.anim.fade_in,
        android.R.anim.fade_out
    )
    context.startActivity(intent, options.toBundle())
}

@Composable
fun Modifier.shimmerPlaceholder(
    isLoading: Boolean,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(4.dp)
): Modifier {
    if (!isLoading) return this
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    return this.clip(shape).background(Color.Gray.copy(alpha = alpha))
}

@Composable
fun TabContent(
    selectedTabIndex: Int,
    displayTabs: List<HomeTab>,
    viewModel: MainViewModel,
    isPortrait: Boolean
) {
    val currentTab = displayTabs.getOrNull(selectedTabIndex) ?: return

    when (currentTab.title) {
        "离线视频" -> OfflineVideoPage()
        "收藏" -> FavoritesPage()
        "推荐", "首页" -> HomeFeed(
            uiState = viewModel.tabStates.collectAsState().value[currentTab.url] ?: HomeUiState.Loading,
            isPortrait = isPortrait,
            onRetry = { viewModel.fetchTabData(currentTab.url) }
        )
        // 电影、电视剧、动漫等走独立沉浸式分类页中转
        else -> TabItemsPages(
            url = currentTab.url,
            viewModel = viewModel,
            isPortrait = isPortrait
        )
    }
}

@Composable
fun HomeFeed(
    uiState: HomeUiState,
    isPortrait: Boolean,
    onRetry: () -> Unit
) {
    val isLoading = uiState is HomeUiState.Loading

    val banners = if (isLoading) List(3) { CmsVod(vod_name = "加载中...") }
    else (uiState as? HomeUiState.Success)?.banners ?: emptyList()

    val sections = if (isLoading) {
        listOf(
            HomeSection("正在热播", "", List(4) { CmsVod(vod_name = "加载中...") }),
            HomeSection("站长推荐", "", List(4) { CmsVod(vod_name = "加载中...") }),
            HomeSection("最新上线", "", List(4) { CmsVod(vod_name = "加载中...") })
        )
    } else {
        (uiState as? HomeUiState.Success)?.sections ?: emptyList()
    }

    if (uiState is HomeUiState.Error) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = uiState.message, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Text("重新加载")
                }
            }
        }
        return
    }

    val displayItems = remember(sections, isPortrait) {
        if (isPortrait) {
            sections
        } else {
            val result = mutableListOf<Any>()
            var hotSec: HomeSection? = null
            var recSec: HomeSection? = null
            var insertIndex = -1

            for (sec in sections) {
                if (sec.title.contains("热门推荐") || sec.title.contains("正在热播")) {
                    hotSec = sec
                    if (insertIndex == -1) insertIndex = result.size
                } else if (sec.title.contains("最近添加") || sec.title.contains("站长推荐")) {
                    recSec = sec
                    if (insertIndex == -1) insertIndex = result.size
                } else {
                    result.add(sec)
                }
            }

            if (hotSec != null || recSec != null) {
                val safeIndex = if (insertIndex != -1 && insertIndex <= result.size) insertIndex else result.size
                result.add(safeIndex, Pair(hotSec, recSec))
            }
            result
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            if (banners.isNotEmpty()) {
                item { HomeCarousel(carouselList = banners, isLoading = isLoading) }
            }

            items(displayItems) { item ->
                when (item) {
                    is HomeSection -> {
                        CategorySection(section = item, isLoading = isLoading, useGrid = false)
                    }
                    is Pair<*, *> -> {
                        val left = item.first as? HomeSection
                        val right = item.second as? HomeSection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            if (left != null) {
                                Box(modifier = Modifier.weight(1f)) {
                                    CategorySection(section = left, isLoading = isLoading, useGrid = true)
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            if (right != null) {
                                Box(modifier = Modifier.weight(1f)) {
                                    CategorySection(section = right, isLoading = isLoading, useGrid = true)
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun HomeMovieItem(movie: CmsVod, isLoading: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            if (!isLoading) {
                val intent = Intent(context, infoActivity::class.java).apply {
                    putExtra("vodId", movie.vod_id.toString())
                }
                startActivityWithFade(context, intent)
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .shimmerPlaceholder(isLoading)
        ) {
            if (!isLoading) {
                GlideImage(
                    model = movie.vod_pic,
                    contentDescription = movie.vod_name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                )

                val topLeftTag = movie.vod_version.takeIf { it.isNotEmpty() } ?: movie.type_name
                if (topLeftTag.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(bottomStart = 8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = topLeftTag, color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (movie.vod_remarks.isNotEmpty()) {
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                        Text(text = movie.vod_remarks, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = movie.vod_name,
            color = if (isLoading) Color.Transparent else MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp).fillMaxWidth().shimmerPlaceholder(isLoading)
        )

        if (movie.vod_class.isNotEmpty() || isLoading) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = movie.vod_class,
                color = if (isLoading) Color.Transparent else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp).fillMaxWidth(0.6f).shimmerPlaceholder(isLoading)
            )
        }
    }
}

@Composable
fun CategorySection(section: HomeSection, isLoading: Boolean, useGrid: Boolean) {
    val context = LocalContext.current

    val hardcodedUrl = when {
        section.title.contains("电影") -> "https://hellociqryx6e.com/vod/show/id/1"
        section.title.contains("电视剧") -> "https://hellociqryx6e.com/vod/show/id/2"
        section.title.contains("综艺") -> "https://hellociqryx6e.com/vod/show/id/3"
        section.title.contains("动漫") -> "https://hellociqryx6e.com/vod/show/id/4"
        else -> ""
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.title,
                color = if (isLoading) Color.Transparent else MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.shimmerPlaceholder(isLoading)
            )
            Spacer(modifier = Modifier.weight(1f))

            if (hardcodedUrl.isNotEmpty() && !isLoading) {
                Text(
                    text = "更多 >",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        val intent = Intent(context, InfoCategoryActivity::class.java).apply {
                            putExtra("title", section.title)
                            putExtra("url", hardcodedUrl)
                        }
                        startActivityWithFade(context, intent)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (!useGrid) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                userScrollEnabled = !isLoading
            ) {
                items(section.movies) { movie ->
                    HomeMovieItem(movie = movie, isLoading = isLoading, modifier = Modifier.width(180.dp))
                }
            }
        } else {
            val targetColumns = 3
            val chunkedMovies = section.movies.chunked(targetColumns)

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                for (rowMovies in chunkedMovies) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        for (movie in rowMovies) {
                            Box(modifier = Modifier.weight(1f)) {
                                HomeMovieItem(movie = movie, isLoading = isLoading, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        repeat(targetColumns - rowMovies.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineVideoPage() {
    OfflineVideoScreen()
}

@Composable
fun FavoritesPage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(16.dp))
            Text("暂无收藏内容", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("点击 ♡ 可收藏喜欢的影片", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
fun HomeCarousel(carouselList: List<CmsVod>, isLoading: Boolean) {
    if (carouselList.isEmpty()) return

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val context = LocalContext.current

    val pageCount = carouselList.size
    val virtualCount = if (isLoading) pageCount else Int.MAX_VALUE
    val initialPage = if (isLoading) 0 else (virtualCount / 2) - ((virtualCount / 2) % pageCount)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { virtualCount })

    if (!isLoading) {
        LaunchedEffect(pagerState) {
            while (true) {
                delay(5000)
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth().height(360.dp).padding(top = 12.dp)) {
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(screenWidth * 0.46f),
            contentPadding = PaddingValues(horizontal = 12.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val realIndex = page % pageCount
            val movie = carouselList[realIndex]

            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isLoading) 0.dp else 1.dp),
                modifier = Modifier.fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (!isLoading) {
                            val intent = Intent(context, infoActivity::class.java).apply {
                                putExtra("vodId", movie.vod_id.toString())
                            }
                            context.startActivity(intent)
                        }
                    }
            ) {
                if (!isLoading) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val targetCoverUrl = movie.vod_pic_slide.takeIf { it.isNotEmpty() } ?: movie.vod_pic
                        GlideImage(model = targetCoverUrl, contentDescription = movie.vod_name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())

                        Box(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).align(Alignment.BottomCenter)
                                .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.95f))))
                        )

                        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                            Text(movie.vod_name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val displayType = movie.vod_class.takeIf { it.isNotEmpty() } ?: movie.type_name
                                if (displayType.isNotEmpty()) {
                                    Text(displayType, color = Color(0xFFFF980), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("•", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                }
                                val tags = listOf(movie.vod_version, movie.vod_remarks).filter { it.isNotEmpty() }
                                if (tags.isNotEmpty()) {
                                    Text(tags.joinToString(" | "), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            if (movie.vod_director.isNotEmpty() || movie.vod_actor.isNotEmpty()) {
                                val castStr = listOf(movie.vod_director, movie.vod_actor).filter { it.isNotEmpty() }.joinToString(" | ")
                                Text(castStr, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            if (movie.vod_blurb.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(movie.vod_blurb, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        if (!isLoading) {
                                            val intent = Intent(context, infoActivity::class.java).apply { putExtra("vodId", movie.vod_id.toString()) }
                                            context.startActivity(intent)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, "Play", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("播放", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                Surface(
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(32.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.FavoriteBorder, "Favorite", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 76.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(pageCount) { iteration ->
                val isActive = (pagerState.currentPage % pageCount) == iteration
                Box(
                    modifier = Modifier.height(6.dp).width(if (isActive) 14.dp else 6.dp).clip(CircleShape)
                        .background(if (isActive) Color.White else Color.White.copy(alpha = 0.4f))
                )
            }
        }
    }
}