package app.feature.dashboard.port

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
 * 仪表盘用到的**端口（port）**。
 *
 * 为什么不是直接依赖 `:core:data` / `:core:network` / `:core:tailscale`：
 * 这三个模块在 M1 期间由 A 线（Reed）、B 线（Vela）负责填充，模块目录当前还是空壳；
 * 而 `:feature:*` 按冻结契约（docs/contracts.md §6）不允许依赖 `:app`，
 * 也不允许 `:feature:*` 之间互相依赖 —— 于是界面层需要一层自己的端口。
 *
 * 端口签名与冻结契约**逐条 1:1 对应**，接线由装配壳（`:app`）里的适配器完成：
 *
 * | 端口 | 冻结契约 | 适配器（`:app`，Iris 维护） |
 * | --- | --- | --- |
 * | [ServiceCatalog] | C-02 `ServiceRepository` | `RealServiceCatalog` |
 * | [HealthProbe] | C-04 `ServiceProbe` | `RealHealthProbe` |
 * | [EndpointResolverPort] | C-05 `EndpointResolver` | `RealEndpointResolver` |
 * | [UrlRendererPort] | C-05 `EndpointUrlRenderer` | `RealUrlRenderer` |
 * | [NetworkContextPort] | C-06 `NetworkContextResolver` | `RealNetworkContextResolver` |
 * | [TailnetPort] | C-09 `TailnetRepository` | `RealTailnetPort` |
 *
 * A/B 线落地后，只要把适配器换真实实现，界面代码一行都不用改。
 */

/** C-02 `ServiceRepository` 的只读子集。 */
interface ServiceCatalog {
    fun observeServices(): Flow<List<Service>>
    fun observeCategories(): Flow<List<Category>>
    fun observeHealth(serviceId: String): Flow<List<HealthSample>>
}

/** C-04 `ServiceProbe`。契约保证「永不抛异常」。 */
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

    /** 与 C-05 的 `EndpointResolver.Resolution` 同构。 */
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

/** C-06 `NetworkContextResolver`。 */
interface NetworkContextPort {
    fun resolve(
        manualLock: NetworkContext? = null,
        tailnet: TailnetState? = null,
        currentSsid: String? = null,
        homeSsid: String? = null,
        isCellular: Boolean = false,
        tailnetReachable: Boolean? = null,
        lanReachable: Boolean? = null,
    ): NetworkContext
}

/** C-09 `TailnetRepository` 的只读子集（仪表盘只关心 tailnet 状态与 MagicDNS 后缀）。 */
interface TailnetPort {
    suspend fun refreshLocalState(): TailnetState
}
