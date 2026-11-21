package app.feature.service.port

import app.core.model.Category
import app.core.model.Endpoint
import app.core.model.EndpointScope
import app.core.model.HealthMode
import app.core.model.HealthSample
import app.core.model.NetworkContext
import app.core.model.ProbeResult
import app.core.model.Service
import app.core.model.TailnetState
import kotlinx.coroutines.flow.Flow

/**
 * 服务详情 / 编辑用到的端口。
 *
 * 签名与冻结契约 1:1 对应（docs/contracts.md）：
 * - [ServiceStore] ↔ C-02 `ServiceRepository`
 * - [EndpointResolverPort] ↔ C-05 `EndpointResolver`
 * - [UrlRendererPort] ↔ C-05 `EndpointUrlRenderer`
 * - [TailnetPort] ↔ C-09 `TailnetRepository`
 *
 * 适配器放在装配壳（`:app`），A/B 线落地后只换适配器，界面代码不动。
 */
interface ServiceStore {
    fun observeCategories(): Flow<List<Category>>
    fun observeHealth(serviceId: String): Flow<List<HealthSample>>
    suspend fun findService(id: String): Service?
    suspend fun upsert(service: Service)
    suspend fun delete(serviceId: String)
}

/** C-04 `ServiceProbe`（详情页的「立即探测」按钮）。 */
interface HealthProbe {
    suspend fun probe(
        endpoint: Endpoint,
        mode: HealthMode,
        path: String,
        timeoutMs: Int,
    ): ProbeResult
}

/** C-05 `EndpointResolver`。 */
interface EndpointResolverPort {

    data class Resolution(
        val endpoint: Endpoint?,
        /** True when we had to deviate from the preferred scope (UI shows a hint). */
        val fellBack: Boolean,
    )

    fun scopeOrder(context: NetworkContext): List<EndpointScope>

    fun resolve(
        service: Service,
        context: NetworkContext,
        reachability: Map<String, Boolean> = emptyMap(),
    ): Resolution
}

/** C-05 `EndpointUrlRenderer`。 */
interface UrlRendererPort {
    fun render(endpoint: Endpoint, magicDnsSuffix: String? = null): String
}

/** C-09 `TailnetRepository` 的只读子集（详情页需要 MagicDNS 后缀来渲染地址）。 */
interface TailnetPort {
    suspend fun refreshLocalState(): TailnetState
}

/** C-06 `NetworkContextResolver`（详情页要按当前网络挑端点）。 */
interface NetworkContextPort {
    fun resolve(
        manualLock: NetworkContext? = null,
        tailnet: TailnetState? = null,
        currentSsid: String? = null,
        homeSsid: String? = null,
        isCellular: Boolean = false,
        lanReachable: Boolean? = null,
    ): NetworkContext
}