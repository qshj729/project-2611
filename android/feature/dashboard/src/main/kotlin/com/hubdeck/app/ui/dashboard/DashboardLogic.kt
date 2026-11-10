package com.hubdeck.app.ui.dashboard

import com.hubdeck.app.core.model.HealthMode
import com.hubdeck.app.core.model.Service

/**
 * 仪表盘的纯函数：筛选与徽标判定。
 *
 * 抽成顶层函数是为了能在 JVM 单测里直接验证（不需要 Android 运行时）。
 */
object DashboardLogic {

    /**
     * 搜索 + 分类筛选。
     *
     * - 搜索命中名称、备注或端点主机名；
     * - 收藏筛选用 [CategoryFilter.Favorites]（`Service.pinned`）；
     * - 排序沿用仓储给出的顺序（C-02：`pinned DESC, sortIndex ASC, name ASC`），这里不重排。
     */
    fun filterServices(
        services: List<Service>,
        query: String,
        filter: CategoryFilter,
    ): List<Service> {
        val keyword = query.trim()
        return services.filter { service ->
            matchesFilter(service, filter) && matchesQuery(service, keyword)
        }
    }

    private fun matchesFilter(service: Service, filter: CategoryFilter): Boolean = when (filter) {
        CategoryFilter.All -> true
        CategoryFilter.Favorites -> service.pinned
        is CategoryFilter.ByCategory -> service.categoryId == filter.categoryId
    }

    private fun matchesQuery(service: Service, keyword: String): Boolean {
        if (keyword.isEmpty()) return true
        if (service.name.contains(keyword, ignoreCase = true)) return true
        if (service.notes?.contains(keyword, ignoreCase = true) == true) return true
        return service.endpoints.any { endpoint ->
            endpoint.host?.contains(keyword, ignoreCase = true) == true
        }
    }

    /**
     * 徽标判定。
     *
     * C-04 的约定：`HealthMode.NONE` 与「探测失败」在返回值上分不开，
     * 所以必须先看 mode —— 没开探测的服务显示「未启用探测」，而不是「不可达」。
     */
    fun healthBadge(mode: HealthMode, reachable: Boolean?): HealthBadge = when {
        mode == HealthMode.NONE -> HealthBadge.DISABLED
        reachable == true -> HealthBadge.REACHABLE
        reachable == false -> HealthBadge.UNREACHABLE
        else -> HealthBadge.NOT_PROBED
    }
}
