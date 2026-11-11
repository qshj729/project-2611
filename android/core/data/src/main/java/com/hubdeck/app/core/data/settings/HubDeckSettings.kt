package com.hubdeck.app.core.data.settings

import com.hubdeck.app.core.model.NetworkContext

/**
 * 用户手动锁定的网络模式（`docs/design.md` §6.5）。
 *
 * * [AUTO] —— 不锁定，按 SSID / Local API / 探测结果自动判定。
 * * [HOME] —— 强制「在家」：优先用 LAN 端点。
 * * [AWAY] —— 强制「在外」：优先用 tailnet 端点。
 *
 * 序列化值与 `hubdeck.yaml` 的 `settings.forceNetworkContext` 一致：`auto` / `home` / `away`。
 */
enum class NetworkModeLock(val raw: String) {
    AUTO("auto"),
    HOME("home"),
    AWAY("away"),
    ;

    /** 锁定的上下文；[AUTO] 表示「没有锁定」。 */
    fun toNetworkContext(): NetworkContext? = when (this) {
        AUTO -> null
        HOME -> NetworkContext.HOME
        AWAY -> NetworkContext.AWAY
    }

    companion object {
        /** 未知值一律回退到 [AUTO]（不锁定），不抛异常。 */
        fun fromRaw(raw: String?): NetworkModeLock {
            val text = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.raw == text } ?: AUTO
        }
    }
}

/**
 * HubDeck 的用户设置（DataStore 持久化）。
 *
 * 与 `hubdeck.yaml` 的 `settings` 段一一对应（见 `HubDeckYaml.Settings`），
 * 这样「导出的文件」和「App 里的设置」不会出现两套语义。
 */
data class HubDeckSettings(
    /** 家里 Wi-Fi 的 SSID：命中即判为「在家」。 */
    val homeSsid: String? = null,
    /** 周期性探测的间隔（分钟）。 */
    val probeIntervalMinutes: Int = DEFAULT_PROBE_INTERVAL_MINUTES,
    /** 手动锁定的网络模式。 */
    val networkModeLock: NetworkModeLock = NetworkModeLock.AUTO,
    /** 首选的 tailnet 设备名。 */
    val preferredDevice: String? = null,
    /** 缓存的 MagicDNS 后缀（`tailnet-xyz.ts.net`）。 */
    val magicDnsSuffix: String? = null,
) {
    companion object {
        const val DEFAULT_PROBE_INTERVAL_MINUTES = 15
    }
}
