package com.hubdeck.app.di

import com.hubdeck.app.core.data.repo.ServiceRepository
import com.hubdeck.app.core.network.ServiceProbe
import com.hubdeck.app.core.tailscale.EndpointResolver
import com.hubdeck.app.core.tailscale.EndpointUrlRenderer
import com.hubdeck.app.core.tailscale.NetworkContextResolver
import com.hubdeck.app.core.tailscale.TailnetRepository
import com.hubdeck.app.ui.dashboard.port.EndpointResolverPort as DashboardEndpointResolverPort
import com.hubdeck.app.ui.dashboard.port.HealthProbe as DashboardHealthProbe
import com.hubdeck.app.ui.dashboard.port.NetworkContextPort as DashboardNetworkContextPort
import com.hubdeck.app.ui.dashboard.port.ServiceCatalog
import com.hubdeck.app.ui.dashboard.port.TailnetPort as DashboardTailnetPort
import com.hubdeck.app.ui.dashboard.port.UrlRendererPort as DashboardUrlRendererPort
import com.hubdeck.app.ui.service.port.EndpointResolverPort as ServiceEndpointResolverPort
import com.hubdeck.app.ui.service.port.HealthProbe as ServiceHealthProbe
import com.hubdeck.app.ui.service.port.NetworkContextPort as ServiceNetworkContextPort
import com.hubdeck.app.ui.service.port.ServiceStore
import com.hubdeck.app.ui.service.port.TailnetPort as ServiceTailnetPort
import com.hubdeck.app.ui.service.port.UrlRendererPort as ServiceUrlRendererPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 端口适配器接线（装配壳，docs/design.md §7.2 / 冻结契约 docs/contracts.md §6）。
 *
 * `:feature:*` 只依赖自己的 port 接口；这里把 core 的真实实现
 * （ServiceRepository / ServiceProbe / EndpointResolver / EndpointUrlRenderer /
 * NetworkContextResolver / TailnetRepository）适配成各 feature 的端口类型。
 */
@Module
@InstallIn(SingletonComponent::class)
object PortModule {

    // ---- core 无 @Inject 构造的实现 ----

    @Provides
    @Singleton
    fun provideNetworkContextResolver(): NetworkContextResolver = NetworkContextResolver()

    // ---- dashboard 端口 ----

    @Provides
    @Singleton
    fun provideServiceCatalog(repo: ServiceRepository): ServiceCatalog =
        object : ServiceCatalog {
            override fun observeServices() = repo.observeServices()
            override fun observeCategories() = repo.observeCategories()
            override fun observeHealth(serviceId: String) = repo.observeHealth(serviceId)
        }

    @Provides
    @Singleton
    fun provideDashboardHealthProbe(probe: ServiceProbe): DashboardHealthProbe =
        object : DashboardHealthProbe {
            override suspend fun probe(
                endpoint: com.hubdeck.app.core.model.Endpoint,
                mode: com.hubdeck.app.core.model.HealthMode,
                path: String,
                timeoutMs: Int,
            ) = probe.probe(endpoint, mode, path, timeoutMs)
        }

    @Provides
    @Singleton
    fun provideDashboardEndpointResolver(): DashboardEndpointResolverPort =
        object : DashboardEndpointResolverPort {
            override fun scopeOrder(context: com.hubdeck.app.core.model.NetworkContext) =
                EndpointResolver.scopeOrder(context)

            override fun resolve(
                service: com.hubdeck.app.core.model.Service,
                context: com.hubdeck.app.core.model.NetworkContext,
                reachability: Map<String, Boolean>,
            ): DashboardEndpointResolverPort.Resolution {
                val r = EndpointResolver.resolve(service, context, reachability)
                return DashboardEndpointResolverPort.Resolution(r.endpoint, r.fellBack)
            }
        }

    @Provides
    @Singleton
    fun provideDashboardUrlRenderer(): DashboardUrlRendererPort =
        object : DashboardUrlRendererPort {
            override fun render(
                endpoint: com.hubdeck.app.core.model.Endpoint,
                magicDnsSuffix: String?,
            ) = EndpointUrlRenderer.render(endpoint, magicDnsSuffix)
        }

