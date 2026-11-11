package com.hubdeck.app.core.tailscale

import com.hubdeck.app.core.security.SecretProvider
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tailscale 控制平面客户端（契约 C-08）。
 *
 * 核心验收项：**任何失败都返回空列表，绝不抛异常**；只读、scope 最小化。
 */
class TailscaleControlApiClientTest {

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

    private fun client(token: String?): OkHttpTailscaleControlApiClient =
        OkHttpTailscaleControlApiClient(
            client = OkHttpClient(),
            secrets = object : SecretProvider {
                override suspend fun localApiSecret(): String? = null
                override suspend fun controlPlaneToken(): String? = token
            },
            baseUrl = server.url("/").toString().trimEnd('/'),
        )

    @Test
    fun `without a token nothing is requested and the list is empty`() = runTest {
        assertTrue(client(token = null).devices("example.com").isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a rejected token means an empty list`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(client(token = "bad").devices("example.com").isEmpty())
    }

    @Test
    fun `devices are mapped onto the domain model`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {
                    "id": "dev-1",
                    "name": "Phone",
                    "hostname": "phone.hubdeck.tailnet-xyz.ts.net",
                    "addresses": ["100.101.102.103"],
                    "clientConnectivity": { "connected": true, "lastSeen": "2026-11-10T00:00:00Z" },
                    "os": "android"
                  },
                  {
                    "id": "dev-2",
                    "name": "",
                    "hostname": "nas.hubdeck.tailnet-xyz.ts.net",
                    "addresses": ["100.64.0.2"]
                  }
                ]
                """.trimIndent(),
            ),
        )

        val devices = client(token = "token").devices("example.com")
        assertEquals(2, devices.size)

        assertEquals("dev-1", devices[0].id)
        assertEquals("Phone", devices[0].name)
        assertEquals("phone.hubdeck.tailnet-xyz.ts.net", devices[0].dnsName)
        assertEquals(listOf("100.101.102.103"), devices[0].addresses)
        assertTrue(devices[0].online)
        assertEquals("android", devices[0].os)

        // 名字为空时退回 hostname；没有 clientConnectivity 时按离线算。
        assertEquals("nas.hubdeck.tailnet-xyz.ts.net", devices[1].name)
        assertFalse(devices[1].online)
    }

    @Test
    fun `a broken payload means an empty list`() = runTest {
        server.enqueue(MockResponse().setBody("not json at all"))
        assertTrue(client(token = "token").devices("example.com").isEmpty())
    }

    @Test
    fun `the token travels in the authorization header`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        client(token = "token").devices("example.com")
        val request = server.takeRequest()
        assertEquals("/api/v2/tailnet/example.com/devices", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
    }
}
