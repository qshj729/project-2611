package com.hubdeck.app.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubdeck.app.core.model.Category
import com.hubdeck.app.core.model.EndpointScope
import com.hubdeck.app.core.model.HealthMode
import com.hubdeck.app.core.model.Service
import com.hubdeck.app.core.model.HealthSample
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 新建 / 编辑服务。
 *
 * 重点：新建时能按 MagicDNS 后缀自动生成 tailnet 端点（交付项 3）。
 */
@HiltViewModel
class ServiceEditViewModel @Inject constructor(
    private val store: ServiceStore,
    private val tailnet: TailnetPort,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServiceEditUiState())
    val uiState: StateFlow<ServiceEditUiState> = _uiState.asStateFlow()

    private var loaded = false
    private var existing: Service? = null

    fun load(serviceId: String) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val categories: List<Category> = runCatching {
                store.observeCategories().first()
            }.getOrDefault(emptyList())
            val tailnetState = runCatching { tailnet.refreshLocalState() }.getOrNull()

            if (serviceId == ServiceDetailViewModel.NEW_SERVICE_ID) {
                _uiState.value = ServiceEditUiState(
                    serviceId = "",
                    isNew = true,
                    categories = categories,
                    magicDnsSuffix = tailnetState?.magicDnsSuffix,
                    endpoints = listOf(
                        EndpointDraft(
                            id = "draft-lan",
                            scope = EndpointScope.LAN,
                            urlTemplate = "http://{host}",
                        ),
                    ),
                )
                return@launch
            }

            val service = runCatching { store.findService(serviceId) }.getOrNull()
            if (service == null) {
                _uiState.value = ServiceEditUiState(
                    serviceId = serviceId,
                    categories = categories,
                    error = "服务不存在或已被删除",
                )
                return@launch
            }
            existing = service
            _uiState.value = ServiceEditLogic.toEditState(service, categories).copy(
                magicDnsSuffix = tailnetState?.magicDnsSuffix,
            )
        }
    }

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(name = value, error = null)
    }

    fun onNotesChange(value: String) {
        _uiState.value = _uiState.value.copy(notes = value)
    }

    fun onCategorySelected(categoryId: String?) {
        _uiState.value = _uiState.value.copy(categoryId = categoryId)
    }

    fun onPinnedChange(value: Boolean) {
        _uiState.value = _uiState.value.copy(pinned = value)
    }

    fun onHealthModeChange(mode: HealthMode) {
        _uiState.value = _uiState.value.copy(healthMode = mode)
    }

    fun onHealthPathChange(value: String) {
        _uiState.value = _uiState.value.copy(healthPath = value)
    }

    fun onHealthTimeoutChange(value: Int) {
        _uiState.value = _uiState.value.copy(healthTimeoutMs = value.coerceIn(500, 30_000))
    }

    fun onEndpointHostChange(draftId: String, host: String) {
        updateEndpoint(draftId) { it.copy(host = host) }
    }

    fun onEndpointPortChange(draftId: String, port: String) {
        updateEndpoint(draftId) { it.copy(port = port.filter { char -> char.isDigit() }) }
    }

    fun onEndpointTemplateChange(draftId: String, template: String) {
        updateEndpoint(draftId) { it.copy(urlTemplate = template) }
    }

    fun onEndpointScopeChange(draftId: String, scope: EndpointScope) {
        updateEndpoint(draftId) { draft ->
            draft.copy(
                scope = scope,
                id = if (draft.id.startsWith("draft-")) {
                    ServiceEditLogic.endpointId(_uiState.value.serviceId, scope, draft.host)
                } else {
                    draft.id
                },
            )
        }
    }

    fun onEndpointPrimaryChange(draftId: String, isPrimary: Boolean) {
        val state = _uiState.value
        _uiState.value = state.copy(
            endpoints = state.endpoints.map { draft ->
                if (draft.id == draftId) {
                    draft.copy(isPrimary = isPrimary)
                } else if (isPrimary) {
                    draft.copy(isPrimary = false)
                } else {
                    draft
                }
            },
            error = null,
        )
    }

    fun onAddEndpoint(scope: EndpointScope = EndpointScope.LAN) {
        val state = _uiState.value
        val draft = EndpointDraft(
            id = "draft-${scope.name.lowercase()}-${state.endpoints.size + 1}",
            scope = scope,
            urlTemplate = if (scope == EndpointScope.TAILNET) "https://{host}" else "http://{host}",
        )
        _uiState.value = state.copy(endpoints = state.endpoints + draft)
    }

    fun onRemoveEndpoint(draftId: String) {
        val state = _uiState.value
        _uiState.value = state.copy(endpoints = state.endpoints.filterNot { it.id == draftId })
    }

    /**
     * 按 MagicDNS 后缀自动生成 tailnet 端点。
     *
     * - 已经有 tailnet 端点就更新它（保持端点 ID 稳定，C-01 不变量 3）；
     * - 没有就用当前第一个地址的主机名生成一个。
     */
    fun onGenerateTailnetEndpoint() {
        val state = _uiState.value
        val host = state.endpoints.firstOrNull { it.host.isNotBlank() }?.host.orEmpty()
        if (host.isBlank()) {
            _uiState.value = state.copy(error = "请先填写一个主机名，再生成 Tailnet 地址")
            return
        }
        val existingDraft = state.endpoints.firstOrNull { it.scope == EndpointScope.TAILNET }
        val draft = ServiceEditLogic.tailnetEndpointDraft(
            serviceId = state.serviceId,
            host = host,
            magicDnsSuffix = state.magicDnsSuffix,
            existing = state.endpoints,
        )
        val endpoints = if (existingDraft != null) {
            state.endpoints.map { current ->
                if (current.id == existingDraft.id) current.copy(host = draft.host) else current
            }
        } else {
            state.endpoints + draft
        }
        _uiState.value = state.copy(endpoints = endpoints, error = null)
    }

    /** 保存。 */
    fun save() {
        val state = _uiState.value
        val error = ServiceEditLogic.validate(state)
        if (error != null) {
            _uiState.value = state.copy(error = error)
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, saved = true, serviceId = state.serviceId)
            val service = ServiceEditLogic.toService(state, existing)
            runCatching { store.upsert(service) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isSaving = false, saved = true, serviceId = service.id)
                }
                .onFailure { error1 ->
                    _uiState.value = _uiState.value.copy(isSaving = false, error = "保存失败：${error1.message ?: "未知错误"}")
                }
        }
    }

    private fun updateEndpoint(draftId: String, transform: (EndpointDraft) -> EndpointDraft) {
        val state = _uiState.value
        _uiState.value = state.copy(
            endpoints = state.endpoints.map { draft ->
                if (draft.id == draftId) transform(draft) else draft
            },
            error = null,
        )
    }
}