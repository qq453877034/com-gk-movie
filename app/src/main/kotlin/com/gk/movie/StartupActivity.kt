// 文件路径: app/src/main/kotlin/com/gk/movie/StartupActivity.kt
package com.gk.movie

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gk.movie.Utils.okhttpUtils.OkhttpManager
import com.gk.movie.ui.theme.ComposeEmptyActivityTheme

class StartupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // ★ 在 App 刚启动时，进行网络管理器的全局初始化
        OkhttpManager.init(this.applicationContext)

        setContent {
            ComposeEmptyActivityTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StartupScreen(onStartupComplete = {
                        // 1.5秒后，跳转到 MainActivity，并销毁当前的启动页
                        startActivity(Intent(this@StartupActivity, MainActivity::class.java))
                        finish()
                        
                        // 修复废弃警告：根据 Android 版本适配平滑淡入淡出动画
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
                        } else {
                            @Suppress("DEPRECATION")
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        }
                    })
                }
            }
        }
    }
}