// 文件路径: com/gk/movie/Utils/Media3Play/ui/infoActivity.kt
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

class infoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 在这里初始化 OkhttpManager，把应用的 Context 传给它
        OkhttpManager.init(this.applicationContext)
        
        // 检查并申请所有文件访问权限 (Android 11+)
        requestManageExternalStoragePermission()

        setContent {
            ComposeEmptyActivityTheme {
                InfoScreen()
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

    // ★ 核心修复3：彻底退出页面时，一定要释放底层的视频播放器，避免后台声音残留
    override fun onDestroy() {
        super.onDestroy()
        com.gk.movie.Utils.Media3Play.Util.Media3Manager.release()
    }
}