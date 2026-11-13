package com.hubdeck.app.core.data.importer

import com.charleskorn.kaml.Yaml
import com.hubdeck.app.core.model.Endpoint
import com.hubdeck.app.core.model.EndpointScope
import com.hubdeck.app.core.model.HealthConfig
import com.hubdeck.app.core.model.HealthMode
import com.hubdeck.app.core.model.IconKind
import com.hubdeck.app.core.model.Service
import com.hubdeck.app.core.model.ServiceKind
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Imports a Homarr v1 `config.yaml` into HubDeck services.
 *
 * Design reference: docs/design.md §7.5 (Homarr import mapping table).
 *
 * Homarr fields we map: name / url / description / icon / tags, plus `groups` -> categories.
 */
object HomarrImporter {

    @Serializable
    data class HomarrConfig(
        val items: List<HomarrItem> = emptyList(),
        val groups: List<HomarrGroup> = emptyList(),
    )

    @Serializable
    data class HomarrItem(
        val name: String = "",
        val url: String = "",
        val description: String? = null,
        val icon: String? = null,
        val tags: List<String> = emptyList(),
    )

    @Serializable
    data class HomarrGroup(
        val id: String? = null,
        val name: String = "",
    )

    data class ImportResult(
        val services: List<Service>,
        val categories: Map<String, String>,
        /** Non fatal problems, surfaced as line hints in the import preview. */
        val warnings: List<String>,
    )

    fun parse(yamlText: String): ImportResult {
        val config = Yaml.default.decodeFromString(HomarrConfig.serializer(), yamlText)
        val warnings = mutableListOf<String>()
        val categories = config.groups.associate { group ->
            val name = group.name.trim()
            name to (group.id ?: name)
        }

        // Build a map from item index to approximate line number for better error reporting
        val itemLineNumbers = findItemLineNumbers(yamlText)

        val services = config.items.mapIndexedNotNull { index, item ->
            val url = item.url.trim()
            val lineInfo = itemLineNumbers.getOrDefault(index, index + 1).let { "第 $it 行" }
            if (url.isEmpty()) {
                warnings += "$lineInfo 缺少 url，已跳过"
                return@mapIndexedNotNull null
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                warnings += "$lineInfo 的 url 不是 http(s)（$url），已跳过"
                return@mapIndexedNotNull null
            }
            val serviceId = UUID.randomUUID().toString()
            Service(
                id = serviceId,
                name = item.name.trim().ifEmpty { url },
                kind = ServiceKind.WEB,
                iconKind = iconKindOf(item.icon),
                iconValue = item.icon,
                notes = item.description,
                sortIndex = index,
                endpoints = listOf(
                    Endpoint(
                        id = UUID.randomUUID().toString(),
                        serviceId = serviceId,
                        scope = EndpointScope.LAN,
                        urlTemplate = templateOf(url),
                        host = hostOf(url),
                        port = portOf(url),
                        isPrimary = true,
                        note = "来自 Homarr 导入",
                    ),
                ),
                health = HealthConfig(mode = HealthMode.TCP),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
        }

        return ImportResult(services = services, categories = categories, warnings = warnings)
    }

    /** Attempts to find the starting line number for each item in the `items` array. */
    private fun findItemLineNumbers(yamlText: String): Map<Int, Int> {
        val lines = yamlText.lines()
        val result = mutableMapOf<Int, Int>()
        var inItems = false
        var currentItemIndex = -1
        var itemStartLine = -1

        for ((lineIndex, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed == "items:") {
                inItems = true
                continue
            }
            if (inItems) {
                // Detect new item start (lines starting with "- " at the same indent level)
                if (trimmed.startsWith("- ")) {
                    if (currentItemIndex >= 0) {
                        result[currentItemIndex] = itemStartLine
                    }
                    currentItemIndex++
                    itemStartLine = lineIndex + 1 // 1-based line number
                } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !line.startsWith("  ") && !line.startsWith("\t")) {
                    // Left the items array
                    break
                }
            }
        }
        // Don't forget the last item
        if (currentItemIndex >= 0) {
            result[currentItemIndex] = itemStartLine
        }
        return result
    }

    private fun iconKindOf(icon: String?): IconKind = when {
        icon.isNullOrBlank() -> IconKind.NONE
        icon.startsWith("http") -> IconKind.URL
        icon.length <= 4 -> IconKind.EMOJI
        else -> IconKind.BUILTIN
    }

    /** Splits a URL into a reusable template + host + port so the user can add a tailnet endpoint later. */
    internal fun templateOf(url: String): String {
        val withoutScheme = url.substringAfter("://", url)
        val path = withoutScheme.substringAfter('/', "")
        return if (path.isEmpty()) "{host}" else "{host}/$path"
    }

    internal fun hostOf(url: String): String {
        val withoutScheme = url.substringAfter("://", url)
        val authority = withoutScheme.substringBefore('/')
        return authority.substringBefore(':')
    }

    internal fun portOf(url: String): Int? {
        val withoutScheme = url.substringAfter("://", url)
        val authority = withoutScheme.substringBefore('/')
        return authority.substringAfter(':', "").toIntOrNull()
    }
}