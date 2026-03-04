// 文件路径: com/gk/movie/Utils/Media3Play/ui/infoScreen.kt
package com.gk.movie.Utils.Media3Play.ui

// 导入 Android 日志工具
import android.util.Log
// 导入 Compose 动画修饰符
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
// 导入 Compose 基础修饰符、边框和选中特性
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
// 导入 LazyColumn 核心组件，实现吸顶的关键
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
// 导入 Material 风格的图标库
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Star
// 导入 Material 3 设计系统的核心组件 (包含最新的 TabRow)
import androidx.compose.material3.*
// 导入 Compose 状态管理相关组件
import androidx.compose.runtime.*
// 导入对齐方式枚举
import androidx.compose.ui.Alignment
// 导入修饰符核心类
import androidx.compose.ui.Modifier
// 导入裁剪和颜色相关的图形工具
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
// 导入图片缩放模式
import androidx.compose.ui.layout.ContentScale
// 导入获取屏幕配置的工具，用于替代废弃的 AdaptiveInfo
import androidx.compose.ui.platform.LocalConfiguration
// 导入字体粗细和文本溢出处理工具
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
// 导入尺寸单位
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// 导入 ViewModel 相关的 Compose 集成工具
import androidx.lifecycle.viewmodel.compose.viewModel
// 导入 Glide 图片加载库的 Compose 支持
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

// ==========================================
// 主入口页面：负责状态的监听与分发
// ==========================================
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

// ==========================================
// 电影内容主体容器：负责处理自适应布局与吸顶效果
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieContent(movieInfo: MovieInfo) {
    // 只要屏幕宽度大于等于 840dp，即认为是 Expanded (平板/宽屏) 模式
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 840

    // 状态提升 (State Hoisting)
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isReversed by remember { mutableStateOf(false) }
    var selectedEpisodeUrl by remember { mutableStateOf<String?>(null) }

    if (isExpandedScreen) {
        // ------------------------------------------
        // 平板 / 宽屏布局：左右双栏，无需吸顶
        // ------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, top = 60.dp, bottom = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 左半边独立滑动
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .verticalScroll(rememberScrollState())
            ) {
                MovieHeaderInfo(movieInfo, isExpandedScreen)
                Spacer(modifier = Modifier.height(30.dp))
                MovieDescriptionSection(movieInfo, isExpandedScreen)
            }

            // 右半边剧集区
            Column(
                modifier = Modifier.weight(0.4f)
            ) {
                if (movieInfo.playLists.isNotEmpty()) {
                    PlayListHeader(
                        playLists = movieInfo.playLists,
                        selectedTabIndex = selectedTabIndex,
                        isReversed = isReversed,
                        onTabSelected = { selectedTabIndex = it; isReversed = false },
                        onReverseToggle = { isReversed = !isReversed },
                        isExpandedScreen = isExpandedScreen,
                        isStuck = false // 平板不涉及吸顶状态变色
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    PlayListGrid(
                        playLists = movieInfo.playLists,
                        selectedTabIndex = selectedTabIndex,
                        isReversed = isReversed,
                        selectedEpisodeUrl = selectedEpisodeUrl,
                        onEpisodeSelected = { selectedEpisodeUrl = it },
                        isExpandedScreen = isExpandedScreen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f) // 撑满剩余高度
                            .verticalScroll(rememberScrollState()) // 允许独立滚动
                    )
                }
            }
        }
    } else {
        // ------------------------------------------
        // 手机 / 窄屏布局：使用 LazyColumn 实现完美吸顶
        // ------------------------------------------
        val listState = rememberLazyListState()
        
        val isStuck by remember { 
            derivedStateOf { 
                if (listState.firstVisibleItemIndex == 0) {
                    val firstItemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                    // 滑出超过 1/1.1 时开始触发透明变色，时机刚刚好！
                    listState.firstVisibleItemScrollOffset > (firstItemSize / 1.1)
                } else {
                    listState.firstVisibleItemIndex >= 1
                }
            } 
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 10.dp)
        ) {
            // 第一项：头部海报与简介 (Index = 0)
            item {
                Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 45.dp)) {
                    MovieHeaderInfo(movieInfo, isExpandedScreen)
                    Spacer(modifier = Modifier.height(10.dp))
                    MovieDescriptionSection(movieInfo, isExpandedScreen)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            if (movieInfo.playLists.isNotEmpty()) {
                // 第二项：线路和排序选项卡 (Index = 1) -> 开启吸顶魔法
                stickyHeader {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background) // 阻挡底层内容的背景
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

                // 第三项：剧集网格列表 (Index = 2)
                item {
                    PlayListGrid(
                        playLists = movieInfo.playLists,
                        selectedTabIndex = selectedTabIndex,
                        isReversed = isReversed,
                        selectedEpisodeUrl = selectedEpisodeUrl,
                        onEpisodeSelected = { selectedEpisodeUrl = it },
                        isExpandedScreen = isExpandedScreen,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 1. 头部海报与核心信息区域
// ==========================================
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MovieHeaderInfo(movieInfo: MovieInfo, isExpandedScreen: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        GlideImage(
            model = movieInfo.coverUrl,
            contentDescription = "Cover",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(if (isExpandedScreen) 155.dp else 120.dp)
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.LightGray)
        )

        Spacer(modifier = Modifier.width(if (isExpandedScreen) 15.dp else 6.dp))

        Column(modifier = Modifier.weight(1f)) {
            Spacer(modifier = Modifier.height(if (isExpandedScreen) 10.dp else 4.dp))
            
            Text(
                text = movieInfo.title,
                style = if (isExpandedScreen) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = if (isExpandedScreen) 30.sp else 20.sp,
                lineHeight = if (isExpandedScreen) 32.sp else 28.sp
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            InfoLabelRow(label = "导演", value = movieInfo.director, isExpandedScreen = isExpandedScreen)
            Spacer(modifier = Modifier.height(4.dp))
            InfoLabelRow(label = "主演", value = movieInfo.actors, isExpandedScreen = isExpandedScreen)
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoLabelRow(
                    label = "类型", 
                    value = movieInfo.types, 
                    isExpandedScreen = isExpandedScreen,
                    modifier = Modifier.weight(1f) 
                )
                RatingArea(movieInfo, isExpandedScreen)
            }

            Spacer(modifier = Modifier.height(18.dp))
            
            ActionArea(isExpandedScreen)
        }
    }
}

// ------------------------------------------
// 辅助组件：评分和星级展示区域
// ------------------------------------------
@Composable
fun RatingArea(movieInfo: MovieInfo, isExpandedScreen: Boolean) {
    val scoreStyle = if (isExpandedScreen) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
    val starSize = if (isExpandedScreen) 14.dp else 10.dp

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = movieInfo.score, 
            color = MaterialTheme.colorScheme.primary,
            style = scoreStyle,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(if (isExpandedScreen) 8.dp else 2.dp))
        
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
                     Icon(Icons.Outlined.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(starSize)) 
                 }
                 repeat(emptyStars) { 
                     Icon(Icons.Outlined.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(starSize)) 
                 }
             }
        }
    }
}

