package com.hubdeck.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hubdeck.app.core.data.repo.ServiceRepository
import com.hubdeck.app.core.model.Category
import com.hubdeck.app.core.model.NetworkContext
import com.hubdeck.app.core.model.ProbeResult
import com.hubdeck.app.core.model.Service
import com.hubdeck.app.core.model.TailnetState
import com.hubdeck.app.core.network.ServiceProbe
import com.hubdeck.app.core.tailscale.EndpointResolver
import com.hubdeck.app.core.tailscale.EndpointUrlRenderer
import com.hubdeck.app.core.tailscale.NetworkContextResolver
import com.hubdeck.app.core.tailscale.TailnetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val services: List<Service> = emptyList(),
    val categories: List<Category> = emptyList(),
    val query: String = "",
    val selectedCategoryId: String? = null,
    val health: Map<String, ProbeResult> = emptyMap(),
    val tailnet: TailnetState = TailnetState(),
    val networkContext: NetworkContext = NetworkContext.UNKNOWN,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ServiceRepository,
    private val probe: ServiceProbe,
    private val tailnetRepository: TailnetRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val health = MutableStateFlow<Map<String, ProbeResult>>(emptyMap())
    private val tailnet = MutableStateFlow(TailnetState())
    private val networkContext = MutableStateFlow(NetworkContext.UNKNOWN)

    private val contextResolver = NetworkContextResolver()

    private data class Filters(val query: String, val categoryId: String?)

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeServices(),
        repository.observeCategories(),
        combine(query, selectedCategoryId) { q, c -> Filters(q, c) },
        combine(health, tailnet, networkContext) { h, t, c -> Triple(h, t, c) },
    ) { services, categories, filters, extra ->
        val (healthMap, tailnetState, context) = extra
        DashboardUiState(
            services = services.filter { service ->
                val matchesQuery = filters.query.isBlank() ||
                    service.name.contains(filters.query, ignoreCase = true)
                val matchesCategory = filters.categoryId == null || service.categoryId == filters.categoryId
                matchesQuery && matchesCategory
            },
            categories = categories,
            query = filters.query,
            selectedCategoryId = filters.categoryId,
            health = healthMap,
            tailnet = tailnetState,
            networkContext = context,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onCategorySelected(categoryId: String?) {
        selectedCategoryId.value = if (selectedCategoryId.value == categoryId) null else categoryId
    }

    /** Refreshes the tailnet state and re-evaluates the network context. */
    fun refresh() {
        viewModelScope.launch {
            val state = tailnetRepository.refreshLocalState()
            tailnet.value = state
            networkContext.value = contextResolver.resolve(
                tailnet = state,
                isCellular = false,
                tailnetReachable = health.value.values.any { it.reachable },
                lanReachable = health.value.values.any { it.reachable },
            )
        }
    }

    /** Probes the services that are currently visible; failures never block opening a service. */
    fun probeVisible(services: List<Service>) {
        viewModelScope.launch {
            services.forEach { service ->
                val endpoint = EndpointResolver.resolve(
                    service = service,
                    context = networkContext.value,
                ).endpoint ?: return@forEach
                val result = probe.probe(
                    endpoint = endpoint,
                    mode = service.health.mode,
                    path = service.health.path,
                    timeoutMs = service.health.timeoutMs,
                )
                health.value = health.value + (service.id to result)
            }
        }
    }

    fun urlFor(service: Service): String? {
        val resolution = EndpointResolver.resolve(service, networkContext.value)
        val endpoint = resolution.endpoint ?: return null
        return EndpointUrlRenderer.render(endpoint, tailnet.value.magicDnsSuffix)
    }
}
