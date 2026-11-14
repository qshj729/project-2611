package com.hubdeck.app

import android.content.Context
import android.util.Log

/**
 * TunnelProvider: manages Tailscale auth key persistence and tunnel lifecycle.
 *
 * This NO LONGER sets JVM global proxy properties (http.proxyHost, etc.).
 * WebView proxy routing is now handled by [ProxyConfigurator] via
 * [androidx.webkit.ProxyController.setProxyOverride].
 *
 * Design reference: docs/design.md §7.2, CMP-25 T1.
 */
object TunnelProvider {

    /** Environment variable name for Tailscale auth key */
    private const val ENV_AUTH_KEY = "HUB_TAILSCALE_AUTHKEY"

    /** Whether tunnel has been initialized */
    @Volatile
    var isInitialized = false

    /**
     * Initialize tunnel configuration (auth key only, no proxy properties).
     *
     * @param context Application context
     * @return auth key source information
     */
    fun initTunnel(context: Context): String {
        // Determine auth key preference: SharedPreferences first, then env var
        val prefs = context.getSharedPreferences("hubdeck_prefs", Context.MODE_PRIVATE)

        // Determine auth key source
        val hasSavedKey = prefs.getString("tailscale_auth_key_saved", null) != null
        val hasEnvKey = System.getenv(ENV_AUTH_KEY) != null

        var authKeySource: String
        if (hasSavedKey) {
            authKeySource = "credential-store"
        } else if (hasEnvKey) {
            authKeySource = "env"
        } else {
            authKeySource = "placeholder"
        }

        // Determine actual auth key: CredentialStore saved key, then env var
        val authKey = prefs.getString("tailscale_auth_key_saved", null)
            ?: System.getenv(ENV_AUTH_KEY)
            ?: "placeholder-key-no-env"

        isInitialized = true

        Log.i("TunnelProvider", "TunnelProvider initialized, auth key source: $authKeySource")

        // Record that we've saved the key to SharedPreferences (for persistence)
        // The actual Keystore encryption is handled by CredentialStore in the UI layer
        if (authKey != "placeholder-key-no-env" && !hasSavedKey) {
            prefs.edit().putString("tailscale_auth_key_saved", authKey).apply()
        }

        return authKey
    }

    /**
     * Clear any JVM global proxy properties that may have been set by legacy code.
     * Called during proxy reconfiguration to ensure clean state.
     */
    fun clearLegacyProxyProperties() {
        System.clearProperty("http.proxyHost")
        System.clearProperty("http.proxyPort")
        System.clearProperty("https.proxyHost")
        System.clearProperty("https.proxyPort")
        System.clearProperty("http.nonProxyHosts")
        System.clearProperty("https.nonProxyHosts")
        System.clearProperty("*.nonProxyHosts")
        Log.i("TunnelProvider", "Cleared legacy JVM proxy properties")
    }
}