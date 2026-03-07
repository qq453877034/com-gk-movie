package com.gk.movie.Utils.Category.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.shimmerEffect(): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    return this.background(Color.LightGray.copy(alpha = alpha))
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
            
            // 1. 模拟上层普通分类筛选骨架
            item {
                // ★ 还原为绝对的 padding(top = 8.dp)，不再人为偏移
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    repeat(4) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp),
                            userScrollEnabled = false
                        ) {
                            // ★ 还原：标题宽32、高20
                            item { Box(modifier = Modifier.padding(end = 8.dp, top = 10.dp).width(32.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect()) }
                            // ★ 还原：药丸高 36，宽 60
                            items(18) { Box(modifier = Modifier.width(60.dp).height(36.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect()) }
                        }
                    }
                }
            }
            
            // 2. 模拟吸顶排序骨架
            stickyHeader {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        userScrollEnabled = false
                    ) {
                        // ★ 还原：排序药丸高 32，宽 70
                        items(4) {
                            Box(modifier = Modifier.width(70.dp).height(32.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
                        }
                    }
                }
            }

            // 3. 模拟视频网格骨架
            items(6) { 
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
                                    // ★ 还原：电影文字线高 14
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