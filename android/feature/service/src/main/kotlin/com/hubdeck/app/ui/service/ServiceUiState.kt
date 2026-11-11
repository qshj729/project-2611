package com.hubdeck.app.ui.service

import com.hubdeck.app.core.model.Endpoint
import com.hubdeck.app.core.model.HealthSample
import com.hubdeck.app.core.model.ProbeResult
import com.hubdeck.app.core.model.Service

/** 详情页里展示的一个端点。 */
data class EndpointUi(
    val endpoint: Endpoint,
    /** 渲染后的最终 URL。 */
    val url: String,
    /** 是否是当前选中的端点。 */
    val isSelected: Boolean,
    /** 是否是当前网络下的首选端点（与选中态不同：选中是用户手动指定的）。 */
    val isPreferred: Boolean,
)

/** 服务详情界面状态。 */
data class ServiceDetailUiState(
    val serviceId: String = "",
    /** 新建模式（路由里的 serviceId 是 [NEW_SERVICE_ID]）。 */
    val isNew: Boolean = false,
    val service: Service? = null,
    val endpoints: List<EndpointUi> = emptyList(),
    /** 当前要打开的地址。 */
    val url: String = "",
    /** 端点回退提示（C-05 `fellBack`）。 */
    val fellBack: Boolean = false,
    val healthSamples: List<HealthSample> = emptyList(),
    val probeResult: ProbeResult? = null,
    /** 当前 tailnet 的 MagicDNS 后缀。 */
    val magicDnsSuffix: String? = null,
    /** 用户手动选了非首选端点。 */
    val manualSelection: Boolean = false,
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val canOpen: Boolean get() = url.isNotBlank()
}
