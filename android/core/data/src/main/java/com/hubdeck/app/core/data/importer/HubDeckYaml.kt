package com.hubdeck.app.core.data.importer

import com.charleskorn.kaml.Yaml
import com.hubdeck.app.core.model.Category
import com.hubdeck.app.core.model.Endpoint
import com.hubdeck.app.core.model.EndpointScope
import com.hubdeck.app.core.model.HealthConfig
import com.hubdeck.app.core.model.HealthMode
import com.hubdeck.app.core.model.IconKind
import com.hubdeck.app.core.model.Service
import com.hubdeck.app.core.model.ServiceKind
import com.hubdeck.app.core.model.Tag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The portable `hubdeck.yaml` format (schema version 1).
 *
 * Design reference: docs/design.md §7.5.
 *
 * The file is meant to be human editable and safe to commit to Git, therefore it
 * never contains credentials unless the user explicitly opts in.
 */
object HubDeckYaml {

    @Serializable
    data class File(
        val version: Int = 1,
        val app: String = "hubdeck",
        @SerialName("exportedAt") val exportedAt: String? = null,
        val settings: Settings = Settings(),
        val tailnet: Tailnet = Tailnet(),
        val categories: List<Category> = emptyList(),
        val services: List<ServiceEntry> = emptyList(),
        val credentials: List<String> = emptyList(),
    )

    @Serializable
    data class Settings(
        @SerialName("homeSsid") val homeSsid: String? = null,
        @SerialName("probeIntervalMinutes") val probeIntervalMinutes: Int = 15,
        @SerialName("forceNetworkContext") val forceNetworkContext: String = "auto",
    )

    @Serializable
    data class Tailnet(
        @SerialName("magicDnsSuffix") val magicDnsSuffix: String? = null,
        @SerialName("preferredDevice") val preferredDevice: String? = null,
    )

    @Serializable
    data class Category(
        val id: String,
        val name: String,
        val color: String? = null,
    )

    @Serializable
    data class ServiceEntry(
        val id: String,
        val name: String,
        val kind: String = "web",
        val category: String? = null,
        val tags: List<String> = emptyList(),
        val pinned: Boolean = false,
        val icon: Icon = Icon(),
        val notes: String? = null,
        val endpoints: List<EndpointEntry> = emptyList(),
        val health: Health = Health(),
    )

    @Serializable
    data class Icon(
        val kind: String = "none",
        val value: String? = null,
    )

    @Serializable
    data class Health(
        val mode: String = "none",
        val path: String = "/",
        @SerialName("timeoutMs") val timeoutMs: Int = 3000,
    )

    @Serializable
    data class EndpointEntry(
        val scope: String = "lan",
        @SerialName("urlTemplate") val urlTemplate: String = "",
        val host: String? = null,
        val port: Int? = null,
        val primary: Boolean = false,
        val note: String? = null,
    )

    /** Complete catalog for round-trip export/import (includes categories, tags, settings). */
    data class Catalog(
        val services: List<Service>,
        val categories: List<Category>,
        val tags: List<Tag>,
        val settings: Settings,
        val tailnet: Tailnet,
    )

    fun export(
        services: List<Service>,
        categories: List<Category> = emptyList(),
        tags: List<Tag> = emptyList(),
        magicDnsSuffix: String? = null,
        homeSsid: String? = null,
    ): String {
        val file = File(
            exportedAt = java.time.Instant.now().toString(),
            settings = Settings(homeSsid = homeSsid),
            tailnet = Tailnet(magicDnsSuffix = magicDnsSuffix),
            categories = categories.map { cat ->
                Category(id = cat.id, name = cat.name, color = formatColor(cat.color))
            },
            services = services.map { service ->
                ServiceEntry(
                    id = service.id,
                    name = service.name,
                    kind = service.kind.name.lowercase(),
                    category = service.categoryId,
                    tags = service.tagIds,
                    pinned = service.pinned,
                    icon = Icon(service.iconKind.name.lowercase(), service.iconValue),
                    notes = service.notes,
                    endpoints = service.endpoints.map { endpoint ->
                        EndpointEntry(
                            scope = endpoint.scope.name.lowercase(),
                            urlTemplate = endpoint.urlTemplate,
                            host = endpoint.host,
                            port = endpoint.port,
                            primary = endpoint.isPrimary,
                            note = endpoint.note,
                        )
                    },
                    health = Health(
                        mode = service.health.mode.name.lowercase(),
                        path = service.health.path,
                        timeoutMs = service.health.timeoutMs,
                    ),
                )
            },
        )
        return Yaml.default.encodeToString(File.serializer(), file)
    }

    fun parse(yamlText: String): List<Service> {
        val file = Yaml.default.decodeFromString(File.serializer(), yamlText)
        return file.services.map { entry ->
            Service(
                id = entry.id,
                name = entry.name,
                kind = runCatching { ServiceKind.valueOf(entry.kind.uppercase()) }.getOrDefault(ServiceKind.WEB),
                categoryId = entry.category,
                iconKind = runCatching { IconKind.valueOf(entry.icon.kind.uppercase()) }.getOrDefault(IconKind.NONE),
                iconValue = entry.icon.value,
                notes = entry.notes,
                pinned = entry.pinned,
                tagIds = entry.tags,
                endpoints = entry.endpoints.map { endpoint ->
                    Endpoint(
                        id = "${entry.id}-${endpoint.scope}-${endpoint.host.orEmpty()}",
                        serviceId = entry.id,
                        scope = runCatching { EndpointScope.valueOf(endpoint.scope.uppercase()) }
                            .getOrDefault(EndpointScope.LAN),
                        urlTemplate = endpoint.urlTemplate,
                        host = endpoint.host,
                        port = endpoint.port,
                        isPrimary = endpoint.primary,
                        note = endpoint.note,
                    )
                },
                health = HealthConfig(
                    mode = runCatching { HealthMode.valueOf(entry.health.mode.uppercase()) }
                        .getOrDefault(HealthMode.NONE),
                    path = entry.health.path,
                    timeoutMs = entry.health.timeoutMs,
                ),
            )
        }
    }

    /** Parses the full catalog including categories, tags, settings, and tailnet info. */
    fun parseCatalog(yamlText: String): Catalog {
        val file = Yaml.default.decodeFromString(File.serializer(), yamlText)
        val services = parse(yamlText)
        val categories = file.categories.map { cat ->
            Category(id = cat.id, name = cat.name, color = parseColor(cat.color))
        }
        val tags = file.services.flatMap { it.tags }.distinct().map { tagName ->
            Tag(id = tagName, name = tagName)
        }
        return Catalog(
            services = services,
            categories = categories,
            tags = tags,
            settings = file.settings,
            tailnet = file.tailnet,
        )
    }

    // --- Color helpers (ARGB Long <-> #RRGGBB / #AARRGGBB) ---

    fun parseColor(colorStr: String?): Long = colorStr?.let {
        val hex = it.replace("#", "")
        when (hex.length) {
            6 -> "FF$hex".toLong(16)
            8 -> hex.toLong(16)
            else -> 0xFF14B8A6
        }
    } ?: 0xFF14B8A6

    fun formatColor(color: Long): String = String.format("#%08X", color)
}