package app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ProxyRule 单测（契约 C-04 同类）。
 *
 * 核心验收项：
 * - **永不抛异常**：所有无效输入返回 DIRECT / false，不抛异常。
 * - 覆盖两态：connected=false ⇒ 全 DIRECT；connected=true ⇒ 规则生效。
 * - 覆盖失败路径：非法 URL、非法 IP、空字符串、IPv6、非标准端口写法。
 * - 用例数 > 0、失败 0（CI 绿）。
 */
class ProxyRuleTest {

    // ------------------------------------------------------------
    // decideProxy(url, connected)
    // ------------------------------------------------------------

    @Test
    fun `disconnected state returns DIRECT for all inputs`() {
        val inputs = listOf(
            "http://100.64.0.5:8080",
            "https://nas.tailnet.ts.net",
            "https://github.com",
            "http://localhost:3000",
            "http://192.168.1.50",
            "invalid-url",
            "",
        )
        for (url in inputs) {
            assertEquals("disconnected: $url", ProxyDecision.DIRECT, decideProxy(url, connected = false))
        }
    }

    @Test
    fun `connected tailnet IPv4 in 100 64 10 returns PROXY`() {
        val tailnetIps = listOf(
            "100.64.0.1",
            "100.64.255.255",
            "100.127.255.255", // upper bound of /10
            "http://100.64.0.5:8080",
            "https://100.100.50.1:443",
        )
        for (input in tailnetIps) {
            assertEquals("tailnet IP: $input", ProxyDecision.PROXY, decideProxy(input, connected = true))
        }
    }

    @Test
    fun `connected tailnet IPv4 outside 100 64 10 returns DIRECT`() {
        val nonTailnetIps = listOf(
            "100.63.255.255",  // just below
            "100.128.0.1",     // just above
            "10.0.0.1",        // RFC1918
            "192.168.1.1",     // RFC1918
            "172.16.0.1",      // RFC1918
            "8.8.8.8",         // public
            "1.1.1.1",         // public
        )
        for (ip in nonTailnetIps) {
            assertEquals("non-tailnet IP: $ip", ProxyDecision.DIRECT, decideProxy("http://$ip", connected = true))
        }
    }

    @Test
    fun `connected MagicDNS ts net FQDN returns PROXY`() {
        val magicDns = listOf(
            "nas.ts.net",
            "nas.tailnet.ts.net",
            "my-server.my-tailnet.ts.net",
            "http://nas.ts.net",
            "https://nas.tailnet.ts.net:8080",
            "NAS.TAILNET.TS.NET", // case insensitive
        )
        for (host in magicDns) {
            assertEquals("MagicDNS: $host", ProxyDecision.PROXY, decideProxy(host, connected = true))
        }
    }

    @Test
    fun `connected non ts net hostnames return DIRECT`() {
        val hosts = listOf(
            "github.com",
            "localhost",
            "nas.local",
            "server.lan",
            "192.168.1.50",
            "example.com",
            "ts.net",           // bare domain, not subdomain
            "something.ts.net.extra", // not ending with .ts.net
        )
        for (host in hosts) {
            assertEquals("non-MagicDNS: $host", ProxyDecision.DIRECT, decideProxy("http://$host", connected = true))
        }
    }

    @Test
    fun `connected malformed URLs return DIRECT never throw`() {
        val bad = listOf(
            "not-a-url",
            "http://",
            "://missing-scheme",
            "http://[::1]", // IPv6 - not in tailnet range
            "http://[::1]:8080",
            "",
            "   ",
        )
        for (input in bad) {
            assertEquals("malformed: '$input'", ProxyDecision.DIRECT, decideProxy(input, connected = true))
        }
    }

    // ------------------------------------------------------------
    // decideProxyForHost(host, connected)
    // ------------------------------------------------------------

    @Test
    fun `decideProxyForHost disconnected returns DIRECT`() {
        assertEquals(ProxyDecision.DIRECT, decideProxyForHost("100.64.0.5", connected = false))
        assertEquals(ProxyDecision.DIRECT, decideProxyForHost("nas.ts.net", connected = false))
    }

    @Test
    fun `decideProxyForHost connected tailnet IP returns PROXY`() {
        assertEquals(ProxyDecision.PROXY, decideProxyForHost("100.64.0.5", connected = true))
        assertEquals(ProxyDecision.PROXY, decideProxyForHost("100.100.50.1", connected = true))
    }

    @Test
    fun `decideProxyForHost connected non-tailnet IP returns DIRECT`() {
        assertEquals(ProxyDecision.DIRECT, decideProxyForHost("10.0.0.1", connected = true))
        assertEquals(ProxyDecision.DIRECT, decideProxyForHost("192.168.1.1", connected = true))
        assertEquals(ProxyDecision.DIRECT, decideProxyForHost("8.8.8.8", connected = true))
    }