// ------------------------------------------
// 辅助组件：操作按钮区域（播放、收藏）
// ------------------------------------------
@Composable
fun ActionArea(isExpandedScreen: Boolean) {
    val btnHeight = if (isExpandedScreen) 40.dp else 35.dp
    val fontSize = if (isExpandedScreen) 14.sp else 12.sp
    val iconSize = if (isExpandedScreen) 18.dp else 14.dp
    val paddingH = if (isExpandedScreen) 14.dp else 10.dp

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
                 Text("收藏", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, fontSize = fontSize, maxLines = 1)
            }
        }
    }
}

// ==========================================
// 2. 影片简介区域
// ==========================================
@Composable
fun MovieDescriptionSection(movieInfo: MovieInfo, isExpandedScreen: Boolean) {
    // 保持与“线路”标签一致的大小
    val titleSize = if (isExpandedScreen) 16.sp else 13.sp

    Column {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
             Text("简介", fontSize = titleSize, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
        }
        
        Spacer(modifier = Modifier.height(5.dp))
        ExpandableDescription(text = movieInfo.description, isExpandedScreen)
    }
}

// ------------------------------------------
// 辅助组件：带有主题底色的小标签行
// ------------------------------------------
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
                .weight(1f) 
                .padding(top = 1.dp)
        )
    }
}

// ------------------------------------------
// 辅助组件：支持动态展开/收起的多行文本
// ------------------------------------------
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
                .padding(top = 0.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                 Text(
                     text = if (expanded) "收起 Ⅹ" else "展开 Ⅴ",
                     fontSize = btnTextSize,
                     fontWeight = FontWeight.Bold,
                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                     style = MaterialTheme.typography.labelMedium
                 )
            }
        }
    }
}

// ==========================================
// 3. 将播放列表拆分为：吸顶头部 与 剧集网格
// ==========================================

