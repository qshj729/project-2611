package com.hubdeck.app.ui.browser

import com.hubdeck.app.core.model.TailnetState
import kotlinx.coroutines.flow.Flow

/**
 * 浏览器屏用到的端口（port）。
 *
 * 与 docs/contracts.md 的冻结契约保持一致，接线由装配壳（:app）里的适配器完成。
 */

// T2 的隧道状态订阅接口（T2 未就绪时先接桩，签名按 T2 事件模型写，避免返工）
interface TunnelStatusPort {
    /** 当前隧道状态的单向流。 */
    val state: Flow<TunnelState>

    /** 触发授权流程，返回登录 URL（外部浏览器或 WebView 打开均可）。 */
    suspend fun startAuth(): String?

    /** 断开隧道。 */
    suspend fun disconnect()

    /** 是否有保存的 auth key（用于判断是否显示"连接"按钮）。 */
    suspend fun hasAuthKey(): Boolean
}

/** 隧道状态事件模型（与 T2 契约对齐）。 */
sealed interface TunnelState {
    /** 未启动 / 未授权：状态条显示"未连接内网" + "连接"按钮。 */
    data object Disconnected : TunnelState

    /** RUNNING：状态条显示节点名 + 100.x IP + 断连按钮。 */
    data class Connected(
        val nodeName: String,
        val tailnetIp: String, // 100.x.x.x
        val tailnetName: String? = null,
    ) : TunnelState

    /** 授权中 / 失败 / 掉线：状态条显示"点此授权"链接 + 失败原因。 */
    data class NeedsAuth(
        val reason: String,
        val loginUrl: String? = null,
    ) : TunnelState
}

/** 收藏持久化端口（本地存储）。 */
interface BookmarkStore {
    /** 获取所有收藏。 */
    suspend fun getAll(): List<Bookmark>

    /** 添加收藏。 */
    suspend fun add(bookmark: Bookmark)

    /** 删除收藏。 */
    suspend fun remove(id: String)

    /** 清空收藏。 */
    suspend fun clear()
}

/** 收藏数据模型。 */
data class Bookmark(
    val id: String,
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 导航页常用入口。 */
data class StartPageEntry(
    val id: String,
    val title: String,
    val url: String,
    val icon: String? = null, // emoji 或图标名
)