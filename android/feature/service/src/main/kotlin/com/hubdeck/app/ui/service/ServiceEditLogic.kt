package com.hubdeck.app.ui.service

import com.hubdeck.app.core.model.Endpoint
import com.hubdeck.app.core.model.EndpointScope
import com.hubdeck.app.core.model.HealthConfig
import com.hubdeck.app.core.model.HealthMode
import com.hubdeck.app.core.model.Service
import com.hubdeck.app.core.model.ServiceKind

/**
 * 新建 / 编辑服务的纯逻辑（可在 JVM 单测里直接验证）。
 */
object ServiceEditLogic {

    /**
     * 端点 ID 必须稳定（C-01 不变量 3：仓储的 `upsert` 先删后插端点，ID 变了等于换了端点）。
     *
     * 命名与 C-10 的导入约定一致：`"$serviceId-$scope-$host"`。
     */
    fun endpointId(serviceId: String, scope: EndpointScope, host: String): String {
        val normalizedHost = host.trim().lowercase().ifEmpty { "endpoint" }
        return "$serviceId-${scope.name.lowercase()}-$normalizedHost"
    }

    /**
     * 按 MagicDNS 后缀自动生成一个 tailnet 端点。
     *
     * - 后缀已知时，host 写短名（`jellyfin`），由 C-05 的渲染器补成 FQDN；
     * - 后缀未知时，host 直接写 FQDN，用户之后可以在 tailnet 后缀变化时再改。
     */
    fun tailnetEndpointDraft(
        serviceId: String,
        host: String,
        magicDnsSuffix: String?,
        existing: List<EndpointDraft>,
        template: String = "https://{host}",
    ): EndpointDraft {
        val trimmed = host.trim()
        val resolvedHost = when {
            magicDnsSuffix.isNullOrBlank() -> trimmed
            trimmed.contains('.') || trimmed.contains(':') -> trimmed
            else -> "$trimmed.$magicDnsSuffix"
        }
        return EndpointDraft(
            id = endpointId(serviceId, EndpointScope.TAILNET, resolvedHost),
            scope = EndpointScope.TAILNET,
            host = resolvedHost,
            urlTemplate = template,
            isPrimary = existing.none { it.scope == EndpointScope.TAILNET },
        )
    }

    /**
     * 表单校验，返回第一条错误信息（null 表示可以保存）。
     */
    fun validate(state: ServiceEditUiState): String? = when {
        state.name.isBlank() -> "请填写服务名称"
        state.endpoints.isEmpty() -> "至少需要一个访问地址"
        state.endpoints.all { it.host.isBlank() } -> "至少需要一个填写了主机名的地址"
        state.healthMode != HealthMode.NONE && state.healthPath.isBlank() ->
            "启用 HTTP 探测时需要填写探测路径"
        else -> null
    }

    /**
     * 把编辑状态转换成领域模型。
     */
    fun toService(state: ServiceEditUiState, existing: Service?): Service {
        val serviceId = state.serviceId.ifBlank { "service-${System.currentTimeMillis()}" }
        val endpoints = state.endpoints
            .filter { it.host.isNotBlank() }
            .map { draft ->
                Endpoint(
                    id = draft.id,
                    serviceId = serviceId,
                    scope = draft.scope,
                    urlTemplate = draft.urlTemplate.ifBlank { "https://{host}" },
                    host = draft.host.trim(),
                    port = draft.port.trim().toIntOrNull(),
                    isPrimary = draft.isPrimary,
                )
            }
        return Service(
            id = serviceId,
            name = state.name.trim(),
            kind = existing?.kind ?: ServiceKind.WEB,
            categoryId = state.categoryId,
            tagIds = emptyList(),
            iconKind = existing?.iconKind ?: com.hubdeck.app.core.model.IconKind.NONE,
            iconValue = existing?.iconValue,
            notes = state.notes.trim().ifBlank { null },
            pinned = state.pinned,
            sortIndex = existing?.sortIndex ?: 0,
            endpoints = endpoints,
            health = HealthConfig(
                mode = state.healthMode,
                path = state.healthPath.ifBlank { "/" },
                timeoutMs = state.healthTimeoutMs,
            ),
            createdAt = existing?.createdAt ?: 0L,
            updatedAt = existing?.updatedAt ?: 0L,
        )
    }

    /** 领域模型 → 编辑状态。 */
    fun toEditState(service: Service?, categories: List<com.hubdeck.app.core.model.Category>): ServiceEditUiState {
        if (service == null) {
            return ServiceEditUiState(categories = categories)
        }
        return ServiceEditUiState(
            serviceId = service.id,
            isNew = false,
            name = service.name,
            categories = categories,
            categoryId = service.categoryId,
            notes = service.notes.orEmpty(),
            pinned = service.pinned,
            endpoints = service.endpoints.map { endpoint ->
                EndpointDraft(
                    id = endpoint.id,
                    scope = endpoint.scope,
                    host = endpoint.host.orEmpty(),
                    port = endpoint.port?.toString().orEmpty(),
                    urlTemplate = endpoint.urlTemplate,
                    isPrimary = endpoint.isPrimary,
                )
            },
            healthMode = service.health.mode,
            healthPath = service.health.path,
            healthTimeoutMs = service.health.timeoutMs,
        )
    }
}