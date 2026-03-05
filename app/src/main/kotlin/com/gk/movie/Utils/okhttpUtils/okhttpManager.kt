// 文件路径: com/gk/movie/Utils/okhttpUtils/OkhttpManager.kt
package com.gk.movie.Utils.okhttpUtils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
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
    private var appContext: Context? = null

    // 初始化 Context，用于获取系统网络服务
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * 核心黑科技：寻找真实的物理网络（剔除 VPN 虚拟网卡）
     */
    private fun getPhysicalNetwork(): Network? {
        val ctx = appContext ?: return null
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val activeCaps = cm.getNetworkCapabilities(activeNetwork)

        // 如果当前网络根本不是 VPN，那就正常返回 null，走系统默认即可
        if (activeCaps != null && !activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return null
        }

        // 如果当前连着 VPN，我们就遍历所有网卡，揪出那个真实的、能上网的物理网卡 (Wi-Fi / 蜂窝)
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network)
            if (caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return network
            }
        }
        return null
    }

    // 自定义 Socket 工厂，强行绑定到物理网卡，让流量不进 VPN 隧道
    private val vpnBypassSocketFactory = object : SocketFactory() {
        override fun createSocket(): Socket {
            val network = getPhysicalNetwork()
            return network?.socketFactory?.createSocket() ?: getDefault().createSocket()
        }
        override fun createSocket(host: String, port: Int): Socket {
            val network = getPhysicalNetwork()
            return network?.socketFactory?.createSocket(host, port) ?: getDefault().createSocket(host, port)
        }
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
            val network = getPhysicalNetwork()
            return network?.socketFactory?.createSocket(host, port, localHost, localPort) ?: getDefault().createSocket(host, port, localHost, localPort)
        }
        override fun createSocket(host: InetAddress, port: Int): Socket {
            val network = getPhysicalNetwork()
            return network?.socketFactory?.createSocket(host, port) ?: getDefault().createSocket(host, port)
        }
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
            val network = getPhysicalNetwork()
            return network?.socketFactory?.createSocket(address, port, localAddress, localPort) ?: getDefault().createSocket(address, port, localAddress, localPort)
        }
    }

    // 自定义 DNS 解析，防止被 VPN 劫持 DNS
    private val vpnBypassDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val network = getPhysicalNetwork()
            return if (network != null) {
                network.getAllByName(hostname).toList()
            } else {
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    private val insecureTrustManager: X509TrustManager = object : X509TrustManager {
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

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(BrowserSimulationInterceptor())
            // ★ 核心注入：强制接管 Socket 生成和 DNS 解析，绕开 VPN
            .socketFactory(vpnBypassSocketFactory)
            .dns(vpnBypassDns)
            .sslSocketFactory(createInsecureSslSocketFactory(), insecureTrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    class BrowserSimulationInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()

            val requestBuilder = originalRequest.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")

            return chain.proceed(requestBuilder.build())
        }
    }
}