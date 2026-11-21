package app.core.tailscale

import app.core.model.TailnetSource
import app.core.model.TailnetState
import app.core.security.SecretProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Read-only client for the Tailscale Local API.
 *
 * The Local API is served by tailscaled on `127.0.0.1:41112` and requires
 * `Authorization: Bearer <LocalAPI Secret>`.
 *
 * Design reference: docs/tailscale-integration.md §5.3 (ADR-002).
 *
 * IMPORTANT CONTRACT: every call returns `null` instead of throwing when the
 * Local API is missing, unauthenticated or answers with an unexpected shape.
 * The app must keep working without the secret - it is an optional enhancement.
 */
interface TailscaleLocalApiClient {
    suspend fun status(): LocalStatus?
    suspend fun selfIp(): String?
}

@Serializable
data class LocalStatus(
    @SerialName("BackendState") val backendState: String? = null,
    @SerialName("TailscaleIPs") val tailscaleIps: List<String> = emptyList(),
    @SerialName("CurrentTailnet") val currentTailnet: LocalTailnet? = null,
    @SerialName("Self") val self: LocalSelf? = null,
)

@Serializable
data class LocalTailnet(
    @SerialName("Name") val name: String? = null,
    @SerialName("MagicDNSSuffix") val magicDnsSuffix: String? = null,
)

@Serializable
data class LocalSelf(
    @SerialName("DNSName") val dnsName: String? = null,
    @SerialName("Online") val online: Boolean? = null,
)

class OkHttpTailscaleLocalApiClient(
    private val client: OkHttpClient,
    private val secrets: SecretProvider,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : TailscaleLocalApiClient {

    override suspend fun status(): LocalStatus? = withContext(Dispatchers.IO) {
        val secret = secrets.localApiSecret() ?: return@withContext null
        get("/api/v2/status", secret)?.let { body ->
            runCatching { json.decodeFromString(LocalStatus.serializer(), body) }.getOrNull()
        }
    }

    override suspend fun selfIp(): String? = withContext(Dispatchers.IO) {
        val secret = secrets.localApiSecret() ?: return@withContext null
        get("/api/v2/ip", secret)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun get(path: String, secret: String): String? = runCatching {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $secret")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        }
    }.getOrNull()

    companion object {
        const val DEFAULT_BASE_URL = "http://127.0.0.1:41112"
    }
}

/** Maps the raw Local API payload onto the domain model used by the UI. */
fun LocalStatus.toTailnetState(source: TailnetSource = TailnetSource.LOCAL_API): TailnetState = TailnetState(
    connected = backendState.equals("Running", ignoreCase = true),
    magicDnsSuffix = currentTailnet?.magicDnsSuffix,
    tailnetName = currentTailnet?.name,
    selfDnsName = self?.dnsName,
    selfIps = tailscaleIps,
    backendState = backendState,
    source = source,
    updatedAt = System.currentTimeMillis(),
)
