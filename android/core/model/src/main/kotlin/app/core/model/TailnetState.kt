package app.core.model

/**
 * Tailnet state as observed by HubDeck.
 *
 * Design reference: docs/tailscale-integration.md §5.1.
 */
enum class TailnetSource {
    /** Read from the Tailscale Local API (`127.0.0.1:41112`). */
    LOCAL_API,

    /** Derived from heuristics (SSID / transport / probe results). */
    HEURISTIC,

    /** Explicitly locked by the user in Settings. */
    MANUAL,
}

data class TailnetState(
    val connected: Boolean = false,
    /** e.g. `tailnet-xyz.ts.net` - used to build MagicDNS FQDNs. */
    val magicDnsSuffix: String? = null,
    val tailnetName: String? = null,
    /** FQDN of this phone inside the tailnet. */
    val selfDnsName: String? = null,
    /** This device's tailnet IPs (100.64.0.0/10). */
    val selfIps: List<String> = emptyList(),
    /** Raw `BackendState` reported by tailscaled, e.g. "Running". */
    val backendState: String? = null,
    val source: TailnetSource = TailnetSource.HEURISTIC,
    val updatedAt: Long = 0L,
) {
    val isUsable: Boolean get() = connected && magicDnsSuffix != null
}

data class TailnetDevice(
    val id: String,
    val name: String,
    val dnsName: String? = null,
    val addresses: List<String> = emptyList(),
    val online: Boolean = false,
    val os: String? = null,
)
