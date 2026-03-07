package com.gk.movie.Utils.Category.Util

// 影片数据模型
data class CategoryMovie(
    val title: String,
    val detailUrl: String,
    val coverUrl: String,
    val score: String,
    val remark: String,
    val subTitle: String
)

// 筛选标签模型 (如: 喜剧、动作、年份、最新、热度等)
data class FilterItem(
    val name: String,
    val url: String,
    val isSelected: Boolean
)

// 筛选分组模型 (如: 按“类型”、按“年份”)
data class FilterGroup(
    val groupName: String,
    val items: List<FilterItem>
)

// 分页按钮模型
data class PageItem(
    val title: String,       
    val url: String,         
    val isActive: Boolean,   
    val isDisabled: Boolean  
)

// 整个页面的聚合数据
data class CategoryPageData(
    val pageTitle: String,           // ★ 页面大标题 (例如: "电影")
    val filters: List<FilterGroup>,  // 常规过滤条件 (分类、地区、年份)
    val sortItems: List<FilterItem>, // ★ 吸顶的排序条件 (最新、热度、好评)
    val movies: List<CategoryMovie>, // 影片列表
    val pageItems: List<PageItem>,   // 分页按钮
    val pageTips: String             // 页码提示
)

// UI 状态密封类
sealed class CategoryUiState {
    object Loading : CategoryUiState()
    data class Success(val data: CategoryPageData) : CategoryUiState()
    data class Error(val message: String) : CategoryUiState()
}