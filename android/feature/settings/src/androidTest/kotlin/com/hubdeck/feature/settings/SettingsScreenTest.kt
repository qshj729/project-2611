package com.hubdeck.feature.settings

import androidx.compose.foundation.text.input.ImeAction
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DisabledOn
import androidx.compose.ui.test.Orientation
import androidx.compose.ui.test.Platform
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubdeck.app.core.data.settings.CardDensityPreference
import com.hubdeck.app.core.data.settings.HubDeckSettings
import com.hubdeck.app.core.data.settings.NetworkModeLock
import com.hubdeck.app.core.data.settings.SettingsStore
import com.hubdeck.app.core.data.settings.ThemePreference
import com.hubdeck.app.core.tunnel.TunnelManager
import com.hubdeck.app.core.tunnel.TunnelState
import com.hubdeck.app.core.data.repo.ServiceRepository
import com.hubdeck.app.core.data.importer.HomarrImporter
import com.hubdeck.app.core.data.importer.HubDeckYaml
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

/**
 * 设置界面测试。
 *
 * 测试覆盖：
 * - 内网连接状态显示
 * - 数据导出
 * - Homarr 导入展开/收起
 * - 网络模式选择（自动/强制在家/强制在外）
 * - 探测设置（间隔、超时、仅 Wi-Fi）
 * - 外观设置（主题、卡片密度）
 * - 凭据管理占位
 * - 关于页面（版本、协议、源代码）
 */
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val mockRepository = mock<ServiceRepository>()
    private val mockSettingsStore = mock<SettingsStore>()
    private val mockTunnelManager = mock<TunnelManager>()

    private val testSettings = HubDeckSettings(
        homeSsid = "MyHomeWiFi",
        probeIntervalMinutes = 15,
        probeTimeoutMs = 3000,
        probeWifiOnly = true,
        networkModeLock = NetworkModeLock.AUTO,
        themePreference = ThemePreference.SYSTEM,
        cardDensity = CardDensityPreference.NORMAL,
    )

    private val mockSettingsFlow = MutableStateFlow(testSettings)
    private val mockTunnelStateFlow = MutableStateFlow(TunnelState.Disconnected)
    private val mockExportedYamlFlow = MutableStateFlow<String?>(null)
    private val mockImportSummaryFlow = MutableStateFlow<String?>(null)

    @Suppress("UNUSED_PARAMETER")
    @Composable
    private fun TestSettingsScreen(
        viewModel: SettingsViewModel,
    ) {
        SettingsScreen(onBack = {}, viewModel = viewModel)
    }

    private fun setupMocks() {
        `when`(mockSettingsStore.observe()).thenReturn(mockSettingsFlow.asStateFlow())
        `when`(mockTunnelManager.tunnelState).thenReturn(mockTunnelStateFlow.asStateFlow())
        `when`(mockRepository.observeServices()).thenReturn(
            kotlinx.coroutines.flow.flowOf(emptyList()).asStateFlow()
        )
    }

    private fun createTestViewModel(): SettingsViewModel {
        return SettingsViewModel(
            repository = mockRepository,
            settingsStore = mockSettingsStore,
            tunnelManager = mockTunnelManager,
            context = androidx.compose.ui.platform.LocalContext.current.applicationContext,
        )
    }

    @Test
    fun settings_rendersAllSections() {
        setupMocks()

        composeRule.setContent {
            TestSettingsScreen(viewModel = createTestViewModel())
        }

        // 验证所有主要区块标题
        composeRule.onNodeWithText("内网连接").assertIsDisplayed()
        composeRule.onNodeWithText("数据").assertIsDisplayed()
        composeRule.onNodeWithText("从 Homarr 导入").assertIsDisplayed()
        composeRule.onNodeWithText("网络模式").assertIsDisplayed()
        composeRule.onNodeWithText("探测设置").assertIsDisplayed()
        composeRule.onNodeWithText("外观").assertIsDisplayed()
        composeRule.onNodeWithText("凭据管理").assertIsDisplayed()
        composeRule.onNodeWithText("关于").assertIsDisplayed()
    }

    @Test
    fun settings_connectionCard_showsDisconnectedState() {
        setupMocks()

        composeRule.setContent {
            TestSettingsScreen(viewModel = createTestViewModel())
        }

        composeRule.onNodeWithText("状态：未连接内网").assertIsDisplayed()
        composeRule.onNodeWithText("开始授权连接内网").assertIsDisplayed()
    }

    @Test
    fun settings_connectionCard_showsConnectedState() {
        setupMocks()
        mockTunnelStateFlow.value = com.hubdeck.app.core.tunnel.TunnelState.Connected(
            nodeName = "test-device",
            tailnetIps = listOf("100.64.0.1"),
            magicDnsSuffix = "test.tailnet.ts.net",
            loginUrl = "",
        )

        composeRule.setContent {
            TestSettingsScreen(viewModel = createTestViewModel())
        }

        composeRule.onNodeWithText("状态：已连接 (test-device)").assertIsDisplayed()
        composeRule.onNodeWithText("MagicDNS 后缀：test.tailnet.ts.net").assertIsDisplayed()
        composeRule.onNodeWithText("断开连接并清除凭据").assertIsDisplayed()
    }

    @Test
    fun settings_networkModeCard_showsRadioButtons() {
        setupMocks()

        composeRule.setContent {
            TestSettingsScreen(viewModel = createTestViewModel())
        }

        composeRule.onNodeWithText("自动").assertIsDisplayed()
        composeRule.onNodeWithText("强制在家").assertIsDisplayed()
        composeRule.onNodeWithText("强制在外").assertIsDisplayed()
    }

    @Test
    fun settings_networkModeCard_showsHomeSsidField() {
        setupMocks()

        composeRule.setContent {
            TestSettingsScreen(viewModel = createTestViewModel())
        }

        composeRule.onNodeWithText("家中 Wi-Fi SSID").assertIsDisplayed()
        composeRule.onNodeWithText("MyHomeWiFi").assertIsDisplayed()
    }

    @Test
    fun settings_probeSettingsCard_showsSliders() {
        setupMocks()

        composeRule.setContent {
            TestSettingsScreen(viewModel = createTestViewModel())
        }

        composeRule.onNodeWithText("探测间隔").assertIsDisplayed()
        composeRule.onNodeWithText("探测超时").assertIsDisplayed()
        composeRule.onNodeWithText("仅在 Wi-Fi 下探测").assertIsDisplayed()

        // 验证滑块存在
        composeRule.onNode(hasTag("probe_interval_slider")).assertIsDisplayed()
        composeRule.onNode(hasTag("probe_timeout_slider")).assertIsDisplayed()
    }

    @Test
    fun settings_appearanceCard_showsThemeOptions() {
        setupMocks()

        composeRule.setContent {
            TestSettingsScreen(viewModel = createTestViewModel())
        }

        composeRule.onNodeWithText("跟随系统").assertIsDisplayed()
        composeRule.onNodeWithText("浅色模式").assertIsDisplayed()
        composeRule.onNodeWithText("深色模式").assertIsDisplayed()
    }

    @Test
    fun settings_appearanceCard_showsCardDensityOptions() {
        setupMocks()

        composeRule.setContent {
            TestSettingsScreen(viewModel = createTestViewModel())
        }

        composeRule.onNodeWithText("紧凑").assertIsDisplayed()
        composeRule.onNodeWithText("标准").assertIsDisplayed()
        composeRule.onNodeWithText("舒适").assertIsDisplayed()
    }

    @Test
    fun settings_aboutCard_showsVersionAndLinks() {
        setupMocks()

        composeRule.setContent {
            TestSettingsScreen(viewModel = createTestViewModel())
        }

        composeRule.onNodeWithText("版本").assertIsDisplayed()
        composeRule.onNodeWithText("0.1.0").assertIsDisplayed()
        composeRule.onNodeWithText("开源协议").assertIsDisplayed()
        composeRule.onNodeWithText("Apache-2.0").assertIsDisplayed()
        composeRule.onNodeWithText("源代码").assertIsDisplayed()
        composeRule.onNodeWithText("GitHub").assertIsDisplayed()
    }

    @Test
    fun settings_homarrImportCard_expandCollapse() {
        setupMocks()

        composeRule.setContent {
            TestSettingsScreen(viewModel = createTestViewModel())
        }

        // 初始状态：收起
        composeRule.onNodeWithText("粘贴 Homarr config.yaml").assertDoesNotExist()

        // 点击展开
        composeRule.onNodeWithText("从 Homarr 导入").performClick()

        // 验证展开后的内容
        composeRule.onNodeWithText("粘贴 Homarr config.yaml").assertIsDisplayed()
        composeRule.onNodeWithText("导入").assertIsDisplayed()
    }

    @Test
    fun settings_dataCard_exportButton() {
        setupMocks()

        composeRule.setContent {
            TestSettingsScreen(viewModel = createTestViewModel())
        }

        composeRule.onNodeWithText("导出 hubdeck.yaml").assertIsDisplayed()
    }
}

// Tag 匹配器辅助
import androidx.compose.ui.test.hasTag
import androidx.compose.ui.test.assertDoesNotExist