    @Provides
    @Singleton
    fun provideDashboardNetworkContextResolver(resolver: NetworkContextResolver): DashboardNetworkContextPort =
        object : DashboardNetworkContextPort {
            override fun resolve(
                manualLock: com.hubdeck.app.core.model.NetworkContext?,
                tailnet: com.hubdeck.app.core.model.TailnetState?,
                currentSsid: String?,
                homeSsid: String?,
                isCellular: Boolean,
                tailnetReachable: Boolean?,
                lanReachable: Boolean?,
            ) = resolver.resolve(
                manualLock = manualLock,
                tailnet = tailnet,
                currentSsid = currentSsid,
                homeSsid = homeSsid,
                isCellular = isCellular,
                tailnetReachable = tailnetReachable,
                lanReachable = lanReachable,
            )
        }

    @Provides
    @Singleton
    fun provideDashboardTailnetPort(repo: TailnetRepository): DashboardTailnetPort =
        object : DashboardTailnetPort {
            override suspend fun refreshLocalState() = repo.refreshLocalState()
        }

    // ---- service 端口 ----

    @Provides
    @Singleton
    fun provideServiceStore(repo: ServiceRepository): ServiceStore =
        object : ServiceStore {
            override fun observeCategories() = repo.observeCategories()
            override fun observeHealth(serviceId: String) = repo.observeHealth(serviceId)
            override suspend fun findService(id: String) = repo.findService(id)
            override suspend fun upsert(service: com.hubdeck.app.core.model.Service) = repo.upsert(service)
            override suspend fun delete(serviceId: String) = repo.delete(serviceId)
        }

    @Provides
    @Singleton
    fun provideServiceHealthProbe(probe: ServiceProbe): ServiceHealthProbe =
        object : ServiceHealthProbe {
            override suspend fun probe(
                endpoint: com.hubdeck.app.core.model.Endpoint,
                mode: com.hubdeck.app.core.model.HealthMode,
                path: String,
                timeoutMs: Int,
            ) = probe.probe(endpoint, mode, path, timeoutMs)
        }

    @Provides
    @Singleton
    fun provideServiceEndpointResolver(): ServiceEndpointResolverPort =
        object : ServiceEndpointResolverPort {
            override fun scopeOrder(context: com.hubdeck.app.core.model.NetworkContext) =
                EndpointResolver.scopeOrder(context)

            override fun resolve(
                service: com.hubdeck.app.core.model.Service,
                context: com.hubdeck.app.core.model.NetworkContext,
                reachability: Map<String, Boolean>,
            ): ServiceEndpointResolverPort.Resolution {
                val r = EndpointResolver.resolve(service, context, reachability)
                return ServiceEndpointResolverPort.Resolution(r.endpoint, r.fellBack)
            }
        }

    @Provides
    @Singleton
    fun provideServiceUrlRenderer(): ServiceUrlRendererPort =
        object : ServiceUrlRendererPort {
            override fun render(
                endpoint: com.hubdeck.app.core.model.Endpoint,
                magicDnsSuffix: String?,
            ) = EndpointUrlRenderer.render(endpoint, magicDnsSuffix)
        }

    @Provides
    @Singleton
    fun provideServiceNetworkContextResolver(resolver: NetworkContextResolver): ServiceNetworkContextPort =
        object : ServiceNetworkContextPort {
            override fun resolve(
                manualLock: com.hubdeck.app.core.model.NetworkContext?,
                tailnet: com.hubdeck.app.core.model.TailnetState?,
                currentSsid: String?,
                homeSsid: String?,
                isCellular: Boolean,
                lanReachable: Boolean?,
            ) = resolver.resolve(
                manualLock = manualLock,
                tailnet = tailnet,
                currentSsid = currentSsid,
                homeSsid = homeSsid,
                isCellular = isCellular,
                lanReachable = lanReachable,
            )
        }

    @Provides
    @Singleton
    fun provideServiceTailnetPort(repo: TailnetRepository): ServiceTailnetPort =
        object : ServiceTailnetPort {
            override suspend fun refreshLocalState() = repo.refreshLocalState()
        }
}
