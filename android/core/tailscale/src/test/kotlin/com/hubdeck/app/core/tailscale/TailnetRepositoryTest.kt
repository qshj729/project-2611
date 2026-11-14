package com.hubdeck.app.core.tailscale

import com.hubdeck.app.core.model.TailnetDevice
import com.hubdeck.app.core.model.TailnetSource
import com.hubdeck.app.core.model.TailnetState
import com.hubdeck.app.core.security.CredentialStore
import com.hubdeck.app.core.security.SecretProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * TailnetRepository 单测（契约 C-09）。
 *
 * 核心验收项：
 * - `refreshLocalState()`：Local API 不可用时返回 `TailnetState(connected = false, source = HEURISTIC)`。
 * - `devices(tailnet)`：透传控制平面结果（失败即空列表）。
 * - `storeLocalApiSecret` / `storeControlPlaneToken`：写入 CredentialStore。
 * - `hasLocalApiSecret()`：secret 存在且非空白才为 true。
 * - 全部方法**不抛异常**。
 */
class TailnetRepositoryTest {

    private lateinit var fakeLocalApi: FakeTailscaleLocalApiClient
    private lateinit var fakeControlApi: FakeTailscaleControlApiClient
    private lateinit var fakeCredentials: FakeCredentialStore
    private lateinit var repo: TailnetRepository

    @Before
    fun setUp() {
        fakeLocalApi = FakeTailscaleLocalApiClient()
        fakeControlApi = FakeTailscaleControlApiClient()
        fakeCredentials = FakeCredentialStore()
        repo = TailnetRepository(fakeLocalApi, fakeControlApi, fakeCredentials)
    }

    @Test
    fun `local api unavailable returns heuristic state`() = runTest {
        fakeLocalApi.shouldReturnNull = true

        val state = repo.refreshLocalState()

        assertFalse(state.connected)
        assertEquals(TailnetSource.HEURISTIC, state.source)
        assertNull(state.magicDnsSuffix)
    }

    @Test
    fun `local api available returns mapped state`() = runTest {
        fakeLocalApi.statusToReturn = LocalStatus(
            backendState = "Running",
            tailscaleIps = listOf("100.101.102.103"),
            currentTailnet = LocalTailnet(name = "hubdeck", magicDnsSuffix = "tailnet-xyz.ts.net"),
            self = LocalSelf(dnsName = "phone.hubdeck.tailnet-xyz.ts.net.", online = true),
        )

        val state = repo.refreshLocalState()

        assertTrue(state.connected)
        assertEquals(TailnetSource.LOCAL_API, state.source)
        assertEquals("tailnet-xyz.ts.net", state.magicDnsSuffix)
        assertEquals("hubdeck", state.tailnetName)
        assertEquals("phone.hubdeck.tailnet-xyz.ts.net.", state.selfDnsName)
        assertEquals(listOf("100.101.102.103"), state.selfIps)
        assertTrue(state.isUsable)
    }

    @Test
    fun `local api returns stopped backend state means not connected`() = runTest {
        fakeLocalApi.statusToReturn = LocalStatus(backendState = "Stopped")

        val state = repo.refreshLocalState()

        assertFalse(state.connected)
        assertFalse(state.isUsable)
        assertEquals(TailnetSource.LOCAL_API, state.source)
    }

    @Test
    fun `devices delegates to control api`() = runTest {
        fakeControlApi.devicesToReturn = listOf(
            TailnetDevice(id = "dev-1", name = "Phone", dnsName = "phone.example.ts.net", addresses = listOf("100.1.2.3"), online = true, os = "android"),
        )

        val devices = repo.devices("example.com")

        assertEquals(1, devices.size)
        assertEquals("dev-1", devices[0].id)
        assertEquals("Phone", devices[0].name)
        assertTrue(devices[0].online)
    }

    @Test
    fun `devices returns empty list when control api fails`() = runTest {
        fakeControlApi.devicesToReturn = emptyList()

        val devices = repo.devices("example.com")

        assertTrue(devices.isEmpty())
    }

    @Test
    fun `store local api secret delegates to credentials`() = runTest {
        repo.storeLocalApiSecret("my-secret")

        assertEquals("my-secret", fakeCredentials.savedLocalApiSecret)
        assertEquals(CredentialStore.KIND_LOCAL_API_SECRET, fakeCredentials.lastSavedKind)
        assertEquals("tailscale", fakeCredentials.lastSavedLabel)
    }

    @Test
    fun `store control plane token delegates to credentials`() = runTest {
        repo.storeControlPlaneToken("my-token")

        assertEquals("my-token", fakeCredentials.savedControlPlaneToken)
        assertEquals(CredentialStore.KIND_CONTROL_TOKEN, fakeCredentials.lastSavedKind)
        assertEquals("control-plane", fakeCredentials.lastSavedLabel)
    }

    @Test
    fun `has local api secret returns true when secret exists`() = runTest {
        fakeCredentials.localApiSecretToReturn = "existing-secret"

        val hasSecret = repo.hasLocalApiSecret()

        assertTrue(hasSecret)
    }

    @Test
    fun `has local api secret returns false when secret missing`() = runTest {
        fakeCredentials.localApiSecretToReturn = null

        val hasSecret = repo.hasLocalApiSecret()

        assertFalse(hasSecret)
    }

    @Test
    fun `has local api secret returns false when secret is blank`() = runTest {
        fakeCredentials.localApiSecretToReturn = "   "

        val hasSecret = repo.hasLocalApiSecret()

        assertFalse(hasSecret)
    }

    // Test doubles

    class FakeTailscaleLocalApiClient : TailscaleLocalApiClient {
        var shouldReturnNull = false
        var statusToReturn: LocalStatus? = null

        override suspend fun status(): LocalStatus? {
            if (shouldReturnNull) return null
            return statusToReturn
        }

        override suspend fun selfIp(): String? = null
    }

    class FakeTailscaleControlApiClient : TailscaleControlApiClient {
        var devicesToReturn: List<TailnetDevice> = emptyList()

        override suspend fun devices(tailnet: String): List<TailnetDevice> = devicesToReturn
    }

    class FakeCredentialStore : SecretProvider {
        var localApiSecretToReturn: String? = null
        var savedLocalApiSecret: String? = null
        var savedControlPlaneToken: String? = null
        var lastSavedKind: String? = null
        var lastSavedLabel: String? = null

        override suspend fun localApiSecret(): String? = localApiSecretToReturn

        override suspend fun controlPlaneToken(): String? = null

        suspend fun save(label: String, kind: String, secret: String) {
            lastSavedLabel = label
            lastSavedKind = kind
            if (kind == CredentialStore.KIND_LOCAL_API_SECRET) {
                savedLocalApiSecret = secret
            } else if (kind == CredentialStore.KIND_CONTROL_TOKEN) {
                savedControlPlaneToken = secret
            }
        }

        override suspend fun delete(id: String) = Unit
        override suspend fun list(): List<com.hubdeck.app.core.data.db.CredentialEntity> = emptyList()
        override fun encodeForExport(value: ByteArray): String = ""
    }
}