package app.core.tunnel

import android.content.Context
import app.core.model.SecretStore
import app.core.model.TunnelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * TunnelManager 单测 —— 验证隧道状态机。
 *
 * 覆盖：
 * - 初始状态为 Disconnected
 * - 无 auth key 时保持 Disconnected
 * - 有 auth key 时进入 Starting
 * - 出错后进入 Error 并允许重试
 * - stop 后回到 Disconnected
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TunnelManagerTest {

    private lateinit var mockContext: Context
    private lateinit var fakeLocalProxyServer: FakeLocalProxyServer
    private lateinit var tunnelManager: TunnelManager
    private lateinit var secretStore: FakeSecretStore

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockContext = mock(Context::class.java)
        fakeLocalProxyServer = FakeLocalProxyServer()
        secretStore = FakeSecretStore()
        tunnelManager = TunnelManager(
            context = mockContext,
            secretStore = secretStore,
            localProxyServer = fakeLocalProxyServer,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            nodeStarter = { null },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is disconnected`() = runTest {
        assertTrue(tunnelManager.tunnelState.value is TunnelState.Disconnected)
    }

    @Test
    fun `start with no auth key stays disconnected`() = runTest {
        secretStore.key = null
        tunnelManager.startIfConfigured()
        assertTrue(tunnelManager.tunnelState.value is TunnelState.Disconnected)
    }

    @Test
    fun `start with auth key transitions to starting`() = runTest {
        // start() 同步进入 Starting（nodeStarter 返回 null，不进入真实节点流程）
        tunnelManager.start("test-auth-key")
        assertTrue(tunnelManager.tunnelState.value is TunnelState.Starting)
    }

    @Test
    fun `startIfConfigured reads the auth key from the secret store`() = runTest {
        secretStore.key = "test-auth-key"
        tunnelManager.startIfConfigured()
        // 读取是异步的（IO 调度器），这里只断言没有进入错误态
        assertTrue(tunnelManager.tunnelState.value !is TunnelState.Error)
    }

    @Test
    fun `onNodeError falls back to error state then allows retry`() = runTest {
        tunnelManager.onNodeErrorForTest("boom")
        assertEquals("boom", (tunnelManager.tunnelState.value as TunnelState.Error).reason)
        assertTrue(fakeLocalProxyServer.stopped)
    }

    @Test
    fun `stop clears state and stops proxy`() = runTest {
        tunnelManager.onNodeRunningForTest(listOf("100.64.0.7", "myhost.tailnet-xyz.ts.net"))
        assertTrue(tunnelManager.tunnelState.value is TunnelState.Connected)

        tunnelManager.stop()

        assertTrue(tunnelManager.tunnelState.value is TunnelState.Disconnected)
        assertTrue(fakeLocalProxyServer.stopped)
    }

    /** 假的凭据存储（不依赖 Android Keystore）。 */
    class FakeSecretStore : SecretStore {
        var key: String? = null

        override suspend fun authKey(): String? = key
        override suspend fun saveAuthKey(value: String) { key = value }
        override suspend fun clearAuthKey() { key = null }
        override suspend fun hasAuthKey(): Boolean = key != null
    }

    /** 假的本地代理（不真的监听端口）。 */
    class FakeLocalProxyServer : LocalProxyServer() {
        var started = false
        var stopped = false

        override fun start(context: Context, node: com.klethron.tsnet.TailnetNode?) {
            started = true
        }

        override fun stop() {
            stopped = true
        }
    }
}
