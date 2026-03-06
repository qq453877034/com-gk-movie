// 文件路径: com/gk/movie/Utils/Media3Play/ui/InfoViewModel.kt
package com.gk.movie.Utils.Media3Play.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gk.movie.Utils.okhttpUtils.OkhttpManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import kotlin.math.min

class InfoViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    // ★ 调试开关：true = 读取本地文件，false = 发起真实网络请求
    private val isDebugLocalMode = true
    // ★ 本地测试文件的绝对路径
    private val localFilePath = "/storage/emulated/0/Alarms/信息页.html"

    init {
        // 传入新站点的测试地址
        fetchMovieInfo("https://www.jkan.app/video/261653.html")
    }

    private fun fetchMovieInfo(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val html: String

                if (isDebugLocalMode) {
                    // ================= 调试模式：读取本地 HTML =================
                    val file = File(localFilePath)
                    if (!file.exists()) {
                        _uiState.value = UiState.Error("本地测试文件不存在，请检查路径:\n$localFilePath")
                        return@launch
                    }
                    html = file.readText(Charsets.UTF_8)
                    Log.d("InfoViewModel", "成功加载本地测试 HTML: ${file.length()} bytes")
                    
                    // 模拟一下网络延迟（可选，如果你想测试骨架屏加载动画，可以取消下面这行的注释）
                    // kotlinx.coroutines.delay(1000) 
                } else {
                    // ================= 生产模式：发起网络请求 =================
                    val request = Request.Builder().url(url).build()
                    val response = OkhttpManager.client.newCall(request).execute()
                    
                    if (!response.isSuccessful) {
                        _uiState.value = UiState.Error("网络请求失败: ${response.code}")
                        return@launch
                    }
                    html = response.body?.string() ?: ""
                }

                val doc = Jsoup.parse(html)

                // 1. 优先使用 head 里的 meta 标签精准获取信息，防止出错
                val title = doc.selectFirst("meta[property=og:title]")?.attr("content") ?: "未知标题"
                val coverUrl = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
                
                val directorMeta = doc.selectFirst("meta[property=og:video:director]")?.attr("content")
                val director = if (directorMeta.isNullOrBlank()) "未知" else directorMeta
                
                val actorsMeta = doc.selectFirst("meta[property=og:video:actor]")?.attr("content")
                val actors = if (actorsMeta.isNullOrBlank()) "未知" else actorsMeta
                
                val types = doc.selectFirst("meta[property=og:video:class]")?.attr("content") ?: "未知类型"
                
                // 获取评分和简介
                val score = doc.selectFirst(".douban_score i")?.text()?.replace("评分", "")?.trim() ?: "暂无"
                val scoreCount = "豆瓣/TMDB"
                val description = doc.selectFirst("meta[name=description]")?.attr("content") ?: "暂无简介"

                // 2. 解析播放列表 (剧集)
                val playlists = mutableListOf<PlayList>()
                // 获取所有的线路 Tab (如：1080 LFF, 1080 LZ)
                val sourceNodes = doc.select("#NumTab a")
                // 获取对应的剧集列表容器
                val listBoxNodes = doc.select(".play_list_box")

                val count = min(sourceNodes.size, listBoxNodes.size)
                for (i in 0 until count) {
                    // 线路名：优先拿 alt 属性，拿不到再拿内部文本
                    val sourceName = sourceNodes[i].attr("alt").trim().takeIf { it.isNotEmpty() } 
                        ?: sourceNodes[i].text().replace("\u00A0", "").trim()
                    
                    val episodes = mutableListOf<Episode>()
                    
                    // 获取当前线路下完整的剧集 a 标签
                    val links = listBoxNodes[i].select(".playlist_full ul.content_playlist li a")
                    for (link in links) {
                        val name = link.text().trim()
                        val href = link.attr("href")
                        
                        // ★ 核心过滤：过滤掉“APP播放”这种广告链接
                        if (href.isNotBlank() && !name.contains("APP") && !href.contains("gkan.net")) {
                            // 补全相对路径
                            val fullUrl = if (href.startsWith("http")) href else "https://www.jkan.app$href"
                            episodes.add(Episode(name = name, url = fullUrl))
                        }
                    }
                    
                    // 只有包含有效剧集的线路才被添加
                    if (episodes.isNotEmpty()) {
                        playlists.add(PlayList(sourceName, episodes))
                    }
                }

                val movieInfo = MovieInfo(
                    title = title,
                    coverUrl = coverUrl,
                    director = director,
                    actors = actors,
                    types = types,
                    score = score,
                    scoreCount = scoreCount,
                    description = description,
                    playLists = playlists
                )

                _uiState.value = UiState.Success(movieInfo)

            } catch (e: Exception) {
                Log.e("InfoViewModel", "解析异常", e)
                _uiState.value = UiState.Error("解析异常: ${e.message}")
            }
        }
    }
}