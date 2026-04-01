// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/ui/infoActivity.kt
package com.gk.movie.Utils.Media3Play.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gk.movie.Utils.okhttpUtils.OkhttpManager
import com.gk.movie.ui.theme.ComposeEmptyActivityTheme
import com.gk.movie.Utils.Media3Play.Util.Media3Manager

class infoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        OkhttpManager.init(this.applicationContext)
        requestManageExternalStoragePermission()

        val targetVodId = intent.getStringExtra("vodId") ?: ""

        setContent {
            ComposeEmptyActivityTheme {
                InfoScreen(vodId = targetVodId)
            }
        }
    }

    private fun requestManageExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", packageName))
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    startActivity(intent)
                }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onDestroy() {
        super.onDestroy()
        Media3Manager.release()
    }

    override fun finish() {
        super.finish()
        // 修复废弃警告，适配 Android 14+ 转场动画
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}