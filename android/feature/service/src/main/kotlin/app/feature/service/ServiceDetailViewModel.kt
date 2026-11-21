package app.feature.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.core.model.Endpoint
import app.core.model.HealthSample
import app.core.model.ProbeResult
import app.core.model.Service
import app.core.model.TailnetState
import app.feature.service.port.EndpointResolverPort
import app.feature.service.port.HealthProbe
import app.feature.service.port.NetworkContextPort
import app.feature.service.port.UrlRendererPort
import app.feature.service.port.ServiceStore
import app.feature.service.port.TailnetPort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 服务详情状态机。
 *
 * 关注三件事：
 * 1. 多端点切换（C-05：按当前网络解析出首选端点，用户可以手动改）；
 * 2. 端点回退提示（C-05 `fellBack`）；
 * 3. 打开服务走 Custom Tab —— 这里只负责算出 URL，交给上层回调（C-11）。
 */
@HiltViewModel
class ServiceDetailViewModel @Inject constructor(
    private val store: ServiceStore,
    private val probe: HealthProbe,
    private val endpointResolver: EndpointResolverPort,
    private val urlRenderer: UrlRendererPort,
    private val networkContextResolver: NetworkContextPort,
    private val tailnet: TailnetPort,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServiceDetailUiState())
    val uiState: StateFlow<ServiceDetailUiState> = _uiState.asStateFlow()

    private var loaded = false
    private var tailnetState: TailnetState = TailnetState()

    /** 端点 ID → 是否可达（C-05 不变量 7）。 */
    private val reachability = mutableMapOf<String, Boolean>()

    /** 用户手动选中的端点（null = 跟随网络自动选）。 */
    private var selectedEndpointId: String? = null

    fun load(serviceId: String) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            tailnetState = runCatching { tailnet.refreshLocalState() }.getOrElse { TailnetState() }
            if (serviceId == ServiceDetailViewModel.NEW_SERVICE_ID) {
                _uiState.value = ServiceDetailUiState(
                    serviceId = serviceId,
                    isNew = true,
                    isLoading = false,
                    magicDnsSuffix = tailnetState.magicDnsSuffix,
                )
                return@launch
            }
            val service = runCatching { store.findService(serviceId) }.getOrNull()
            if (service == null) {
                _uiState.value = ServiceDetailUiState(
                    serviceId = serviceId,
                    isLoading = false,
                    error = "服务不存在或已被删除",
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                serviceId = serviceId,
                service = service,
                isLoading = false,
                magicDnsSuffix = tailnetState.magicDnsSuffix,
            )
            refreshProjection()
            observeHealth(serviceId)
        }
    }

    /** 编辑保存后重新读取。 */
    fun reload() {
        val serviceId = _uiState.value.serviceId
        loaded = false
        selectedEndpointId = null
        load(serviceId)
    }

    fun startEditing() {
        _uiState.value = _uiState.value.copy(isEditing = true, error = null)
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(isEditing = false, error = null)
    }

    /** 手动切换端点。 */
    fun onSelectEndpoint(endpointId: String) {
        selectedEndpointId = endpointId
        refreshProjection()
    }

    /** 回到「跟随网络自动选择」。 */
    fun onUseAutoEndpoint() {
        selectedEndpointId = null
        refreshProjection()
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /** 探测当前选中的端点。 */
    fun probe() {
        val service = _uiState.value.service ?: return
        val endpoint = currentEndpoint(service) ?: return
        viewModelScope.launch {
            val result = runCatching {
                // C-04：mode / path / timeoutMs 必须与 service.health 一致。
                probe.probe(
                    endpoint = endpoint,
                    mode = service.health.mode,
                    path = service.health.path,
                    timeoutMs = service.health.timeoutMs,
                )
            }.getOrElse { ProbeResult(reachable = false, reason = "探测失败") }
            reachability[endpoint.id] = result.reachable
            _uiState.value = _uiState.value.copy(probeResult = result)
            refreshProjection()
        }
    }

    /** 删除服务。 */
    fun delete(onDeleted: () -> Unit) {
        val serviceId = _uiState.value.serviceId
        if (serviceId == ServiceDetailViewModel.NEW_SERVICE_ID) return
        viewModelScope.launch {
            runCatching { store.delete(serviceId) }
            onDeleted()
        }
    }

    private fun currentEndpoint(service: Service): Endpoint? {
        val state = _uiState.value
        val selected = selectedEndpointId?.let { id -> service.endpoints.firstOrNull { it.id == id } }
        if (selected != null) return selected
        val context = networkContextResolver.resolve(tailnet = tailnetState)
        return endpointResolver.resolve(
            service = service,
            context = context,
            reachability = reachability,
        ).endpoint
    }

    /** 重新计算「当前端点 / 最终 URL / 回退提示 / 端点列表」。 */
    private fun refreshProjection() {
        val state = _uiState.value
        val service = state.service ?: return
        val context = networkContextResolver.resolve(tailnet = tailnetState)
        val resolution = endpointResolver.resolve(
            service = service,
            context = context,
            reachability = reachability,
        )
        val preferredId = resolution.endpoint?.id
        val manualId = selectedEndpointId
        val effectiveId = manualId ?: preferredId
        val effective = service.endpoints.firstOrNull { it.id == effectiveId } ?: resolution.endpoint

        val url = effective?.let { urlRenderer.render(it, tailnetState.magicDnsSuffix) }.orEmpty()

        val endpoints = service.endpoints.map { endpoint ->
            EndpointUi(
                endpoint = endpoint,
                url = urlRenderer.render(endpoint, tailnetState.magicDnsSuffix),
                isSelected = endpoint.id == effectiveId,
                isPreferred = endpoint.id == preferredId,
            )
        }

        _uiState.value = state.copy(
            endpoints = endpoints,
            url = url,
            fellBack = manualId == null && resolution.fellBack,
            manualSelection = manualId != null && manualId != preferredId,
        )
    }

    private fun observeHealth(serviceId: String) {
        viewModelScope.launch {
            val samples: List<HealthSample> = runCatching {
                store.observeHealth(serviceId).first()
            }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(healthSamples = samples)
        }
    }

    companion object {
        /**
         * 路由里代表「新建服务」的 serviceId。
         *
         * 契约 C-12 冻结了 4 条路由，没有单独的「新建」路由，
         * 所以新建入口复用详情路由，用这个哨兵值区分。
         */
        const val NEW_SERVICE_ID = "new"
    }
}