// 文件路径: com/gk/movie/Utils/Media3Play/ui/infoScreen.kt
package com.gk.movie.Utils.Media3Play.ui

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowWidthSizeClass
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

@Composable
fun InfoScreen(viewModel: InfoViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (val state = uiState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
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

@Composable
fun MovieContent(movieInfo: MovieInfo) {
    // 获取当前窗口的自适应信息（判断是手机、折叠屏还是平板）
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isExpandedScreen = windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

    if (isExpandedScreen) {
        // ==========================================
        // 平板 / 宽屏布局：左右双栏显示
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // 左半边：海报、基础信息、简介 (占据 45% 宽度)
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .verticalScroll(rememberScrollState())
            ) {
                MovieHeaderInfo(movieInfo, isExpandedScreen)
                
                Spacer(modifier = Modifier.height(32.dp))
                MovieDescriptionSection(movieInfo, isExpandedScreen)
            }

            // 右半边：剧集列表与线路选择 (占据 55% 宽度)
            Column(
                modifier = Modifier
                    .weight(0.55f)
            ) {
                if (movieInfo.playLists.isNotEmpty()) {
                    PlayListSection(playLists = movieInfo.playLists, isExpandedScreen)
                }
            }
        }
    } else {
        // ==========================================
        // 手机 / 窄屏布局
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 45.dp, bottom = 20.dp, end = 20.dp)
        ) {
            MovieHeaderInfo(movieInfo, isExpandedScreen)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            MovieDescriptionSection(movieInfo, isExpandedScreen)

            Spacer(modifier = Modifier.height(24.dp))

            if (movieInfo.playLists.isNotEmpty()) {
                PlayListSection(playLists = movieInfo.playLists, isExpandedScreen)
            }
        }
    }
}

// ==========================================
// 抽离出来的组件：1. 头部海报与核心信息区域
// ==========================================
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MovieHeaderInfo(movieInfo: MovieInfo, isExpandedScreen: Boolean) {
    val coverWidth = if (isExpandedScreen) 150.dp else 115.dp

    Row(modifier = Modifier.fillMaxWidth()) {
        GlideImage(
            model = movieInfo.coverUrl,
            contentDescription = "Cover",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(coverWidth)
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.LightGray)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 右侧信息栏
        Column(modifier = Modifier.weight(1f)) {
            Spacer(modifier = Modifier.height(if (isExpandedScreen) 10.dp else 4.dp))
            
            Text(
                text = movieInfo.title,
                style = if (isExpandedScreen) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                lineHeight = if (isExpandedScreen) 32.sp else 28.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            InfoLabelRow(label = "导演", value = movieInfo.director, isExpandedScreen = isExpandedScreen)
            Spacer(modifier = Modifier.height(6.dp))
            InfoLabelRow(label = "主演", value = movieInfo.actors, isExpandedScreen = isExpandedScreen)
            Spacer(modifier = Modifier.height(6.dp))
            
            // 完美组合：类型和评分合并到同一行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoLabelRow(
                    label = "类型", 
                    value = movieInfo.types, 
                    isExpandedScreen = isExpandedScreen,
                    modifier = Modifier.weight(1f) // 给类型分配剩余空间，防止挤压右边的评分
                )
               // Spacer(modifier = Modifier.width(0.dp))
                RatingArea(movieInfo, isExpandedScreen)
            }

            Spacer(modifier = Modifier.height(18.dp))
            
            // 操作按钮紧跟在类型评分的下方（即封面右侧的中下部）
            ActionArea(isExpandedScreen)
        }
    }
}

// 辅助组件：评分区域
@Composable
fun RatingArea(movieInfo: MovieInfo, isExpandedScreen: Boolean) {
    val scoreStyle = if (isExpandedScreen) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
    val starSize = if (isExpandedScreen) 14.dp else 10.dp

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = movieInfo.score, 
            color = MaterialTheme.colorScheme.primary, //Color(0xFFFF0000),
            style = scoreStyle,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(if (isExpandedScreen) 8.dp else 4.dp))
        Column {
             Text(
                 text = movieInfo.scoreCount, 
                 style = MaterialTheme.typography.labelSmall, 
                 color = MaterialTheme.colorScheme.onSurfaceVariant,
                 fontSize = if (isExpandedScreen) 11.sp else 9.sp
             )
             Row {
                 val numScore = movieInfo.score.toFloatOrNull() ?: 0f
                 val filledStars = (numScore / 2).toInt().coerceIn(0, 5)
                 val emptyStars = 5 - filledStars
                 
                 repeat(filledStars) { 
                     Icon(Icons.Outlined.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary /*Color(0xFFFF0000)*/, modifier = Modifier.size(starSize)) 
                 }
                 repeat(emptyStars) { 
                     Icon(Icons.Outlined.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(starSize)) 
                 }
             }
        }
    }
}

