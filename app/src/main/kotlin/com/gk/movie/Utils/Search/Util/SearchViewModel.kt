// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Search/Util/SearchViewModel.kt
package com.gk.movie.Utils.Search.Util

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gk.movie.Utils.Category.Util.PageItem
import com.gk.movie.Utils.okhttpUtils.OkhttpManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import uniffi.m3u8_parser.extractSearchResults
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

// =========================================================
// ★ 完美破解：前端 JS 嵌套双重加密算法 SHA1(MD5(参数))
// =========================================================
fun md5(string: String): String {
    val bytes = MessageDigest.getInstance("MD5").digest(string.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

fun sha1(string: String): String {
    val bytes = MessageDigest.getInstance("SHA-1").digest(string.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

// 终极签名生成器
fun generateSign(params: Map<String, String>, timestamp: Long): String {
    val signKey = "cb808529bae6b6be45ecfab29a4889bc"
    // 1. 使用原始中文参数按照 A-Z 进行字典序排序拼接
    val sortedParamsString = params.toSortedMap().map { "${it.key}=${it.value}" }.joinToString("&")
    val baseString = "$sortedParamsString&key=$signKey&t=$timestamp"
    
    // 2. 核心机密：先对拼接串进行 MD5，再对结果进行 SHA-1
    return sha1(md5(baseString))
}

@Serializable
data class RustCmsVod(
    val vod_id: Int = 0, val vod_name: String = "", val vod_pic: String = "",
    val vod_remarks: String = "", val vod_version: String = "", val vod_actor: String = ""
)

data class SearchMovie(
    val vodId: Int, val title: String, val coverUrl: String, 
    val score: String, val remark: String, val subTitle: String
)

data class EngineItem(val id: String, val name: String, var count: Int = 0)
data class SearchPageData(val movies: List<SearchMovie>, val pageItems: List<PageItem>, val pageTips: String)

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(val data: SearchPageData) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

private val globalJsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

class SearchViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    private val _engines = MutableStateFlow(listOf(
        EngineItem("all", "全部", 0), EngineItem("jiguang", "GK引擎", 0),
    ))
    val engines: StateFlow<List<EngineItem>> = _engines
    
    var selectedEngineId = MutableStateFlow("all")
    var currentKeyword: String = ""
    var currentPage: Int = 1
    private val pageSize = 24

    // ★ 完美模拟浏览器 LocalStorage 的持久化 deviceId 生成器
    private val deviceId: String by lazy {
        val context = OkhttpManager.appContext
        if (context != null) {
            val sp = context.getSharedPreferences("app_security_config", Context.MODE_PRIVATE)
            var id = sp.getString("client_uuid", "")
            if (id.isNullOrEmpty()) {
                // 如果是首次运行，生成一个全新的 UUID 并永久保存
                id = UUID.randomUUID().toString()
                sp.edit().putString("client_uuid", id).apply()
            }
            id!!
        } else {
            // 兜底方案：如果获取不到 Context，至少保证每次冷启动是一个新设备
            UUID.randomUUID().toString()
        }
    }

    fun search(keyword: String, page: Int = 1) {
        if (keyword.isBlank()) return
        currentKeyword = keyword
        currentPage = page
        
        Log.d("SearchDebug", "===================================================")
        Log.d("SearchDebug", "🎬 开始执行搜索/翻页 -> 关键字: [$keyword], 目标页码: [$page]")

        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            try {
                val baseUrl = OkhttpManager.REFERER.trimEnd('/')
                val encodedKeyword = URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")
                
                val movies = mutableListOf<SearchMovie>()
                var totalCount = 0
                var jsonResponse = ""

                if (currentPage == 1) {
                    Log.d("SearchDebug", "🌐 [第1页] 正在请求 SSR HTML...")
                    val searchUrl = "$baseUrl/vod/search/$encodedKeyword"
                    val rawHtml = withContext(Dispatchers.IO) {
                        val request = Request.Builder().url(searchUrl).build()
                        OkhttpManager.client.newCall(request).execute().use { response -> 
                            response.body?.string() ?: "" 
                        }
                    }

                    try {
                        val jsonStr = extractSearchResults(rawHtml, baseUrl)
                        val rustList = globalJsonParser.decodeFromString<List<RustCmsVod>>(jsonStr)
                        Log.d("SearchDebug", "✅ [第1页] Rust 成功解析到 ${rustList.size} 条数据")
                        rustList.forEach {
                            movies.add(SearchMovie(
                                vodId = it.vod_id, title = it.vod_name, coverUrl = it.vod_pic,
                                score = it.vod_version, remark = it.vod_remarks, subTitle = it.vod_actor
                            ))
                        }
                    } catch (e: Exception) { 
                        Log.e("SearchViewModel", "Rust 解析异常", e) 
                    }

                    val countMatch = Regex("""共\D*?(\d+)\D*?个""").find(rawHtml)
                    totalCount = if (countMatch != null) countMatch.groupValues[1].toIntOrNull() ?: movies.size else movies.size
                    Log.d("SearchDebug", "📊 [第1页] 正则提取影片总数: $totalCount")

                } else {
                    Log.d("SearchDebug", "📡 [第${currentPage}页] 准备请求 JSON API...")
                    val sourceCode = if (selectedEngineId.value == "jiguang") "2" else "1"
                    val currentTimestamp = System.currentTimeMillis()
                    
                    val queryParams = mapOf(
                        "keyword" to keyword, 
                        "pageNum" to currentPage.toString(),
                        "pageSize" to pageSize.toString(),
                        "sourceCode" to sourceCode
                    )
                    
                    val dynamicSign = generateSign(queryParams, currentTimestamp)
                    val apiUrl = "$baseUrl/api/mw-movie/anonymous/video/searchByWord?keyword=$encodedKeyword&pageNum=$currentPage&pageSize=$pageSize&sourceCode=$sourceCode"
                    
                    val host = java.net.URI(baseUrl).host 
                    val dynamicReferer = "$baseUrl/vod/search/$encodedKeyword"
                    
                    // 这个 51La 追踪 Cookie 也是随机的，我们让它每次请求都伪装得天衣无缝
                    val fakeUuid = UUID.randomUUID().toString()
                    val jsFakeCookie = "__51vcke__3I14AJXLSVMADZTk=$fakeUuid; __51vuft__3I14AJXLSVMADZTk=$currentTimestamp; __51uvsct__3I14AJXLSVMADZTk=1"

                    jsonResponse = withContext(Dispatchers.IO) {
                        val request = Request.Builder()
                            .url(apiUrl)
                            .header("Host", host ?: "")
                            .header("Referer", dynamicReferer)
                            .header("client-type", "1")
                            .header("deviceId", deviceId) // 注入从本地读取到的持久化设备 ID
                            .header("t", currentTimestamp.toString())
                            .header("sign", dynamicSign)
                            .header("Cookie", jsFakeCookie)
                            .build()

                        OkhttpManager.client.newCall(request).execute().use { response -> 
                            response.body?.string() ?: "" 
                        }
                    }

                    try {
                        if (jsonResponse.trim().startsWith("<")) {
                            _uiState.value = SearchUiState.Error("防火墙拦截，返回了网页代码")
                            return@launch
                        }

                        val root = org.json.JSONObject(jsonResponse)
                        val code = root.optInt("code", 200)
                        
                        if (code == 200) {
                            val data = root.optJSONObject("data")
                            val resultObj = data?.optJSONObject("result") ?: data
                            
                            val list = resultObj?.optJSONArray("list") ?: resultObj?.optJSONArray("records") ?: root.optJSONArray("data")
                            val parsedSize = list?.length() ?: 0
                            Log.d("SearchDebug", "✅ API 成功获取到列表，长度: $parsedSize")
                            
                            if (list != null) {
                                for (i in 0 until list.length()) {
                                    val item = list.getJSONObject(i)
                                    movies.add(SearchMovie(
                                        vodId = item.optInt("vodId", item.optInt("vod_id")),
                                        title = item.optString("vodName", item.optString("vod_name")),
                                        coverUrl = item.optString("vodPic", item.optString("vod_pic")),
                                        score = item.optString("vodVersion", item.optString("vod_version")),
                                        remark = item.optString("vodRemarks", item.optString("vod_remarks")),
                                        subTitle = item.optString("vodActor", item.optString("vod_actor"))
                                    ))
                                }
                            }
                            
                            val apiTotal = resultObj?.optInt("totalCount", 0) ?: resultObj?.optInt("total", 0) ?: 0
                            totalCount = if (apiTotal > 0) apiTotal else (movies.size * currentPage)
                            
                        } else {
                            val msg = root.optString("msg", "未知 API 错误")
                            Log.e("SearchDebug", "❌ 服务端返回错误码: $code, msg: $msg")
                            _uiState.value = SearchUiState.Error("服务端拒接请求: $msg")
                            return@launch
                        }
                    } catch (e: Exception) {
                        Log.e("SearchDebug", "❌ API 解析异常", e)
                        val safeRes = if (jsonResponse.length > 100) jsonResponse.take(100) + "..." else jsonResponse
                        _uiState.value = SearchUiState.Error("数据解析异常: $safeRes")
                        return@launch
                    }
                }

                updateEngineCounts("jiguang", totalCount)

                if (movies.isEmpty()) {
                    Log.w("SearchDebug", "⚠️ 最终 movies 列表为空！")
                    if (currentPage > 1) {
                        val safeRes = if (jsonResponse.length > 150) jsonResponse.take(150) + "..." else jsonResponse
                        _uiState.value = SearchUiState.Error("第${currentPage}页无数据，服务器返回: $safeRes")
                    } else {
                        _uiState.value = SearchUiState.Error("未找到与“${keyword}”相关的影片，或已翻到底部")
                    }
                    return@launch
                }

                val pageItems = mutableListOf<PageItem>()
                var maxPageNum = if (totalCount > 0) (totalCount + pageSize - 1) / pageSize else 1
                if (maxPageNum < 1) maxPageNum = 1

                pageItems.add(PageItem("上一页", (currentPage - 1).coerceAtLeast(1).toString(), isActive = false, isDisabled = currentPage <= 1))
                val startPage = maxOf(1, currentPage - 2)
                val endPage = minOf(maxPageNum, currentPage + 2)
                for (i in startPage..endPage) {
                    pageItems.add(PageItem(i.toString(), i.toString(), isActive = (i == currentPage), isDisabled = false))
                }
                pageItems.add(PageItem("下一页", (currentPage + 1).coerceAtMost(maxPageNum).toString(), isActive = false, isDisabled = currentPage >= maxPageNum))

                Log.d("SearchDebug", "🎉 页面渲染准备完毕，更新UI状态。当前页: $currentPage / 最大页: $maxPageNum")
                _uiState.value = SearchUiState.Success(SearchPageData(movies, pageItems, "第 $currentPage 页 / 共 $maxPageNum 页"))

            } catch (e: Exception) {
                Log.e("SearchViewModel", "搜索网络请求异常", e)
                _uiState.value = SearchUiState.Error("网络请求异常，请检查网络连接")
            }
        }
    }

    private fun updateEngineCounts(sourceId: String, count: Int) {
        val currentList = _engines.value.toMutableList()
        var total = 0
        for (i in currentList.indices) {
            if (currentList[i].id == sourceId) currentList[i] = currentList[i].copy(count = count)
            if (currentList[i].id != "all") total += currentList[i].count
        }
        currentList[0] = currentList[0].copy(count = total)
        _engines.value = currentList
    }

    fun selectEngine(engineId: String) { 
        selectedEngineId.value = engineId 
    }
}