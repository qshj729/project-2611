package com.hubdeck.app.core.data.importer

import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
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
import java.util.Locale

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
        val categories: List<YamlCategory> = emptyList(),
        val tags: List<YamlTag> = emptyList(),
        val services: List<YamlServiceEntry> = emptyList(),
        val credentials: List<String> = emptyList(),
    )

    /** 顶层标签条目：id 是服务里引用的 tagId，name 是给人看的名字。 */
    @Serializable
    data class YamlTag(
        val id: String,
        val name: String,
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
    data class YamlCategory(
        val id: String,
        val name: String,
        val color: String? = null,
        val sortIndex: Int = 0,
    )

    @Serializable
    data class YamlServiceEntry(
        val id: String,
        val name: String,
        val kind: String = "web",
        val category: String? = null,
        val tags: List<String> = emptyList(),
        val pinned: Boolean = false,
        val sortIndex: Int = 0,
        val icon: YamlIcon = YamlIcon(),
        val notes: String? = null,
        val endpoints: List<YamlEndpointEntry> = emptyList(),
        val health: YamlHealth = YamlHealth(),
        val createdAt: Long = 0L,
        val updatedAt: Long = 0L,
    )

    @Serializable
    data class YamlIcon(
        val kind: String = "none",
        val value: String? = null,
    )

    @Serializable
    data class YamlHealth(
        val mode: String = "none",
        val path: String = "/",
        @SerialName("timeoutMs") val timeoutMs: Int = 3000,
    )

    @Serializable
    data class YamlEndpointEntry(
        val id: String? = null,
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

    /**
     * YAML 读写配置：
     *
     * - `strictMode = false`：文件里有我们不认识的字段（新版本导出的）直接忽略，不炸；
     * - `PlainExceptAmbiguous`：字符串默认不加引号，导出结果可以手改（`app: hubdeck` 而不是 `app: "hubdeck"`）。
     */
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false,
            singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous,
        )
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
                YamlCategory(
                    id = cat.id,
                    name = cat.name,
                    color = formatColor(cat.color),
                    sortIndex = cat.sortIndex,
                )
            },
            tags = tags.map { tag -> YamlTag(id = tag.id, name = tag.name) },
            services = services.map { service ->
                YamlServiceEntry(
                    id = service.id,
                    name = service.name,
                    kind = service.kind.name.lowercase(),
                    category = service.categoryId,
                    tags = service.tagIds,
                    pinned = service.pinned,
                    sortIndex = service.sortIndex,
                    icon = YamlIcon(service.iconKind.name.lowercase(), service.iconValue),
                    notes = service.notes,
                    endpoints = service.endpoints.map { endpoint ->
                        YamlEndpointEntry(
                            id = endpoint.id,
                            scope = endpoint.scope.name.lowercase(),
                            urlTemplate = endpoint.urlTemplate,
                            host = endpoint.host,
                            port = endpoint.port,
                            primary = endpoint.isPrimary,
                            note = endpoint.note,
                        )
                    },
                    health = YamlHealth(
                        mode = service.health.mode.name.lowercase(),
                        path = service.health.path,
                        timeoutMs = service.health.timeoutMs,
                    ),
                    createdAt = service.createdAt,
                    updatedAt = service.updatedAt,
                )
            },
        )
        return yaml.encodeToString(File.serializer(), file)
    }

    fun parse(yamlText: String): List<Service> {
        val file = yaml.decodeFromString(File.serializer(), yamlText)
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
                sortIndex = entry.sortIndex,
                tagIds = entry.tags,
                endpoints = entry.endpoints.map { endpoint ->
                    Endpoint(
                        id = endpoint.id ?: "${entry.id}-${endpoint.scope}-${endpoint.host.orEmpty()}",
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
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt,
            )
        }
    }

    /** Parses the full catalog including categories, tags, settings, and tailnet info. */
    fun parseCatalog(yamlText: String): Catalog {
        val file = yaml.decodeFromString(File.serializer(), yamlText)
        val services = parse(yamlText)
        val categories = file.categories.map { cat ->
            Category(
                id = cat.id,
                name = cat.name,
                color = parseColor(cat.color),
                sortIndex = cat.sortIndex,
            )
        }
        val tags = file.tags.map { tag -> Tag(id = tag.id, name = tag.name) }
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

    /**
     * 颜色导出规则（冻结契约 C-10）：
     * - alpha 是 FF 时写 6 位 `#RRGGBB`（人读友好）；
     * - 其余情况写 8 位 `#AARRGGBB`（半透明必须保住）。
     *
     * `parseColor` 两种长度都能读回来，所以导出→导入是无损的。
     */
    fun formatColor(color: Long): String =
        if (((color ushr 24) and 0xFFL) == 0xFFL) {
            String.format(Locale.US, "#%06X", color and 0xFFFFFFL)
        } else {
            String.format(Locale.US, "#%08X", color and 0xFFFFFFFFL)
        }
}