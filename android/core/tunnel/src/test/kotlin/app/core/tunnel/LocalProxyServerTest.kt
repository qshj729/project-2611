package app.core.tunnel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LocalProxyServer 单测 - 验证尾网/公网路由判断逻辑。
 *
 * 核心验收项：
 * - isTailnetHost 正确识别 100.64.0.0/10 地址
 * - isTailnetHost 正确识别 *.ts.net 域名
 * - isTailnetHost 拒绝公网 IP/域名
 * - isTailnetIp 正确解析 IPv4 尾网地址
 *
 * 放在 :core:tunnel —— LocalProxyServer 随模块拆分已经搬到这里（:app 里那份是遗留副本）。
 */
class LocalProxyServerTest {

    private val proxyServer = LocalProxyServer()

    @Test
    fun `tailnet IP inside the 100x64x0x0 slash 10 range is recognized`() {
        assertTrue(proxyServer.isTailnetHost("100.64.0.1"))
        assertTrue(proxyServer.isTailnetHost("100.127.255.254"))
        assertTrue(proxyServer.isTailnetHost("100.96.1.1")) // Middle of range
    }

    @Test
    fun `tailnet IP outside range is not recognized`() {
        assertFalse(proxyServer.isTailnetHost("100.63.255.255")) // Just below
        assertFalse(proxyServer.isTailnetHost("100.128.0.0")) // Just above
        assertFalse(proxyServer.isTailnetHost("192.168.1.1")) // Private LAN
        assertFalse(proxyServer.isTailnetHost("10.0.0.1")) // Private LAN
        assertFalse(proxyServer.isTailnetHost("172.16.0.1")) // Private LAN
        assertFalse(proxyServer.isTailnetHost("8.8.8.8")) // Public DNS
        assertFalse(proxyServer.isTailnetHost("1.1.1.1")) // Public DNS
    }

    @Test
    fun `magic dns ts dot net domains are recognized`() {
        assertTrue(proxyServer.isTailnetHost("host.tailnet-xyz.ts.net"))
        assertTrue(proxyServer.isTailnetHost("nas.ts.net"))
        assertTrue(proxyServer.isTailnetHost("service.my-tailnet.ts.net"))
        assertTrue(proxyServer.isTailnetHost("HOST.TAILNET.TS.NET")) // Case insensitive
    }

    @Test
    fun `non-ts-net domains are not recognized`() {
        assertFalse(proxyServer.isTailnetHost("example.com"))
        assertFalse(proxyServer.isTailnetHost("github.com"))
        assertFalse(proxyServer.isTailnetHost("localhost"))
        assertFalse(proxyServer.isTailnetHost("myserver.local"))
        assertFalse(proxyServer.isTailnetHost("192.168.1.100"))
    }

    @Test
    fun `isTailnetIp parses IPv4 correctly`() {
        assertTrue(proxyServer.isTailnetIp("100.64.0.1"))
        assertTrue(proxyServer.isTailnetIp("100.127.255.254"))
        assertFalse(proxyServer.isTailnetIp("100.63.255.255"))
        assertFalse(proxyServer.isTailnetIp("100.128.0.0"))
        assertFalse(proxyServer.isTailnetIp("192.168.1.1"))
        assertFalse(proxyServer.isTailnetIp("not-an-ip"))
        assertFalse(proxyServer.isTailnetIp("")) // Empty string
    }

    @Test
    fun `invalid hostnames handled gracefully`() {
        // Should not throw, just return false
        assertFalse(proxyServer.isTailnetHost("invalid..host"))
        assertFalse(proxyServer.isTailnetHost(""))
        assertFalse(proxyServer.isTailnetHost("host with spaces"))
    }
}
