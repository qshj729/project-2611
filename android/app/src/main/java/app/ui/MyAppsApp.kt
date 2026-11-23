package app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.core.data.config.InternalServicesStore
import app.feature.browser.BrowserScreen
import app.feature.home.HomeScreen
import app.feature.settings.SettingsScreen

private enum class MainTab(val label: String) {
    HOME("内网"),
    BROWSER("浏览器"),
    SETTINGS("设置"),
}

/**
 * 主界面：三个一级页签
 * - 内网：内网服务导航页（YAML 渲染的静态卡片）
 * - 浏览器：GeckoView 浏览器底座
 * - 设置：隧道授权、服务清单导入导出、主题
 */
@Composable
fun MyAppsApp(
    servicesStore: InternalServicesStore,
) {
    var tab by remember { mutableStateOf(MainTab.HOME) }
    // 从导航页点进来的目标地址（浏览器屏消费）
    var pendingUrl by remember { mutableStateOf<String?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {
                            Icon(
                                imageVector = when (entry) {
                                    MainTab.HOME -> Icons.Default.Home
                                    MainTab.BROWSER -> Icons.Default.Public
                                    MainTab.SETTINGS -> Icons.Default.Settings
                                },
                                contentDescription = entry.label,
                            )
                        },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (tab) {
            MainTab.HOME -> HomeScreen(
                servicesStore = servicesStore,
                onOpenService = { url ->
                    pendingUrl = url
                    tab = MainTab.BROWSER
                },
                modifier = contentModifier,
            )

            MainTab.BROWSER -> BrowserScreen(
                initialUrl = pendingUrl ?: "",
                onUrlConsumed = { pendingUrl = null },
            )

            MainTab.SETTINGS -> SettingsScreen(onBack = { tab = MainTab.HOME })
        }
    }
}
