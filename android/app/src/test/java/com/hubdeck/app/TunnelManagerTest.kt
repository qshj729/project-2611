package com.hubdeck.app

import com.hubdeck.app.core.model.TailnetSource
import com.hubdeck.app.core.model.TailnetState
import com.hubdeck.app.core.tailscale.TailnetRepository
import com.hubdeck.app.core.tailscale.TailscaleLocalApiClient
import com.hubdeck.app.core.tailscale.TailscaleControlApiClient
import com.hubdeck.app.core.tunnel.LocalProxyServer
import com.hubdeck.app.core.tunnel.ProxyConfigurator
import com.hubdeck.app.core.tunnel.TunnelManager
import com.hubdeck.app.core.tunnel.TunnelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.unconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class TunnelManagerTest {

    private lateinit var testScope: TestCoroutineScope
    private lateinit var fakeContext: FakeContext
    private lateinit var fakeTailnetRepository: FakeTailnetRepository
    private lateinit var fakeProxyConfigurator: FakeProxyConfigurator
    private lateinit var fakeLocalProxyServer: FakeLocalProxyServer
    private lateinit var tunnelManager: TunnelManager

    @Before
    fun setUp() {
        testScope = TestCoroutineScope(unconfinedTestDispatcher)
        fakeContext = FakeContext()
        fakeTailnetRepository = FakeTailnetRepository()
        fakeProxyConfigurator = FakeProxyConfigurator()
        fakeLocalProxyServer = FakeLocalProxyServer()
        tunnelManager = TunnelManager(
            context = fakeContext,
            tailnetRepository = fakeTailnetRepository,
            proxyConfigurator = fakeProxyConfigurator,
            localProxyServer = fakeLocalProxyServer,
            scope = testScope,
        )
    }

    @Test
    fun `initial state is disconnected`() = runTest {
        val state = tunnelManager.tunnelState.first()
        assertTrue(state is TunnelState.Disconnected)
    }

    @Test
    fun `start with no auth key stays disconnected`() = runTest {
        // FakeContext returns null for auth key
        tunnelManager.startIfConfigured()
        testScope.advanceUntilIdle()

        val state = tunnelManager.tunnelState.first()
        assertTrue(state is TunnelState.Disconnected)
    }

    @Test
    fun `start with auth key transitions through starting to connected`() = runTest {
        // Setup fake context with auth key
        fakeContext.authKey = "tskey-test123"

        tunnelManager.start("tskey-test123")
        testScope.advanceUntilIdle()

        // Should transition to Starting
        var state = tunnelManager.tunnelState.first()
        assertTrue(state is TunnelState.Starting)

        // Simulate node RUNNING by calling onNodeRunning directly (since we can't mock TailnetNode)
        // This tests the state transition logic
        tunnelManager.onNodeRunningForTest(
            addresses = listOf("100.64.0.1", "host.tailnet-xyz.ts.net")
        )
        testScope.advanceUntilIdle()

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
        fakeContext.authKey = "tskey-test123"

        tunnelManager.start("tskey-test123")
        testScope.advanceUntilIdle()

        // Trigger error
        tunnelManager.onNodeErrorForTest("Test error")
        testScope.advanceUntilIdle()

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
        fakeContext.authKey = "tskey-test123"
        tunnelManager.start("tskey-test123")
        testScope.advanceUntilIdle()

        tunnelManager.onNodeRunningForTest(listOf("100.64.0.1"))
        testScope.advanceUntilIdle()

        tunnelManager.stop()
        testScope.advanceUntilIdle()

        val state = tunnelManager.tunnelState.first()
        assertTrue(state is TunnelState.Disconnected)
        assertTrue(fakeProxyConfigurator.cleared)
        assertTrue(fakeLocalProxyServer.stopped)
    }

    @Test
    fun `restart stops and starts again`() = runTest {
        fakeContext.authKey = "tskey-test123"
        tunnelManager.start("tskey-test123")
        testScope.advanceUntilIdle()
        tunnelManager.onNodeRunningForTest(listOf("100.64.0.1"))
        testScope.advanceUntilIdle()

        tunnelManager.restart("tskey-new456")
        testScope.advanceUntilIdle()

        assertTrue(fakeLocalProxyServer.stopped)
        assertEquals("tskey-new456", fakeLocalProxyServer.lastAuthKey)
    }

    // Test doubles

    class FakeContext : android.content.ContextWrapper(android.app.Application()) {
        var authKey: String? = null
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            return object : android.content.SharedPreferences {
                override fun getString(key: String, defValue: String?): String? {
                    if (key == "tailscale_auth_key_saved") return authKey
                    return defValue
                }
                override fun edit(): Editor = object : Editor {
                    override fun putString(key: String, value: String?): Editor = this
                    override fun apply() = Unit
                    override fun commit() = true
                    override fun remove(key: String): Editor = this
                    override fun clear(): Editor = this
                }
                override fun contains(key: String) = false
                override fun getAll() = emptyMap()
                override fun getBoolean(key: String, defValue: Boolean) = defValue
                override fun getInt(key: String, defValue: Int) = defValue
                override fun getLong(key: String, defValue: Long) = defValue
                override fun getFloat(key: String, defValue: Float) = defValue
                override fun getStringSet(key: String, defValue: Set<String>?) = defValue
                override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) = Unit
                override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) = Unit
            }
        }
    }

    class FakeTailnetRepository : TailnetRepository(
        localApi = object : TailscaleLocalApiClient {
            override suspend fun status() = null
            override suspend fun selfIp() = null
        },
        controlApi = object : TailscaleControlApiClient {
            override suspend fun devices(tailnet: String) = emptyList()
        },
        credentials = object : com.hubdeck.app.core.security.SecretProvider {
            override suspend fun localApiSecret() = null
            override suspend fun controlPlaneToken() = null
        }
    )

    class FakeProxyConfigurator : ProxyConfigurator() {
        var applied = false
        var cleared = false
        var lastTailnetState: TailnetState? = null

        override fun apply(context: android.content.Context, tailnetState: TailnetState): Boolean {
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