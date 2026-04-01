// 文件路径: app/src/main/kotlin/com/gk/movie/ProfilePage.kt
package com.gk.movie

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePage() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "我的",
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 22.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("个人中心", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("登录、播放记录等功能开发中...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 14.sp)
            }
        }
    }
}