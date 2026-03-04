// 文件路径: com/gk/movie/Utils/Media3Play/ui/infoScreen.kt
package com.gk.movie.Utils.Media3Play.ui

// 导入 Android 日志工具
import android.util.Log
// 导入 Compose 动画修饰符，用于内容大小改变时提供平滑过渡（例如展开/收起简介）
import androidx.compose.animation.animateContentSize
// 导入 Compose 基础修饰符和布局组件
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
// 导入 Compose 懒加载网格布局相关组件，用于渲染集数列表
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
// 导入滚动状态记忆函数
import androidx.compose.foundation.rememberScrollState
// 导入圆角形状修饰
import androidx.compose.foundation.shape.RoundedCornerShape
// 导入垂直滚动修饰符
import androidx.compose.foundation.verticalScroll
// 导入 Material 风格的图标库
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Star
// 导入 Material 3 设计系统的核心组件（Text, Button, Surface 等）
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
// 导入 Material 3 自适应布局相关信息，用于判断窗口大小
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
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
// 导入字体粗细和文本溢出处理工具
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
// 导入尺寸单位（dp：设备独立像素，sp：缩放独立像素）
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// 导入 ViewModel 相关的 Compose 集成工具
import androidx.lifecycle.viewmodel.compose.viewModel
// 导入 Window Core 的窗口宽度枚举
import androidx.window.core.layout.WindowWidthSizeClass
// 导入 Glide 图片加载库的 Compose 支持（带有实验性注解）
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

// ==========================================
// 主入口页面：负责状态的监听与分发
// ==========================================
@Composable
fun InfoScreen(viewModel: InfoViewModel = viewModel()) {
    // 收集 ViewModel 中的 UI 状态流，并将其转化为 Compose 状态
    val uiState by viewModel.uiState.collectAsState()

    // 最外层容器，使用系统背景色填充整个屏幕
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // 根据不同的 UI 状态展示不同的界面
        when (val state = uiState) {
            is UiState.Loading -> {
                // 加载中状态：屏幕居中显示圆形进度条
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                // 错误状态：屏幕居中显示红色的错误提示信息
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                // 成功状态：解析到数据后，渲染具体的电影内容布局
                MovieContent(movieInfo = state.data)
            }
        }
    }
}

