package com.hubdeck.app.core.network

import com.hubdeck.app.core.model.Endpoint
import com.hubdeck.app.core.model.HealthMode
import com.hubdeck.app.core.model.ProbeResult
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ServiceProbe 单测（契约 C-04）。
 *
 * 核心验收项：
 * - **永不抛异常**：所有失败都变成 `ProbeResult(reachable = false, reason = <中文文案>)`。
 * - 401/403 视为「服务活着但需要登录」→ `reachable = true, reason = "需要登录"`。
 * - 失败原因分类齐全：DNS 解析失败 / 连接超时 / 拒绝连接 / 需要登录 / HTTP 错误 / 未配置主机名 / 未配置端口 / 未启用探测 / 未知。
 */
class ServiceProbeTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun probe(): DefaultServiceProbe = DefaultServiceProbe(okhttp3.OkHttpClient())

    @Test
    fun `health mode none returns not reachable with reason`() = runTest {
        val result = probe().probe(
            endpoint = Endpoint("e1", "s1", com.hubdeck.app.core.model.EndpointScope.LAN, "http://{host}", "localhost", 80),
            mode = HealthMode.NONE,
            path = "/",
            timeoutMs = 1000,
        )
        assertFalse(result.reachable)
        assertEquals("未启用探测", result.reason)
        assertNull(result.latencyMs)
    }

    @Test
    fun `tcp probe with missing host returns not reachable`() = runTest {
        val result = probe().probe(
            endpoint = Endpoint("e1", "s1", com.hubdeck.app.core.model.EndpointScope.LAN, "http://{host}", null, 80),
            mode = HealthMode.TCP,
            path = "/",
            timeoutMs = 1000,
        )
        assertFalse(result.reachable)
        assertEquals("未配置主机名", result.reason)
    }

    @Test
    fun `tcp probe with missing port returns not reachable`() = runTest {
        val result = probe().probe(
            endpoint = Endpoint("e1", "s1", com.hubdeck.app.core.model.EndpointScope.LAN, "http://{host}", "localhost", null),
            mode = HealthMode.TCP,
            path = "/",
            timeoutMs = 1000,
        )
        assertFalse(result.reachable)
        assertEquals("未配置端口", result.reason)
    }

    @Test
    fun `tcp probe dns failure returns dns resolution failed`() = runTest {
        val result = probe().probe(
            endpoint = Endpoint("e1", "s1", com.hubdeck.app.core.model.EndpointScope.LAN, "http://{host}", "nonexistent.invalid.host.example", 80),
            mode = HealthMode.TCP,
            path = "/",
            timeoutMs = 1000,
        )
        assertFalse(result.reachable)
        assertEquals("DNS 解析失败", result.reason)
    }

    @Test
    fun `tcp probe connection timeout returns timeout`() = runTest {
        // Use a non-routable IP that will timeout
        val result = probe().probe(
            endpoint = Endpoint("e1", "s1", com.hubdeck.app.core.model.EndpointScope.LAN, "http://{host}", "10.255.255.1", 80),
            mode = HealthMode.TCP,
            path = "/",
            timeoutMs = 100, // Very short timeout
        )
        assertFalse(result.reachable)
        // Could be "连接超时" or "连接失败" depending on platform
        assertTrue(result.reason == "连接超时" || result.reason == "连接失败")
    }

    @Test
    fun `tcp probe connection refused returns connection refused`() = runTest {
        // Port 1 is typically not listening and will refuse connection quickly
        val result = probe().probe(
            endpoint = Endpoint("e1", "s1", com.hubdeck.app.core.model.EndpointScope.LAN, "http://{host}", "127.0.0.1", 1),
            mode = HealthMode.TCP,
            path = "/",
            timeoutMs = 1000,
        )
        assertFalse(result.reachable)
        // On some systems this may be "连接失败" or "连接被拒绝"
        assertTrue(result.reason?.contains("拒绝") == true || result.reason == "连接失败" || result.reason?.contains("REFUSED") == true)
    }

    @Test
    fun `http probe 200 returns reachable`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val result = probe().probe(
            endpoint = Endpoint("e1", "s1", com.hubdeck.app.core.model.EndpointScope.LAN, "http://{host}:{port}", "localhost", server.port),
            mode = HealthMode.HTTP,
            path = "/health",
            timeoutMs = 2000,
        )
        assertTrue(result.reachable)
        assertTrue(result.latencyMs != null && result.latencyMs!! >= 0)
        assertNull(result.reason)
    }

    @Test
    fun `http probe 401 returns reachable with need login`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = probe().probe(
            endpoint = Endpoint("e1", "s1", com.hubdeck.app.core.model.EndpointScope.LAN, "http://{host}:{port}", "localhost", server.port),
            mode = HealthMode.HTTP,
            path = "/health",
            timeoutMs = 2000,
        )
        assertTrue(result.reachable)
        assertEquals("需要登录", result.reason)
        assertTrue(result.latencyMs != null && result.latencyMs!! >= 0)
    }

    @Test
    fun `http probe 403 returns reachable with need login`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        val result = probe().probe(
            endpoint = Endpoint("e1", "s1", com.hubdeck.app.core.model.EndpointScope.LAN, "http://{host}:{port}", "localhost", server.port),
            mode = HealthMode.HTTP,
            path = "/health",
            timeoutMs = 2000,
        )
        assertTrue(result.reachable)
        assertEquals("需要登录", result.reason)
    }

    @Test
    fun `http probe 500 returns not reachable with http code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = probe().probe(
            endpoint = Endpoint("e1", "s1", com.hubdeck.app.core.model.EndpointScope.LAN, "http://{host}:{port}", "localhost", server.port),
            mode = HealthMode.HTTP,
            path = "/health",
            timeoutMs = 2000,
        )
        assertFalse(result.reachable)
        assertEquals("HTTP 500", result.reason)
    }

    @Test
    fun `http probe 404 returns not reachable with http code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = probe().probe(
            endpoint = Endpoint("e1", "s1", com.hubdeck.app.core.model.EndpointScope.LAN, "http://{host}:{port}", "localhost", server.port),
            mode = HealthMode.HTTP,
            path = "/health",
            timeoutMs = 2000,
        )
        assertFalse(result.reachable)
        assertEquals("HTTP 404", result.reason)
    }

    @Test
    fun `http probe dns failure returns dns resolution failed`() = runTest {
        val result = probe().probe(
            endpoint = Endpoint("e1", "s1", com.hubdeck.app.core.model.EndpointScope.LAN, "http://{host}:{port}", "nonexistent.invalid.host.example", 80),
            mode = HealthMode.HTTP,
            path = "/health",
            timeoutMs = 1000,
        )
        assertFalse(result.reachable)
        assertEquals("DNS 解析失败", result.reason)
    }

    @Test
    fun `http probe timeout returns request timeout`() = runTest {
        // Use a socket policy that never responds
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val result = probe().probe(
            endpoint = Endpoint("e1", "s1", com.hubdeck.app.core.model.EndpointScope.LAN, "http://{host}:{port}", "localhost", server.port),
            mode = HealthMode.HTTP,
            path = "/health",
            timeoutMs = 100, // Very short timeout
        )
        assertFalse(result.reachable)
        assertEquals("请求超时", result.reason)
    }

    @Test
    fun `http probe with https scheme uses https`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val result = probe().probe(
            endpoint = Endpoint("e1", "s1", com.hubdeck.app.core.model.EndpointScope.LAN, "https://{host}:{port}", "localhost", server.port),
            mode = HealthMode.HTTP,
            path = "/health",
            timeoutMs = 2000,
        )
        assertTrue(result.reachable)
        val request = server.takeRequest()
        assertEquals("https", request?.requestUrl?.scheme())
    }
}