// 辅助组件：操作按钮区域
@Composable
fun ActionArea(isExpandedScreen: Boolean) {
    val btnHeight = if (isExpandedScreen) 35.dp else 30.dp
    val fontSize = if (isExpandedScreen) 14.sp else 12.sp
    val iconSize = if (isExpandedScreen) 18.dp else 14.dp
    val paddingH = if (isExpandedScreen) 14.dp else 12.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { /* TODO: 播放逻辑 */ },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .height(btnHeight)
                .weight(1f),
            contentPadding = PaddingValues(horizontal = paddingH),
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "播放", modifier = Modifier.size(iconSize))
            Spacer(modifier = Modifier.width(4.dp))
            Text("播放", fontWeight = FontWeight.Bold, fontSize = fontSize, maxLines = 1)
        }

        Surface(
            onClick = { /* TODO: 收藏逻辑 */ },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .height(btnHeight)
                .weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center, 
                modifier = Modifier.padding(horizontal = paddingH)
            ) {
                 Icon(Icons.Outlined.Star, contentDescription = "收藏", modifier = Modifier.size(iconSize))
                 Spacer(modifier = Modifier.width(4.dp))
                 Text("收藏", style = MaterialTheme.typography.bodyMedium, fontSize = fontSize, maxLines = 1)
            }
        }
    }
}

// ==========================================
// 抽离出来的组件：2. 影片简介区域
// ==========================================
@Composable
fun MovieDescriptionSection(movieInfo: MovieInfo, isExpandedScreen: Boolean) {
    val titleSize = if (isExpandedScreen) 16.sp else 13.sp

    Column {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
             Text("简介", fontSize = titleSize, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        ExpandableDescription(text = movieInfo.description, isExpandedScreen)
    }
}

// 辅助组件：红底白字小标签 (带有 modifier 参数以支持自适应排版)
@Composable
fun InfoLabelRow(label: String, value: String, isExpandedScreen: Boolean, modifier: Modifier = Modifier.fillMaxWidth()) {
    val labelSize = if (isExpandedScreen) 16.sp else 12.sp
    val valueSize = if (isExpandedScreen) 16.sp else 12.sp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = labelSize,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = valueSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f) // 给文字设置 weight，使其在有限空间内变成省略号而不是撑破布局
                .padding(top = 1.dp)
        )
    }
}

// 辅助组件：带展开/收起动画的文字
@Composable
fun ExpandableDescription(text: String, isExpandedScreen: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val bodyStyle = if (isExpandedScreen) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall
    val btnTextSize = if (isExpandedScreen) 16.sp else 12.sp

    Column(modifier = Modifier.animateContentSize()) {
        Text(
            text = text,
            style = bodyStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = bodyStyle.lineHeight * 1.5f
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                 Text(
                     text = if (expanded) "收起 Ⅹ" else "展开 Ⅴ",
                     fontSize = btnTextSize,
                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                     style = MaterialTheme.typography.labelMedium
                 )
            }
        }
    }
}

// ==========================================
// 抽离出来的组件：3. 剧集列表与选集区域
// ==========================================
@Composable
fun PlayListSection(playLists: List<PlayList>, isExpandedScreen: Boolean) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isReversed by remember { mutableStateOf(false) }

    val titleSize = if (isExpandedScreen) 16.sp else 13.sp

    Column {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
             Text("线路选择", fontSize = titleSize, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
        }
        
       // Spacer(modifier = Modifier.height(5.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.weight(1f),
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        Box(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[selectedTabIndex])
                                .padding(horizontal = 16.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                },
                divider = { }
            ) {
                playLists.forEachIndexed { index, playList ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { 
                            selectedTabIndex = index
                            isReversed = false 
                        },
                        text = { 
                            Text(
                                text = playList.sourceName, 
                                fontSize = titleSize,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ) 
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Surface(
                onClick = { isReversed = !isReversed },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = if (isReversed) "倒序 ▼" else "正序 ▲",
                    fontSize = titleSize,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val currentEpisodes = playLists[selectedTabIndex].episodes
        val displayEpisodes = if (isReversed) currentEpisodes.reversed() else currentEpisodes

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 80.dp),
            modifier = Modifier.heightIn(max = 600.dp), 
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(displayEpisodes) { episode ->
                EpisodeItem(episode, isExpandedScreen)
            }
        }
    }
}

@Composable
fun EpisodeItem(episode: Episode, isExpandedScreen: Boolean) {
    val paddingV = if (isExpandedScreen) 12.dp else 10.dp
    val textStyle = if (isExpandedScreen) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable { 
                Log.d("EpisodeItem", "Clicked: ${episode.name}, URL: ${episode.url}")
            }
            .padding(vertical = paddingV, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = episode.name,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}