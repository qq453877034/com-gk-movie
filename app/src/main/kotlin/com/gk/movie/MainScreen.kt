// 文件路径: app/src/main/kotlin/com/gk/movie/MainScreen.kt
package com.gk.movie

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MainScreen(modifier: Modifier = Modifier, viewModel: MainViewModel = viewModel()) {
    var navIndex by remember { mutableIntStateOf(0) }
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.screenHeightDp >= configuration.screenWidthDp
    val useBottomNav = isPortrait

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (!useBottomNav) {
            // 横版布局：左侧边栏 + 右侧主内容
            Row(modifier = Modifier.fillMaxSize()) {
                AppNavigationRail(selectedIndex = navIndex, onItemSelected = { navIndex = it })
                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0)
                ) { innerPadding ->
                    NavigationContent(navIndex, innerPadding, isPortrait, viewModel)
                }
            }
        } else {
            // 竖版布局
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0),
                bottomBar = {
                    AppBottomNavigationBar(selectedIndex = navIndex, onItemSelected = { navIndex = it })
                }
            ) { innerPadding ->
                NavigationContent(navIndex, innerPadding, isPortrait, viewModel)
            }
        }
    }
}

@Composable
fun NavigationContent(
    navIndex: Int,
    innerPadding: PaddingValues,
    isPortrait: Boolean,
    viewModel: MainViewModel
) {
    // ★ 核心修复：每个页面独立占据全部可用空间（包含原顶栏空间），实现彻底解耦！
    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
        when (navIndex) {
            0 -> HomePage(viewModel = viewModel, isPortrait = isPortrait)
            1 -> DiscoverPage()
            2 -> ProfilePage()
        }
    }
}

@Composable
fun AppBottomNavigationBar(selectedIndex: Int, onItemSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        windowInsets = WindowInsets.navigationBars
    ) {
        NavigationBarItem(icon = { Icon(Icons.Default.Home, "首页") }, label = { Text("首页") }, selected = selectedIndex == 0, onClick = { onItemSelected(0) })
        NavigationBarItem(icon = { Icon(Icons.Default.Explore, "发现") }, label = { Text("发现") }, selected = selectedIndex == 1, onClick = { onItemSelected(1) })
        NavigationBarItem(icon = { Icon(Icons.Default.Person, "我的") }, label = { Text("我的") }, selected = selectedIndex == 2, onClick = { onItemSelected(2) })
    }
}

@Composable
fun AppNavigationRail(selectedIndex: Int, onItemSelected: (Int) -> Unit) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxHeight(),
        windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Vertical)
    ) {
        Spacer(modifier = Modifier.weight(1f))
        NavigationRailItem(icon = { Icon(Icons.Default.Home, "首页") }, label = { Text("首页") }, selected = selectedIndex == 0, onClick = { onItemSelected(0) })
        Spacer(Modifier.height(16.dp))
        NavigationRailItem(icon = { Icon(Icons.Default.Explore, "发现") }, label = { Text("发现") }, selected = selectedIndex == 1, onClick = { onItemSelected(1) })
        Spacer(Modifier.height(16.dp))
        NavigationRailItem(icon = { Icon(Icons.Default.Person, "我的") }, label = { Text("我的") }, selected = selectedIndex == 2, onClick = { onItemSelected(2) })
        Spacer(modifier = Modifier.weight(1f))
    }
}