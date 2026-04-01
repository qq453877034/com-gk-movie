// 文件路径: app/src/main/kotlin/com/gk/movie/Utils/Category/Util/CategoryInfo.kt
package com.gk.movie.Utils.Category.Util

// 影片数据模型 (适配解析后的清洗数据)
data class CategoryMovie(
    val vodId: Int,          // 真实视频 ID
    val title: String,       // 剧名
    val coverUrl: String,    // 海报
    val score: String,       // 评分
    val remark: String,      // 更新状态 (如: 41/52, 蓝光等)
    val subTitle: String     // 副标题/主演类型
)

// 筛选标签模型 (用于子分类切换)
data class FilterItem(
    val name: String,
    val targetId: String,    // 对应的 URL Path 或 type_id
    val isSelected: Boolean
)

// 筛选分组模型
data class FilterGroup(
    val groupName: String,
    val items: List<FilterItem>
)

// 分页按钮模型
data class PageItem(
    val title: String,       
    val pageStr: String,     // 页码数字
    val isActive: Boolean,   
    val isDisabled: Boolean  
)

// 整个页面的聚合数据
data class CategoryPageData(
    val pageTitle: String,           // 页面大标题
    val filters: List<FilterGroup>,  // 顶部的子分类 Tab
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