package app.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.core.model.TailnetState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 浏览器屏状态机。
 *
 * 状态包括：
 * - 地址栏输入 / 加载中 / 错误提示
 * - 前进 / 后退 / 停止 / 重载
 * - 收藏（本地持久化）
 * - 导航页（常用入口）
 * - 隧道状态三态（来自 TunnelStatusPort）
 */
@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val tunnelStatus: TunnelStatusPort,
    private val bookmarkStore: BookmarkStore,
) : ViewModel() {

    // 地址栏状态
    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl

    private val _showStartPage = MutableStateFlow(true)
    val showStartPage: StateFlow<Boolean> = _showStartPage

    // 收藏
    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks

    // 导航页常用入口（静态配置，后续可做成可编辑）
    private val _startPageEntries = MutableStateFlow<List<StartPageEntry>>(DEFAULT_START_ENTRIES)
    val startPageEntries: StateFlow<List<StartPageEntry>> = _startPageEntries

    // 隧道状态三态
    private val _tunnelState = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val tunnelState: StateFlow<TunnelState> = _tunnelState

    // 组合 UI 状态
    private val runtime: StateFlow<BrowserRuntime> = urlInput
        .combine(currentUrl) { input, current -> BrowserRuntime(urlInput = input, currentUrl = current) }
        .combine(loadError) { base, error -> base.copy(loadError = error) }
        .combine(isLoading) { base, loading -> base.copy(isLoading = loading) }
        .combine(canGoBack) { base, back -> base.copy(canGoBack = back) }
        .combine(canGoForward) { base, forward -> base.copy(canGoForward = forward) }
        .combine(showStartPage) { base, show -> base.copy(showStartPage = show) }
        .combine(bookmarks) { base, marks -> base.copy(bookmarks = marks) }
        .combine(startPageEntries) { base, entries -> base.copy(startPageEntries = entries) }
        .combine(tunnelState) { base, tunnel -> base.copy(tunnelState = tunnel) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowserRuntime())

    val uiState: StateFlow<BrowserUiState> = runtime
        .map { r ->
            BrowserUiState(
                urlInput = r.urlInput,
                isLoading = r.isLoading,
                loadError = r.loadError,
                canGoBack = r.canGoBack,
                canGoForward = r.canGoForward,
                currentUrl = r.currentUrl,
                showStartPage = r.showStartPage,
                bookmarks = r.bookmarks,
                startPageEntries = r.startPageEntries,
                tunnelState = r.tunnelState,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowserUiState())

    init {
        loadBookmarks()
        observeTunnelState()
    }

    private fun loadBookmarks() {
        viewModelScope.launch {
            _bookmarks.value = bookmarkStore.getAll()
        }
    }

    private fun observeTunnelState() {
        viewModelScope.launch {
            tunnelStatus.state.collect { state ->
                _tunnelState.value = state
            }
        }
    }

    /** 地址栏输入变化。 */
    fun onUrlInputChange(text: String) {
        _urlInput.value = text
    }

    /** 提交地址（回车或点击前往）。 */
    fun onSubmitUrl() {
        val input = _urlInput.value.trim()
        if (input.isEmpty()) return

        val url = normalizeUrl(input)
        _urlInput.value = url
        _currentUrl.value = url
        _showStartPage.value = false
        _loadError.value = null
        _isLoading.value = true
        // 实际加载由 WebView 客户端触发回调通知
    }

    /** 规范化 URL：补全 scheme。 */
    private fun normalizeUrl(input: String): String {
        return if (input.startsWith("http://") || input.startsWith("https://")) {
            input
        } else if (input.contains(".") && !input.contains(" ")) {
            // 看起来像域名，默认 https
            "https://$input"
        } else {
            // 当作搜索词，跳转到搜索引擎
            "https://www.google.com/search?q=${input.replace(" ", "+")}"
        }
    }

    /** WebView 回调：页面开始加载。 */
    fun onPageStarted(url: String?) {
        url?.let { _currentUrl.value = it }
        _urlInput.value = url ?: ""
        _isLoading.value = true
        _loadError.value = null
        _showStartPage.value = false
    }

    /** WebView 回调：页面加载完成。 */
    fun onPageFinished(url: String?, canGoBack: Boolean, canGoForward: Boolean) {
        url?.let { _currentUrl.value = it }
        _urlInput.value = url ?: ""
        _isLoading.value = false
        _canGoBack.value = canGoBack
        _canGoForward.value = canGoForward
    }

    /** WebView 回调：加载失败。 */
    fun onReceivedError(error: String) {
        _isLoading.value = false
        _loadError.value = error
    }

    /** WebView 命令总线：把导航命令真实送到 WebView 实例。 */
    val commandBus = Channel<BrowserCommand>(Channel.UNLIMITED)
    val commands: ReceiveChannel<BrowserCommand> = commandBus

    /** 前进。 */
    fun onGoForward() {
        commandBus.trySend(BrowserCommand.GoForward)
    }

    /** 后退。 */
    fun onGoBack() {
        commandBus.trySend(BrowserCommand.GoBack)
    }

    /** 停止加载。 */
    fun onStopLoading() {
        _isLoading.value = false
        commandBus.trySend(BrowserCommand.StopLoading)
    }

    /** 重载。 */
    fun onReload() {
        _currentUrl.value?.let { url ->
            _urlInput.value = url
            _loadError.value = null
            _isLoading.value = true
            _showStartPage.value = false
            commandBus.trySend(BrowserCommand.Reload)
        }
    }

    /** 切换收藏状态。 */
    fun onToggleBookmark() {
        val url = _currentUrl.value ?: _urlInput.value
        val title = _urlInput.value
        if (url.isNullOrBlank() || title.isBlank()) return

        viewModelScope.launch {
            val existing = _bookmarks.value.find { it.url == url }
            if (existing != null) {
                bookmarkStore.remove(existing.id)
                _bookmarks.value = _bookmarks.value.filter { it.id != existing.id }
            } else {
                val bookmark = Bookmark(
                    id = java.util.UUID.randomUUID().toString(),
                    title = title,
                    url = url,
                )
                bookmarkStore.add(bookmark)
                _bookmarks.value = _bookmarks.value + bookmark
            }
        }
    }

    /** 打开收藏。 */
    fun onOpenBookmark(bookmark: Bookmark) {
        _urlInput.value = bookmark.url
        _currentUrl.value = bookmark.url
        _showStartPage.value = false
        _loadError.value = null
        _isLoading.value = true
        // 实际加载由 WebView 客户端触发
    }

    /** 删除收藏。 */
    fun onDeleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkStore.remove(bookmark.id)
            _bookmarks.value = _bookmarks.value.filter { it.id != bookmark.id }
        }
    }

    /** 点击导航页入口。 */
    fun onOpenStartEntry(entry: StartPageEntry) {
        _urlInput.value = entry.url
        _currentUrl.value = entry.url
        _showStartPage.value = false
        _loadError.value = null
        _isLoading.value = true
    }

    /** 连接隧道（打开授权 URL）。 */
    fun onConnectTunnel() {
        viewModelScope.launch {
            tunnelStatus.startAuth()
        }
    }

    /** 断开隧道。 */
    fun onDisconnectTunnel() {
        viewModelScope.launch {
            tunnelStatus.disconnect()
        }
    }

    /** 刷新隧道状态。 */
    fun refreshTunnelState() {
        // TunnelStatusPort.state 是 Flow，会自动更新
    }

    companion object {
        /** 默认导航页入口。 */
        val DEFAULT_START_ENTRIES = listOf(
            StartPageEntry("1", "GitHub", "https://github.com", "🐙"),
            StartPageEntry("2", "Google", "https://google.com", "🔍"),
            StartPageEntry("3", "Stack Overflow", "https://stackoverflow.com", "📚"),
            StartPageEntry("4", "MDN Web Docs", "https://developer.mozilla.org", "📖"),
            StartPageEntry("5", "Tailscale", "https://tailscale.com", "🔐"),
        )
    }
}

/** 组合用的中间状态（对应 DashboardViewModel 的 Runtime 写法）。 */
private data class BrowserRuntime(
    val urlInput: String = "",
    val currentUrl: String? = null,
    val loadError: String? = null,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val showStartPage: Boolean = true,
    val bookmarks: List<Bookmark> = emptyList(),
    val startPageEntries: List<StartPageEntry> = emptyList(),
    val tunnelState: TunnelState = TunnelState.Disconnected,
)

/** 浏览器屏 UI 状态。 */
data class BrowserUiState(
    val urlInput: String = "",
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val currentUrl: String? = null,
    val showStartPage: Boolean = true,
    val bookmarks: List<Bookmark> = emptyList(),
    val startPageEntries: List<StartPageEntry> = emptyList(),
    val tunnelState: TunnelState = TunnelState.Disconnected,
)

/** WebView 导航命令。 */
sealed interface BrowserCommand {
    data object GoBack : BrowserCommand
    data object GoForward : BrowserCommand
    data object Reload : BrowserCommand
    data object StopLoading : BrowserCommand
}