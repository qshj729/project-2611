package app.core.tailscale

import app.core.model.TailnetDevice
import app.core.security.SecretProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Optional read-only client for the Tailscale control plane API.
 *
 * Used for the "tailnet device directory" feature (roadmap M3.1): listing devices
 * so the user can turn a device into a service card with one tap.
 *
 * Design reference: docs/tailscale-integration.md §5.4 (ADR-005).
 *
 * Scope policy: read-only, minimal scopes (devices:core, dns). No write access
 * to ACLs, DNS records or Serve configuration is ever requested.
 */
interface TailscaleControlApiClient {
    suspend fun devices(tailnet: String): List<TailnetDevice>
}

@Serializable
data class ControlDevice(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("hostname") val hostname: String? = null,
    @SerialName("addresses") val addresses: List<String> = emptyList(),
    @SerialName("clientConnectivity") val clientConnectivity: ClientConnectivity? = null,
    @SerialName("os") val os: String? = null,
    @SerialName("tags") val tags: List<String> = emptyList(),
)

@Serializable
data class ClientConnectivity(
    @SerialName("connected") val connected: Boolean? = null,
    @SerialName("lastSeen") val lastSeen: String? = null,
)

class OkHttpTailscaleControlApiClient(
    private val client: OkHttpClient,
    private val secrets: SecretProvider,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TailscaleControlApiClient {

    override suspend fun devices(tailnet: String): List<TailnetDevice> = withContext(Dispatchers.IO) {
        val token = secrets.controlPlaneToken() ?: return@withContext emptyList()
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/api/v2/tailnet/$tailnet/devices")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching emptyList()
                val body = response.body?.string() ?: return@runCatching emptyList()
                json.decodeFromString<List<ControlDevice>>(body).map { it.toDomain() }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.tailscale.com"
    }
}

fun ControlDevice.toDomain(): TailnetDevice = TailnetDevice(
    id = id,
    name = name.ifBlank { hostname.orEmpty() },
    dnsName = hostname,
    addresses = addresses,
    online = clientConnectivity?.connected ?: false,
    os = os,
)
