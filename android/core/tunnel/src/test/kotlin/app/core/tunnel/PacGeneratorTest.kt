package app.core.tunnel

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PAC 脚本单测 —— 浏览器据此决定"哪些地址走隧道"。
 *
 * 覆盖：
 * - tailnet IP 段（100.64.0.0/10）走代理
 * - *.ts.net（MagicDNS FQDN）走代理
 * - MagicDNS 短名（不含点）走代理
 * - 其余（公网）直连
 * - 代理不可用时回退直连（PROXY …; DIRECT）
 */
class PacGeneratorTest {

    private val pac = PacGenerator.build()

    @Test
    fun `pac defines FindProxyForURL`() {
        assertTrue(pac.contains("function FindProxyForURL(url, host)"))
    }

    @Test
    fun `tailnet range is routed to proxy`() {
        assertTrue(pac.contains("isInNet(host, \"100.64.0.0\", \"100.127.255.255\")"))
    }

    @Test
    fun `magic dns domains are routed to proxy`() {
        assertTrue(pac.contains("dnsDomainIs(host, \".ts.net\")"))
    }

    @Test
    fun `short names are routed to proxy`() {
        // MagicDNS 短名不含点 → 走代理（公网几乎没有单段主机名）
        assertTrue(pac.contains("host.indexOf('.') === -1"))
    }

    @Test
    fun `proxy rule falls back to direct`() {
        assertTrue(pac.contains("PROXY ${PacGenerator.PROXY_HOST}:${PacGenerator.PROXY_PORT}; DIRECT"))
    }

    @Test
    fun `default is direct`() {
        assertTrue(pac.contains("return \"DIRECT\";"))
    }
}
