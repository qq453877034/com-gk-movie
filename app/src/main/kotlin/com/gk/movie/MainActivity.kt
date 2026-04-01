// 文件路径: app/src/main/kotlin/com/gk/movie/MainActivity.kt
package com.gk.movie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gk.movie.ui.theme.ComposeEmptyActivityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 开启沉浸式
        enableEdgeToEdge()
        setContent {
            ComposeEmptyActivityTheme {
                // 直接加载 MainScreen 首页界面，不要包在 Scaffold 里！
                MainScreen()
            }
        }
    }
}