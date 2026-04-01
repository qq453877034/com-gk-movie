// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/okhttpUtils/OkhttpManager.kt
package com.gk.movie.Utils.okhttpUtils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import okhttp3.*
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.io.File

object OkhttpManager {
    private const val TAG = "OkhttpManager"
    var appContext: Context? = null

    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"
    const val REFERER = "https://hellociqryx6e.com/"

    @Volatile
    var physicalNetwork: Network? = null

    // ★ 全局内存 Cookie 存储器
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    fun init(context: Context) {
        appContext = context.applicationContext
        startPhysicalNetworkBypassMonitor()
    }
    
    
      // =======================================================
    // 🛠️ 新增神器：网页源码/API数据 强制物理保存工具
    // 调用此方法，可将任何字符串保存到手机本地目录，方便随时查看分析！
    // =======================================================
    fun dumpSourceCode(fileName: String, content: String) {
        try {
            appContext?.let { ctx ->
                // 保存在 Android/data/com.gk.movie/files/DebugSources/ 目录下
                val dir = File(ctx.getExternalFilesDir(null), "DebugSources")
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                val file = File(dir, safeFileName)
                file.writeText(content, Charsets.UTF_8)
                Log.e(TAG, "📁 源码已成功保存至本地物理文件: ${file.absolutePath} (大小: ${content.length / 1024} KB)")
            } ?: Log.e(TAG, "❌ 保存失败：appContext 为空，请确保已调用 OkhttpManager.init()")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 写入本地物理文件失败", e)
        }
    }


    @Suppress("DEPRECATION")
    private fun startPhysicalNetworkBypassMonitor() {
        val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        val networks = cm.allNetworks
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network)
            if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) {
                physicalNetwork = network
                cm.bindProcessToNetwork(network)
                Log.d(TAG, "🚀 [底层穿透] 初始化找到物理网卡，已强行绑定进程，全面避开 VPN！")
                break
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    Log.d(TAG, "🚀 [动态反制] 发现新的物理网卡: $network，执行进程重绑并避开 VPN！")
                    physicalNetwork = network
                    cm.bindProcessToNetwork(network)
                }
            }
            override fun onLost(network: Network) {
                if (network == physicalNetwork) {
                    physicalNetwork = null
                    cm.bindProcessToNetwork(null)
                    Log.w(TAG, "⚠️ [警告] 物理网络丢失！")
                }
            }
        })
    }

    fun isBypassFailed(): Boolean {
        val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)
        val isVpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        
        if (isVpnActive && physicalNetwork == null) {
            return true
        }
        return false
    }

    private val insecureTrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private fun createInsecureSslSocketFactory() = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(insecureTrustManager), SecureRandom())
    }.socketFactory

    val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .sslSocketFactory(createInsecureSslSocketFactory(), insecureTrustManager)
            .hostnameVerifier { _, _ -> true }
            // ★ 让 OkHttp 全局自动接管 Cookie 管理
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val host = url.host
                    val existing = cookieStore[host] ?: mutableListOf()
                    for (newCookie in cookies) {
                        existing.removeAll { it.name == newCookie.name }
                        existing.add(newCookie)
                    }
                    cookieStore[host] = existing
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return cookieStore[url.host] ?: emptyList()
                }
            })

        builder.dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return physicalNetwork?.getAllByName(hostname)?.toList() 
                    ?: Dns.SYSTEM.lookup(hostname)
            }
        })

        builder.socketFactory(object : SocketFactory() {
            override fun createSocket(): Socket = physicalNetwork?.socketFactory?.createSocket() ?: SocketFactory.getDefault().createSocket()
            override fun createSocket(host: String?, port: Int): Socket = physicalNetwork?.socketFactory?.createSocket(host, port) ?: SocketFactory.getDefault().createSocket(host, port)
            override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = physicalNetwork?.socketFactory?.createSocket(host, port, localHost, localPort) ?: SocketFactory.getDefault().createSocket(host, port, localHost, localPort)
            override fun createSocket(host: InetAddress?, port: Int): Socket = physicalNetwork?.socketFactory?.createSocket(host, port) ?: SocketFactory.getDefault().createSocket(host, port)
            override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = physicalNetwork?.socketFactory?.createSocket(address, port, localAddress, localPort) ?: SocketFactory.getDefault().createSocket(address, port, localAddress, localPort)
        })
        
        builder.addInterceptor(StrictVpnBlockInterceptor())
        builder.addInterceptor(BrowserSimulationInterceptor())
        
        builder.build()
    }

    class StrictVpnBlockInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            if (isBypassFailed()) {
                Log.e(TAG, "🛑 [高危阻断] 穿透失败！检测到强制接管且无可用物理网卡，拒绝发起任何请求！")
                throw IOException("Access Denied: 主动避开 VPN 失败，坚决不使用虚拟通道进行网络请求。")
            }
            return chain.proceed(chain.request())
        }
    }

    class BrowserSimulationInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val urlString = originalRequest.url.toString().lowercase()
            
            val requestBuilder = originalRequest.newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Upgrade-Insecure-Requests", "1")
                .header("Referer", REFERER)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                .removeHeader("Connection") 

            when {
                // ★ 新增：针对 JSON API 的完美伪装，防止被防火墙识别为脚本抓取
                urlString.contains("/api/") -> {
                    requestBuilder
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Sec-Fetch-Dest", "empty")
                        .header("Sec-Fetch-Mode", "cors")
                        .header("Sec-Fetch-Site", "same-origin")
                }
                urlString.endsWith(".jpg") || urlString.endsWith(".png") || 
                urlString.endsWith(".webp") || urlString.endsWith(".jpeg") -> {
                    requestBuilder
                        .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        .header("Sec-Fetch-Dest", "image")
                        .header("Sec-Fetch-Mode", "no-cors")
                        .header("Sec-Fetch-Site", "cross-site")
                }
                urlString.contains(".m3u8") || urlString.contains(".ts") || urlString.contains(".mp4") -> {
                    requestBuilder
                        .header("Accept", "*/*")
                        .header("Sec-Fetch-Dest", "video")
                        .header("Sec-Fetch-Mode", "cors")
                        .header("Sec-Fetch-Site", "cross-site")
                        .header("Range", "bytes=0-") 
                }
                else -> {
                    requestBuilder
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                        .header("Sec-Fetch-Dest", "document")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Site", "same-origin")
                        .header("Sec-Fetch-User", "?1")
                }
            }
            return chain.proceed(requestBuilder.build())
        }
    }
}