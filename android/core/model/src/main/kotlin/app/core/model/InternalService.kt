package app.core.model

/**
 * 内网服务条目（导航页的一行）。
 *
 * 产品定位：**自用的 Tailscale 套壳浏览器**。
 * 这里只描述"内网服务的静态清单"——标题、地址、图标、分组；
 * 不做服务目录 CRUD、不做健康探测、不做多端点地址模型。
 *
 * 设计参考：design.md §4.2（C-01 契约本体）
 */
data class InternalService(
    val title: String,
    /** 完整地址（含 scheme）。允许 MagicDNS 短名（如 `http://jellyfin:8096`）。 */
    val url: String,
    /** emoji 图标（导航页展示）。 */
    val icon: String? = null,
    /** 分组名（导航页按组分段）。 */
    val group: String? = null,
    /** 备注（不展示，仅随文件保存）。 */
    val note: String? = null,
) {
    /** URL 是否可安全交给浏览器打开：只允许 http/https。 */
    val isSafeUrl: Boolean
        get() = url.startsWith("http://") || url.startsWith("https://")
}
