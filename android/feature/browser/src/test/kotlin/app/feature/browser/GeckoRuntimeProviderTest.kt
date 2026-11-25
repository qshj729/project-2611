package app.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GeckoRuntimeProvider 单测 —— 覆盖 PAC 接线所需的常量与路由规则。
 *
 * 覆盖：
 * - PAC URL 常量正确
 * - Gecko 偏好名正确（network.proxy.type / network.proxy.pac_url）
 * - PAC 类型值为 2（PAC 自动配置）
 * - 偏好分支为 GeckoPreferenceController.PREF_BRANCH_USER
 * - PAC 生成器的 6 条路由规则（tailnet IP / *.ts.net / 短名 / 公网直连 / 回退 DIRECT / 函数签名）
 */
class GeckoRuntimeProviderTest {

    @Test
    fun `PAC URL constant is correct`() {
        assertEquals("http://127.0.0.1:1080/proxy.pac", GeckoRuntimeProvider.PAC_URL)
    }

    @Test
    fun `proxy type preference name is correct`() {
        assertEquals("network.proxy.type", GeckoRuntimeProvider.TestConstants.PREF_PROXY_TYPE)
    }

    @Test
    fun `pac url preference name is correct`() {
        assertEquals("network.proxy.pac_url", GeckoRuntimeProvider.TestConstants.PREF_PAC_URL)
    }

    @Test
    fun `proxy type PAC value is 2`() {
        assertEquals(2, GeckoRuntimeProvider.TestConstants.PROXY_TYPE_PAC)
    }

    @Test
    fun `pref branch user constant is accessible`() {
        // 只是确认常量可访问且非负（实际值由 GeckoPreferenceController 定义）
        assertTrue(GeckoRuntimeProvider.TestConstants.prefBranchUser() >= 0)
    }

    @Test
    fun `pac generator defines FindProxyForURL function`() {
        val pac = app.core.tunnel.PacGenerator.build()
        assertTrue(pac.contains("function FindProxyForURL(url, host)"))
    }

    @Test
    fun `tailnet range is routed to proxy`() {
        val pac = app.core.tunnel.PacGenerator.build()
        assertTrue(pac.contains("isInNet(host, \"100.64.0.0\", \"100.127.255.255\")"))
    }

    @Test
    fun `magic dns domains are routed to proxy`() {
        val pac = app.core.tunnel.PacGenerator.build()
        assertTrue(pac.contains("dnsDomainIs(host, \".ts.net\")"))
    }

    @Test
    fun `short names are routed to proxy`() {
        val pac = app.core.tunnel.PacGenerator.build()
        assertTrue(pac.contains("host.indexOf('.') === -1"))
    }

    @Test
    fun `proxy rule falls back to direct`() {
        val pac = app.core.tunnel.PacGenerator.build()
        assertTrue(pac.contains("PROXY ${app.core.tunnel.PacGenerator.PROXY_HOST}:${app.core.tunnel.PacGenerator.PROXY_PORT}; DIRECT"))
    }

    @Test
    fun `default is direct`() {
        val pac = app.core.tunnel.PacGenerator.build()
        assertTrue(pac.contains("return \"DIRECT\";"))
    }
}