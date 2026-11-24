package app.core.model

/**
 * 隧道状态契约本体（冻结契约 C-16）。
 *
 * **唯一一份定义**：`:core:tunnel` 产生它，`:feature:*` 消费它，`:app` 只做接线。
 * 任何模块不得再自定义同名状态类型。
 *
 * 设计参考：AGENTS.md §8（C-01 契约本体）/ contracts.md C-01
 */
sealed interface TunnelState {
    /** 未配置 auth key / 未启动隧道。 */
    data object Disconnected : TunnelState

    /** 节点启动中（tsnet start → awaitStatus 之间）。 */
    data object Starting : TunnelState

    /** 需要交互式网页授权；[loginUrl] 是要在外部浏览器里打开的授权地址。 */
    data class Authorizing(val loginUrl: String) : TunnelState

    /** 已连接。 */
    data class Connected(
        val nodeName: String,
        /** 本节点在 tailnet 里的 100.x 地址。 */
        val tailnetIps: List<String>,
        /** MagicDNS 后缀（如 `tailnet-xyz.ts.net`），可用作 FQDN 拼接。 */
        val magicDnsSuffix: String? = null,
    ) : TunnelState

    /** 启动失败 / 运行中掉线。 */
    data class Error(val reason: String) : TunnelState

    /** 是否处于可用状态（代理可以把 tailnet 目标转进隧道）。 */
    val isReady: Boolean get() = this is Connected
}
