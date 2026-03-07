package com.gk.movie.Utils.Category.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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

@Composable
fun CategorySkeletonScreen(isCompactWindow: Boolean) {
    // 此时外层已经做好了 LocalDensity 缩放管理，这里只需要老老实实画 UI 占位
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val minItemWidth = 130.dp
        val rawColumns = (maxWidth / minItemWidth).toInt()
        val columns = maxOf(1, if (rawColumns >= 4) rawColumns - 1 else rawColumns)
        
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. 模拟上层普通分类筛选骨架
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                repeat(4) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Box(modifier = Modifier.width(40.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                        Spacer(modifier = Modifier.width(16.dp))
                        repeat(20) {
                            Box(modifier = Modifier.width(60.dp).height(36.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }
                }
            }
            
            // 2. 模拟吸顶排序骨架
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                repeat(4) {
                    Box(modifier = Modifier.width(70.dp).height(32.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }

            // 3. 模拟视频网格骨架
            Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                repeat(3) { 
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
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
            }
        }
    }
}