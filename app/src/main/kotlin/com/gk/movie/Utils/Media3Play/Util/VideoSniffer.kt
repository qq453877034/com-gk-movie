// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/Util/VideoSniffer.kt
package com.gk.movie.Utils.Media3Play.Util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object VideoSniffer {
    private const val TAG = "VideoSniffer"
    private const val BASE_HOST = "ww66.taiee.lol"

    /**
     * 挂起函数：传入网页 URL，后台静默执行 JS 并在抓取到 m3u8 后返回
     */
    suspend fun sniff(context: Context, pageUrl: String): String? = suspendCancellableCoroutine { continuation ->
        // WebView 必须在主线程创建和操作
        Handler(Looper.getMainLooper()).post {
            val webView = WebView(context)
            
            // 1. 核心设置：开启JS，允许DOM，伪装成 PC 浏览器环境
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // 允许网页自动播放视频，这是触发 iframe 去请求 m3u8 的关键
                mediaPlaybackRequiresUserGesture = false 
                // 伪装浏览器 UserAgent
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            // 2. Cookie 管理：静默接受所有 Cookie，通过云端防护
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)

            // 3. 拦截网络请求，抓取真实视频流
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url?.toString() ?: ""
                    
                    // 命中 m3u8 或 mp4 视频流后缀
                    if (url.contains(".m3u8") || url.contains(".mp4")) {
                        Log.d(TAG, "🎉 成功嗅探到视频流: $url")
                        if (continuation.isActive) {
                            continuation.resume(url) // 返回真实地址
                            
                            // 嗅探成功后，立刻停止加载并销毁 WebView 释放内存
                            Handler(Looper.getMainLooper()).post {
                                webView.stopLoading()
                                webView.destroy()
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }

            // 4. ★ 核心防屏蔽：自动处理网页弹窗 ★
            // 破解诸如“欢迎回家”、“点我知道了”等 JS 拦截器
            webView.webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    Log.d(TAG, "拦截到网页 Alert: $message，已自动确认")
                    result?.confirm()
                    return true
                }

                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    result?.confirm()
                    return true
                }

                override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                    result?.confirm()
                    return true
                }
            }

            // 5. 补全域名并装载 Header 请求
            val fullUrl = if (pageUrl.startsWith("/")) "https://$BASE_HOST$pageUrl" else pageUrl
            val headers = mutableMapOf(
                "Referer" to "https://$BASE_HOST/index.php",
                "Origin" to "https://$BASE_HOST"
            )
            
            Log.d(TAG, "🔍 开始嗅探解析页面: $fullUrl")
            webView.loadUrl(fullUrl, headers)
        }
    }
}