package com.gk.movie.Utils.Media3Play.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// import com.gk.movie.Utils.okhttpUtils.OkhttpManager // 暂且不需要
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
// import okhttp3.Request // 暂且不需要
import org.jsoup.Jsoup
import java.io.File
import kotlin.math.min

class InfoViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    init {
        // 调试期间，这里的 URL 参数暂时不会被用到，但保留方法签名方便以后改回来
        fetchMovieInfo("https://ww66.taiee.lol/index.php/vod/detail/id/529.html")
    }

    private fun fetchMovieInfo(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // ==========================================
                // 使用本地文件进行 UI 调试
                // ==========================================
                val localFile = File("/storage/emulated/0/Alarms/信息页.html")
                if (!localFile.exists()) {
                    _uiState.value = UiState.Error("找不到本地文件，请检查路径: ${localFile.absolutePath}")
                    return@launch
                }
                
                // 读取本地 HTML 内容
                val html = localFile.readText(Charsets.UTF_8)
                
                // 复用原有的解析逻辑
                val movieInfo = parseHtml(html)
                
                _uiState.value = UiState.Success(movieInfo)

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = UiState.Error("解析或读取异常: ${e.message}")
            }
        }
    }

    private fun parseHtml(html: String): MovieInfo {
        val doc = Jsoup.parse(html)

        // 解析基本信息
        val title = doc.selectFirst("h3.slide-info-title")?.text() ?: "未知标题"
        val coverUrl = doc.selectFirst(".detail-pic img")?.attr("data-src") ?: ""
        val score = doc.selectFirst(".fraction")?.text() ?: "N/A"
        
        // 【新增】解析真实的评分次数 (如 "482次评分")
        val scoreCount = doc.selectFirst(".score-title .text-site")?.text() ?: "暂无评分"
        
        val description = doc.selectFirst("#height_limit")?.text() ?: "暂无简介"

        // 解析导演、演员、类型
        var director = "未知"
        var actors = "未知"
        var types = "未知"
        
        doc.select(".slide-info.partition").forEach { element ->
            val text = element.text()
            when {
                text.contains("导演") -> director = element.select("a").joinToString(", ") { it.text() }
                text.contains("演员") -> actors = element.select("a").joinToString(", ") { it.text() }
                text.contains("类型") -> types = element.select("a").joinToString(", ") { it.text() }
            }
        }

        // 解析播放列表
        val playlists = mutableListOf<PlayList>()
        val sourceNodes = doc.select(".anthology-tab a")
        val listBoxNodes = doc.select(".anthology-list-box")
        
        val count = min(sourceNodes.size, listBoxNodes.size)
        for (i in 0 until count) {
            val sourceName = sourceNodes[i].ownText().replace("\u00a0", "").trim()
            val episodes = listBoxNodes[i].select("ul.anthology-list-play li a").map {
                Episode(name = it.text(), url = it.attr("href"))
            }
            playlists.add(PlayList(sourceName, episodes))
        }

        return MovieInfo(
            title = title,
            coverUrl = coverUrl,
            director = director,
            actors = actors,
            types = types,
            score = score,
            scoreCount = scoreCount, // 【新增】传入真实数据
            description = description,
            playLists = playlists
        )
    }
}