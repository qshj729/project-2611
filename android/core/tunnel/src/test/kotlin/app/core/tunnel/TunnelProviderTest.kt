package app.core.tunnel

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * TunnelProvider 单测 - 验证 Auth Key 存取与初始化逻辑。
 *
 * 核心验收项：
 * - getAuthKey 返回 SharedPreferences 中保存的 key
 * - getAuthKey 忽略 placeholder 和 skipped 值
 * - initTunnel 正确标记初始化状态
 * - saveAuthKey/clearAuthKey 正确读写
 *
 * 放在 :core:tunnel —— TunnelProvider 随模块拆分已经搬到这里（:app 里那份是遗留副本）。
 */
class TunnelProviderTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockPrefs = mock(SharedPreferences::class.java)
        mockEditor = mock(SharedPreferences.Editor::class.java)

        `when`(mockContext.getSharedPreferences("hubdeck_prefs", Context.MODE_PRIVATE)).thenReturn(mockPrefs)
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(Mockito.anyString(), Mockito.anyString())).thenReturn(mockEditor)
        `when`(mockEditor.remove(Mockito.anyString())).thenReturn(mockEditor)
    }

    @Test
    fun `getAuthKey returns saved key from SharedPreferences`() {
        `when`(mockPrefs.getString("tailscale_auth_key_saved", null)).thenReturn("tskey-abc123")

        val key = TunnelProvider.getAuthKey(mockContext)

        assertEquals("tskey-abc123", key)
    }

    @Test
    fun `getAuthKey returns null for placeholder value`() {
        `when`(mockPrefs.getString("tailscale_auth_key_saved", null)).thenReturn("placeholder-key-no-env")

        val key = TunnelProvider.getAuthKey(mockContext)

        assertNull(key)
    }

    @Test
    fun `getAuthKey returns null for skipped value`() {
        `when`(mockPrefs.getString("tailscale_auth_key_saved", null)).thenReturn("skipped")

        val key = TunnelProvider.getAuthKey(mockContext)

        assertNull(key)
    }

    @Test
    fun `getAuthKey returns null for blank value`() {
        `when`(mockPrefs.getString("tailscale_auth_key_saved", null)).thenReturn("   ")

        val key = TunnelProvider.getAuthKey(mockContext)

        assertNull(key)
    }

    @Test
    fun `initTunnel returns auth key when available`() {
        `when`(mockPrefs.getString("tailscale_auth_key_saved", null)).thenReturn("tskey-test")

        val result = TunnelProvider.initTunnel(mockContext)

        assertEquals("tskey-test", result)
        assertTrue(TunnelProvider.isInitialized)
    }

    @Test
    fun `initTunnel returns null when no auth key`() {
        `when`(mockPrefs.getString("tailscale_auth_key_saved", null)).thenReturn(null)

        val result = TunnelProvider.initTunnel(mockContext)

        assertNull(result)
        assertTrue(TunnelProvider.isInitialized)
    }

    @Test
    fun `saveAuthKey writes to SharedPreferences`() {
        TunnelProvider.saveAuthKey(mockContext, "tskey-new")

        Mockito.verify(mockEditor).putString("tailscale_auth_key_saved", "tskey-new")
        Mockito.verify(mockEditor).apply()
    }

    @Test
    fun `clearAuthKey removes from SharedPreferences`() {
        TunnelProvider.clearAuthKey(mockContext)

        Mockito.verify(mockEditor).remove("tailscale_auth_key_saved")
    }

    @Test
    fun `hasAuthKey returns true when key exists`() {
        `when`(mockPrefs.getString("tailscale_auth_key_saved", null)).thenReturn("tskey-abc")

        assertTrue(TunnelProvider.hasAuthKey(mockContext))
    }

    @Test
    fun `hasAuthKey returns false when key missing`() {
        `when`(mockPrefs.getString("tailscale_auth_key_saved", null)).thenReturn(null)

        assertFalse(TunnelProvider.hasAuthKey(mockContext))
    }

    @Test
    fun `clearLegacyProxyProperties clears system properties`() {
        System.setProperty("http.proxyHost", "127.0.0.1")
        System.setProperty("http.proxyPort", "1080")

        TunnelProvider.clearLegacyProxyProperties()

        assertNull(System.getProperty("http.proxyHost"))
        assertNull(System.getProperty("http.proxyPort"))
        assertNull(System.getProperty("https.proxyHost"))
        assertNull(System.getProperty("https.proxyPort"))
    }
}
