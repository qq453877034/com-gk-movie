// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/Util/VideoSniffer.kt
package com.gk.movie.Utils.Media3Play.Util

import android.content.Context
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
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

        // --- 🚀 第一道防线：极速解析引擎 (耗时极短) ---
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "⚡ 启动极速解析引擎: $fullUrl")
                val request = Request.Builder().url(fullUrl).build()
                val response = OkhttpManager.client.newCall(request).execute()
                val html = response.body?.string() ?: ""

                // 1. 匹配 iframe 里的 url=https://...m3u8 (遇到 & 或引号即停止)
                val regex1 = Regex("url=(https?://[^&\"']+\\.m3u8)")
                // 2. 匹配部分 MacCMS 自带的 JSON 格式 "url":"https:\/\/...m3u8"
                val regex2 = Regex("\"url\":\"(https?:\\\\/\\\\/[^\"]+\\.m3u8)\"")

                var fastM3u8: String? = null

                val match1 = regex1.find(html)
                if (match1 != null) {
                    // 解码以防 URL 被 Encode 过
                    fastM3u8 = URLDecoder.decode(match1.groupValues[1], "UTF-8")
                } else {
                    val match2 = regex2.find(html)
                    if (match2 != null) {
                        fastM3u8 = match2.groupValues[1].replace("\\/", "/")
                    }
                }

                if (fastM3u8 != null) {
                    Log.d(TAG, "🚀 极速解析成功！秒获纯净 M3U8: $fastM3u8")
                    if (continuation.isActive) {
                        continuation.resume(fastM3u8)
                    }
                    return@launch
                }
                
                Log.d(TAG, "⏳ 源码未直接暴露 M3U8，进入 WebView 深度嗅探模式...")
            } catch (e: Exception) {
                Log.w(TAG, "极速解析网络请求失败，回退至 WebView", e)
            }

            // --- 🛡️ 第二道防线：WebView 真实渲染嗅探兜底 ---
            Handler(Looper.getMainLooper()).post {
                val webView = WebView(context)
                
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false 
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                }

                var isFinished = false

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val url = request?.url?.toString() ?: ""
                        
                        if (!isFinished && url.contains(".m3u8", ignoreCase = true)) {
                            isFinished = true
                            Log.d(TAG, "🔍 WebView 拦截到混合链接: $url")
                            
                            var realM3u8 = url
                            try {
                                // ★ 核心截取逻辑：提取真实的 m3u8 地址，剔除 next= 和 tittle= 等参数
                                val uri = android.net.Uri.parse(url)
                                val targetUrl = uri.getQueryParameter("url")
                                
                                if (targetUrl != null && targetUrl.contains(".m3u8", ignoreCase = true)) {
                                    realM3u8 = URLDecoder.decode(targetUrl, "UTF-8")
                                } else {
                                    // 暴力正则提取兜底
                                    val regex = Regex("(https?://[^&\\s]+\\.m3u8)")
                                    val match = regex.find(url)
                                    if (match != null) {
                                        realM3u8 = match.value
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "URL 解析截取异常", e)
                            }

                            Log.d(TAG, "🎯 最终提取的纯正 M3U8: $realM3u8")

                            Handler(Looper.getMainLooper()).post {
                                if (continuation.isActive) {
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

                webView.loadUrl(fullUrl)
            }
        }
    }
}