// ------------------------------------------
// 3.1 头部：线路标签、选项卡、排序按钮
// ------------------------------------------
@Composable
fun PlayListHeader(
    playLists: List<PlayList>,
    selectedTabIndex: Int,
    isReversed: Boolean,
    onTabSelected: (Int) -> Unit,
    onReverseToggle: () -> Unit,
    isExpandedScreen: Boolean,
    isStuck: Boolean // 接收吸顶状态
) {
    // ★ 关键修复：分离标签文字大小和操作项文字大小 ★
    // 标签文字大小，与“简介”的尺寸绝对对齐 (16.sp / 13.sp)
    val labelSize = if (isExpandedScreen) 16.sp else 13.sp
    // 选项卡和排序按钮的文字大小，维持小一号防止拥挤 (14.sp / 12.sp)
    val tabTextSize = if (isExpandedScreen) 14.sp else 12.sp

    Column {
        // 吸顶时透明化过渡动画
        val targetBgColor = if (isStuck) Color.Transparent else MaterialTheme.colorScheme.primary
        val targetTextColor = if (isStuck) Color.Transparent else MaterialTheme.colorScheme.onPrimary
        
        val animatedBgColor by animateColorAsState(targetValue = targetBgColor, label = "bgColorAnimation")
        val animatedTextColor by animateColorAsState(targetValue = targetTextColor, label = "textColorAnimation")

        Box(
            modifier = Modifier
                .background(animatedBgColor, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
             Text(
                 text = "线路", 
                 fontSize = labelSize, // 使用对齐的 labelSize
                 fontWeight = FontWeight.Bold, 
                 color = animatedTextColor,
                 style = MaterialTheme.typography.labelMedium
             )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SecondaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.weight(1f),
                edgePadding = 0.dp,
                indicator = {
                    Box(
                        modifier = Modifier
                            .tabIndicatorOffset(selectedTabIndex)
                            .padding(horizontal = 30.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                },
                divider = { }
            ) {
                playLists.forEachIndexed { index, playList ->
                    // 彻底消除点击涟漪
                    Box(
                        modifier = Modifier
                            .height(48.dp) 
                            .selectable(
                                selected = selectedTabIndex == index,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null, 
                                onClick = { onTabSelected(index) }
                            )
                            .padding(horizontal = 16.dp), 
                        contentAlignment = Alignment.Center 
                    ) {
                        Text(
                            text = "${playList.sourceName} [${playList.episodes.size}]", 
                            fontSize = tabTextSize, // 使用稍小的 tabTextSize
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        ) 
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Surface(
                onClick = { onReverseToggle() },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = if (isReversed) "倒序 ▼" else "正序 ▲",
                    fontSize = tabTextSize, // 使用稍小的 tabTextSize
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ------------------------------------------
// 3.2 剧集网格：原生的自适应块渲染，告别嵌套滚动崩溃
// ------------------------------------------
@Composable
fun PlayListGrid(
    playLists: List<PlayList>,
    selectedTabIndex: Int,
    isReversed: Boolean,
    selectedEpisodeUrl: String?,
    onEpisodeSelected: (String) -> Unit,
    isExpandedScreen: Boolean,
    modifier: Modifier = Modifier
) {
    val currentEpisodes = playLists[selectedTabIndex].episodes
    val displayEpisodes = if (isReversed) currentEpisodes.reversed() else currentEpisodes

    BoxWithConstraints(modifier = modifier) {
        val minColumnWidth = 80f 
        val spacing = 6f         
        val columns = maxOf(1, ((maxWidth.value + spacing) / (minColumnWidth + spacing)).toInt())
        
        val chunkedEpisodes = displayEpisodes.chunked(columns)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            chunkedEpisodes.forEach { rowEpisodes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowEpisodes.forEach { episode ->
                        EpisodeItem(
                            episode = episode, 
                            isExpandedScreen = isExpandedScreen,
                            isSelected = selectedEpisodeUrl == episode.url,
                            onClick = { onEpisodeSelected(episode.url) },
                            modifier = Modifier.weight(1f) 
                        )
                    }
                    repeat(columns - rowEpisodes.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ------------------------------------------
// 辅助组件：带有选中状态与主题色感应的剧集块
// ------------------------------------------
@Composable
fun EpisodeItem(
    episode: Episode, 
    isExpandedScreen: Boolean,
    isSelected: Boolean, 
    onClick: () -> Unit, 
    modifier: Modifier = Modifier 
) {
    val paddingV = if (isExpandedScreen) 12.dp else 10.dp
    val textStyle = if (isExpandedScreen) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall

    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp, 
                color = borderColor, 
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { 
                onClick()
                Log.d("EpisodeItem", "Clicked: ${episode.name}, URL: ${episode.url}")
            }
            .padding(vertical = paddingV, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = episode.name,
            style = textStyle,
            color = contentColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, 
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}