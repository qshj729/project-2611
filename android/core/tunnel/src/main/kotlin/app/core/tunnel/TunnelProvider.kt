package app.core.tunnel

import android.content.Context
import android.util.Log

/**
 * TunnelProvider: manages Tailscale auth key persistence and provides access to it.
 *
 * This NO LONGER sets JVM global proxy properties (http.proxyHost, etc.).
 * WebView proxy routing is handled by [ProxyConfigurator] via
 * [androidx.webkit.ProxyController.setProxyOverride].
 *
 * The actual tunnel lifecycle (tsnet node, proxy server) is managed by [TunnelManager].
 *
 * Design reference: docs/design.md §7.2, CMP-25 T1.
 */
object TunnelProvider {

    /** SharedPreferences key for saved auth key */
    private const val PREFS_NAME = "hubdeck_prefs"
    private const val KEY_AUTH_KEY_SAVED = "tailscale_auth_key_saved"

    /** Environment variable name for Tailscale auth key (fallback) */
    private const val ENV_AUTH_KEY = "HUB_TAILSCALE_AUTHKEY"

    /** Whether tunnel has been initialized (auth key loaded) */
    @Volatile
    var isInitialized = false

    /**
     * Get the saved auth key from SharedPreferences or environment variable.
     *
     * @param context Application context
     * @return auth key if available, null otherwise
     */
    fun getAuthKey(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Priority 1: Saved in SharedPreferences (from browser-based authorization flow)
        val savedKey = prefs.getString(KEY_AUTH_KEY_SAVED, null)
        if (savedKey != null && savedKey != "placeholder-key-no-env" && savedKey != "skipped" && savedKey.isNotBlank()) {
            return savedKey
        }

        // Priority 2: Environment variable (for CI/testing)
        val envKey = System.getenv(ENV_AUTH_KEY)
        if (envKey != null && envKey.isNotBlank()) {
            return envKey
        }

        return null
    }

    /**
     * Save the auth key to SharedPreferences.
     *
     * @param context Application context
     * @param authKey The auth key to save
     */
    fun saveAuthKey(context: Context, authKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_AUTH_KEY_SAVED, authKey).apply()
        Log.i("TunnelProvider", "Auth key saved to SharedPreferences")
    }

    /**
     * Clear the saved auth key.
     *
     * @param context Application context
     */
    fun clearAuthKey(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_AUTH_KEY_SAVED).apply()
        Log.i("TunnelProvider", "Auth key cleared from SharedPreferences")
    }

    /**
     * Check if an auth key is configured (saved or in env).
     */
    fun hasAuthKey(context: Context): Boolean {
        return getAuthKey(context) != null
    }

    /**
     * Initialize tunnel configuration (load auth key, no proxy properties).
     * Called during app startup.
     *
     * @param context Application context
     * @return auth key source information, or null if no auth key
     */
    fun initTunnel(context: Context): String? {
        val authKey = getAuthKey(context)
        if (authKey == null) {
            isInitialized = true
            Log.i("TunnelProvider", "TunnelProvider initialized, no auth key configured")
            return null
        }

        isInitialized = true
        Log.i("TunnelProvider", "TunnelProvider initialized, auth key available")
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