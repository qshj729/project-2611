package com.hubdeck.app.core.tailscale

import com.hubdeck.app.core.model.NetworkContext
import com.hubdeck.app.core.model.TailnetSource
import com.hubdeck.app.core.model.TailnetState

/**
 * Decides whether the device is at home or away.
 *
 * Design reference: docs/tailscale-integration.md §5.1 / ADR-004.
 *
 * Signal priority:
 *  1. explicit user lock (Settings);
 *  2. Tailscale Local API (authoritative "am I in the tailnet?");
 *  3. Wi-Fi SSID vs. the configured home SSID;
 *  4. reachability probes as a last resort.
 */
class NetworkContextResolver {

    fun resolve(
        manualLock: NetworkContext? = null,
        tailnet: TailnetState? = null,
        currentSsid: String? = null,
        homeSsid: String? = null,
        isCellular: Boolean = false,
        tailnetReachable: Boolean? = null,
        lanReachable: Boolean? = null,
    ): NetworkContext {
        if (manualLock != null && manualLock != NetworkContext.UNKNOWN) return manualLock

        val atHomeSsid = currentSsid != null && homeSsid != null && currentSsid == homeSsid

        if (tailnet != null && tailnet.source == TailnetSource.LOCAL_API && tailnet.connected) {
            return if (atHomeSsid) NetworkContext.HOME else NetworkContext.AWAY
        }

        if (atHomeSsid) return NetworkContext.HOME
        if (isCellular) return NetworkContext.AWAY
        if (tailnetReachable == true) return NetworkContext.AWAY
        if (lanReachable == true) return NetworkContext.HOME
        return NetworkContext.UNKNOWN
    }
}
