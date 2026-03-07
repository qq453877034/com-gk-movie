package com.gk.movie.Utils.Category.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gk.movie.Utils.Category.Util.InfoCategoryModel
import com.gk.movie.ui.theme.ComposeEmptyActivityTheme

class InfoCategoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            // 你可以使用你们项目的 AppTheme 包裹
            ComposeEmptyActivityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InfoCategoryScreen(
                        onMovieClick = { detailUrl ->
                            Log.d("CategoryActivity", "点击了影片，准备跳转详情页: $detailUrl")
                            // TODO: 在这里执行跳转到你的详情页逻辑
                            // val intent = Intent(this, YourDetailActivity::class.java)
                            // intent.putExtra("url", detailUrl)
                            // startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}