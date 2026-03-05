// 文件路径: com/gk/movie/Utils/Media3Play/ui/InfoScreenComponents.kt
package com.gk.movie.Utils.Media3Play.ui

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

@Composable
fun SniffingLoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "正在破译真实播放流...",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MovieHeaderInfo(
    movieInfo: MovieInfo, 
    isExpandedScreen: Boolean,
    onPlayClick: () -> Unit
) {
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
            
            ActionArea(isExpandedScreen, onPlayClick)
        }
    }
}

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

@Composable
fun ActionArea(isExpandedScreen: Boolean, onPlayClick: () -> Unit) {
    val btnHeight = if (isExpandedScreen) 40.dp else 35.dp
    val fontSize = if (isExpandedScreen) 14.sp else 12.sp
    val iconSize = if (isExpandedScreen) 18.dp else 14.dp
    val paddingH = if (isExpandedScreen) 14.dp else 10.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onPlayClick,
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

@Composable
fun MovieDescriptionSection(movieInfo: MovieInfo, isExpandedScreen: Boolean) {
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

@Composable
fun PlayListHeader(
    playLists: List<PlayList>,
    selectedTabIndex: Int,
    isReversed: Boolean,
    onTabSelected: (Int) -> Unit,
    onReverseToggle: () -> Unit,
    isExpandedScreen: Boolean,
    isStuck: Boolean
) {
    val labelSize = if (isExpandedScreen) 16.sp else 13.sp
    val tabTextSize = if (isExpandedScreen) 14.sp else 12.sp

    Column {
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
                 fontSize = labelSize, 
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
                            fontSize = tabTextSize, 
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
                    fontSize = tabTextSize, 
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PlayListGrid(
    playLists: List<PlayList>,
    selectedTabIndex: Int,
    isReversed: Boolean,
    selectedEpisodeUrl: String?,
    onEpisodeSelected: (String, String) -> Unit,
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
                            onClick = { onEpisodeSelected(episode.url, episode.name) },
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