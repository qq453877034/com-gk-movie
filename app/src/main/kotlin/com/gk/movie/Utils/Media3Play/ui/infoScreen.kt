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
                MovieHeaderInfo(movieInfo)
                
                // 将简介移动到了操作按钮的下方
                Spacer(modifier = Modifier.height(32.dp))
                MovieDescriptionSection(movieInfo)
            }

            // 右半边：剧集列表与线路选择 (占据 55% 宽度)
            Column(
                modifier = Modifier
                    .weight(0.55f)
            ) {
                if (movieInfo.playLists.isNotEmpty()) {
                    PlayListSection(playLists = movieInfo.playLists)
                }
            }
        }
    } else {
        // ==========================================
        // 手机 / 窄屏布局：现有的上下单栏滑动显示 (整体结构不变)
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 45.dp, bottom = 20.dp, end = 20.dp)
        ) {
            MovieHeaderInfo(movieInfo)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            MovieDescriptionSection(movieInfo)

            Spacer(modifier = Modifier.height(32.dp))

            if (movieInfo.playLists.isNotEmpty()) {
                PlayListSection(playLists = movieInfo.playLists)
            }
        }
    }
}

// ==========================================
// 抽离出来的组件：1. 头部海报与核心信息区域
// ==========================================
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MovieHeaderInfo(movieInfo: MovieInfo) {
    Row(modifier = Modifier.fillMaxWidth()) {
        GlideImage(
            model = movieInfo.coverUrl,
            contentDescription = "Cover",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(150.dp) // 稍微调窄一点让右侧信息更从容
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.LightGray)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
        
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = movieInfo.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            InfoLabelRow(label = "导演", value = movieInfo.director)
            Spacer(modifier = Modifier.height(8.dp))
            
            InfoLabelRow(label = "主演", value = movieInfo.actors)
            Spacer(modifier = Modifier.height(8.dp))
            
            InfoLabelRow(label = "类型", value = movieInfo.types)

            Spacer(modifier = Modifier.height(16.dp))

            // 动态真实的评分区域
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = movieInfo.score, 
                    color = Color(0xFFFF9800),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                     // 使用解析出的真实评价次数
                     Text(
                         text = movieInfo.scoreCount, 
                         style = MaterialTheme.typography.labelSmall, 
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )
                     Row {
                         // 动态计算星星数量 (假设 10 分制，转换为 5 颗星)
                         val numScore = movieInfo.score.toFloatOrNull() ?: 0f
                         val filledStars = (numScore / 2).toInt().coerceIn(0, 5)
                         val emptyStars = 5 - filledStars
                         
                         repeat(filledStars) { 
                             Icon(Icons.Outlined.Star, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(14.dp)) 
                         }
                         repeat(emptyStars) { 
                             Icon(Icons.Outlined.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp)) 
                         }
                     }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 操作按钮区域：宽度自适应
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { /* TODO: 播放逻辑 */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "播放", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("播放", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Surface(
                    onClick = { /* TODO: 收藏逻辑 */ },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.height(40.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center, 
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                         Icon(Icons.Outlined.Star, contentDescription = "收藏", modifier = Modifier.size(16.dp))
                         Spacer(modifier = Modifier.width(4.dp))
                         Text("收藏", style = MaterialTheme.typography.bodyMedium, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// 抽离出来的组件：2. 影片简介区域
// ==========================================
@Composable
fun MovieDescriptionSection(movieInfo: MovieInfo) {
    Column {
        Box(
            modifier = Modifier
                .background(Color(0xFFE50914), shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
             Text("简介", fontSize = 16.sp, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        ExpandableDescription(text = movieInfo.description)
    }
}

// 辅助组件：红底白字小标签
@Composable
fun InfoLabelRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .background(Color(0xFFE50914), shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

// 辅助组件：带展开/收起动画的文字
@Composable
fun ExpandableDescription(text: String) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.animateContentSize()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5f
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
                     text = if (expanded) "收起 ∧" else "展开 ∨",
                     fontSize = 16.sp,
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
fun PlayListSection(playLists: List<PlayList>) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isReversed by remember { mutableStateOf(false) }

    Column {
        // --- 新增：带有红底白字的“线路选择”标签 ---
        Box(
            modifier = Modifier
                .background(Color(0xFFE50914), shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
             Text("线路选择", fontSize = 16.sp, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        
        Spacer(modifier = Modifier.height(16.dp))

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
                                .background(Color(0xFFE50914))
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
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedTabIndex == index) Color(0xFFE50914) else MaterialTheme.colorScheme.onSurface
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
                    text = if (isReversed) "倒序 " else "正序 ",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 获取真实的剧集数据并渲染
        val currentEpisodes = playLists[selectedTabIndex].episodes
        val displayEpisodes = if (isReversed) currentEpisodes.reversed() else currentEpisodes

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 80.dp),
            modifier = Modifier.heightIn(max = 800.dp), // 平板模式下如果不限制高度会撑满，有需要可以去掉 heightIn
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(displayEpisodes) { episode ->
                EpisodeItem(episode)
            }
        }
    }
}

@Composable
fun EpisodeItem(episode: Episode) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable { 
                Log.d("EpisodeItem", "Clicked: ${episode.name}, URL: ${episode.url}")
            }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        // 使用解析出的真实集数名称 (如：第1集，或者 1)
        Text(
            text = episode.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}