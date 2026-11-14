package com.hubdeck.app.core.network

import java.net.InetAddress
import java.net.URL

/**
 * Proxy routing decision for WebView traffic.
 *
 * Design reference: docs/design.md §7.2, CMP-25 T1.
 *
 * This is a pure JVM function with no Android dependencies — fully unit-testable.
 * Input: URL string or host string → Output: [ProxyDecision].
 *
 * Rules (single source of truth for both connected/disconnected states):
 * - Tailnet IP range 100.64.0.0/10 → PROXY (when connected)
 * - Hostname ending with .ts.net (MagicDNS FQDN) → PROXY (when connected)
 * - Everything else (public internet, localhost, private LAN) → DIRECT
 *
 * The [connected] parameter gates the PROXY branch:
 * - connected = false ⇒ all DIRECT (ordinary browser behavior)
 * - connected = true  ⇒ tailnet targets → PROXY, rest → DIRECT
 */
enum class ProxyDecision {
    /** Route directly without proxy. */
    DIRECT,

    /** Route through local HTTP proxy at 127.0.0.1:1080. */
    PROXY
}

/**
 * Decide whether a URL should go through the tailnet proxy or direct.
 *
 * @param urlString Full URL (e.g. "http://100.64.0.5:8080", "https://nas.tailnet.ts.net")
 * @param connected Whether the tailnet tunnel is currently up
 * @return [ProxyDecision.DIRECT] or [ProxyDecision.PROXY]
 */
fun decideProxy(urlString: String, connected: Boolean): ProxyDecision {
    if (!connected) return ProxyDecision.DIRECT

    val host = extractHost(urlString) ?: return ProxyDecision.DIRECT
    return when {
        isTailnetIp(host) -> ProxyDecision.PROXY
        isMagicDnsFqdn(host) -> ProxyDecision.PROXY
        else -> ProxyDecision.DIRECT
    }
}

/**
 * Decide proxy routing for a host (without full URL).
 *
 * @param host Hostname or IP address (e.g. "100.64.0.5", "nas.tailnet.ts.net", "github.com")
 * @param connected Whether the tailnet tunnel is currently up
 * @return [ProxyDecision.DIRECT] or [ProxyDecision.PROXY]
 */
fun decideProxyForHost(host: String, connected: Boolean): ProxyDecision {
    if (!connected) return ProxyDecision.DIRECT
    return when {
        isTailnetIp(host) -> ProxyDecision.PROXY
        isMagicDnsFqdn(host) -> ProxyDecision.PROXY
        else -> ProxyDecision.DIRECT
    }
}

/**
 * Extract host from a URL string. Returns null if parsing fails.
 * Does not throw — safe for untrusted input.
 */
fun extractHost(urlString: String): String? {
    return try {
        val url = URL(urlString)
        url.host.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        // Also try parsing as bare host:port or host
        parseBareHost(urlString)
    }
}

/** Parse "host", "host:port", "[ipv6]:port", etc. */
private fun parseBareHost(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    // IPv6 in brackets: [::1]:8080 or [::1]
    if (trimmed.startsWith('[')) {
        val endBracket = trimmed.indexOf(']')
        if (endBracket > 1) {
            return trimmed.substring(1, endBracket).takeIf { it.isNotBlank() }
        }
        return null
    }
    // host:port or just host
    val colonIdx = trimmed.indexOf(':')
    return if (colonIdx > 0) trimmed.substring(0, colonIdx) else trimmed
}

/**
 * Check if a host string is an IPv4 address in the tailnet range 100.64.0.0/10.
 * Returns false for invalid IPs, IPv6, hostnames — never throws.
 */
fun isTailnetIp(host: String): Boolean {
    return try {
        val addr = InetAddress.getByName(host)
        val bytes = addr.address
        // IPv4 only: 4 bytes, first byte 100, second byte 64-127 (100.64.0.0/10)
        bytes.size == 4 && bytes[0].toInt() and 0xFF == 100 && (bytes[1].toInt() and 0xFF) in 64..127
    } catch (e: Exception) {
        false
    }
}

/**
 * Check if a hostname is a MagicDNS FQDN ending with .ts.net.
 * Case-insensitive. Returns false for invalid input — never throws.
 */
fun isMagicDnsFqdn(host: String): Boolean {
    return host.lowercase().endsWith(".ts.net")
}

/**
 * Build the proxy rule string for [androidx.webkit.ProxyConfig.Builder.addProxyRule].
 * Format: "PROXY 127.0.0.1:1080"
 */
const val PROXY_RULE = "PROXY 127.0.0.1:1080"

/**
 * Build the bypass rule string for [androidx.webkit.ProxyConfig.Builder.addBypassRule].
 * We use reverse bypass mode: proxy rules apply to tailnet targets, bypass rules = everything else.
 * But since addProxyRule only takes a single rule, we express tailnet targets via proxy rules
 * and use bypass for "direct" targets. However, the API is limited.
 *
 * Actual strategy (per CMP-25 T1):
 * - Use reverse bypass: setReverseBypassEnabled(true)
 * - Add PROXY rule for tailnet IP range and .ts.net
 * - This means: "use proxy for these rules, direct for everything else"
 *
 * Note: ProxyConfig doesn't support CIDR in proxy rules directly.
 * We'll add multiple proxy rules for the tailnet range via heuristic host matching
 * at the WebViewClient level (R2 fallback) or use the proxy override with
 * broad rules and fine-grained interception.
 */
const val TAILNET_IP_PROXY_RULE = "PROXY 127.0.0.1:1080"
const val TAILNET_DNS_PROXY_RULE = "PROXY 127.0.0.1:1080"