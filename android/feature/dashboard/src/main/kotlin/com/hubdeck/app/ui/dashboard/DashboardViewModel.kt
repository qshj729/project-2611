package com.hubdeck.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubdeck.app.core.data.settings.SettingsStore
import com.hubdeck.app.core.model.EndpointScope
import com.hubdeck.app.core.model.NetworkContext
import com.hubdeck.app.core.model.ProbeResult
import com.hubdeck.app.core.model.Service
import com.hubdeck.app.core.model.TailnetState
import com.hubdeck.app.ui.dashboard.port.EndpointResolverPort
import com.hubdeck.app.ui.dashboard.port.HealthProbe
import com.hubdeck.app.ui.dashboard.port.NetworkContextPort
import com.hubdeck.app.ui.dashboard.port.ServiceCatalog
import com.hubdeck.app.ui.dashboard.port.TailnetPort
import com.hubdeck.app.ui.dashboard.port.UrlRendererPort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 仪表盘状态机。
 *
 * 契约要点（docs/contracts.md）：
 * - C-02：服务列表由仓储排序，UI 只做筛选；
 * - C-04：探测永不抛异常，失败只是 `reachable = false`；
 * - C-05：`resolve` 的 `reachability` 键是 `Endpoint.id`（G-07：以前没传，导致回退提示永远不出现）；
 * - C-06：`tailnetReachable` 与 `lanReachable` 必须语义不同（G-06：以前传了同一个值）。
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val catalog: ServiceCatalog,
    private val probe: HealthProbe,
    private val endpointResolver: EndpointResolverPort,
    private val urlRenderer: UrlRendererPort,
    private val networkContextResolver: NetworkContextPort,
    private val tailnet: TailnetPort,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val settings = settingsStore.observe().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.hubdeck.app.core.data.settings.HubDeckSettings()
    )

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<CategoryFilter>(CategoryFilter.All)

    /** 服务 ID → 最近一次探测结果。 */
    private val probes = MutableStateFlow<Map<String, ProbeResult>>(emptyMap())

    /** 端点 ID → 是否可达（C-05 不变量 7：`reachability` 的键是 `Endpoint.id`）。 */
    private val reachability = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** 端点 ID → scope（用来区分 tailnet 可达与 LAN 可达，G-06）。 */
    private val endpointScopes = MutableStateFlow<Map<String, EndpointScope>>(emptyMap())

    private val tailnetState = MutableStateFlow(TailnetState())
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    /** 全部服务（未筛选），供探测使用。 */
    private val allServices = MutableStateFlow<List<Service>>(emptyList())

    private data class Runtime(
        val probes: Map<String, ProbeResult> = emptyMap(),
        val reachability: Map<String, Boolean> = emptyMap(),
        val scopes: Map<String, EndpointScope> = emptyMap(),
        val tailnet: TailnetState = TailnetState(),
        val refreshing: Boolean = false,
        val message: String? = null,
    )

    private val runtime: StateFlow<Runtime> = combine(
        probes,
        reachability,
        endpointScopes,
        tailnetState,
    ) { probeMap, reachMap, scopes, tailnet ->
        Runtime(probeMap, reachMap, scopes, tailnet)
    }.combine(combine(refreshing, message) { busy, text -> busy to text }) { base, extra ->
        base.copy(refreshing = extra.first, message = extra.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Runtime())

    val uiState: StateFlow<DashboardUiState> = combine(
        catalog.observeServices(),
        catalog.observeCategories(),
        combine(query, filter) { keyword, selected -> keyword to selected },
        runtime,
    ) { services, categories, filters, rt ->
        val context = resolveNetworkContext(rt)
        val visible = DashboardLogic.filterServices(services, filters.first, filters.second)
        DashboardUiState(
            services = visible.map { service -> toCardUi(service, context, rt) },
            categories = categories,
            query = filters.first,
            filter = filters.second,
            networkContext = context,
            tailnet = rt.tailnet,
            isRefreshing = rt.refreshing,
            message = rt.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        viewModelScope.launch {
            catalog.observeServices().collect { allServices.value = it }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterSelected(filter: CategoryFilter) {
        this.filter.value = filter
    }

    fun dismissMessage() {
        message.value = null
    }

    /** 刷新 tailnet 状态并重新探测当前可见的服务。 */
    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            message.value = null
            val state = runCatching { tailnet.refreshLocalState() }
                .getOrElse { TailnetState() }
            tailnetState.value = state
            probeVisibleServices()
            refreshing.value = false
        }
    }

    /**
     * 探测当前可见的服务。
     *
     * 只探测开了探测的服务（C-04：`HealthMode.NONE` 没有意义），
     * 并且一轮最多 [MAX_PROBES_PER_ROUND] 个，避免 100 条服务时把网络打满。
     */
    fun probeVisibleServices() {
        viewModelScope.launch {
            val visible = DashboardLogic.filterServices(
                services = allServices.value,
                query = query.value,
                filter = filter.value,
            )
            val context = resolveNetworkContext(runtime.value)
            visible.filter { it.health.mode != com.hubdeck.app.core.model.HealthMode.NONE }
                .take(MAX_PROBES_PER_ROUND)
                .forEach { service ->
                    val endpoint = endpointResolver.resolve(
                        service = service,
                        context = context,
                        reachability = reachability.value,
                    ).endpoint ?: return@forEach

                    val result = runCatching {
                        probe.probe(
                            endpoint = endpoint,
                            mode = service.health.mode,
                            path = service.health.path,
                            timeoutMs = service.health.timeoutMs,
                        )
                    }.getOrElse { ProbeResult(reachable = false, reason = "探测失败") }

                    probes.value = probes.value + (service.id to result)
                    reachability.value = reachability.value + (endpoint.id to result.reachable)
                    endpointScopes.value = endpointScopes.value + (endpoint.id to endpoint.scope)
                }
        }
    }

    /** 解析某个服务当前应该使用的地址（详情页与卡片共用同一套规则）。 */
    fun resolveEndpoint(service: Service): EndpointResolverPort.Resolution =
        endpointResolver.resolve(
            service = service,
            context = resolveNetworkContext(runtime.value),
            reachability = reachability.value,
        )

    /** 渲染最终 URL（详情页「打开」按钮与卡片地址标签共用）。 */
    fun renderUrl(endpoint: com.hubdeck.app.core.model.Endpoint): String =
        urlRenderer.render(endpoint, runtime.value.tailnet.magicDnsSuffix)

    private fun toCardUi(
        service: Service,
        context: NetworkContext,
        rt: Runtime,
    ): ServiceCardUi {
        val resolution = endpointResolver.resolve(
            service = service,
            context = context,
            reachability = rt.reachability,
        )
        val endpoint = resolution.endpoint
        val reachable = endpoint?.let { rt.reachability[it.id] }
        return ServiceCardUi(
            service = service,
            badge = DashboardLogic.healthBadge(service.health.mode, reachable),
            scope = endpoint?.scope,
            fellBack = resolution.fellBack,
        )
    }

    /**
     * 网络上下文判定。
     *
     * G-06：以前把同一个 `any { it.reachable }` 同时传给了 `tailnetReachable` 与 `lanReachable`，
     * 这里按 scope 分开统计 —— tailnet 端点可达才说明「人在外但能走 tailnet」。
     */
    private fun resolveNetworkContext(rt: Runtime): NetworkContext = networkContextResolver.resolve(
        tailnet = rt.tailnet,
        tailnetReachable = rt.reachability.any { (id, ok) ->
            ok && rt.scopes[id] == EndpointScope.TAILNET
        },
        lanReachable = rt.reachability.any { (id, ok) ->
            ok && rt.scopes[id] == EndpointScope.LAN
        },
    )

    private companion object {
        const val MAX_PROBES_PER_ROUND = 12
    }
}
