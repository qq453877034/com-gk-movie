// 文件路径: com/gk/movie/Utils/okhttpUtils/OkhttpManager.kt
package com.gk.movie.Utils.okhttpUtils

import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object OkhttpManager {
    private const val TAG = "OkhttpManager"

    // 基础主机名，方便稍后动态替换 Referer
    private const val BASE_HOST = "ww66.taiee.lol"

    /**
     * 构建一个模拟真实浏览器行为的 OkHttpClient
     */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(BrowserSimulationInterceptor())
            // 💡(可选) 如果目标站点的 HTTPS 证书过期或不规范导致报错，解开下面两行的注释来忽略证书错误
            // .sslSocketFactory(createInsecureSslSocketFactory(), insecureTrustManager)
            // .hostnameVerifier { _, _ -> true }
            .build()
    }

    /**
     * 核心拦截器：注入强大的浏览器伪装头
     */
    class BrowserSimulationInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()

            Log.d(TAG, "Requesting URL: ${originalRequest.url}")

            val requestBuilder = originalRequest.newBuilder()
                // 1. 设置极强的主流浏览器 User-Agent (这里使用的是较新的 Chrome Windows 版)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                )
                // 2. Accept 头：告诉服务器我们要的是什么（包含 html, 图片和一些新型格式）
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
                )
                // 3. 语言偏好设置，装成中文用户
                .header(
                    "Accept-Language",
                    "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7"
                )
                // 4. 跨域与防盗链的核心头
                // 根据要求，当前主要针对信息页的域名进行防盗链伪装
                .header("Referer", "https://$BASE_HOST/index.php")
                .header("Origin", "https://$BASE_HOST")

                // 5. 告诉服务器保持连接
                .header("Connection", "keep-alive")
                
                // 6. 某些防护系统需要的缓存控制头
                .header("Cache-Control", "max-age=0")
                .header("Upgrade-Insecure-Requests", "1")
                
                // 7. 设置获取数据相关请求标识
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Sec-Fetch-User", "?1")

            return chain.proceed(requestBuilder.build())
        }
    }


    // ==========================================
    // 危险操作：忽略所有 SSL 证书的工具方法 (备用)
    // ==========================================

    /**
     * 创建一个不信任任何证书的 TrustManager
     */
    private val insecureTrustManager: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    /**
     * 创建一个忽略证书校验的 SSLSocketFactory
     */
    private fun createInsecureSslSocketFactory(): javax.net.ssl.SSLSocketFactory {
        return try {
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, arrayOf<TrustManager>(insecureTrustManager), SecureRandom())
            sslContext.socketFactory
        } catch (e: Exception) {
            throw RuntimeException("Failed to create insecure SSL SocketFactory", e)
        }
    }
}