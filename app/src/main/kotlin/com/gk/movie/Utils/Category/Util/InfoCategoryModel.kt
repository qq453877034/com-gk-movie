// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Category/Util/InfoCategoryModel.kt
package com.gk.movie.Utils.Category.Util

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gk.movie.Utils.okhttpUtils.OkhttpManager
//import com.gk.movie.Utils.NativeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File

import uniffi.m3u8_parser.extractCategoryImages

class InfoCategoryModel : ViewModel() {

    private val isLocalDebug = false
    private val localHtmlPath = "/storage/emulated/0/ALua布局助手/极光影视网页源码/点击更多时的分类页.html"

    private val _uiState = MutableStateFlow<CategoryUiState>(CategoryUiState.Loading)
    val uiState: StateFlow<CategoryUiState> = _uiState

    var currentTitle: String = "全部分类"
    var dynamicBaseUrl: String = OkhttpManager.REFERER.trimEnd('/')
    var currentUrlPath: String = ""
    var currentPage: Int = 1

    fun initCategory(title: String, targetUrl: String) {
        currentTitle = title
        if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
            try {
                val urlObj = java.net.URL(targetUrl)
                dynamicBaseUrl = "${urlObj.protocol}://${urlObj.authority}"
                currentUrlPath = urlObj.path
            } catch (e: Exception) {
                Log.e("InfoCategoryModel", "URL解析异常", e)
                currentUrlPath = targetUrl
            }
        } else {
            currentUrlPath = if (targetUrl.startsWith("/")) targetUrl else "/$targetUrl"
        }
        currentPage = 1
        fetchCategoryData()
    }

    fun updateFilter(newUrlPath: String) {
        currentUrlPath = newUrlPath
        currentPage = 1
        fetchCategoryData()
    }

    fun loadPage(page: Int) {
        currentPage = page
        fetchCategoryData()
    }

    fun fetchCategoryData() {
        viewModelScope.launch {
            _uiState.value = CategoryUiState.Loading
            try {
                val url = if (currentPage == 1) {
                    "$dynamicBaseUrl$currentUrlPath"
                } else {
                    "$dynamicBaseUrl$currentUrlPath/page/$currentPage"
                }

                withContext(Dispatchers.IO) {
                    val rawHtml = if (isLocalDebug) {
                        val file = File(localHtmlPath)
                        if (file.exists() && file.canRead()) file.readText(Charsets.UTF_8) else ""
                    } else {
                        Log.d("InfoCategoryModel", "🌐 【网络请求】: $url")
                        val request = Request.Builder().url(url).build()
                        OkhttpManager.client.newCall(request).execute().use { response ->
                            response.body.string()
                        }
                    }

                    var globalImageMap = emptyMap<String, String>()
                    try {
                        val startTime = System.currentTimeMillis()
                        
                       // val jsonStr = NativeParser.extractCategoryImages(rawHtml, dynamicBaseUrl)
                       
                        val jsonStr = extractCategoryImages(rawHtml, dynamicBaseUrl)
                        val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }
                        globalImageMap = jsonParser.decodeFromString<Map<String, String>>(jsonStr)
                        
                        val costTime = System.currentTimeMillis() - startTime
                        Log.d("InfoCategoryModel", "✅ Rust 底层锚点提取成功，极速解码了 ${globalImageMap.size} 个高清海报！耗时: ${costTime}ms")
                        
                        // =========================================================
                        // ★ 强制开启深度侦测日志，打印所有真实的 CDN 链接
                        // =========================================================
                        globalImageMap.forEach { (id, coverUrl) ->
                            Log.e("InfoCategoryModel", "🕵️‍♂️ 深度侦测 -> 电影ID: $id | 提取到的URL: $coverUrl")
                        }

                    } catch (e: Exception) {
                        Log.e("InfoCategoryModel", "Rust 底层提取海报异常", e)
                    }

                    val document = Jsoup.parse(rawHtml)

                    val filterGroups = mutableListOf<FilterGroup>()
                    val filterBoxes = document.select(".filter-box")
                    
                    if (filterBoxes.isNotEmpty()) {
                        for (box in filterBoxes) {
                            val groupName = box.selectFirst(".filter-title")?.text()?.trim() ?: ""
                            val filterItems = box.select("a.filter-li").mapNotNull { aTag ->
                                val name = aTag.text().trim()
                                val href = aTag.attr("href")
                                if (name.isNotEmpty() && href.isNotEmpty()) FilterItem(name, href, aTag.hasClass("active")) else null
                            }
                            if (filterItems.isNotEmpty()) filterGroups.add(FilterGroup(groupName, filterItems))
                        }
                        
                        val sortElements = document.select("div[class^=tab-button__StyleTabBtn][value]")
                        if (sortElements.isNotEmpty()) {
                            val sortItems = sortElements.mapNotNull { div ->
                                val name = div.selectFirst("span")?.text()?.trim() ?: return@mapNotNull null
                                val value = div.attr("value")
                                val isSelected = div.hasClass("active")
                                
                                var targetUrl = currentUrlPath
                                if (targetUrl.contains(Regex("sortType/\\d+"))) {
                                    targetUrl = targetUrl.replace(Regex("sortType/\\d+"), "sortType/$value")
                                } else if (targetUrl.contains(Regex("/id/\\d+"))) {
                                    targetUrl = targetUrl.replace(Regex("/id/\\d+"), "$0/sortType/$value/sortOrder/0")
                                } else {
                                    targetUrl = "$targetUrl/sortType/$value/sortOrder/0"
                                }
                                
                                FilterItem(name, targetUrl, isSelected)
                            }
                            if (sortItems.isNotEmpty()) filterGroups.add(FilterGroup("排序", sortItems))
                        }
                    } else {
                        val navLayout = document.selectFirst("div[class^=movie-nav__Layout]")
                        navLayout?.selectFirst(".content")?.let { contentBox ->
                            val filterItems = contentBox.select("a").mapNotNull { aTag ->
                                val name = aTag.text().trim()
                                val href = aTag.attr("href")
                                if (name.isNotEmpty() && href.isNotEmpty()) FilterItem(name, href, currentUrlPath == href) else null
                            }
                            if (filterItems.isNotEmpty()) filterGroups.add(FilterGroup("类型", filterItems))
                        }
                    }

                    val movies = mutableListOf<CategoryMovie>()
                    val cardElements = document.select(".content-card")
                    for (card in cardElements) {
                        val aTag = card.selectFirst("a[href^=/detail/]") ?: continue
                        val href = aTag.attr("href")
                        
                        // ★ 坚决剔除 .html，防止跳过视频
                        val vodId = href.substringAfter("/detail/").substringBefore(".html").substringBefore("/").toIntOrNull() ?: continue
                        
                        // 从 Rust 传回的完美映射表中取图！
                        var coverUrl = globalImageMap[vodId.toString()] ?: ""

                        // 终极 DOM 兜底
                        if (coverUrl.isEmpty()) {
                            val imgNode = card.selectFirst("img")
                            
                            coverUrl = imgNode?.attr("src")?.ifEmpty { null }
                                ?: imgNode?.attr("data-src")?.ifEmpty { null }
                                ?: imgNode?.attr("srcset")?.substringBefore(" ")?.ifEmpty { null } ?: ""
                            
                            if (coverUrl.isEmpty()) {
                                val bgNode = card.selectFirst("div[style*=background-image]")
                                if (bgNode != null) {
                                    val bgMatch = """url\(['"]?(.*?)['"]?\)""".toRegex().find(bgNode.attr("style"))
                                    if (bgMatch != null) coverUrl = bgMatch.groupValues[1]
                                }
                            }
                            
                            if (coverUrl.contains("/_next/image?url=")) {
                                val encodedUrl = coverUrl.substringAfter("url=").substringBefore("&")
                                coverUrl = runCatching { java.net.URLDecoder.decode(encodedUrl, "UTF-8") }.getOrDefault(coverUrl)
                            }
                            // 兜底代码里也不能去乱加主站域名，顺其自然
                            if (coverUrl.startsWith("//")) coverUrl = "https:$coverUrl"
                            else if (coverUrl.startsWith("/")) coverUrl = "$dynamicBaseUrl$coverUrl"
                        }

                        val title = card.selectFirst(".title")?.text()?.trim() ?: ""
                        if (title.isEmpty()) continue

                        val score = card.selectFirst(".score")?.text()?.trim() ?: ""
                        val remark = card.selectFirst(".bottom > div:first-child")?.text()?.trim() 
                            ?: card.selectFirst(".tag")?.text()?.trim() ?: ""
                        val subTitle = card.selectFirst(".role span")?.text()?.trim() 
                            ?: card.selectFirst(".info-roles")?.text()?.replace("主演：", "")?.trim() ?: ""

                        movies.add(CategoryMovie(vodId, title, coverUrl, score, remark, subTitle))
                    }

                    val pageItems = mutableListOf<PageItem>()
                    var maxPageNum = currentPage
                    val paginationDiv = document.selectFirst(".pagination")
                    val pageElements = paginationDiv?.select("[page]")
                    
                    if (pageElements != null && pageElements.isNotEmpty()) {
                        for (el in pageElements) {
                            val aTag = el.selectFirst("a") ?: continue
                            val label = el.attr("label")
                            val pageNumStr = el.attr("page")
                            
                            if (label == "GO" || label == "jump") continue
                            
                            val btnTitle = when (label) {
                                "prev" -> "上一页"
                                "next" -> "下一页"
                                "first" -> "首页"
                                "last" -> "尾页"
                                else -> pageNumStr
                            }
                            
                            val isActive = aTag.hasClass("active") || el.attr("current") == pageNumStr
                            val isDisabled = aTag.hasClass("isDisabled") || pageNumStr == "0" || pageNumStr.isEmpty()
                            
                            if (pageItems.none { it.title == btnTitle } && pageNumStr.isNotEmpty()) {
                                pageItems.add(PageItem(btnTitle, pageNumStr, isActive, isDisabled))
                            }
                            pageNumStr.toIntOrNull()?.let { if (it > maxPageNum) maxPageNum = it }
                        }
                    } else {
                        pageItems.add(PageItem("上一页", (currentPage - 1).coerceAtLeast(1).toString(), false, currentPage <= 1))
                        pageItems.add(PageItem(currentPage.toString(), currentPage.toString(), true, false))
                        pageItems.add(PageItem("下一页", (currentPage + 1).toString(), false, movies.isEmpty()))
                    }

                    _uiState.value = CategoryUiState.Success(
                        CategoryPageData(
                            pageTitle = currentTitle,
                            filters = filterGroups,
                            movies = movies,
                            pageItems = pageItems,
                            pageTips = "第 $currentPage 页 / 共 $maxPageNum 页"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("InfoCategoryModel", "分类页解析异常", e)
                _uiState.value = CategoryUiState.Error("数据获取或解析失败: ${e.localizedMessage}")
            }
        }
    }
}