// 文件路径: com/gk/movie/Utils/Media3Play/ui/InfoScreenSkeleton.kt
package com.gk.movie.Utils.Media3Play.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * 自定义 Modifier 扩展：呼吸灯（Shimmer）效果
 */
@Composable
fun Modifier.shimmerEffect(): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f)
    return this.background(color)
}

/**
 * 完整页面的骨架屏布局
 */
@Composable
fun MovieContentSkeleton() {
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 840

    if (isExpandedScreen) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, top = 60.dp, bottom = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(modifier = Modifier.weight(0.6f)) {
                MovieHeaderSkeleton(isExpandedScreen)
                Spacer(modifier = Modifier.height(30.dp))
                MovieDescriptionSkeleton(isExpandedScreen)
            }
            Column(modifier = Modifier.weight(0.4f)) {
                PlayListSkeleton(isExpandedScreen)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 45.dp)) {
                MovieHeaderSkeleton(isExpandedScreen)
                Spacer(modifier = Modifier.height(10.dp))
                MovieDescriptionSkeleton(isExpandedScreen)
                Spacer(modifier = Modifier.height(10.dp))
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 12.dp, top = 8.dp)
            ) {
                PlayListHeaderSkeleton(isExpandedScreen)
            }
            
            Box(modifier = Modifier.padding(horizontal = 10.dp)) {
                PlayListGridSkeleton(isExpandedScreen)
            }
        }
    }
}

@Composable
fun MovieHeaderSkeleton(isExpandedScreen: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(if (isExpandedScreen) 155.dp else 120.dp)
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .shimmerEffect()
        )

        Spacer(modifier = Modifier.width(if (isExpandedScreen) 15.dp else 6.dp))

        Column(modifier = Modifier.weight(1f)) {
            Spacer(modifier = Modifier.height(if (isExpandedScreen) 10.dp else 4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(if (isExpandedScreen) 32.dp else 24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(14.dp))
            repeat(3) { index ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(36.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index == 2) 0.5f else 0.9f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val btnHeight = if (isExpandedScreen) 40.dp else 35.dp
                Box(modifier = Modifier.weight(1f).height(btnHeight).clip(RoundedCornerShape(20.dp)).shimmerEffect())
                Box(modifier = Modifier.weight(1f).height(btnHeight).clip(RoundedCornerShape(20.dp)).shimmerEffect())
            }
        }
    }
}

@Composable
fun MovieDescriptionSkeleton(isExpandedScreen: Boolean) {
    Column {
        Box(modifier = Modifier.width(40.dp).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
        Spacer(modifier = Modifier.height(10.dp))
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index == 2) 0.6f else 1f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun PlayListSkeleton(isExpandedScreen: Boolean) {
    Column {
        PlayListHeaderSkeleton(isExpandedScreen)
        Spacer(modifier = Modifier.height(16.dp))
        PlayListGridSkeleton(isExpandedScreen)
    }
}

@Composable
fun PlayListHeaderSkeleton(isExpandedScreen: Boolean) {
    Column {
        Box(modifier = Modifier.width(40.dp).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.width(80.dp).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Box(modifier = Modifier.width(80.dp).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            }
            Box(modifier = Modifier.width(60.dp).height(24.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
        }
    }
}

@Composable
fun PlayListGridSkeleton(isExpandedScreen: Boolean) {
    val columns = 4
    val rows = 3
    val itemHeight = if (isExpandedScreen) 42.dp else 36.dp
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(columns) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(itemHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .shimmerEffect()
                    )
                }
            }
        }
    }
}