// ==========================================
// 电影内容主体容器：负责处理自适应布局（平板横屏 vs 手机/小窗）
// ==========================================
@Composable
fun MovieContent(movieInfo: MovieInfo) {
    // 获取当前窗口的自适应信息
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    // 判断当前屏幕宽度是否属于 EXPANDED（大屏/平板横屏）级别
    val isExpandedScreen = windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

    if (isExpandedScreen) {
        // ------------------------------------------
        // 平板 / 宽屏布局：采用左右双栏并排显示
        // ------------------------------------------
        Row(
            // 填满屏幕并设置四周外边距
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, top = 60.dp, bottom = 25.dp, end = 20.dp),
            // 左右两栏之间的间距
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 左侧栏：占据 60% 宽度，支持独立上下滑动
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .verticalScroll(rememberScrollState())
            ) {
                // 渲染头部信息（海报、标题、评分、操作按钮）
                MovieHeaderInfo(movieInfo, isExpandedScreen)
                // 留白间距
                Spacer(modifier = Modifier.height(30.dp))
                // 渲染影片简介区域
                MovieDescriptionSection(movieInfo, isExpandedScreen)
            }

            // 右侧栏：占据剩余 40% 宽度，主要显示剧集
            Column(
                modifier = Modifier.weight(0.4f)
            ) {
                // 如果播放列表不为空，则渲染线路和剧集区域
                if (movieInfo.playLists.isNotEmpty()) {
                    PlayListSection(playLists = movieInfo.playLists, isExpandedScreen)
                }
            }
        }
    } else {
        // ------------------------------------------
        // 手机 / 窄屏 / 平板小窗 布局：采用上下单栏流式布局
        // ------------------------------------------
        Column(
            // 填满屏幕，支持整体上下滑动，并设置边距
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, top = 50.dp, bottom = 15.dp, end = 12.dp)
        ) {
            // 从上到下依次渲染：头部信息
            MovieHeaderInfo(movieInfo, isExpandedScreen)
            Spacer(modifier = Modifier.height(10.dp))
            // 影片简介
            MovieDescriptionSection(movieInfo, isExpandedScreen)
            Spacer(modifier = Modifier.height(10.dp))
            
            // 线路和剧集列表
            if (movieInfo.playLists.isNotEmpty()) {
                PlayListSection(playLists = movieInfo.playLists, isExpandedScreen)
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
    // 整体使用水平行排列，横向铺满
    Row(modifier = Modifier.fillMaxWidth()) {
        
        // 渲染影片海报
        GlideImage(
            model = movieInfo.coverUrl, // 图片地址
            contentDescription = "Cover", // 无障碍描述
            contentScale = ContentScale.Crop, // 居中裁剪填满边界
            modifier = Modifier
                // 核心逻辑：大屏固定 155dp，小屏固定 120dp，防止拉伸变形
                .width(if (isExpandedScreen) 155.dp else 120.dp)
                // 锁定宽高比为 0.7 (常见的电影海报比例)
                .aspectRatio(0.7f)
                // 设置圆角
                .clip(RoundedCornerShape(8.dp))
                // 在图片加载出来前显示的占位背景色
                .background(Color.LightGray)
        )

        // 海报与右侧文字信息之间的间距（大屏大间距，小屏小间距）
        Spacer(modifier = Modifier.width(if (isExpandedScreen) 15.dp else 6.dp))

        // 右侧信息容器，利用 weight(1f) 占据海报右侧的全部剩余空间
        Column(modifier = Modifier.weight(1f)) {
            // 顶部间距微调
            Spacer(modifier = Modifier.height(if (isExpandedScreen) 10.dp else 4.dp))
            
            // 影片标题文字
            Text(
                text = movieInfo.title,
                // 根据屏幕大小使用不同的排版样式和字体大小
                style = if (isExpandedScreen) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = if (isExpandedScreen) 30.sp else 20.sp,
                // 行高设置，防止多行标题挤在一起
                lineHeight = if (isExpandedScreen) 32.sp else 28.sp
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            // 渲染导演标签行
            InfoLabelRow(label = "导演", value = movieInfo.director, isExpandedScreen = isExpandedScreen)
            Spacer(modifier = Modifier.height(4.dp))
            // 渲染主演标签行
            InfoLabelRow(label = "主演", value = movieInfo.actors, isExpandedScreen = isExpandedScreen)
            Spacer(modifier = Modifier.height(4.dp))
            
            // 类型标签与评分合并在同一行显示，节省垂直空间
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically // 垂直居中对齐
            ) {
                // 渲染类型标签
                InfoLabelRow(
                    label = "类型", 
                    value = movieInfo.types, 
                    isExpandedScreen = isExpandedScreen,
                    // 使用 weight(1f) 让类型文字挤占剩余空间，并在空间不足时变为省略号
                    modifier = Modifier.weight(1f) 
                )
                // 渲染右侧的评分星级区域
                RatingArea(movieInfo, isExpandedScreen)
            }

            // 距离底部按钮的间距
            Spacer(modifier = Modifier.height(18.dp))
            
            // 渲染底部的操作按钮区域（播放 / 收藏）
            ActionArea(isExpandedScreen)
        }
    }
}

// ------------------------------------------
// 辅助组件：评分和星级展示区域
// ------------------------------------------
@Composable
fun RatingArea(movieInfo: MovieInfo, isExpandedScreen: Boolean) {
    // 根据屏幕尺寸决定评分字体和星星的大小
    val scoreStyle = if (isExpandedScreen) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
    val starSize = if (isExpandedScreen) 14.dp else 10.dp

    // 水平排列数字分数和星星模块
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 数字评分（如：7.8），固定使用橙色突出
        Text(
            text = movieInfo.score, 
            color = MaterialTheme.colorScheme.primary, // 使用主题主色
            style = scoreStyle,
            fontWeight = FontWeight.Bold
        )
        // 评分和右侧星星的间距
        Spacer(modifier = Modifier.width(if (isExpandedScreen) 8.dp else 2.dp))
        
        // 垂直排列“评分次数”和“星星列表”
        Column {
             // 评价次数文本（如：482次评价）
             Text(
                 text = movieInfo.scoreCount, 
                 style = MaterialTheme.typography.labelSmall, 
                 color = MaterialTheme.colorScheme.onSurfaceVariant, // 次要文字颜色
                 fontSize = if (isExpandedScreen) 11.sp else 9.sp
             )
             // 水平排列星星
             Row {
                 // 提取分数并转为浮点数，容错处理默认 0f
                 val numScore = movieInfo.score.toFloatOrNull() ?: 0f
                 // 将 10分制 转化为 5星制，向下取整，并限制在 0-5 之间
                 val filledStars = (numScore / 2).toInt().coerceIn(0, 5)
                 // 计算需要显示的空心星星数量
                 val emptyStars = 5 - filledStars
                 
                 // 循环渲染亮起的星星
                 repeat(filledStars) { 
                     Icon(Icons.Outlined.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(starSize)) 
                 }
                 // 循环渲染灰色的星星
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
    // 动态计算按钮的各项尺寸，适配大小屏
    val btnHeight = if (isExpandedScreen) 40.dp else 35.dp // 按钮高度
    val fontSize = if (isExpandedScreen) 14.sp else 12.sp  // 文字大小
    val iconSize = if (isExpandedScreen) 18.dp else 14.dp  // 图标大小
    val paddingH = if (isExpandedScreen) 14.dp else 10.dp  // 内部水平边距

    // 水平铺满布局，按钮之间间隔 8dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. 播放按钮
        Button(
            onClick = { /* TODO: 播放逻辑填写在这里 */ },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary, // 背景：主题色
                contentColor = MaterialTheme.colorScheme.onPrimary // 文字/图标：反色
            ),
            modifier = Modifier
                .height(btnHeight) // 设置高度
                .weight(1f), // 核心：使用权重平分 50% 的宽度，避免换行
            contentPadding = PaddingValues(horizontal = paddingH), // 内边距
            shape = RoundedCornerShape(20.dp) // 大圆角药丸形状
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "播放", modifier = Modifier.size(iconSize))
            Spacer(modifier = Modifier.width(4.dp))
            Text("播放", fontWeight = FontWeight.Bold, fontSize = fontSize, maxLines = 1)
        }

        // 2. 收藏按钮（使用 Surface 构建自定义按钮体验更好）
        Surface(
            onClick = { /* TODO: 收藏逻辑填写在这里 */ },
            shape = RoundedCornerShape(20.dp), // 大圆角药丸形状
            color = MaterialTheme.colorScheme.surfaceVariant, // 背景：灰色/次要背景色
            modifier = Modifier
                .height(btnHeight)
                .weight(1f) // 核心：使用权重平分另外 50% 的宽度
        ) {
            // Surface 内部排列内容
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center, // 内容绝对居中
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
    // 根据屏幕计算“简介”标签的字体大小
    val titleSize = if (isExpandedScreen) 16.sp else 13.sp

    Column {
        // 渲染带有主题背景色的“简介”小标签
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) // 主题色背景+小圆角
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
             Text("简介", fontSize = titleSize, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
        }
        
        // 标题和内容之间的距离
        Spacer(modifier = Modifier.height(5.dp))

        // 渲染带有折叠/展开功能的内容组件
        ExpandableDescription(text = movieInfo.description, isExpandedScreen)
    }
}

// ------------------------------------------
// 辅助组件：带有主题底色的小标签行（如：导演、演员等）
// ------------------------------------------
@Composable
fun InfoLabelRow(label: String, value: String, isExpandedScreen: Boolean, modifier: Modifier = Modifier.fillMaxWidth()) {
    // 自适应字号
    val labelSize = if (isExpandedScreen) 16.sp else 12.sp
    val valueSize = if (isExpandedScreen) 16.sp else 12.sp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top // 确保在文字换行时，左侧标签依旧与第一行对齐
    ) {
        // 左侧的主题色背景标签
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = label, // 如："导演"
                color = MaterialTheme.colorScheme.onPrimary, // 反色保证清晰
                fontSize = labelSize,
                fontWeight = FontWeight.Bold
            )
        }
        // 标签和内容的间距
        Spacer(modifier = Modifier.width(8.dp))
        // 右侧的具体内容
        Text(
            text = value, // 如人名、类型列表
            fontSize = valueSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant, // 次级文字颜色
            maxLines = 2, // 最多显示两行
            overflow = TextOverflow.Ellipsis, // 超过两行显示省略号
            modifier = Modifier
                .weight(1f) // 给文字设置 weight，使其在被父布局压缩时懂得截断而不是撑爆布局
                .padding(top = 1.dp) // 微调使其视觉上和左边标签在同一水平线
        )
    }
}

