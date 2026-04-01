// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/Util/VideoSniffer.kt
package com.gk.movie.Utils.Media3Play.Util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.gk.movie.Utils.okhttpUtils.OkhttpManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest

// ★ 数据包裹类：将播放链接和片头片尾的时间一起返回给 UI
data class SniffResult(
    val playUrl: String,
    val headEnd: Int,
    val tailStart: Int
)

object VideoSniffer {
    private const val TAG = "VideoSniffer"

    // ==========================================
    // ★ 内部加密签名算法
    // ==========================================
    private fun md5(string: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(string.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha1(string: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(string.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateSign(params: Map<String, String>, timestamp: Long): String {
        val signKey = "cb808529bae6b6be45ecfab29a4889bc"
        val sortedParamsString = params.toSortedMap().map { "${it.key}=${it.value}" }.joinToString("&")
        val baseString = "$sortedParamsString&key=$signKey&t=$timestamp"
        return sha1(md5(baseString))
    }

    // ★ 核心修复：重新使用 SharedPreferences 本地持久化 UUID，实现千人千面，防风控防拉黑！
    private fun getDeviceId(context: Context): String {
        val sp = context.getSharedPreferences("app_security_config", Context.MODE_PRIVATE)
        var id = sp.getString("client_uuid", "")
        if (id.isNullOrEmpty()) {
            id = java.util.UUID.randomUUID().toString()
            sp.edit().putString("client_uuid", id).apply()
        }
        return id
    }

    // ==========================================
    // ★ 核心方法：纯血 API 智能画质秒解引擎
    // ==========================================
    suspend fun sniffVideoUrl(context: Context, fullUrl: String): SniffResult? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "⚡ 启动纯接口秒解引擎: $fullUrl")

                // 1. 智能正则：从原始播放页 URL 中提取 id 和 nid
                val regex = """.*/vod/play/(\d+)/sid/(\d+)""".toRegex()
                val matchResult = regex.find(fullUrl)
                if (matchResult == null) {
                    Log.e(TAG, "❌ 无法从 URL 提取 id 和 nid: $fullUrl")
                    return@withContext null
                }

                val id = matchResult.groupValues[1]
                val nid = matchResult.groupValues[2]

                // 2. 准备 API 请求参数
                val apiUrl = "https://hellociqryx6e.com/api/mw-movie/anonymous/v2/video/episode/url"
                val params = mapOf(
                    "clientType" to "1",
                    "id" to id,
                    "nid" to nid
                )

                // 拼接完整的 URL
                val uriBuilder = Uri.parse(apiUrl).buildUpon()
                params.forEach { (k, v) -> uriBuilder.appendQueryParameter(k, v) }
                val finalUrl = uriBuilder.build().toString()

                // 计算签名
                val currentTimestamp = System.currentTimeMillis()
                val dynamicSign = generateSign(params, currentTimestamp)

                // 构建请求头，动态获取本地持久化的 DeviceId
                val request = Request.Builder()
                    .url(finalUrl)
                    .header("client-type", "1")
                    .header("deviceId", getDeviceId(context))
                    .header("t", currentTimestamp.toString())
                    .header("sign", dynamicSign)
                    .build()

                // 3. 发送闪电网络请求
                val response = OkhttpManager.client.newCall(request).execute()
                val responseStr = response.body.string() 
                Log.d(TAG, "📦 API 原始返回: $responseStr")

                // 4. 解析 JSON 并提取所需数据
                val root = JSONObject(responseStr)
                if (root.optInt("code") == 200) {
                    val dataObj = root.optJSONObject("data")
                    val listArray = dataObj?.optJSONArray("list")
                    
                    if (listArray != null && listArray.length() > 0) {
                        
                        var targetUrl = ""
                        var bestResolution = 0

                        // 遍历所有线路，寻找最高清的线路 (1080 > 720 > 480)
                        for (i in 0 until listArray.length()) {
                            val item = listArray.getJSONObject(i)
                            val resolution = item.optInt("resolution", 0)

                            if (resolution > bestResolution) {
                                val url = item.optString("url", "")
                                if (url.isNotBlank()) {
                                    bestResolution = resolution
                                    targetUrl = url
                                }
                            }
                        }

                        // 安全兜底逻辑：如果所有节点都没有 resolution，则强制拿第一条
                        if (targetUrl.isBlank()) {
                            targetUrl = listArray.getJSONObject(0).optString("url", "")
                        }

                        // 提取片头片尾跳过配置
                        val headEnd = dataObj.optInt("headEnd", 0)
                        val tailStart = dataObj.optInt("tailStart", 0)

                        Log.e(TAG, "🎯 API 纯血秒解成功! 提取画质: ${bestResolution}p, 链接: $targetUrl, 片头: ${headEnd}s, 片尾: ${tailStart}s")
                        
                        // 打包返回所有分析出的关键数据
                        return@withContext SniffResult(targetUrl, headEnd, tailStart)
                    } else {
                        Log.e(TAG, "❌ 找不到播放线路列表")
                        return@withContext null
                    }
                } else {
                    Log.e(TAG, "❌ 接口返回错误: ${root.optString("msg")}")
                    return@withContext null
                }

            } catch (e: Exception) {
                Log.e(TAG, "JSON 解析失败或网络请求异常", e)
                return@withContext null
            }
        }
    }
}