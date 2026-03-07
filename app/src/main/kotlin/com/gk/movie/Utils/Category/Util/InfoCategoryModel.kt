package com.gk.movie.Utils.Category.Util

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

class InfoCategoryModel : ViewModel() {

    private val _uiState = MutableStateFlow<CategoryUiState>(CategoryUiState.Loading)
    val uiState: StateFlow<CategoryUiState> = _uiState

    private val isDebugLocalMode = true
    private val localFilePath = "/storage/emulated/0/Alarms/分类页.html"

    init {
        fetchCategoryData("https://www.jkan.app/show/1-----------.html")
    }

    fun fetchCategoryData(url: String) {
        _uiState.value = CategoryUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val html: String
                if (isDebugLocalMode) {
                    val file = File(localFilePath)
                    if (!file.exists()) {
                        _uiState.value = CategoryUiState.Error("本地测试文件不存在:\n$localFilePath")
                        return@launch
                    }
                    html = file.readText(Charsets.UTF_8)
                    kotlinx.coroutines.delay(800) // 模拟网络延迟展示骨架屏
                } else {
                    val request = Request.Builder().url(url).build()
                    val response = OkhttpManager.client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        _uiState.value = CategoryUiState.Error("网络请求失败: ${response.code}")
                        return@launch
                    }
                    html = response.body?.string() ?: ""
                }

                val doc = Jsoup.parse(html)
                
                // ★ 1. 解析大标题 (如 "电影")
                val pageTitle = doc.selectFirst(".sx_title.hidden_xs")?.text()?.trim() ?: "全部分类"

                // 2. 解析常规筛选条件 (分类、类型、地区、年份)
                val filterGroups = mutableListOf<FilterGroup>()
                val filterIds = listOf("hl01" to "分类", "hl02" to "类型", "hl03" to "地区", "hl04" to "年份")
                for ((id, groupName) in filterIds) {
                    val ulNode = doc.selectFirst("#$id ul.screen_list")
                    if (ulNode != null) {
                        val items = mutableListOf<FilterItem>()
                        val liNodes = ulNode.select("li:not(.text)") 
                        for (li in liNodes) {
                            val aNode = li.selectFirst("a") ?: continue
                            val name = aNode.text().trim()
                            if (name.isEmpty()) continue
                            val href = aNode.attr("href")
                            val isSelected = li.hasClass("hl")
                            val fullUrl = if (href.startsWith("http")) href else "https://www.jkan.app$href"
                            items.add(FilterItem(name, fullUrl, isSelected))
                        }
                        if (items.isNotEmpty()) filterGroups.add(FilterGroup(groupName, items))
                    }
                }

                // ★ 3. 解析吸顶的排序条件 (最新上映、超高人气等)
                val sortItems = mutableListOf<FilterItem>()
                val sortNodes = doc.select("ul.sx_tz > li > a")
                var hasActiveSort = false
                for (node in sortNodes) {
                    val name = node.text().trim()
                    val href = node.attr("href")
                    val fullUrl = if (href.startsWith("http")) href else "https://www.jkan.app$href"
                    // HTML中排序列没有明确的 active class，通过 URL 匹配判断
                    val isSelected = url == fullUrl 
                    if (isSelected) hasActiveSort = true
                    sortItems.add(FilterItem(name, fullUrl, isSelected))
                }
                // 如果没有任何匹配（比如第一次进页面），默认选中第一个
                if (!hasActiveSort && sortItems.isNotEmpty()) {
                    sortItems[0] = sortItems[0].copy(isSelected = true)
                }

                // 4. 解析影片列表 (去广告)
                val movies = mutableListOf<CategoryMovie>()
                val vodItems = doc.select("ul.vodlist > li.vodlist_item")
                for (item in vodItems) {
                    val aThumb = item.selectFirst("a.vodlist_thumb") ?: continue
                    val href = aThumb.attr("href")
                    val title = aThumb.attr("title").ifEmpty { aThumb.text() }.trim()
                    
                    if (href.isBlank() || href.contains("gkan.net") || title.contains("APP")) continue

                    val fullDetailUrl = if (href.startsWith("http")) href else "https://www.jkan.app$href"
                    var coverUrl = aThumb.attr("data-background-image")
                    if (coverUrl.startsWith("//")) coverUrl = "https:$coverUrl"
                    
                    val score = item.selectFirst(".pack_tagtext")?.text()?.trim() ?: ""
                    val remark = item.selectFirst(".xszxj")?.text()?.trim() ?: ""
                    val subTitle = item.selectFirst(".vodlist_sub")?.text()?.replace("看点：", "")?.trim() ?: ""

                    movies.add(CategoryMovie(title, fullDetailUrl, coverUrl, score, remark, subTitle))
                }

                // 5. 解析底部分页导航
                val pageItems = mutableListOf<PageItem>()
                val pageNodes = doc.select("ul.page > li")
                for (node in pageNodes) {
                    val targetNode = node.selectFirst("a") ?: node.selectFirst("span") ?: continue
                    val title = targetNode.text().trim()
                    val href = targetNode.attr("href")
                    val isActive = node.hasClass("active")
                    val isDisabled = targetNode.hasClass("btns_disad") || href.contains("javascript")
                    val fullUrl = if (href.startsWith("http") || href.contains("javascript")) href else "https://www.jkan.app$href"
                    pageItems.add(PageItem(title, fullUrl, isActive, isDisabled))
                }
                val pageTips = doc.selectFirst(".page_tips")?.text()?.trim() ?: ""

                _uiState.value = CategoryUiState.Success(
                    CategoryPageData(pageTitle, filterGroups, sortItems, movies, pageItems, pageTips)
                )

            } catch (e: Exception) {
                Log.e("InfoCategoryModel", "解析异常", e)
                _uiState.value = CategoryUiState.Error("解析异常: ${e.message}")
            }
        }
    }
}