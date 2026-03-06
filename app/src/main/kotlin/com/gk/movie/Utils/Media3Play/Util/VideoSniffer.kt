// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/Util/VideoSniffer.kt
package com.gk.movie.Utils.Media3Play.Util

import android.content.Context
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.*
import com.gk.movie.Utils.okhttpUtils.OkhttpManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Request
import java.net.URLDecoder
import kotlin.coroutines.resume

object VideoSniffer {
    private const val TAG = "VideoSniffer"
    private const val BASE_HOST = "www.jkan.app"

    suspend fun sniff(context: Context, pageUrl: String): String? = suspendCancellableCoroutine { continuation ->
        val fullUrl = if (pageUrl.startsWith("/")) "https://$BASE_HOST$pageUrl" else pageUrl

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "⚡ 启动极速解析引擎: $fullUrl")
                val request = Request.Builder().url(fullUrl).build()
                val response = OkhttpManager.client.newCall(request).execute()
                val html = response.body?.string() ?: ""

                // 🚀 终极杀招 1：降维打击，直接提取苹果 CMS 的加密 URL
                try {
                    // 先把包含 player_aaaa 的那块代码提取出来
                    val blockRegex = """var\s+player_aaaa\s*=\s*(.*?)</script>""".toRegex(RegexOption.DOT_MATCHES_ALL)
                    val blockMatch = blockRegex.find(html)
                    
                    if (blockMatch != null) {
                        val block = blockMatch.groupValues[1]
                        
                        // 像手术刀一样直接剔出 url 和 encrypt 的值，无视任何 JSON 嵌套结构！
                        val urlMatch = """"url"\s*:\s*"([^"]+)"""".toRegex().find(block)
                        val encryptMatch = """"encrypt"\s*:\s*(\d+)""".toRegex().find(block)
                        
                        if (urlMatch != null) {
                            val videoUrl = urlMatch.groupValues[1]
                            val encrypt = encryptMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                            var realUrl = ""
                            if (encrypt == 1) {
                                realUrl = URLDecoder.decode(videoUrl, "UTF-8")
                            } else if (encrypt == 2) {
                                val base64Decoded = String(Base64.decode(videoUrl, Base64.DEFAULT), Charsets.UTF_8)
                                realUrl = URLDecoder.decode(base64Decoded, "UTF-8")
                            } else {
                                realUrl = videoUrl
                            }

                            if (realUrl.contains(".m3u8") || realUrl.contains(".mp4")) {
                                Log.e(TAG, "🎯 极速破解苹果CMS加密成功: $realUrl")
                                Handler(Looper.getMainLooper()).post {
                                    if (continuation.isActive) continuation.resume(realUrl)
                                }
                                return@launch
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "提取苹果CMS数据失败: ${e.message}")
                }

                // 🚀 备用防线 2：普通 iframe
                val m3u8Regex = """url=(https?://[^&"']+\.m3u8)""".toRegex(RegexOption.IGNORE_CASE)
                val match = m3u8Regex.find(html)

                if (match != null) {
                    val encodedUrl = match.groupValues[1]
                    val realUrl = URLDecoder.decode(encodedUrl, "UTF-8")
                    Log.e(TAG, "⚡ 正则提取 iframe 解析成功: $realUrl")
                    
                    Handler(Looper.getMainLooper()).post {
                        if (continuation.isActive) continuation.resume(realUrl)
                    }
                    return@launch
                }

                Log.w(TAG, "⚠️ 极速解析全线失败，启动 WebView 深度嗅探...")
                Handler(Looper.getMainLooper()).post {
                    startWebViewSniffing(context, fullUrl, continuation)
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ 极速解析发生网络异常", e)
                Handler(Looper.getMainLooper()).post {
                    startWebViewSniffing(context, fullUrl, continuation)
                }
            }
        }
    }

    private fun startWebViewSniffing(context: Context, fullUrl: String, continuation: kotlinx.coroutines.CancellableContinuation<String?>) {
        try {
            val webView = WebView(context)
            
            // 防无限转圈机制 (15秒超时强杀)
            val timeoutHandler = Handler(Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                if (continuation.isActive) {
                    Log.e(TAG, "❌ WebView 嗅探超时 (15秒)，强制结束！(在挂VPN的情况下，WebView通常无法访问国内影视站)")
                    continuation.resume(null)
                    webView.destroy()
                }
            }
            timeoutHandler.postDelayed(timeoutRunnable, 15000)

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = OkhttpManager.USER_AGENT
            }

            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url?.toString() ?: ""
                    
                    if (url.contains(".m3u8") && !url.contains("url=")) {
                        val realM3u8 = url
                        Log.e(TAG, "🕸️ WebView 深度嗅探成功: $realM3u8")

                        Handler(Looper.getMainLooper()).post {
                            if (continuation.isActive) {
                                timeoutHandler.removeCallbacks(timeoutRunnable) // 取消超时炸弹
                                continuation.resume(realM3u8)
                            }
                            webView.destroy()
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.proceed()
                }
            }

            webView.webChromeClient = object : WebChromeClient() {
                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
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

            val extraHeaders = mutableMapOf(
                "Referer" to OkhttpManager.REFERER,
                "X-Requested-With" to "XMLHttpRequest"
            )
            webView.loadUrl(fullUrl, extraHeaders)

        } catch (e: Exception) {
            Log.e(TAG, "WebView 初始化失败", e)
            if (continuation.isActive) continuation.resume(null)
        }
    }
}