package app.core.tunnel

import app.core.model.TunnelState
import kotlinx.coroutines.flow.StateFlow

/**
 * 隧道状态的生产者契约（内部 API）。
 *
 * 状态本体在 `:core:model`（冻结契约 C-16），这里只声明"谁在生产状态"。
 */
interface TunnelStateProvider {
    val tunnelState: StateFlow<TunnelState>
    val authUrl: StateFlow<String?>
}
