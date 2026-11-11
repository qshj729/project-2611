package com.hubdeck.app.core.tailscale

import com.hubdeck.app.core.model.TailnetDevice
import com.hubdeck.app.core.model.TailnetSource
import com.hubdeck.app.core.model.TailnetState
import com.hubdeck.app.core.security.CredentialStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates everything HubDeck knows about the tailnet.
 *
 * Design reference: docs/tailscale-integration.md §5.
 */
@Singleton
class TailnetRepository @Inject constructor(
    private val localApi: TailscaleLocalApiClient,
    private val controlApi: TailscaleControlApiClient,
    private val credentials: CredentialStore,
) {

    /** Reads the Local API; returns an "unknown" state when the secret is missing or unavailable. */
    suspend fun refreshLocalState(): TailnetState =
        localApi.status()?.toTailnetState()
            ?: TailnetState(connected = false, source = TailnetSource.HEURISTIC)

    suspend fun devices(tailnet: String): List<TailnetDevice> = controlApi.devices(tailnet)

    suspend fun storeLocalApiSecret(secret: String) =
        credentials.save("tailscale", CredentialStore.KIND_LOCAL_API_SECRET, secret)

    suspend fun storeControlPlaneToken(token: String) =
        credentials.save("control-plane", CredentialStore.KIND_CONTROL_TOKEN, token)

    suspend fun hasLocalApiSecret(): Boolean = !credentials.localApiSecret().isNullOrBlank()
}
