package app.core.data.config

import app.core.model.InternalService
import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable

/**
 * 内网服务清单（导航页数据）的 YAML 格式。
 *
 * ```yaml
 * version: 1
 * services:
 *   - title: Jellyfin
 *     url: https://jellyfin.tailnet-xyz.ts.net
 *     icon: "🎬"
 *     group: 媒体
 * ```
 *
 * 约定：
 * - 只允许 `http://` / `https://`（其它 scheme 一律拒绝，避免 `javascript:` / `file:` / `intent:`）；
 * - `url` 允许 MagicDNS 短名（如 `http://jellyfin:8096`）——隧道代理端负责解析；
 * - 字段缺失不影响导入（除 `title` / `url`）。
 *
 * 设计参考：AGENTS.md §8.2（YAML 清单格式）/ contracts.md C-02
 */
@Serializable
private data class ServicesFile(
    val version: Int = 1,
    val services: List<ServiceEntry> = emptyList(),
)

@Serializable
private data class ServiceEntry(
    val title: String,
    val url: String,
    val icon: String? = null,
    val group: String? = null,
    val note: String? = null,
)

/** 解析结果：合法的条目 + 被拒绝的条目（带原因）。 */
data class ServicesParseResult(
    val services: List<InternalService>,
    val rejected: List<RejectedEntry>,
) {
    data class RejectedEntry(val title: String, val url: String, val reason: String)
}

object InternalServicesConfig {

    private val yaml = Yaml.default

    /** 解析 YAML；语法错误时抛异常，非法条目进 [ServicesParseResult.rejected]。 */
    fun parse(text: String): ServicesParseResult {
        val file = yaml.decodeFromString(ServicesFile.serializer(), text)
        val accepted = mutableListOf<InternalService>()
        val rejected = mutableListOf<ServicesParseResult.RejectedEntry>()
        file.services.forEach { entry ->
            val url = entry.url.trim()
            when {
                entry.title.isBlank() -> rejected +=
                    ServicesParseResult.RejectedEntry(entry.title, url, "标题为空")
                !url.startsWith("http://") && !url.startsWith("https://") -> rejected +=
                    ServicesParseResult.RejectedEntry(entry.title, url, "只允许 http/https 地址")
                else -> accepted += InternalService(
                    title = entry.title.trim(),
                    url = url,
                    icon = entry.icon?.takeIf { it.isNotBlank() },
                    group = entry.group?.takeIf { it.isNotBlank() },
                    note = entry.note?.takeIf { it.isNotBlank() },
                )
            }
        }
        return ServicesParseResult(accepted, rejected)
    }

    /** 渲染成 YAML。 */
    fun render(services: List<InternalService>): String {
        val file = ServicesFile(
            services = services.map {
                ServiceEntry(
                    title = it.title,
                    url = it.url,
                    icon = it.icon,
                    group = it.group,
                    note = it.note,
                )
            },
        )
        return yaml.encodeToString(ServicesFile.serializer(), file)
    }
}