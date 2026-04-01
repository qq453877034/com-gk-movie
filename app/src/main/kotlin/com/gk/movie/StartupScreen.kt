// 文件路径: app/src/main/kotlin/com/gk/movie/StartupScreen.kt
package com.gk.movie

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun StartupScreen(onStartupComplete: () -> Unit) {
    val scale = remember { Animatable(0.5f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1.2f,
            animationSpec = tween(durationMillis = 800)
        )
        delay(700) 
        onStartupComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ★ 修复：完全拥抱 Material 3 的动态背景色，拒绝硬编码！
            .background(MaterialTheme.colorScheme.background) 
    ) {
        Icon(
            imageVector = Icons.Default.PlayCircleFilled,
            contentDescription = "App Logo",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(100.dp)
                .scale(scale.value)
                .align(Alignment.Center)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GK Movie",
                color = MaterialTheme.colorScheme.onBackground, // ★ 动态文字颜色
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "© 2026 GK Studio All Rights Reserved",
                color = MaterialTheme.colorScheme.onSurfaceVariant, // ★ 动态次要文字颜色
                fontSize = 12.sp
            )
        }
    }
}