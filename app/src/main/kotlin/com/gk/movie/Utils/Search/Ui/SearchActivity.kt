// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Search/Ui/SearchActivity.kt
package com.gk.movie.Utils.Search.Ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gk.movie.Utils.Media3Play.ui.infoActivity
import com.gk.movie.ui.theme.ComposeEmptyActivityTheme

class SearchActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 接收从别的地方传过来的搜索字符
        val passedKeyword = intent.getStringExtra("keyword") ?: ""
        
        setContent {
            ComposeEmptyActivityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SearchScreen(
                        initialKeyword = passedKeyword,
                        onBackClick = { finish() },
                        onMovieClick = { vodIdStr ->
                            try {
                                val intent = Intent(this@SearchActivity, infoActivity::class.java)
                                intent.putExtra("vodId", vodIdStr)
                                startActivity(intent)
                                
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
                                } else {
                                    @Suppress("DEPRECATION")
                                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                                }
                            } catch (e: Exception) {
                                Log.e("SearchActivity", "跳转详情页崩溃", e)
                                Toast.makeText(this@SearchActivity, "拉起播放页失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
    
    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                android.app.Activity.OVERRIDE_TRANSITION_CLOSE,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}