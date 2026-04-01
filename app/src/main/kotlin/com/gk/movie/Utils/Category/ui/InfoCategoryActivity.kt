// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Category/ui/InfoCategoryActivity.kt
package com.gk.movie.Utils.Category.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gk.movie.Utils.Category.Util.InfoCategoryModel
import com.gk.movie.ui.theme.ComposeEmptyActivityTheme
import com.gk.movie.Utils.Media3Play.ui.infoActivity

class InfoCategoryActivity : ComponentActivity() {
    
    private val viewModel: InfoCategoryModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ✅ 核心修复：精准接收 MainScreen 传过来的 url，丢弃错误的 typeId
        val targetUrl = intent.getStringExtra("url") ?: "/vod/show/id/1"
        val title = intent.getStringExtra("title")?.replace("更多 >", "")?.trim() ?: "全部分类"
        
        viewModel.initCategory(title, targetUrl)

        setContent {
            ComposeEmptyActivityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InfoCategoryScreen(
                        viewModel = viewModel,
                        onMovieClick = { vodIdStr ->
                            try {
                                Log.d("CategoryActivity", "准备跳转详情页: ID=$vodIdStr")
                                val intent = Intent(this@InfoCategoryActivity, infoActivity::class.java)
                                intent.putExtra("vodId", vodIdStr)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                startActivity(intent)
                                
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
                                } else {
                                    @Suppress("DEPRECATION")
                                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                                }
                            } catch (e: Exception) {
                                Log.e("CategoryActivity", "跳转详情页崩溃", e)
                                Toast.makeText(
                                    this@InfoCategoryActivity, 
                                    "拉起播放页失败: ${e.message}", 
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
    
    override fun finish() {
        super.finish()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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