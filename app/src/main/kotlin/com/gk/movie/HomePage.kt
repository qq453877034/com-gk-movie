// 文件路径: app/src/main/kotlin/com/gk/movie/HomePage.kt
package com.gk.movie

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gk.movie.Utils.Category.ui.SidebarType
import com.gk.movie.Utils.Category.ui.SidebarPanel
import com.gk.movie.Utils.Search.Ui.SearchActivity

@Composable
fun HomePage(viewModel: MainViewModel, isPortrait: Boolean) {
    val tabStates by viewModel.tabStates.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(2) }
    var activeSidebar by remember { mutableStateOf<SidebarType?>(null) }
    val configuration = LocalConfiguration.current
    val isCompactWindow = configuration.screenWidthDp < 600

    val displayTabs = remember {
        listOf(
            HomeTab("离线视频", "/offline"),
            HomeTab("收藏", "/favorite"),
            HomeTab("推荐", "/"),
            HomeTab("电影", "/type/1"),
            HomeTab("电视剧", "/type/2"),
            HomeTab("综艺", "/type/3"),
            HomeTab("动漫", "/type/4")
        )
    }

    val currentUrl = displayTabs.getOrNull(selectedTabIndex)?.url ?: "/"

    LaunchedEffect(currentUrl) {
        if (!currentUrl.startsWith("/offline") && !currentUrl.startsWith("/favorite")) {
            viewModel.fetchTabData(currentUrl)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar(
            isPortrait = isPortrait,
            activeSidebar = activeSidebar,
            onSidebarToggle = { activeSidebar = it },
            selectedTabIndex = selectedTabIndex,
            onTabSelected = { selectedTabIndex = it },
            displayTabs = displayTabs
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            TabContent(
                selectedTabIndex = selectedTabIndex,
                displayTabs = displayTabs,
                viewModel = viewModel,
                isPortrait = isPortrait
            )

            if (activeSidebar != null) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            activeSidebar = null
                        }
                )
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = activeSidebar != null,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    activeSidebar?.let { type ->
                        SidebarPanel(type = type, isCompactWindow = isCompactWindow, onClose = { activeSidebar = null })
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun HomeTopBar(
    isPortrait: Boolean,
    activeSidebar: SidebarType?,
    onSidebarToggle: (SidebarType?) -> Unit,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    displayTabs: List<HomeTab>
) {
    var searchQuery by remember { mutableStateOf("") }
    val configuration = LocalConfiguration.current
    val isCompactDevice = configuration.smallestScreenWidthDp < 600
    val context = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            val AppNameUI = @Composable {
                Text(
                    text = stringResource(id = R.string.app_name),
                    style = if (isCompactDevice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(end = 16.dp) 
                )
            }

            val TabsUI = @Composable { modifier: Modifier ->
                if (displayTabs.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = modifier,
                        containerColor = Color.Transparent,
                        edgePadding = 0.dp,
                        divider = {},
                        indicator = { tabPositions ->
                            if (selectedTabIndex < tabPositions.size) {
                                Box(
                                    modifier = Modifier
                                        .tabIndicatorOffset(tabPositions[selectedTabIndex])
                                        .wrapContentSize(Alignment.BottomCenter)
                                        .width(12.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    ) {
                        displayTabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { onTabSelected(index) },
                                modifier = Modifier.height(40.dp),
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = {
                                    Text(
                                        text = tab.title,
                                        fontSize = 15.sp,
                                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                } else { Spacer(modifier = modifier) }
            }

            val SearchAndIconsUI = @Composable { modifier: Modifier ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = modifier
                ) {
                    val searchBarModifier = if (isPortrait || isCompactDevice) Modifier.weight(1f) else Modifier.width(260.dp)

                    Box(
                        modifier = searchBarModifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Search, "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    if (searchQuery.isNotBlank()) {
                                        val intent = Intent(context, SearchActivity::class.java)
                                        intent.putExtra("keyword", searchQuery)
                                        context.startActivity(intent)
                                    }
                                }),
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxWidth()) {
                                        if (searchQuery.isEmpty()) {
                                            Text("搜索影视...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (searchQuery.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Rounded.Close, "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp).clip(CircleShape)
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { searchQuery = "" }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        Icons.Rounded.History, "历史记录",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                onSidebarToggle(if (activeSidebar == SidebarType.HISTORY) null else SidebarType.HISTORY)
                            }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        Icons.Rounded.FavoriteBorder, "我的收藏",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                onSidebarToggle(if (activeSidebar == SidebarType.FAVORITES) null else SidebarType.FAVORITES)
                            }
                    )
                }
            }

            if (isPortrait) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    AppNameUI()
                    SearchAndIconsUI(Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    TabsUI(Modifier.fillMaxWidth())
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (!isCompactDevice) { AppNameUI() }
                    TabsUI(Modifier.weight(1f).padding(end = 10.dp))
                    val searchIconsModifier = if (isCompactDevice) Modifier.weight(1f) else Modifier.wrapContentWidth()
                    SearchAndIconsUI(searchIconsModifier)
                }
            }
        }
    }
}