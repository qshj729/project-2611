package com.hubdeck.app.ui.browser

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Module
import dagger.hilt.InstallIn
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 浏览器模块的 Hilt 绑定（存根实现，T2 就绪后替换为真实实现）。
 *
 * 这些实现遵循 BrowserPorts.kt 中定义的接口签名，避免返工。
 */

@Module
@InstallIn(SingletonComponent::class)
object BrowserModule {

    @Provides
    @Singleton
    fun provideTunnelStatusPort(@ApplicationContext context: Context): TunnelStatusPort =
        StubTunnelStatusPort(context)

    @Provides
    @Singleton
    fun provideBookmarkStore(@ApplicationContext context: Context): BookmarkStore =
        DataStoreBookmarkStore(context)
}

/**
 * 存根隧道状态端口（T2 未就绪时使用）。
 *
 * 状态三态：
 * - Disconnected：未启动 / 未授权
 * - Connected：RUNNING（节点名 + 100.x IP）
 * - NeedsAuth：授权中 / 失败 / 掉线
 */
class StubTunnelStatusPort @Inject constructor(
    private val context: Context,
) : TunnelStatusPort {

    private val prefs = context.getSharedPreferences("hubdeck_tunnel_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<TunnelState>(loadInitialState())
    override val state: StateFlow<TunnelState> = _state

    private fun loadInitialState(): TunnelState {
        val hasAuth = prefs.getBoolean("has_auth_key", false)
        val isRunning = prefs.getBoolean("tunnel_running", false)
        val nodeName = prefs.getString("node_name", "") ?: ""
        val tailnetIp = prefs.getString("tailnet_ip", "") ?: ""
        val tailnetName = prefs.getString("tailnet_name", "") ?: ""
        val authError = prefs.getString("auth_error", "") ?: ""

        return when {
            isRunning && nodeName.isNotBlank() && tailnetIp.isNotBlank() -> {
                TunnelState.Connected(nodeName, tailnetIp, tailnetName.takeIf { it.isNotBlank() })
            }
            hasAuth && !isRunning -> {
                TunnelState.NeedsAuth(
                    reason = if (authError.isNotBlank()) authError else "隧道未运行，点击授权启动",
                    loginUrl = "https://login.tailscale.com/start?device=stub".takeIf { hasAuth }
                )
            }
            else -> TunnelState.Disconnected
        }
    }

    override suspend fun startAuth(): String? {
        // 模拟：标记有 auth key，返回登录 URL
        prefs.edit().putBoolean("has_auth_key", true).apply()
        _state.value = TunnelState.NeedsAuth(
            reason = "请在外部浏览器完成 Tailscale 登录",
            loginUrl = "https://login.tailscale.com/start?device=stub",
        )
        return "https://login.tailscale.com/start?device=stub"
    }

    override suspend fun disconnect() {
        prefs.edit()
            .putBoolean("tunnel_running", false)
            .putString("node_name", "")
            .putString("tailnet_ip", "")
            .apply()
        _state.value = TunnelState.Disconnected
    }

    override suspend fun hasAuthKey(): Boolean = prefs.getBoolean("has_auth_key", false)

    /** 测试用：模拟隧道已连接（T2 接入后由真实实现替换）。 */
    fun simulateConnected(nodeName: String = "pixel-8", tailnetIp: String = "100.101.102.103", tailnetName: String = "my-tailnet") {
        prefs.edit()
            .putBoolean("has_auth_key", true)
            .putBoolean("tunnel_running", true)
            .putString("node_name", nodeName)
            .putString("tailnet_ip", tailnetIp)
            .putString("tailnet_name", tailnetName)
            .apply()
        _state.value = TunnelState.Connected(nodeName, tailnetIp, tailnetName)
    }

    /** 测试用：模拟授权失败。 */
    fun simulateAuthFailure(reason: String) {
        prefs.edit().putString("auth_error", reason).apply()
        _state.value = TunnelState.NeedsAuth(reason = reason, loginUrl = "https://login.tailscale.com/start?device=stub")
    }
}

/** 基于 DataStore/SharedPreferences 的收藏持久化实现。 */
class DataStoreBookmarkStore @Inject constructor(
    private val context: Context,
) : BookmarkStore {

    private val prefs = context.getSharedPreferences("hubdeck_bookmarks", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<List<Bookmark>>() {}.type

    override suspend fun getAll(): List<Bookmark> = prefs.getString("bookmarks", "[]")?.let {
        gson.fromJson(it, type) ?: emptyList()
    } ?: emptyList()

    override suspend fun add(bookmark: Bookmark) {
        val current = getAll()
        if (current.none { it.url == bookmark.url }) {
            val updated = current + bookmark
            prefs.edit().putString("bookmarks", gson.toJson(updated)).apply()
        }
    }

    override suspend fun remove(id: String) {
        val current = getAll()
        val updated = current.filter { it.id != id }
        prefs.edit().putString("bookmarks", gson.toJson(updated)).apply()
    }

    override suspend fun clear() {
        prefs.edit().remove("bookmarks").apply()
    }
}