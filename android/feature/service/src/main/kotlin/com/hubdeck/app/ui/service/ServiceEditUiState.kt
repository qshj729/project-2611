package com.hubdeck.app.ui.service

import com.hubdeck.app.core.model.EndpointScope
import com.hubdeck.app.core.model.HealthMode

/** 编辑界面状态。 */
data class ServiceEditUiState(
    val serviceId: String = "",
    val isNew: Boolean = false,
    val name: String = "",
    val categories: List<com.hubdeck.app.core.model.Category> = emptyList(),
    val categoryId: String? = null,
    val notes: String = "",
    val pinned: Boolean = false,
    val endpoints: List<EndpointDraft> = emptyList(),
    val healthMode: HealthMode = HealthMode.NONE,
    val healthPath: String = "/",
    val healthTimeoutMs: Int = 3_000,
    val magicDnsSuffix: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && !isSaving
}

/** 编辑界面的端点草稿。 */
data class EndpointDraft(
    val id: String,
    val scope: EndpointScope,
    val host: String = "",
    val port: String = "",
    val urlTemplate: String = "https://{host}",
    val isPrimary: Boolean = false,
) {
    /** 预览用的最终地址（MagicDNS 后缀由 C-05 的渲染器补全，这里只做展示）。 */
    fun previewUrl(magicDnsSuffix: String?): String {
        val trimmedHost = host.trim()
        val resolvedHost = if (scope == EndpointScope.TAILNET && magicDnsSuffix != null &&
            trimmedHost.isNotEmpty() && !trimmedHost.contains('.') && !trimmedHost.contains(':')
        ) {
            "$trimmedHost.$magicDnsSuffix"
        } else {
            trimmedHost
        }
        val portPart = port.trim().takeIf { it.isNotEmpty() }?.let { ":$it" }.orEmpty()
        return urlTemplate
            .replace("{host}", resolvedHost)
            .replace("{port}", port.trim())
            .let { if (portPart.isEmpty()) it else it.replace("{host}", "$resolvedHost$portPart") }
            .trim()
    }
}