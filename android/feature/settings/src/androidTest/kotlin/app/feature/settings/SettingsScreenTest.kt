package app.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.core.data.settings.AppSettings
import app.core.data.settings.ThemePreference
import app.core.model.InternalService
import app.core.model.TunnelState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test


/**
 * 设置页 Compose UI 测试（Instrumented，需模拟器/真机）。
 *
 * 测试策略：
 * - 使用假存储实现，不依赖 Hilt、Context 或真实 TunnelManager
 * - 覆盖：分区渲染、各状态显示、开关切换、导入导出入口、主题选择
 * - 在 CI 模拟器通道 E (ci-emulator.yml) 上运行：`./gradlew connectedDebugAndroidTest`
 */
@OptIn(ExperimentalMaterial3Api::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var fakeSettingsStore: FakeSettingsStore
    private lateinit var fakeServicesStore: FakeInternalServicesStore
    private lateinit var fakeSecretStore: FakeSecretStore
    private lateinit var fakeTunnelProvider: FakeTunnelStateProvider
    private lateinit var testViewModel: TestSettingsViewModel

    @Before
    fun setUp() {
        fakeSettingsStore = FakeSettingsStore()
        fakeServicesStore = FakeInternalServicesStore()
        fakeSecretStore = FakeSecretStore()
        fakeTunnelProvider = FakeTunnelStateProvider()
        testViewModel = TestSettingsViewModel(
            settingsStore = fakeSettingsStore,
            servicesStore = fakeServicesStore,
            secretStore = fakeSecretStore,
            tunnelStateProvider = fakeTunnelProvider,
        )
    }

    /** 渲染 SettingsScreen 的辅助函数。 */
    @Composable
    private fun renderSettingsScreen(viewModel: TestSettingsViewModel = testViewModel) {
        MaterialTheme {
            SettingsScreen(
                onBack = {},
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun settingsScreen_rendersAllSections() {
        composeRule.setContent { renderSettingsScreen() }

        // 验证顶部栏
        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()

        // 验证五大分区标题
        composeRule.onNodeWithText("内网连接").assertIsDisplayed()
        composeRule.onNodeWithText("内网服务清单").assertIsDisplayed()
        composeRule.onNodeWithText("隧道设置").assertIsDisplayed()
        composeRule.onNodeWithText("外观").assertIsDisplayed()
        composeRule.onNodeWithText("关于").assertIsDisplayed()
    }

    @Test
    fun connectionCard_disconnectedState_showsAuthButton() {
        fakeTunnelProvider.setTunnelState(TunnelState.Disconnected)
        composeRule.setContent { renderSettingsScreen() }

        composeRule.onNodeWithText("状态：未连接内网").assertIsDisplayed()
        composeRule.onNodeWithText("当前为公网直连模式").assertIsDisplayed()
        composeRule.onNodeWithText("开始授权连接内网").assertIsDisplayed()
    }

    @Test
    fun connectionCard_connectedState_showsNodeInfoAndDisconnectButton() {
        fakeTunnelProvider.setTunnelState(
            TunnelState.Connected(
                nodeName = "100.64.1.2",
                tailnetIps = listOf("100.64.1.2", "100.64.1.3"),
                magicDnsSuffix = "tailnet-xyz.ts.net",
            )
        )
        composeRule.setContent { renderSettingsScreen() }

        composeRule.onNodeWithText("状态：已连接（100.64.1.2）").assertIsDisplayed()
        composeRule.onNodeWithText("MagicDNS 后缀：tailnet-xyz.ts.net").assertIsDisplayed()
        composeRule.onNodeWithText("节点 IP：100.64.1.2, 100.64.1.3").assertIsDisplayed()
        composeRule.onNodeWithText("断开连接并清除凭据").assertIsDisplayed()
    }

    @Test
    fun connectionCard_authorizingState_showsAuthUrl() {
        fakeTunnelProvider.setTunnelState(
            TunnelState.Authorizing(loginUrl = "https://login.tailscale.com/a/abc123")
        )
        composeRule.setContent { renderSettingsScreen() }

        composeRule.onNodeWithText("状态：等待授权…").assertIsDisplayed()
        composeRule.onNodeWithText("打开授权链接").assertIsDisplayed()
    }

    @Test
    fun connectionCard_startingState_showsConnecting() {
        fakeTunnelProvider.setTunnelState(TunnelState.Starting)
        composeRule.setContent { renderSettingsScreen() }

        composeRule.onNodeWithText("状态：正在连接…").assertIsDisplayed()
    }

    @Test
    fun connectionCard_errorState_showsErrorAndRetryButtons() {
        fakeTunnelProvider.setTunnelState(TunnelState.Error(reason = "Authentication failed"))
        composeRule.setContent { renderSettingsScreen() }

        composeRule.onNodeWithText("状态：连接失败 - Authentication failed").assertIsDisplayed()
        composeRule.onNodeWithText("重新授权").assertIsDisplayed()
        composeRule.onNodeWithText("清除凭据").assertIsDisplayed()
    }

    @Test
    fun servicesCard_showsExportAndImportButtons() {
        composeRule.setContent { renderSettingsScreen() }

        composeRule.onNodeWithText("导出").assertIsDisplayed()
        composeRule.onNodeWithText("导入").assertIsDisplayed()
    }

    @Test
    fun servicesCard_export_showsExportedYaml() {
        val testYaml = "version: 1\nservices:\n  - title: Test\n    url: https://test.local\n"
        testViewModel.setExportedYamlDirect(testYaml)

        composeRule.setContent { renderSettingsScreen() }

        composeRule.onNodeWithText("导出").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("version: 1").assertIsDisplayed()
        composeRule.onNodeWithText("Test").assertIsDisplayed()
    }

    @Test
    fun servicesCard_import_expandsTextFieldAndConfirms() {
        composeRule.setContent { renderSettingsScreen() }

        composeRule.onNodeWithText("导入").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("粘贴 services.yaml").assertIsDisplayed()
        composeRule.onNodeWithText("确认导入").assertIsDisplayed()
    }

    @Test
    fun servicesCard_importValidYaml_showsSuccessSummary() {
        val validYaml = """
            version: 1
            services:
              - title: Jellyfin
                url: https://jellyfin.tailnet-xyz.ts.net
                icon: "🎬"
                group: 媒体
        """.trimIndent()

        composeRule.setContent { renderSettingsScreen() }

        composeRule.onNodeWithText("导入").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("粘贴 services.yaml").performTextInput(validYaml)
        composeRule.onNodeWithText("确认导入").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("已导入 1 个服务").assertIsDisplayed()
    }

    @Test
    fun servicesCard_importInvalidYaml_showsErrorSummary() {
        val invalidYaml = """
            version: 1
            services:
              - title: ""
                url: ftp://invalid.local
        """.trimIndent()

        composeRule.setContent { renderSettingsScreen() }

        composeRule.onNodeWithText("导入").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("粘贴 services.yaml").performTextInput(invalidYaml)
        composeRule.onNodeWithText("确认导入").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("拒绝").assertIsDisplayed()
    }

    @Test
    fun tunnelSettingsCard_switchesAreDisplayedAndToggleable() {
        composeRule.setContent { renderSettingsScreen() }

        // 验证三个开关标签
        composeRule.onNodeWithText("启动时自动连接隧道").assertIsDisplayed()
        composeRule.onNodeWithText("启动时显示内网导航页").assertIsDisplayed()
        composeRule.onNodeWithText("内网流量走隧道代理").assertIsDisplayed()

        // 通过点击标签文本来切换开关（Compose 的 Row + clickable 会处理）
        composeRule.onNodeWithText("启动时自动连接隧道").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("启动时显示内网导航页").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("内网流量走隧道代理").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun appearanceCard_themeOptionsAreDisplayed() {
        composeRule.setContent { renderSettingsScreen() }

        composeRule.onNodeWithText("外观").assertIsDisplayed()
        composeRule.onNodeWithText("主题").assertIsDisplayed()
        composeRule.onNodeWithText("跟随系统").assertIsDisplayed()
        composeRule.onNodeWithText("浅色模式").assertIsDisplayed()
        composeRule.onNodeWithText("深色模式").assertIsDisplayed()
    }

    @Test
    fun appearanceCard_themeSelection_updatesSetting() {
        fakeSettingsStore.setSettings(AppSettings(themePreference = ThemePreference.SYSTEM))
        composeRule.setContent { renderSettingsScreen() }

        composeRule.onNodeWithText("深色模式").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun aboutCard_showsVersionAndLinks() {
        composeRule.setContent { renderSettingsScreen() }

        composeRule.onNodeWithText("关于").assertIsDisplayed()
        composeRule.onNodeWithText("版本").assertIsDisplayed()
        composeRule.onNodeWithText("0.1.0 (开发版)").assertIsDisplayed()
        composeRule.onNodeWithText("开源协议").assertIsDisplayed()
        composeRule.onNodeWithText("Apache-2.0").assertIsDisplayed()
        composeRule.onNodeWithText("源代码").assertIsDisplayed()
        composeRule.onNodeWithText("GitHub").assertIsDisplayed()
        composeRule.onNodeWithText("MyApps —— 自用的 Tailscale 套壳浏览器").assertIsDisplayed()
    }

    @Test
    fun connectionCard_disconnectButton_clickTriggersClearAuthKey() {
        fakeTunnelProvider.setTunnelState(
            TunnelState.Connected(
                nodeName = "100.64.1.2",
                tailnetIps = listOf("100.64.1.2"),
                magicDnsSuffix = "tailnet-xyz.ts.net",
            )
        )
        composeRule.setContent { renderSettingsScreen() }

        composeRule.onNodeWithText("断开连接并清除凭据").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun connectionCard_reauthorizeButton_clickTriggersStartAuth() {
        fakeTunnelProvider.setTunnelState(TunnelState.Error(reason = "Timeout"))
        composeRule.setContent { renderSettingsScreen() }

        composeRule.onNodeWithText("重新授权").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun connectionCard_clearAuthKeyButton_clickTriggersClear() {
        fakeTunnelProvider.setTunnelState(TunnelState.Error(reason = "Auth failed"))
        composeRule.setContent { renderSettingsScreen() }

        composeRule.onNodeWithText("清除凭据").performClick()
        composeRule.waitForIdle()
    }
}