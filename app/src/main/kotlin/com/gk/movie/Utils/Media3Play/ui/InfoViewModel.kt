// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Media3Play/ui/InfoViewModel.kt
package com.gk.movie.Utils.Media3Play.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gk.movie.Utils.okhttpUtils.OkhttpManager
//import com.gk.movie.Utils.NativeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.net.URLDecoder

import uniffi.m3u8_parser.extractMovieDetail

// ★ 接收 Rust 抛出的 JSON 数据结构
@Serializable
data class NativeMovieDetail(
    val cover_url: String = "",
    val episodes: List<NativeEpisode> = emptyList()
)

@Serializable
data class NativeEpisode(
    val name: String,
    val url: String
)

class InfoViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    private val isDebug = false

    fun fetchMovieDetail(vodId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                withContext(Dispatchers.IO) {
                    val document: Document
                    var rawHtml = ""

                    if (isDebug) {
                        Log.d("InfoViewModel", "🛠️ 进入本地调试模式")
                        delay(300) 
                        val localFile = File("/storage/emulated/0/ALua布局助手/极光影视网页源码/详情页.html")
                        if (localFile.exists()) {
                            rawHtml = localFile.readText(Charsets.UTF_8)
                        } else {
                            throw Exception("本地调试文件不存在: ${localFile.absolutePath}")
                        }
                    } else {
                        val url = "${OkhttpManager.REFERER.trimEnd('/')}/detail/$vodId"
                        Log.d("InfoViewModel", "🌐 正在请求详情数据: $url")
                        val request = Request.Builder().url(url).build()
                        OkhttpManager.client.newCall(request).execute().use { response ->
                            rawHtml = response.body.string()     //response.body?.string() ?: ""
                        }
                    }
                    
                    // =========================================================
                    // ★ 性能革命：调用 Rust 一次性提取海报和剧集列表
                    // =========================================================
                    val startTime = System.currentTimeMillis()
                    
                   // val jsonStr = NativeParser.extractMovieDetail(rawHtml, vodId)
                    
                    val jsonStr = extractMovieDetail(rawHtml, vodId)
                    val jsonParser = Json { ignoreUnknownKeys = true }
                    val nativeDetail = jsonParser.decodeFromString<NativeMovieDetail>(jsonStr)
                    
                    val costTime = System.currentTimeMillis() - startTime
                    Log.d("InfoViewModel", "🚀 Rust 底层瞬间解析完毕！海报状态: ${nativeDetail.cover_url.isNotEmpty()}，获取了 ${nativeDetail.episodes.size} 集，耗时: ${costTime}ms")

                    document = Jsoup.parse(rawHtml)

                    val title = document.select("h1.title").first()?.text()?.trim() ?: "未知影片"
                    var coverUrl = nativeDetail.cover_url

                    // 兜底 DOM 提取海报
                    if (coverUrl.isEmpty()) {
                        coverUrl = document.select("div[class*=detail__CardImg] img").attr("src").ifEmpty {
                            document.select("img[alt=$title]").attr("src")
                        }
                        if (coverUrl.contains("/_next/image?url=")) {
                            val encodedUrl = coverUrl.substringAfter("url=").substringBefore("&")
                            coverUrl = runCatching { URLDecoder.decode(encodedUrl, "UTF-8") }.getOrDefault(coverUrl)
                        }
                    }

                    val directorNodes = document.select("div.director:contains(导演:)")
                    val director = directorNodes.select("a").eachText().joinToString("/")
                    val actorNodes = document.select("div.director:contains(主演:)")
                    val actors = actorNodes.select("a").eachText().joinToString("/")
                    val types = document.select(".tags a.tag").eachText().joinToString(" / ")
                    val score = document.select(".score").first()?.text()?.trim() ?: "暂无"
                    val introNode = document.select(".wrapper_more_text").first()
                    introNode?.select("label")?.remove() 
                    val description = introNode?.text()?.trim() ?: "暂无简介"

                    val playlists = mutableListOf<PlayList>()
                    
                    // 1. 标准 DOM 提取剧集 (优先级最高)
                    val episodeContainers = document.select("div[class*=main-list-sections__BodyArea]")
                    if (episodeContainers.isNotEmpty()) {
                        var sourceIndex = 1
                        for (container in episodeContainers) {
                            val episodes = mutableListOf<Episode>()
                            val links = container.select("a")
                            for (link in links) {
                                val name = link.text().trim()
                                    .ifEmpty { link.attr("title").trim() }
                                    .ifEmpty { "第${episodes.size + 1}集" }
                                val href = link.attr("href")
                                if (href.isNotEmpty() && episodes.none { it.url == href }) {
                                    episodes.add(Episode(name = name, url = href))
                                }
                            }
                            if (episodes.isNotEmpty()) {
                                val sourceNameNode = container.previousElementSibling()?.select(".player_name")?.first()
                                val sourceName = sourceNameNode?.text()?.trim() ?: "播放线路 $sourceIndex"
                                playlists.add(PlayList(sourceName = sourceName, episodes = episodes))
                                sourceIndex++
                            }
                        }
                    }

                    // 2. 如果 DOM 没有拿到，使用 Rust 挖出的底层 JSON 剧集
                    if (playlists.isEmpty() && nativeDetail.episodes.isNotEmpty()) {
                        val rustEpisodes = nativeDetail.episodes.map { Episode(name = it.name, url = it.url) }
                        playlists.add(PlayList(sourceName = "GK解析", episodes = rustEpisodes))
                    }

                    // 3. 最终兜底提取
                    if (playlists.isEmpty()) {
                        val allPlayLinks = document.select("a[href*=/vod/play/]")
                        val episodes = mutableListOf<Episode>()
                        for (link in allPlayLinks) {
                            if (link.select(".bofang-icon").isNotEmpty()) continue 
                            val name = link.text().trim().ifEmpty { "播放" }
                            val href = link.attr("href")
                            if (href.isNotEmpty() && episodes.none { it.url == href }) {
                                episodes.add(Episode(name = name, url = href))
                            }
                        }
                        if (episodes.isNotEmpty()) {
                            playlists.add(PlayList(sourceName = "极光线路", episodes = episodes))
                        }
                    }

                    Log.d("InfoViewModel", "✅ 详情构建完成: $title, 包含 ${playlists.size} 条线路")

                    val movieInfo = MovieInfo(
                        title = title,
                        coverUrl = coverUrl,
                        director = director.ifEmpty { "未知" },
                        actors = actors.ifEmpty { "未知" },
                        types = types.ifEmpty { "未知" },
                        score = score,
                        scoreCount = "豆瓣评分",
                        description = description.ifEmpty { "暂无简介" },
                        playLists = playlists
                    )

                    _uiState.value = UiState.Success(movieInfo)
                }

            } catch (e: Exception) {
                Log.e("InfoViewModel", "❌ 详情页解析异常", e)
                _uiState.value = UiState.Error("解析异常或网络错误: ${e.message}")
            }
        }
    }
}