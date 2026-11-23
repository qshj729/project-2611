package app.core.data.settings

/**
 * 主题偏好。
 */
enum class ThemePreference(val raw: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromRaw(raw: String?): ThemePreference {
            val text = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.raw == text } ?: SYSTEM
        }
    }
}

/**
 * App 设置。
 *
 * 套壳浏览器形态下，设置只保留真正会被用户改的几项——
 * 网络上下文判定、服务目录 CRUD、健康探测等 v1 能力已随"瘦身"移除。
 */
data class AppSettings(
    /** 主题。 */
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    /** 启动时自动连接隧道（有 auth key 时）。 */
    val autoConnectTunnel: Boolean = true,
    /** 启动时先显示内网导航页，而不是直接进浏览器。 */
    val openHomeOnLaunch: Boolean = true,
    /** 是否让 GeckoView 使用本地 PAC（关闭后所有流量直连，隧道不可用）。 */
    val useTailnetProxy: Boolean = true,
)
