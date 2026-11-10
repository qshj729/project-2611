package com.hubdeck.app.ui.dashboard

import com.hubdeck.app.core.model.Category
import com.hubdeck.app.core.model.EndpointScope
import com.hubdeck.app.core.model.NetworkContext
import com.hubdeck.app.core.model.Service
import com.hubdeck.app.core.model.TailnetState

/** 分类筛选条件。 */
sealed interface CategoryFilter {
    /** 全部服务。 */
    data object All : CategoryFilter

    /** 只看收藏（`Service.pinned`）。 */
    data object Favorites : CategoryFilter

    /** 只看某个分类。 */
    data class ByCategory(val categoryId: String) : CategoryFilter
}

/**
 * 卡片上的健康徽标（G-11：仪表盘要展示「已连接 / 不可达」）。
 *
 * C-04 明确：`HealthMode.NONE` 与「探测失败」在返回值上无法区分，
 * 所以这里必须先看 [Service.health] 的 mode 再决定怎么显示。
 */
enum class HealthBadge {
    /** 没有探测过（还没轮到 / 探测未完成）。 */
    NOT_PROBED,

    /** 探测通过。 */
    REACHABLE,

    /** 探测失败。 */
    UNREACHABLE,

    /** 该服务没开探测（`HealthMode.NONE`）。 */
    DISABLED,
}

/** 单个服务卡片的展示模型。 */
data class ServiceCardUi(
    val service: Service,
    val badge: HealthBadge,
    /** 解析出来的端点 scope（地址标签），没有端点时为 null。 */
    val scope: EndpointScope?,
    /** 解析出来的端点是否偏离了当前网络的偏好（端点回退提示）。 */
    val fellBack: Boolean,
)

/** 仪表盘界面状态。 */
data class DashboardUiState(
    val services: List<ServiceCardUi> = emptyList(),
    val categories: List<Category> = emptyList(),
    val query: String = "",
    val filter: CategoryFilter = CategoryFilter.All,
    val networkContext: NetworkContext = NetworkContext.UNKNOWN,
    val tailnet: TailnetState = TailnetState(),
    val isRefreshing: Boolean = false,
    /** 一次性提示（探测失败原因 / 刷新结果）。 */
    val message: String? = null,
) {
    /** 空列表（没有服务 / 筛选后没有结果）。 */
    val isEmpty: Boolean get() = services.isEmpty()
}