    @Test
    fun `decideProxyForHost connected MagicDNS returns PROXY`() {
        assertEquals(ProxyDecision.PROXY, decideProxyForHost("nas.ts.net", connected = true))
        assertEquals(ProxyDecision.PROXY, decideProxyForHost("NAS.TAILNET.TS.NET", connected = true))
    }

    @Test
    fun `decideProxyForHost connected non-MagicDNS returns DIRECT`() {
        assertEquals(ProxyDecision.DIRECT, decideProxyForHost("github.com", connected = true))
        assertEquals(ProxyDecision.DIRECT, decideProxyForHost("localhost", connected = true))
        assertEquals(ProxyDecision.DIRECT, decideProxyForHost("nas.local", connected = true))
    }

    @Test
    fun `decideProxyForHost invalid input returns DIRECT`() {
        assertEquals(ProxyDecision.DIRECT, decideProxyForHost("", connected = true))
        assertEquals(ProxyDecision.DIRECT, decideProxyForHost("not-an-ip", connected = true))
        assertEquals(ProxyDecision.DIRECT, decideProxyForHost("[::1]", connected = true))
    }

    // ------------------------------------------------------------
    // extractHost
    // ------------------------------------------------------------

    @Test
    fun `extractHost parses standard URLs`() {
        assertEquals("100.64.0.5", extractHost("http://100.64.0.5:8080"))
        assertEquals("nas.ts.net", extractHost("https://nas.ts.net/path"))
        assertEquals("github.com", extractHost("https://github.com"))
    }

    @Test
    fun `extractHost parses bare host and host port`() {
        assertEquals("100.64.0.5", extractHost("100.64.0.5"))
        assertEquals("100.64.0.5", extractHost("100.64.0.5:8080"))
        assertEquals("nas.ts.net", extractHost("nas.ts.net"))
        assertEquals("nas.ts.net", extractHost("nas.ts.net:443"))
    }

    @Test
    fun `extractHost parses IPv6 in brackets`() {
        assertEquals("::1", extractHost("[::1]"))
        assertEquals("::1", extractHost("[::1]:8080"))
        assertEquals("2001:db8::1", extractHost("[2001:db8::1]:443"))
    }

    @Test
    fun `extractHost returns null for invalid input`() {
        assertEquals(null, extractHost(""))
        assertEquals(null, extractHost("   "))
        assertEquals(null, extractHost("not a host"))
    }

    // ------------------------------------------------------------
    // isTailnetIp
    // ------------------------------------------------------------

    @Test
    fun `isTailnetIp true for 100 64 0 0 10`() {
        assertTrue(isTailnetIp("100.64.0.1"))
        assertTrue(isTailnetIp("100.64.255.255"))
        assertTrue(isTailnetIp("100.127.255.255"))
    }

    @Test
    fun `isTailnetIp false outside range`() {
        assertFalse(isTailnetIp("100.63.255.255"))
        assertFalse(isTailnetIp("100.128.0.1"))
        assertFalse(isTailnetIp("10.0.0.1"))
        assertFalse(isTailnetIp("192.168.1.1"))
        assertFalse(isTailnetIp("8.8.8.8"))
    }

    @Test
    fun `isTailnetIp false for non-IPv4`() {
        assertFalse(isTailnetIp("nas.ts.net"))
        assertFalse(isTailnetIp("github.com"))
        assertFalse(isTailnetIp("::1"))
        assertFalse(isTailnetIp("2001:db8::1"))
        assertFalse(isTailnetIp(""))
        assertFalse(isTailnetIp("not-an-ip"))
    }

    // ------------------------------------------------------------
    // isMagicDnsFqdn
    // ------------------------------------------------------------

    @Test
    fun `isMagicDnsFqdn true for ts net suffix`() {
        assertTrue(isMagicDnsFqdn("nas.ts.net"))
        assertTrue(isMagicDnsFqdn("nas.tailnet.ts.net"))
        assertTrue(isMagicDnsFqdn("NAS.TAILNET.TS.NET"))
    }

    @Test
    fun `isMagicDnsFqdn false for non-suffix`() {
        assertFalse(isMagicDnsFqdn("ts.net"))
        assertFalse(isMagicDnsFqdn("something.ts.net.extra"))
        assertFalse(isMagicDnsFqdn("github.com"))
        assertFalse(isMagicDnsFqdn("localhost"))
        assertFalse(isMagicDnsFqdn(""))
        assertFalse(isMagicDnsFqdn("100.64.0.5"))
    }
}