// ------------------------------------------
// 辅助组件：支持动态展开/收起的多行文本
// ------------------------------------------
@Composable
fun ExpandableDescription(text: String, isExpandedScreen: Boolean) {
    // 记录当前的展开状态，默认收起（false）
    var expanded by remember { mutableStateOf(false) }
    // 字体自适应
    val bodyStyle = if (isExpandedScreen) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall
    val btnTextSize = if (isExpandedScreen) 16.sp else 12.sp

    // Column 加载动画修饰符，使得高度变化时像抽屉一样平滑展开
    Column(modifier = Modifier.animateContentSize()) {
        // 内容文本
        Text(
            text = text,
            style = bodyStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // 核心逻辑：如果 expanded 为真，显示无限多行；否则限制只显示 3 行
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis, // 超出显示省略号
            lineHeight = bodyStyle.lineHeight * 1.5f // 增加 1.5倍 行高提升阅读体验
        )
        
        // 底部居中的控制按钮外层包裹
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp),
            contentAlignment = Alignment.Center // 按钮整体居中
        ) {
            // 具体的点击按钮
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp)) // 胶囊圆角
                    .background(MaterialTheme.colorScheme.surfaceVariant) // 灰色背景
                    .clickable { expanded = !expanded } // 点击切换展开状态
                    .padding(horizontal = 12.dp, vertical = 6.dp) // 按钮内边距
            ) {
                 // 根据状态显示对应的文字和符号
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
// 3. 剧集列表与选集区域
// ==========================================
@Composable
fun PlayListSection(playLists: List<PlayList>, isExpandedScreen: Boolean) {
    // 记住当前选中的 Tab（播放源）索引
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    // 记住当前的排序状态（正序 / 倒序）
    var isReversed by remember { mutableStateOf(false) }

    // 自适应字号
    val titleSize = if (isExpandedScreen) 14.sp else 12.sp

    Column {
        // 渲染带有主题背景色的“线路”小标题标签
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
             Text("线路", fontSize = titleSize, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
        }
        
        // 排列：左侧滑动源切换 Tab + 右侧排序按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically, // 垂直居中对齐
            horizontalArrangement = Arrangement.SpaceBetween // 两端对齐分配空间
        ) {
            // 左侧：可滚动的 Tab 行
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.weight(1f), // 占据左侧主要宽度
                edgePadding = 0.dp, // 移除系统自带的左右内边距
                indicator = { tabPositions -> // 自定义下方那根红色的指示线
                    // 越界检查，防止数组越界奔溃
                    if (selectedTabIndex < tabPositions.size) {
                        Box(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[selectedTabIndex]) // 跟随选中的 tab 移动
                                .padding(horizontal = 30.dp) // 让红线变短居中
                                .height(3.dp) // 线的高度
                                .clip(RoundedCornerShape(1.5.dp)) // 线头圆润
                                .background(MaterialTheme.colorScheme.primary) // 主题色
                        )
                    }
                },
                divider = { } // 移除下方灰色的默认底线，看起来更清爽
            ) {
                // 遍历所有的播放列表源
                playLists.forEachIndexed { index, playList ->
                    Tab(
                        selected = selectedTabIndex == index,
                        // 点击时切换索引，并且把排序状态重置为正常正序
                        onClick = { 
                            selectedTabIndex = index
                            isReversed = false 
                        },
                        text = { 
                            Text(
                                text = playList.sourceName, // 源名称（如：优酷、腾讯）
                                fontSize = titleSize,
                                fontWeight = FontWeight.SemiBold,
                                // 选中时用主题色，未选中用普通文字颜色
                                color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ) 
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 右侧：排序切换按钮
            Surface(
                onClick = { isReversed = !isReversed }, // 翻转排序状态
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant // 灰色底
            ) {
                Text(
                    text = if (isReversed) "倒序 ▼" else "正序 ▲",
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 核心渲染逻辑：取出选中的源的数据，并根据是否倒序进行翻转
        val currentEpisodes = playLists[selectedTabIndex].episodes
        val displayEpisodes = if (isReversed) currentEpisodes.reversed() else currentEpisodes

        // 剧集网格列表
        LazyVerticalGrid(
            // 设置每列最小宽度 80dp，系统会自动计算一行能放下几个方块
            columns = GridCells.Adaptive(minSize = 80.dp),
            // 防止嵌套滚动导致崩溃，设置一个最大高度
            modifier = Modifier.heightIn(max = 600.dp), 
            contentPadding = PaddingValues(bottom = 16.dp), // 底部留白防止贴边
            horizontalArrangement = Arrangement.spacedBy(6.dp), // 每一列的横向间距
            verticalArrangement = Arrangement.spacedBy(8.dp)  // 每一行的垂直间距
        ) {
            // 渲染列表项
            items(displayEpisodes) { episode ->
                EpisodeItem(episode, isExpandedScreen)
            }
        }
    }
}

// ------------------------------------------
// 辅助组件：剧集单项的方块卡片
// ------------------------------------------
@Composable
fun EpisodeItem(episode: Episode, isExpandedScreen: Boolean) {
    // 动态调整剧集框的内边距和字号
    val paddingV = if (isExpandedScreen) 12.dp else 10.dp
    val textStyle = if (isExpandedScreen) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp)) // 方块的圆角
            .background(MaterialTheme.colorScheme.secondaryContainer) // 稍微深一点的背景色突出集数
            .clickable { 
                // TODO: 在这里处理具体点击集数去播放的逻辑
                Log.d("EpisodeItem", "Clicked: ${episode.name}, URL: ${episode.url}")
            }
            .padding(vertical = paddingV, horizontal = 4.dp),
        contentAlignment = Alignment.Center // 让剧集文字绝对居中
    ) {
        // 集数文字（例如："1" 或 "第1集"）
        Text(
            text = episode.name,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Medium,
            maxLines = 1, // 单行显示，防止剧集名过长
            overflow = TextOverflow.Ellipsis // 太长显示省略号
        )
    }
}