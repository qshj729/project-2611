package app.feature.settings

import app.core.data.settings.AppSettings
import app.core.data.settings.ThemePreference
import app.core.model.TunnelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test


/**
 * SettingsViewModel 单元测试（纯逻辑，无 UI，无 Robolectric）。
 *
 * 验证 ViewModel 状态流转与业务逻辑，不依赖 Compose 或 Android 框架。
 * 这部分测试在 JVM 上完全可跑通，作为 testDebugUnitTest 的门禁。
 */
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testViewModel_initialState() = runBlocking {
        val fakeSettingsStore = FakeSettingsStore()
        val fakeServicesStore = FakeInternalServicesStore()
        val fakeSecretStore = FakeSecretStore()
        val fakeTunnelProvider = FakeTunnelStateProvider()
        val viewModel = TestSettingsViewModel(
            settingsStore = fakeSettingsStore,
            servicesStore = fakeServicesStore,
            secretStore = fakeSecretStore,
            tunnelStateProvider = fakeTunnelProvider,
        )

        // 初始设置为默认值
        val settings = viewModel.settings.first()
        assertEquals(AppSettings(), settings)

        // 初始隧道状态为 Disconnected
        val tunnelState = viewModel.tunnelState.first()
        assertTrue(tunnelState is TunnelState.Disconnected)

        // 导出导入摘要初始为 null
        assertEquals(null, viewModel.exportedYaml.first())
        assertEquals(null, viewModel.importSummary.first())
    }

    @Test
    fun testViewModel_themePreferenceChange() = runBlocking {
        val fakeSettingsStore = FakeSettingsStore()
        val fakeServicesStore = FakeInternalServicesStore()
        val fakeSecretStore = FakeSecretStore()
        val fakeTunnelProvider = FakeTunnelStateProvider()
        val viewModel = TestSettingsViewModel(
            settingsStore = fakeSettingsStore,
            servicesStore = fakeServicesStore,
            secretStore = fakeSecretStore,
            tunnelStateProvider = fakeTunnelProvider,
        )

        // 修改主题偏好
        viewModel.setThemePreference(ThemePreference.DARK)

        val settings = viewModel.settings.first()
        assertEquals(ThemePreference.DARK, settings.themePreference)
    }

    @Test
    fun testViewModel_tunnelSettingsChange() = runBlocking {
        val fakeSettingsStore = FakeSettingsStore()
        val fakeServicesStore = FakeInternalServicesStore()
        val fakeSecretStore = FakeSecretStore()
        val fakeTunnelProvider = FakeTunnelStateProvider()
        val viewModel = TestSettingsViewModel(
            settingsStore = fakeSettingsStore,
            servicesStore = fakeServicesStore,
            secretStore = fakeSecretStore,
            tunnelStateProvider = fakeTunnelProvider,
        )

        viewModel.setAutoConnectTunnel(true)
        viewModel.setOpenHomeOnLaunch(false)
        viewModel.setUseTailnetProxy(true)

        val settings = viewModel.settings.first()
        assertTrue(settings.autoConnectTunnel)
        assertEquals(false, settings.openHomeOnLaunch)
        assertTrue(settings.useTailnetProxy)
    }

    @Test
    fun testViewModel_exportServices() = runBlocking {
        val fakeSettingsStore = FakeSettingsStore()
        val fakeServicesStore = FakeInternalServicesStore().apply {
            setServices(listOf(
                app.core.model.InternalService(
                    title = "Test Service",
                    url = "https://test.local",
                    icon = "🧪",
                    group = "测试"
                )
            ))
        }
        val fakeSecretStore = FakeSecretStore()
        val fakeTunnelProvider = FakeTunnelStateProvider()
        val viewModel = TestSettingsViewModel(
            settingsStore = fakeSettingsStore,
            servicesStore = fakeServicesStore,
            secretStore = fakeSecretStore,
            tunnelStateProvider = fakeTunnelProvider,
        )

        viewModel.exportServices()

        val exported = viewModel.exportedYaml.first()
        assertNotNull(exported)
        assertTrue(exported!!.contains("Test Service"))
        assertTrue(exported.contains("https://test.local"))
    }

    @Test
    fun testViewModel_importValidYaml() = runBlocking {
        val fakeSettingsStore = FakeSettingsStore()
        val fakeServicesStore = FakeInternalServicesStore()
        val fakeSecretStore = FakeSecretStore()
        val fakeTunnelProvider = FakeTunnelStateProvider()
        val viewModel = TestSettingsViewModel(
            settingsStore = fakeSettingsStore,
            servicesStore = fakeServicesStore,
            secretStore = fakeSecretStore,
            tunnelStateProvider = fakeTunnelProvider,
        )

        val validYaml = """
            version: 1
            services:
              - title: Imported Service
                url: https://imported.local
        """.trimIndent()

        viewModel.importServices(validYaml)

        val summary = viewModel.importSummary.first()
        assertNotNull(summary)
        assertTrue(summary!!.contains("已导入 1 个服务"))
    }

    @Test
    fun testViewModel_importInvalidYaml() = runBlocking {
        val fakeSettingsStore = FakeSettingsStore()
        val fakeServicesStore = FakeInternalServicesStore()
        val fakeSecretStore = FakeSecretStore()
        val fakeTunnelProvider = FakeTunnelStateProvider()
        val viewModel = TestSettingsViewModel(
            settingsStore = fakeSettingsStore,
            servicesStore = fakeServicesStore,
            secretStore = fakeSecretStore,
            tunnelStateProvider = fakeTunnelProvider,
        )

        val invalidYaml = """
            version: 1
            services:
              - title: ""
                url: ftp://invalid.local
        """.trimIndent()

        viewModel.importServices(invalidYaml)

        val summary = viewModel.importSummary.first()
        assertNotNull(summary)
        assertTrue(summary!!.contains("拒绝"))
    }

    @Test
    fun testViewModel_tunnelStatePropagation() = runBlocking {
        val fakeSettingsStore = FakeSettingsStore()
        val fakeServicesStore = FakeInternalServicesStore()
        val fakeSecretStore = FakeSecretStore()
        val fakeTunnelProvider = FakeTunnelStateProvider()
        val viewModel = TestSettingsViewModel(
            settingsStore = fakeSettingsStore,
            servicesStore = fakeServicesStore,
            secretStore = fakeSecretStore,
            tunnelStateProvider = fakeTunnelProvider,
        )

        // 模拟隧道状态变化
        fakeTunnelProvider.setTunnelState(TunnelState.Starting)
        val startingState = viewModel.tunnelState.first()
        assertTrue(startingState is TunnelState.Starting)

        fakeTunnelProvider.setTunnelState(
            TunnelState.Connected(
                nodeName = "test-node",
                tailnetIps = listOf("100.64.1.1"),
                magicDnsSuffix = "test.ts.net"
            )
        )
        val connectedState = viewModel.tunnelState.first()
        assertTrue(connectedState is TunnelState.Connected)
        assertEquals("test-node", (connectedState as TunnelState.Connected).nodeName)
    }
}