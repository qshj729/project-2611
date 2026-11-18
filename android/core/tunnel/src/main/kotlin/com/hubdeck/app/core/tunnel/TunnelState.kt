package com.hubdeck.app.core.tunnel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tunnel state exposed to UI for subscription (T3 status bar, T4 auth entry).
 */
sealed interface TunnelState {
    data object Disconnected : TunnelState
    data object Starting : TunnelState
    data class Authorizing(val loginUrl: String) : TunnelState
    data class Connected(
        val nodeName: String,
        val tailnetIps: List<String>,
        val magicDnsSuffix: String?,
    ) : TunnelState
    data class Error(val reason: String) : TunnelState
}

/**
 * Interface for observing tunnel state.
 */
interface TunnelStateProvider {
    val tunnelState: StateFlow<TunnelState>
    val authUrl: StateFlow<String?>
}