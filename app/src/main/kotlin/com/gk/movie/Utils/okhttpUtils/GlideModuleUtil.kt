// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/okhttpUtils/MyAppGlideModule.kt
package com.gk.movie.Utils.okhttpUtils

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import java.io.InputStream

/**
 * 全局 Glide 引擎配置模块
 * 编译期由 KAPT 自动生成实现类，全面接管应用内所有 Compose 及 View 的 GlideImage 请求
 */
@GlideModule
class GlideModuleUtil : AppGlideModule() {
    
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // ★ 核心替换：将 Glide 的默认 HttpURLConnection 引擎彻底替换为我们的 OkHttpClient
        // 这样所有的图片请求都会自动走 BrowserSimulationInterceptor 拦截器，自动带上 Referer 防盗链！
        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(OkhttpManager.client)
        )
    }

    // 禁用默认的 Manifest 解析，显著提升 App 冷启动与 Glide 引擎初始化的速度
    override fun isManifestParsingEnabled(): Boolean = false
}