package app.core.model

/**
 * Domain models for HubDeck.
 *
 * Design reference: docs/design.md §7.4.
 * These types are intentionally free of Android/Room annotations so that they can be
 * unit tested on the JVM and reused by importers/exporters.
 */

enum class EndpointScope {
    /** Reachable from the local network (LAN address). */
    LAN,

    /** Reachable through the tailnet (MagicDNS name or 100.x address). */
    TAILNET,

    /** Reachable from the public internet (reverse proxy / funnel). */
    PUBLIC,
}

enum class ServiceKind { WEB, API, APP, MEDIA }

enum class HealthMode {
    /** Do not probe this service. */
    NONE,

    /** TCP connect only - cheap and works for almost every service. */
    TCP,

    /** HTTP HEAD probe - distinguishes "alive but needs login" from "dead". */
    HTTP,
}

enum class IconKind { NONE, URL, EMOJI, BUILTIN }

/** Where the device currently is, which decides which endpoint we open. */
enum class NetworkContext { HOME, AWAY, UNKNOWN }

data class Service(
    val id: String,
    val name: String,
    val kind: ServiceKind = ServiceKind.WEB,
    val categoryId: String? = null,
    val tagIds: List<String> = emptyList(),
    val iconKind: IconKind = IconKind.NONE,
    val iconValue: String? = null,
    val notes: String? = null,
    val pinned: Boolean = false,
    val sortIndex: Int = 0,
    val endpoints: List<Endpoint> = emptyList(),
    val health: HealthConfig = HealthConfig(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class Endpoint(
    val id: String,
    val serviceId: String,
    val scope: EndpointScope,
    /** Template such as `https://{host}:{port}`; `{host}`/`{port}` are substituted on open. */
    val urlTemplate: String,
    /** Semantic host: a MagicDNS short name (`nas`), an FQDN (`nas.tailnet.ts.net`) or an IP. */
    val host: String? = null,
    val port: Int? = null,
    val isPrimary: Boolean = false,
    val note: String? = null,
)

data class HealthConfig(
    val mode: HealthMode = HealthMode.NONE,
    val path: String = "/",
    val timeoutMs: Int = 3_000,
)

data class Category(
    val id: String,
    val name: String,
    val color: Long = 0xFF14B8A6,
    val sortIndex: Int = 0,
)

data class Tag(val id: String, val name: String)

data class HealthSample(
    val id: Long = 0L,
    val serviceId: String,
    val endpointId: String,
    val reachable: Boolean,
    val latencyMs: Int? = null,
    val checkedAt: Long,
    val reason: String? = null,
)

/** Outcome of a single reachability probe. */
data class ProbeResult(
    val reachable: Boolean,
    val latencyMs: Int? = null,
    /** Human readable failure reason, e.g. "DNS 解析失败". Never blocks opening the service. */
    val reason: String? = null,
)
