package app.feature.settings

import app.core.model.TunnelState
import app.core.tunnel.TunnelStateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * [TunnelStateProvider] 的假实现，用于 JVM 单测。
 * 状态保存在内存 MutableStateFlow 里，测试可直接控制状态变化。
 */
class FakeTunnelStateProvider : TunnelStateProvider {

    private val _tunnelState = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    override val tunnelState: StateFlow<TunnelState> = _tunnelState

    private val _authUrl = MutableStateFlow<String?>(null)
    override val authUrl: StateFlow<String?> = _authUrl

    /** 测试辅助：设置隧道状态。 */
    fun setTunnelState(state: TunnelState) {
        _tunnelState.value = state
    }

    /** 测试辅助：设置授权 URL。 */
    fun setAuthUrl(url: String?) {
        _authUrl.value = url
    }
}