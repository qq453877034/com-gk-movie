// 文件路径: app/src/main/kotlin/com/gk/movie/MainViewModel.kt
package com.gk.movie

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gk.movie.Utils.okhttpUtils.OkhttpManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import uniffi.m3u8_parser.extractVodData

@Serializable
data class CmsVod(
    val vod_id: Int = 0,
    val vod_name: String = "",
    val vod_pic: String = "",
    val vod_pic_slide: String = "", 
    val vod_remarks: String = "",
    val type_name: String = "",
    val vod_class: String = "",
    val vod_actor: String = "",
    val vod_director: String = "",
    val vod_blurb: String = "",
    val vod_version: String = ""
)

data class HomeTab(val title: String, val url: String)

data class HomeSection(
    val title: String,
    val moreUrl: String,
    val movies: List<CmsVod>
)

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val banners: List<CmsVod>,
        val sections: List<HomeSection>
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class MainViewModel : ViewModel() {

    private val _tabStates = MutableStateFlow<Map<String, HomeUiState>>(emptyMap())
    val tabStates: StateFlow<Map<String, HomeUiState>> = _tabStates

    fun fetchTabData(url: String) {
        if (_tabStates.value[url] is HomeUiState.Success) return

        viewModelScope.launch {
            val loadingMap = _tabStates.value.toMutableMap()
            loadingMap[url] = HomeUiState.Loading
            _tabStates.value = loadingMap

            try {
                val baseUrl = OkhttpManager.REFERER.trimEnd('/')
                val targetUrl = if (url.startsWith("http")) url else if (url == "/") baseUrl else "$baseUrl$url"

                withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(targetUrl).build()
                    val rawHtml = OkhttpManager.client.newCall(request).execute().use { it.body?.string() ?: "" }

                    // Rust 引擎提取底层 JSON
                    val jsonStr = extractVodData(rawHtml)
                    val jsonParser = Json { ignoreUnknownKeys = true }
                    val globalMovieMap = jsonParser.decodeFromString<List<CmsVod>>(jsonStr).associateBy { it.vod_id }
                    
                    val document = Jsoup.parse(rawHtml)

                    // ★ 1. 强力解析轮播图 (囊括所有已知的类名)
                    val banners = mutableListOf<CmsVod>()
                    val bannerNodes = document.select(".swiper-slide, div[class*=banner__BannerContainer] a[href], div[class*=hero-swiper] a[href]")
                    for (node in bannerNodes) {
                        val aTag = if (node.tagName() == "a") node else node.selectFirst("a[href]") ?: continue
                        val vodId = extractVodId(aTag) ?: continue
                        
                        val title = aTag.selectFirst("img")?.attr("alt")?.trim()?.ifEmpty { aTag.text().trim() } ?: ""
                        var pic = aTag.selectFirst("img")?.let { it.attr("src").ifEmpty { it.attr("data-src") } } ?: ""
                        if (pic.contains("url=")) pic = java.net.URLDecoder.decode(pic.substringAfter("url=").substringBefore("&"), "UTF-8")
                        if (pic.startsWith("//")) pic = "https:$pic" else if (pic.startsWith("/")) pic = "$baseUrl$pic"

                        val finalMovie = globalMovieMap[vodId] ?: CmsVod(vod_id = vodId, vod_name = title, vod_pic = pic, vod_pic_slide = pic)
                        if (banners.none { it.vod_id == vodId }) banners.add(finalMovie)
                    }

                    // ★ 2. 强力解析分组模块 (兼容首页和分类页的 DOM 变种)
                    val sections = mutableListOf<HomeSection>()
                    val panelItems = document.select(".panel-item, div[class*=panel-page__Content], div[class*=style__Panel]")

                    if (panelItems.isNotEmpty()) {
                        for (panel in panelItems) {
                            val title = panel.selectFirst(".title, .content-title, h2")?.text()?.trim() ?: "推荐内容"
                            val moreUrl = panel.selectFirst("a.more")?.attr("href") ?: ""
                            
                            val cards = mutableListOf<CmsVod>()
                            panel.select(".content-card, a.public-list-exp, a[class*=card]").forEach { cardElem ->
                                val vodId = extractVodId(cardElem) ?: return@forEach
                                val cardTitle = cardElem.selectFirst(".card-info .title, .title")?.text()?.trim() ?: ""
                                var pic = cardElem.selectFirst("img")?.let { it.attr("src").ifEmpty { it.attr("data-src") } } ?: ""
                                if (pic.contains("url=")) pic = java.net.URLDecoder.decode(pic.substringAfter("url=").substringBefore("&"), "UTF-8")
                                if (pic.startsWith("//")) pic = "https:$pic" else if (pic.startsWith("/")) pic = "$baseUrl$pic"
                                
                                val remarks = cardElem.selectFirst(".card-img .bottom > div:first-child, .remark")?.text()?.trim() ?: ""
                                val score = cardElem.selectFirst(".score")?.text()?.trim() ?: ""
                                val actor = cardElem.selectFirst(".card-info .role span, .role")?.text()?.trim() ?: ""
                                
                                val finalMovie = globalMovieMap[vodId] ?: CmsVod(
                                    vod_id = vodId, vod_name = cardTitle, vod_pic = pic, 
                                    vod_remarks = remarks, vod_version = score, vod_actor = actor
                                )
                                if (cards.none { it.vod_id == vodId }) cards.add(finalMovie)
                            }
                            
                            val filteredCards = cards.filter { movie -> banners.none { it.vod_id == movie.vod_id } }
                            if (filteredCards.isNotEmpty() && sections.none { it.title == title }) {
                                sections.add(HomeSection(title, moreUrl, filteredCards))
                            }
                        }
                    }

                    // ★ 3. 终极平铺兜底 (如果连分组框都找不到，直接全屏搜刮卡片)
                    if (sections.isEmpty()) {
                        val allCards = document.select(".content-card, a.public-list-exp")
                        if (allCards.isNotEmpty()) {
                            val cards = mutableListOf<CmsVod>()
                            allCards.forEach { cardElem ->
                                val vodId = extractVodId(cardElem) ?: return@forEach
                                val cardTitle = cardElem.selectFirst(".card-info .title, .title")?.text()?.trim() ?: ""
                                var pic = cardElem.selectFirst("img")?.let { it.attr("src").ifEmpty { it.attr("data-src") } } ?: ""
                                if (pic.contains("url=")) pic = java.net.URLDecoder.decode(pic.substringAfter("url=").substringBefore("&"), "UTF-8")
                                if (pic.startsWith("//")) pic = "https:$pic" else if (pic.startsWith("/")) pic = "$baseUrl$pic"
                                
                                val remarks = cardElem.selectFirst(".card-img .bottom > div:first-child, .remark")?.text()?.trim() ?: ""
                                val score = cardElem.selectFirst(".score")?.text()?.trim() ?: ""
                                val actor = cardElem.selectFirst(".card-info .role span, .role")?.text()?.trim() ?: ""
                                
                                val finalMovie = globalMovieMap[vodId] ?: CmsVod(
                                    vod_id = vodId, vod_name = cardTitle, vod_pic = pic, 
                                    vod_remarks = remarks, vod_version = score, vod_actor = actor
                                )
                                if (cards.none { it.vod_id == vodId }) cards.add(finalMovie)
                            }
                            val filteredCards = cards.filter { movie -> banners.none { it.vod_id == movie.vod_id } }
                            if (filteredCards.isNotEmpty()) {
                                val pageTitle = document.title().substringBefore("-").trim().ifEmpty { "最新内容" }
                                sections.add(HomeSection(pageTitle, "", filteredCards))
                            }
                        }
                    }

                    if (sections.isEmpty() && banners.isEmpty()) {
                        throw Exception("解析失败：DOM结构或JSON均未匹配到影视卡片")
                    }

                    val successMap = _tabStates.value.toMutableMap()
                    successMap[url] = HomeUiState.Success(banners, sections)
                    _tabStates.value = successMap
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "抓取栏目 $url 异常", e)
                val errorMap = _tabStates.value.toMutableMap()
                errorMap[url] = HomeUiState.Error("获取数据异常: ${e.message}")
                _tabStates.value = errorMap
            }
        }
    }

    // ★ 终极防泄漏正则：匹配各种被魔改过的详情页链接
    private fun extractVodId(element: Element): Int? {
        val aTag = if (element.tagName() == "a") element else element.selectFirst("a[href]") ?: return null
        val href = aTag.attr("href")
        val regex = Regex("""/(?:detail|play|show)(?:/id/|/)?(\d+)""")
        val match = regex.find(href)
        if (match != null) {
            return match.groupValues[1].toIntOrNull()
        }
        return href.substringAfterLast("/").substringBefore(".").toIntOrNull()
    }
}