package app.core.tailscale

import app.core.model.TailnetSource
import app.core.security.SecretProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tailscale Local API 客户端（契约 C-07）。
 *
 * 核心验收项：**任何失败都返回 `null`，绝不抛异常**。
 * Local API 是可选增强——拿不到 secret 时 App 必须照常工作，所以「没有 secret」时
 * 连请求都不该发出去。
 */
class TailscaleLocalApiClientTest {

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

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    private fun client(secret: String?): OkHttpTailscaleLocalApiClient =
        OkHttpTailscaleLocalApiClient(
            client = okhttp3.OkHttpClient(),
            secrets = object : SecretProvider {
                override suspend fun localApiSecret(): String? = secret
                override suspend fun controlPlaneToken(): String? = null
            },
            baseUrl = baseUrl(),
        )

    @Test
    fun `without a secret nothing is requested and the status is null`() = runTest {
        assertNull(client(secret = null).status())
        assertNull(client(secret = null).selfIp())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a rejected secret means a null status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertNull(client(secret = "wrong").status())
    }

    @Test
    fun `a payload is parsed and mapped onto the domain model`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "BackendState": "Running",
                  "TailscaleIPs": ["100.101.102.103", "fd7a:115c:a1e0::1"],
                  "CurrentTailnet": { "Name": "hubdeck", "MagicDNSSuffix": "tailnet-xyz.ts.net" },
                  "Self": { "DNSName": "phone.hubdeck.tailnet-xyz.ts.net.", "Online": true }
                }
                """.trimIndent(),
            ),
        )

        val status = client(secret = "s3cret").status()
        assertNotNull(status)
        assertEquals("Running", status?.backendState)
        assertEquals(listOf("100.101.102.103", "fd7a:115c:a1e0::1"), status?.tailscaleIps)
        assertEquals("tailnet-xyz.ts.net", status?.currentTailnet?.magicDnsSuffix)
        assertEquals("hubdeck", status?.currentTailnet?.name)
        assertEquals("phone.hubdeck.tailnet-xyz.ts.net.", status?.self?.dnsName)

        val state = status!!.toTailnetState()
        assertTrue(state.connected)
        assertEquals("tailnet-xyz.ts.net", state.magicDnsSuffix)
        assertEquals("hubdeck", state.tailnetName)
        assertEquals("phone.hubdeck.tailnet-xyz.ts.net.", state.selfDnsName)
        assertEquals(listOf("100.101.102.103", "fd7a:115c:a1e0::1"), state.selfIps)
        assertEquals(TailnetSource.LOCAL_API, state.source)
        assertTrue(state.isUsable)
    }

    @Test
    fun `a backend that is not running means not connected`() = runTest {
        server.enqueue(MockResponse().setBody("""{"BackendState":"Stopped"}"""))
        val state = client(secret = "s3cret").status()!!.toTailnetState()
        assertFalse(state.connected)
        assertFalse(state.isUsable)
    }

    @Test
    fun `unknown fields do not break the parsing`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"BackendState":"Running","SomeFutureField":{"a":1}}"""),
        )
        assertEquals("Running", client(secret = "s3cret").status()?.backendState)
    }

    @Test
    fun `a broken payload means a null status`() = runTest {
        server.enqueue(MockResponse().setBody("not json at all"))
        assertNull(client(secret = "s3cret").status())
    }

    @Test
    fun `the secret travels in the authorization header`() = runTest {
        server.enqueue(MockResponse().setBody("""{"BackendState":"Running"}"""))
        client(secret = "s3cret").status()
        val request = server.takeRequest()
        assertEquals("/api/v2/status", request?.path)
        assertEquals("Bearer s3cret", request?.getHeader("Authorization"))
    }

    @Test
    fun `the self ip is trimmed`() = runTest {
        server.enqueue(MockResponse().setBody("100.101.102.103\n"))
        assertEquals("100.101.102.103", client(secret = "s3cret").selfIp())
        assertEquals("/api/v2/ip", server.takeRequest()?.path)
    }

    @Test
    fun `an empty self ip means null`() = runTest {
        server.enqueue(MockResponse().setBody("   "))
        assertNull(client(secret = "s3cret").selfIp())
    }
}
