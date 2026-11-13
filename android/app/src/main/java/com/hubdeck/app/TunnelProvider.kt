package com.hubdeck.app

import android.content.Context
import android.util.Log

/**
 * TunnelProvider: sets up JVM system properties for WebView proxy routing.
 * Chromium Android WebView reads http.proxyHost/http.proxyPort/https.proxyPort
 * from java.system properties to route traffic through a local HTTP proxy.
 */
object TunnelProvider {

    /** Environment variable name for Tailscale auth key */
    private const val ENV_AUTH_KEY = "HUB_TAILSCALE_AUTHKEY"

    /** Whether proxy has been configured */
    @Volatile
    var isConfigured = false

    /**
     * Initialize tunnel proxy configuration for WebView.
     * Configures JVM system properties that Chromium WebView uses to route
     * traffic through the local HTTP proxy.
     *
     * Must be called before WebView creation.
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

        // Configure Chromium WebView proxy properties
        // These properties tell Chromium's ProxyConfigServiceAndroid to use
        // the local proxy at 127.0.0.1:<port> for both HTTP and HTTPS traffic.
        // CONNECT method is used for HTTPS tunnels; TLS handshake occurs directly
        // with the target site, credentials and login state are NOT affected.
        System.setProperty("http.proxyHost", "127.0.0.1")
        System.setProperty("http.proxyPort", "1080")
        System.setProperty("https.proxyHost", "127.0.0.1")
        System.setProperty("https.proxyPort", "1080")

        // nonProxyHosts: exclude localhost and private networks
        // But deliberately DO NOT exclude 100.x or *.ts.net -
        // they MUST traverse the tunnel to the tailnet
        System.setProperty("*.nonProxyHosts", "127.0.0.1,10.0.0.0/8,192.168.0.0/16")

        isConfigured = true

        Log.i("TunnelProvider", "TunnelProvider configured: proxy 127.0.0.1:1080, " +
            "nonProxyHosts set, auth key source: $authKeySource")

        // Record that we've saved the key to SharedPreferences (for persistence)
        // The actual Keystore encryption is handled by CredentialStore in the UI layer
        if (authKey != "placeholder-key-no-env" && !hasSavedKey) {
            prefs.edit().putString("tailscale_auth_key_saved", authKey).apply()
        }

        return authKey
    }
}