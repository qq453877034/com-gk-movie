// 文件路径: com/gk/movie/Utils/okhttpUtils/OkhttpManager.kt
package com.gk.movie.Utils.okhttpUtils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.webkit.CookieManager
import okhttp3.*
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object OkhttpManager {
    private const val TAG = "OkhttpManager"
    var appContext: Context? = null 

    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    const val REFERER = "https://www.jkan.app/"

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ==========================================
    // 🛡️ 黑科技 1：寻找真实的物理网络（剔除 VPN 虚拟网卡）
    // ==========================================
    private fun getPhysicalNetwork(): Network? {
        val ctx = appContext ?: return null
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val activeCaps = cm.getNetworkCapabilities(activeNetwork)

        if (activeCaps != null && !activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return null
        }
        
        val networks = cm.allNetworks
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network)
            if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return network
            }
        }
        return null
    }

    private val vpnBypassSocketFactory: SocketFactory = object : SocketFactory() {
        override fun createSocket(): Socket {
            val network = getPhysicalNetwork()
            val socket = Socket()
            network?.bindSocket(socket)
            return socket
        }
        override fun createSocket(host: String?, port: Int): Socket {
            val network = getPhysicalNetwork()
            val socket = Socket(host, port)
            network?.bindSocket(socket)
            return socket
        }
        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
            val network = getPhysicalNetwork()
            val socket = Socket(host, port, localHost, localPort)
            network?.bindSocket(socket)
            return socket
        }
        override fun createSocket(host: InetAddress?, port: Int): Socket {
            val network = getPhysicalNetwork()
            val socket = Socket(host, port)
            network?.bindSocket(socket)
            return socket
        }
        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
            val network = getPhysicalNetwork()
            val socket = Socket(address, port, localAddress, localPort)
            network?.bindSocket(socket)
            return socket
        }
    }

    private val vpnBypassDns = Dns { hostname ->
        val network = getPhysicalNetwork()
        if (network != null) {
            network.getAllByName(hostname).toList()
        } else {
            Dns.SYSTEM.lookup(hostname)
        }
    }

    // ==========================================
    // 🛡️ 黑科技 2：无视一切 SSL 证书异常（绕过 VPN 必备）
    // ==========================================
    private val insecureTrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private fun createInsecureSslSocketFactory(): javax.net.ssl.SSLSocketFactory {
        return try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(insecureTrustManager), SecureRandom())
            sslContext.socketFactory
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    // 基于 WebView 的 CookieManager 实现的中央 Cookie 共享库
    private val webkitCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val cookieManager = CookieManager.getInstance()
            for (cookie in cookies) {
                cookieManager.setCookie(url.toString(), cookie.toString())
            }
            cookieManager.flush()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val cookieManager = CookieManager.getInstance()
            val cookiesStr = cookieManager.getCookie(url.toString())
            if (!cookiesStr.isNullOrEmpty()) {
                val cookies = mutableListOf<Cookie>()
                val cookieArray = cookiesStr.split(";").toTypedArray()
                for (cookieStr in cookieArray) {
                    Cookie.parse(url, cookieStr.trim())?.let { cookies.add(it) }
                }
                return cookies
            }
            return emptyList()
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            // 解决偶尔出现的 HTTP/2 握手问题
            .protocols(listOf(Protocol.HTTP_1_1)) 
            .addInterceptor(BrowserSimulationInterceptor())
            .cookieJar(webkitCookieJar) 
            
            // ★ 满血复活：强制绕开 VPN，走物理网卡直连国内
            .socketFactory(vpnBypassSocketFactory)
            .dns(vpnBypassDns)
            
            // ★ 满血复活：无视 SSL 错误，为物理直连保驾护航
            .sslSocketFactory(createInsecureSslSocketFactory(), insecureTrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    class BrowserSimulationInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", REFERER)
                .header("Connection", "keep-alive")
            
            return chain.proceed(requestBuilder.build())
        }
    }
}