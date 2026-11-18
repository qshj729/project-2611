package com.hubdeck.app.core.tunnel

import android.content.Context
import android.content.SharedPreferences
import com.hubdeck.app.core.model.TailnetState
import com.hubdeck.app.core.security.CredentialPort
import com.hubdeck.app.core.tailscale.TailnetRepository
import com.hubdeck.app.core.tailscale.TailscaleControlApiClient
import com.hubdeck.app.core.tailscale.TailscaleLocalApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * TunnelManager 单测（状态机 + 代理回落）。
 *
 * 覆盖：初始断开、无 auth key 不启动、启动→连接、错误→回落可重试、停止清理、重启。
 * 放在 :core:tunnel 而不是 :app —— 测试要碰 internal 的测试钩子（onNodeRunningForTest 等），
 * internal 只在同模块可见；:app 里那份是拆模块前的遗留副本，早就编译不过了。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TunnelManagerTest {

    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockContext: Context
    private lateinit var fakeProxyConfigurator: FakeProxyConfigurator
    private lateinit var fakeLocalProxyServer: FakeLocalProxyServer
    private lateinit var tunnelManager: TunnelManager

    @Before
    fun setUp() {
        // onNodeRunningForTest / onNodeErrorForTest 内部走 Dispatchers.Main，
        // JVM 单测里没有 Main dispatcher，必须先换成测试 dispatcher。
        Dispatchers.setMain(UnconfinedTestDispatcher())

        mockPrefs = mock(SharedPreferences::class.java)
        mockContext = mock(Context::class.java)
        `when`(mockContext.getSharedPreferences("hubdeck_prefs", Context.MODE_PRIVATE)).thenReturn(mockPrefs)

        fakeProxyConfigurator = FakeProxyConfigurator()
        fakeLocalProxyServer = FakeLocalProxyServer()

        tunnelManager = TunnelManager(
            context = mockContext,
            tailnetRepository = fakeTailnetRepository(),
            proxyConfigurator = fakeProxyConfigurator,
            localProxyServer = fakeLocalProxyServer,
            scope = CoroutineScope(Dispatchers.Main),
            // tsnet 的 native 库在 JVM 单测里跑不起来，用假节点替身。
            nodeStarter = { null },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is disconnected`() = runTest {
        val state = tunnelManager.tunnelState.first()
        assertTrue(state is TunnelState.Disconnected)
    }

    @Test
    fun `start with no auth key stays disconnected`() = runTest {
        // SharedPreferences 里没有存 auth key
        `when`(mockPrefs.getString("tailscale_auth_key_saved", null)).thenReturn(null)

        tunnelManager.startIfConfigured()

        val state = tunnelManager.tunnelState.first()
        assertTrue(state is TunnelState.Disconnected)
        assertFalse(tunnelManager.startAttempted)
    }

    @Test
    fun `start with auth key transitions through starting to connected`() = runTest {
        tunnelManager.start("tskey-test123")

        // Should transition to Starting
        var state = tunnelManager.tunnelState.first()
        assertTrue(state is TunnelState.Starting)

        // Simulate node RUNNING by calling onNodeRunning directly (since we can't mock TailnetNode)
        tunnelManager.onNodeRunningForTest(
            addresses = listOf("100.64.0.1", "host.tailnet-xyz.ts.net")
        )

        // Should be Connected
        state = tunnelManager.tunnelState.first()
        assertTrue(state is TunnelState.Connected)
        val connected = state as TunnelState.Connected
        assertEquals("100.64.0.1", connected.nodeName)
        assertEquals(listOf("100.64.0.1"), connected.tailnetIps)
        assertEquals("tailnet-xyz.ts.net", connected.magicDnsSuffix)

        // Proxy configurator should have been applied
        assertTrue(fakeProxyConfigurator.applied)
        assertTrue(fakeLocalProxyServer.started)
    }

    @Test
    fun `onNodeError falls back to error state then allows retry`() = runTest {
        tunnelManager.start("tskey-test123")

        // Trigger error
        tunnelManager.onNodeErrorForTest("Test error")

        var state = tunnelManager.tunnelState.first()
        assertTrue(state is TunnelState.Error)
        val error = state as TunnelState.Error
        assertEquals("Test error", error.reason)

        // Should allow retry
        assertFalse(tunnelManager.startAttempted)
        assertTrue(fakeProxyConfigurator.cleared)
        assertTrue(fakeLocalProxyServer.stopped)
    }

    @Test
    fun `stop clears state and stops proxy`() = runTest {
        tunnelManager.start("tskey-test123")
        tunnelManager.onNodeRunningForTest(listOf("100.64.0.1"))

        tunnelManager.stop()

        val state = tunnelManager.tunnelState.first()
        assertTrue(state is TunnelState.Disconnected)
        assertTrue(fakeProxyConfigurator.cleared)
        assertTrue(fakeLocalProxyServer.stopped)
    }

    @Test
    fun `restart stops the old node and starts again`() = runTest {
        tunnelManager.start("tskey-test123")
        tunnelManager.onNodeRunningForTest(listOf("100.64.0.1"))

        tunnelManager.restart("tskey-new456")

        // 旧节点已停，新一轮启动重新进入 Starting
        assertTrue(fakeLocalProxyServer.stopped)
        assertTrue(tunnelManager.tunnelState.value is TunnelState.Starting)
    }

    // Test doubles

    /** TailnetRepository 是 final 类，直接构造即可，不需要继承出假实现。 */
    private fun fakeTailnetRepository(): TailnetRepository = TailnetRepository(
        localApi = object : TailscaleLocalApiClient {
            override suspend fun status() = null
            override suspend fun selfIp() = null
        },
        controlApi = object : TailscaleControlApiClient {
            override suspend fun devices(tailnet: String) = emptyList()
        },
        credentials = object : CredentialPort {
            override suspend fun localApiSecret(): String? = null
            override suspend fun controlPlaneToken(): String? = null
            override suspend fun save(label: String, kind: String, secret: String) = Unit
        },
    )

    class FakeProxyConfigurator : ProxyConfigurator() {
        var applied = false
        var cleared = false
        var lastTailnetState: TailnetState? = null

        override fun apply(context: android.content.Context, tailnetState: com.hubdeck.app.core.model.TailnetState): Boolean {
            applied = true
            lastTailnetState = tailnetState
            return true
        }

        override fun clearProxy(context: android.content.Context) {
            cleared = true
        }
    }

    class FakeLocalProxyServer : LocalProxyServer() {
        var started = false
        var stopped = false
        var lastAuthKey: String? = null

        override fun start(context: android.content.Context, node: com.klethron.tsnet.TailnetNode?) {
            started = true
        }

        override fun stop() {
            stopped = true
        }
    }
}
