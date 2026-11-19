package com.hubdeck.app.ui.dashboard.widget

import android.widget.RemoteViewsService
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hubdeck.app.core.model.EndpointScope
import com.hubdeck.app.core.model.NetworkContext
import com.hubdeck.app.core.model.Service
import com.hubdeck.app.core.model.TailnetState
import com.hubdeck.app.ui.dashboard.port.EndpointResolverPort
import com.hubdeck.app.ui.dashboard.port.NetworkContextPort
import com.hubdeck.app.ui.dashboard.port.ServiceCatalog
import com.hubdeck.app.ui.dashboard.port.TailnetPort
import com.hubdeck.app.ui.dashboard.port.UrlRendererPort
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Widget 取数据的入口。
 *
 * `RemoteViewsService` 不能直接用 `@AndroidEntryPoint`（Hilt 只认 Activity / Fragment /
 * View / Service / BroadcastReceiver 这些直接父类），所以走 Hilt 的 EntryPoint API
 * 从 Application 拿端口实现 —— 效果一样，但不需要改父类。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDataEntryPoint {
    fun serviceCatalog(): ServiceCatalog
    fun endpointResolver(): EndpointResolverPort
    fun urlRenderer(): UrlRendererPort
    fun networkContextResolver(): NetworkContextPort
    fun tailnetPort(): TailnetPort
}

/** 给 Widget 提供收藏服务列表。 */
class WidgetRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WidgetDataEntryPoint::class.java,
        )
        return WidgetRemoteViewsFactory(
            context = applicationContext,
            catalog = entryPoint.serviceCatalog(),
            endpointResolver = entryPoint.endpointResolver(),
            urlRenderer = entryPoint.urlRenderer(),
            networkContextResolver = entryPoint.networkContextResolver(),
            tailnet = entryPoint.tailnetPort(),
        )
    }
}

/**
 * Widget 的数据源。
 *
 * `RemoteViewsService.RemoteViewsFactory` 的回调发生在绑定线程上，
 * 所以这里可以安全地 `runBlocking` 读一次快照（C-02 的 Flow 会立刻发射当前值）。
 */
class WidgetRemoteViewsFactory(
    private val context: Context,
    private val catalog: ServiceCatalog,
    private val endpointResolver: EndpointResolverPort,
    private val urlRenderer: UrlRendererPort,
    private val networkContextResolver: NetworkContextPort,
    private val tailnet: TailnetPort,
) : RemoteViewsService.RemoteViewsFactory {

    data class WidgetItem(
        val name: String,
        val url: String,
        val scopeLabel: String,
    )

    private var items: List<WidgetItem> = emptyList()

    override fun onCreate() = Unit

    override fun onDestroy() {
        // Cleanup if needed
    }

    override fun onDataSetChanged() {
        items = runCatching { loadItems() }.getOrDefault(emptyList())
    }

    private fun loadItems(): List<WidgetItem> = runBlocking {
        val services = catalog.observeServices().first().filter { it.pinned }
        if (services.isEmpty()) return@runBlocking emptyList()

        val tailnetState = runCatching { tailnet.refreshLocalState() }.getOrNull()
        val networkContext: NetworkContext = networkContextResolver.resolve(tailnet = tailnetState)

        services.mapNotNull { service: Service ->
            val endpoint = endpointResolver.resolve(
                service = service,
                context = networkContext,
            ).endpoint ?: return@mapNotNull null
            val url = urlRenderer.render(endpoint, tailnetState?.magicDnsSuffix)
            if (url.isBlank()) return@mapNotNull null
            WidgetItem(
                name = service.name,
                url = url,
                scopeLabel = when (endpoint.scope) {
                    EndpointScope.LAN -> "局域网"
                    EndpointScope.TAILNET -> "Tailnet"
                    EndpointScope.PUBLIC -> "公网"
                },
            )
        }
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        val item = items.getOrNull(position) ?: return null
        val views = RemoteViews(context.packageName, com.hubdeck.feature.dashboard.R.layout.widget_myapps_item)
        views.setTextViewText(com.hubdeck.feature.dashboard.R.id.widget_item_name, item.name)
        views.setTextViewText(com.hubdeck.feature.dashboard.R.id.widget_item_scope, item.scopeLabel)
        views.setOnClickPendingIntent(
            com.hubdeck.feature.dashboard.R.id.widget_item_root,
            pendingIntent(item, position),
        )
        return views
    }

    private fun pendingIntent(item: WidgetItem, position: Int) = android.app.PendingIntent.getActivity(
        context,
        REQUEST_BASE + position,
        WidgetLaunchActivity.intent(context, item.url),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
    )

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false

    private companion object {
        const val REQUEST_BASE = 4200
    }
}
