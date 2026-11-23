package app.core.tunnel

/**
 * 生成给浏览器用的 PAC（Proxy Auto-Config）脚本。
 *
 * 为什么用 PAC 而不是"把代理规则写进 App"：
 * - 浏览器（GeckoView）只需要一个 `network.proxy.pac_url`，**不需要 App 参与每一次路由判断**；
 * - PAC 自带容错：代理不可用时 `PROXY …; DIRECT` 会回退直连，隧道没连上时公网照常访问；
 * - "只有内网走隧道"这条规则集中在一处，改一处即可。
 *
 * 规则（与 `LocalProxyServer.isTailnetHost` 保持一致）：
 * - `100.64.0.0/10` → 走本地代理；
 * - `*.ts.net`（MagicDNS FQDN） → 走本地代理；
 * - **不含点的主机名**（MagicDNS 短名，如 `jellyfin`） → 走本地代理（公网几乎没有单段主机名）；
 * - 其余 → 直连。
 *
 * 设计参考：/home/jack/project-2611-docs/docs/design.md §6.3
 */
object PacGenerator {

    /** PAC 在本地代理上的路径。 */
    const val PAC_PATH = "/proxy.pac"

    /** 本地代理监听地址（与 [LocalProxyServer] 保持一致）。 */
    const val PROXY_HOST = "127.0.0.1"
    const val PROXY_PORT = 1080

    /**
     * 生成 PAC 脚本。
     *
     * @param proxyHost 代理主机（默认 [PROXY_HOST]）
     * @param proxyPort 代理端口（默认 [PROXY_PORT]）
     */
    fun build(proxyHost: String = PROXY_HOST, proxyPort: Int = PROXY_PORT): String {
        val proxy = "PROXY $proxyHost:$proxyPort"
        return """
            // 由 MyApps 生成：只有 tailnet 目标走本地隧道代理，其余直连。
            function FindProxyForURL(url, host) {
              if (isInNet(host, "100.64.0.0", "100.127.255.255")) return "$proxy; DIRECT";
              if (dnsDomainIs(host, ".ts.net")) return "$proxy; DIRECT";
              if (host.indexOf('.') === -1) return "$proxy; DIRECT";
              return "DIRECT";
            }
        """.trimIndent()
    }
}
