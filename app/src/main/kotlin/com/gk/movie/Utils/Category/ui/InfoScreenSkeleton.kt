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
            
            // ★ 同步打平：不再将所有行包裹在一个 item 中，彻底释放计算压力
            repeat(4) { rowIndex ->
                item(key = "Skeleton_FilterRow_$rowIndex", contentType = "FilterRow") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        // 同样完美还原间距
                        modifier = Modifier.padding(
                            top = if (rowIndex == 0) 12.dp else 4.dp,
                            bottom = 4.dp
                        ),
                        userScrollEnabled = false
                    ) {
                        item(key = "Skeleton_Title_$rowIndex", contentType = "FilterTitle") { 
                            Box(modifier = Modifier.padding(end = 8.dp, top = 10.dp).width(32.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect()) 
                        }
                        items(
                            count = 18,
                            key = { colIndex -> "Skeleton_FilterItem_${rowIndex}_$colIndex" },
                            contentType = { "FilterItem" }
                        ) { 
                            Box(modifier = Modifier.width(60.dp).height(36.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect()) 
                        }
                    }
                }
            }
            
            stickyHeader(key = "Skeleton_StickySortHeader", contentType = "SortHeader") {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        userScrollEnabled = false
                    ) {
                        items(
                            count = 4,
                            key = { index -> "Skeleton_SortItem_$index" },
                            contentType = { "SortItem" }
                        ) {
                            Box(modifier = Modifier.width(70.dp).height(32.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
                        }
                    }
                }
            }

            items(
                count = 6,
                key = { index -> "Skeleton_MovieRow_$index" },
                contentType = { "LoadingMovieRow" }
            ) { 
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