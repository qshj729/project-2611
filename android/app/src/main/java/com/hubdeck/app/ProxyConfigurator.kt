package com.hubdeck.app

import android.content.Context
import android.util.Log
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.hubdeck.app.core.network.ProxyDecision
import com.hubdeck.app.core.network.decideProxy
import com.hubdeck.app.core.model.TailnetState
import java.util.concurrent.Executor

/**
 * Configures WebView proxy routing via [androidx.webkit.ProxyController].
 *
 * This replaces the old JVM global proxy properties (http.proxyHost, etc.) with
 * the modern ProxyOverride API, which allows per-URL routing rules.
 *
 * Design reference: docs/design.md §7.2, CMP-25 T1.
 *
 * Rules (single source of truth in core:network ProxyRule.decideProxy):
 * - connected = false ⇒ all DIRECT (ordinary browser)
 * - connected = true  ⇒ tailnet targets (100.64/10, *.ts.net) → PROXY 127.0.0.1:1080
 *                       everything else → DIRECT
 *
 * R2 Fallback: If WebViewFeature.PROXY_OVERRIDE is not supported,
 * the caller should use WebViewClient.shouldInterceptRequest to intercept
 * only tailnet domains. This class does NOT fall back to global proxy properties.
 */
class ProxyConfigurator {

    companion object {
        private const val TAG = "ProxyConfigurator"
        private const val PROXY_HOST = "127.0.0.1"
        private const val PROXY_PORT = 1080
        private const val PROXY_RULE = "PROXY $PROXY_HOST:$PROXY_PORT"

        /**
         * ProxyController 1.18.0 的 setProxyOverride / clearProxyOverride 是异步 API，
         * 需要一个 Executor 来回调执行结果。这里用同线程执行，避免引入线程池。
         */
        private val CALLBACK_EXECUTOR: Executor = Executor { command -> command.run() }
    }

    /**
     * Apply proxy configuration for the current tailnet state.
     *
     * @param context Application context
     * @param tailnetState Current tailnet state (connected, magicDnsSuffix, etc.)
     * @return true if ProxyOverride was applied, false if PROXY_OVERRIDE not supported
     *         (caller should use R2 fallback via shouldInterceptRequest)
     */
    fun apply(context: Context, tailnetState: TailnetState): Boolean {
        // Check if PROXY_OVERRIDE is supported on this device's WebView
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            Log.w(TAG, "WebViewFeature.PROXY_OVERRIDE not supported on this device; R2 fallback required")
            return false
        }

        val connected = tailnetState.isUsable

        if (!connected) {
            // Disconnected: clear all proxy rules → everything DIRECT
            clearProxy(context)
            return true
        }

        // Connected: build ProxyConfig with rules for tailnet targets
        val config = buildProxyConfig(tailnetState)

        try {
            // androidx.webkit 1.18.0：setProxyOverride(ProxyConfig, Executor, Runnable)
            ProxyController.getInstance().setProxyOverride(
                config,
                CALLBACK_EXECUTOR,
                Runnable {
                    Log.i(TAG, "ProxyOverride applied: tailnet targets → PROXY $PROXY_HOST:$PROXY_PORT")
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set ProxyOverride", e)
            return false
        }

        return true
    }

    /**
     * Clear all proxy rules (everything DIRECT).
     */
    fun clearProxy(context: Context) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            return
        }
        val config = ProxyConfig.Builder()
            .setReverseBypassEnabled(false)
            .build()
        try {
            // androidx.webkit 1.18.0：clearProxyOverride(Executor, Runnable)
            ProxyController.getInstance().clearProxyOverride(
                CALLBACK_EXECUTOR,
                Runnable { Log.i(TAG, "ProxyOverride cleared: all traffic DIRECT") },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear ProxyOverride", e)
        }
    }

    /**
     * Build ProxyConfig for connected tailnet state.
     *
     * Strategy: Use reverse bypass mode.
     * - addProxyRule for tailnet IP range pattern and .ts.net pattern
     * - setReverseBypassEnabled(true) means: proxy rules = use proxy, else DIRECT
     *
     * Note: ProxyConfig doesn't support CIDR in proxy rules directly.
     * We express tailnet IPs via multiple rules or rely on host matching at
     * the WebViewClient level (R2 fallback). Here we use broad patterns
     * that ProxyConfig supports: "100.64.*.*", "100.65.*.*", etc. for /10 range,
     * and "*.ts.net" for MagicDNS.
     *
     * Actually, ProxyConfig proxy rules use host glob patterns, not CIDR.
     * The pattern "100.*.*.*" would be too broad. We'll use the most specific
     * patterns supported: "100.64.*.*" through "100.127.*.*".
     */
    private fun buildProxyConfig(tailnetState: TailnetState): ProxyConfig {
        val builder = ProxyConfig.Builder()

        // Enable reverse bypass: proxy rules specify what GOES THROUGH PROXY,
        // everything else goes DIRECT.
        // 注意：reverse bypass 是独立能力位（PROXY_OVERRIDE_REVERSE_BYPASS），
        // 设备不支持时调用 setReverseBypassEnabled 会抛 UnsupportedOperationException。
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE_REVERSE_BYPASS)) {
            builder.setReverseBypassEnabled(true)
        } else {
            Log.w(TAG, "PROXY_OVERRIDE_REVERSE_BYPASS not supported; tailnet rules fall back to normal order")
        }

        // Add proxy rules for tailnet IP range 100.64.0.0/10
        // ProxyConfig supports glob patterns like "100.64.*.*"
        for (secondOctet in 64..127) {
            builder.addProxyRule("100.$secondOctet.*.*", PROXY_RULE)
        }

        // Add proxy rule for MagicDNS FQDNs (*.ts.net)
        builder.addProxyRule("*.ts.net", PROXY_RULE)

        // Also add the specific magicDnsSuffix if available (e.g., "*.tailnet-name.ts.net")
        tailnetState.magicDnsSuffix?.let { suffix ->
            if (suffix.isNotBlank()) {
                builder.addProxyRule("*.$suffix", PROXY_RULE)
            }
        }

        // Also add self IPs explicitly
        for (ip in tailnetState.selfIps) {
            if (isTailnetIp(ip)) {
                builder.addProxyRule(ip, PROXY_RULE)
            }
        }

        return builder.build()
    }

    /**
     * Check if an IP string is in the tailnet range 100.64.0.0/10.
     * Copied logic from core:network to avoid dependency cycle.
     */
    private fun isTailnetIp(ip: String): Boolean {
        return try {
            val parts = ip.split(".")
            if (parts.size != 4) return false
            val first = parts[0].toIntOrNull() ?: return false
            val second = parts[1].toIntOrNull() ?: return false
            first == 100 && second in 64..127
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Decide proxy for a given URL using the same rules as ProxyRule.decideProxy.
     * This is a convenience for WebViewClient.shouldInterceptRequest (R2 fallback).
     */
    fun decideProxyForUrl(url: String, tailnetState: TailnetState): ProxyDecision {
        return decideProxy(url, tailnetState.isUsable)
    }
}