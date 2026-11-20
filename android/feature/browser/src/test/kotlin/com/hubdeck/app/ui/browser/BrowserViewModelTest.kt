package com.hubdeck.app.ui.browser

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull

/**
 * BrowserViewModel 状态机单测。
 *
 * 验收标准：
 * - 提交 URL 后 currentUrl 被更新
 * - 后退/前进状态变化
 * - 导航命令正确发送到 commandBus
 */
@ExperimentalCoroutinesApi
class BrowserViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher()
    private lateinit var tunnelStatusPort: TunnelStatusPort
    private lateinit var bookmarkStore: BookmarkStore
    private lateinit var viewModel: BrowserViewModel

    @Before
    fun setup() {
        // BrowserViewModel 用 viewModelScope（Dispatchers.Main），JVM 单测没有 Main dispatcher。
        Dispatchers.setMain(UnconfinedTestDispatcher())

        tunnelStatusPort = mock(TunnelStatusPort::class.java, org.mockito.Mockito.RETURNS_DEEP_STUBS)
        bookmarkStore = mock(BookmarkStore::class.java)

        // 默认返回断开状态
        val stateFlow = kotlinx.coroutines.flow.MutableStateFlow<TunnelState>(TunnelState.Disconnected)
        whenever(tunnelStatusPort.state).thenReturn(stateFlow)
        whenever { tunnelStatusPort.startAuth() }.thenReturn("https://login.tailscale.com/start")
        whenever { tunnelStatusPort.disconnect() }.thenReturn(Unit)
        whenever { tunnelStatusPort.hasAuthKey() }.thenReturn(false)

        whenever { bookmarkStore.getAll() }.thenReturn(emptyList())
        whenever { bookmarkStore.add(any()) }.thenReturn(Unit)
        whenever { bookmarkStore.remove(any()) }.thenReturn(Unit)
        whenever { bookmarkStore.clear() }.thenReturn(Unit)

        viewModel = BrowserViewModel(tunnelStatusPort, bookmarkStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onSubmitUrl updates currentUrl and sets loading`() = runTest {
        // 初始状态检查
        assertEquals("", viewModel.uiState.first().urlInput)
        assertNull(viewModel.uiState.first().currentUrl)
        assertTrue(viewModel.uiState.first().showStartPage)
        assertFalse(viewModel.uiState.first().isLoading)

        // 提交 URL
        viewModel.onUrlInputChange("https://example.com")
        viewModel.onSubmitUrl()

        // 验证状态更新
        val state = viewModel.uiState.first()
        assertEquals("https://example.com", state.urlInput)
        assertEquals("https://example.com", state.currentUrl)
        assertFalse(state.showStartPage)
        assertTrue(state.isLoading)
        assertNull(state.loadError)
    }

    @Test
    fun `onSubmitUrl with search term normalizes to Google search`() = runTest {
        viewModel.onUrlInputChange("hello world")
        viewModel.onSubmitUrl()

        val state = viewModel.uiState.first()
        assertEquals("https://www.google.com/search?q=hello+world", state.urlInput)
        assertEquals("https://www.google.com/search?q=hello+world", state.currentUrl)
        assertFalse(state.showStartPage)
        assertTrue(state.isLoading)
    }

    @Test
    fun `onSubmitUrl with domain-like input adds https`() = runTest {
        viewModel.onUrlInputChange("github.com")
        viewModel.onSubmitUrl()

        val state = viewModel.uiState.first()
        assertEquals("https://github.com", state.urlInput)
        assertEquals("https://github.com", state.currentUrl)
    }

    @Test
    fun `onOpenStartEntry updates currentUrl and sets loading`() = runTest {
        val entry = StartPageEntry("1", "GitHub", "https://github.com", "🐙")

        viewModel.onOpenStartEntry(entry)

        val state = viewModel.uiState.first()
        assertEquals("https://github.com", state.urlInput)
        assertEquals("https://github.com", state.currentUrl)
        assertFalse(state.showStartPage)
        assertTrue(state.isLoading)
        assertNull(state.loadError)
    }

    @Test
    fun `onOpenBookmark updates currentUrl and sets loading`() = runTest {
        val bookmark = Bookmark("1", "GitHub", "https://github.com")

        viewModel.onOpenBookmark(bookmark)

        val state = viewModel.uiState.first()
        assertEquals("https://github.com", state.urlInput)
        assertEquals("https://github.com", state.currentUrl)
        assertFalse(state.showStartPage)
        assertTrue(state.isLoading)
        assertNull(state.loadError)
    }

    @Test
    fun `onPageStarted updates currentUrl and urlInput`() = runTest {
        viewModel.onPageStarted("https://loaded.com")

        val state = viewModel.uiState.first()
        assertEquals("https://loaded.com", state.currentUrl)
        assertEquals("https://loaded.com", state.urlInput)
        assertTrue(state.isLoading)
        assertFalse(state.showStartPage)
        assertNull(state.loadError)
    }

    @Test
    fun `onPageFinished updates canGoBack and canGoForward`() = runTest {
        viewModel.onPageFinished("https://finished.com", true, false)

        val state = viewModel.uiState.first()
        assertEquals("https://finished.com", state.currentUrl)
        assertEquals("https://finished.com", state.urlInput)
        assertFalse(state.isLoading)
        assertTrue(state.canGoBack)
        assertFalse(state.canGoForward)
    }

    @Test
    fun `onReceivedError sets error and stops loading`() = runTest {
        viewModel.onReceivedError("网络错误")

        val state = viewModel.uiState.first()
        assertFalse(state.isLoading)
        assertEquals("网络错误", state.loadError)
    }

    @Test
    fun `onGoBack sends GoBack command`() = runTest {
        val commands = viewModel.commands as Channel<BrowserCommand>

        viewModel.onGoBack()

        // 验证命令已发送（非阻塞 trySend）
        val cmd = commands.tryReceive().getOrNull()
        assertNotNull(cmd)
        assertTrue(cmd is BrowserCommand.GoBack)
    }

    @Test
    fun `onGoForward sends GoForward command`() = runTest {
        val commands = viewModel.commands as Channel<BrowserCommand>

        viewModel.onGoForward()

        val cmd = commands.tryReceive().getOrNull()
        assertNotNull(cmd)
        assertTrue(cmd is BrowserCommand.GoForward)
    }

    @Test
    fun `onStopLoading sends StopLoading command and sets loading false`() = runTest {
        val commands = viewModel.commands as Channel<BrowserCommand>

        // 先设置加载中
        viewModel.onPageStarted("https://example.com")
        assertTrue(viewModel.uiState.first().isLoading)

        viewModel.onStopLoading()

        assertFalse(viewModel.uiState.first().isLoading)
        val cmd = commands.tryReceive().getOrNull()
        assertNotNull(cmd)
        assertTrue(cmd is BrowserCommand.StopLoading)
    }

    @Test
    fun `onReload sends Reload command and sets loading`() = runTest {
        val commands = viewModel.commands as Channel<BrowserCommand>

        // 先设置一个 currentUrl
        viewModel.onPageStarted("https://example.com")

        viewModel.onReload()

        val state = viewModel.uiState.first()
        assertTrue(state.isLoading)
        assertNull(state.loadError)
        assertFalse(state.showStartPage)
        assertEquals("https://example.com", state.urlInput)

        val cmd = commands.tryReceive().getOrNull()
        assertNotNull(cmd)
        assertTrue(cmd is BrowserCommand.Reload)
    }

    @Test
    fun `onReload without currentUrl does nothing`() = runTest {
        val commands = viewModel.commands as Channel<BrowserCommand>

        // currentUrl 为 null 时
        viewModel.onReload()

        val state = viewModel.uiState.first()
        assertFalse(state.isLoading)
        // 没有发送命令
        assertNull(commands.tryReceive().getOrNull())
    }

    @Test
    fun `onToggleBookmark adds bookmark when not bookmarked`() = runTest {
        viewModel.onPageStarted("https://example.com")

        viewModel.onToggleBookmark()

        verify(bookmarkStore).add(anyOrNull())
        val state = viewModel.uiState.first()
        assertEquals(1, state.bookmarks.size)
        assertEquals("https://example.com", state.bookmarks[0].url)
    }

    @Test
    fun `onToggleBookmark removes bookmark when already bookmarked`() = runTest {
        val existingBookmark = Bookmark("1", "Example", "https://example.com")
        whenever { bookmarkStore.getAll() }.thenReturn(listOf(existingBookmark))

        // 重新初始化 viewModel 以加载现有书签
        viewModel = BrowserViewModel(tunnelStatusPort, bookmarkStore)
        // 等待初始化完成
        kotlinx.coroutines.delay(10)

        viewModel.onPageStarted("https://example.com")
        viewModel.onToggleBookmark()

        verify(bookmarkStore).remove("1")
        val state = viewModel.uiState.first()
        assertEquals(0, state.bookmarks.size)
    }

    @Test
    fun `onDeleteBookmark removes bookmark`() = runTest {
        val bookmark = Bookmark("1", "Example", "https://example.com")
        whenever { bookmarkStore.getAll() }.thenReturn(listOf(bookmark))

        viewModel = BrowserViewModel(tunnelStatusPort, bookmarkStore)
        kotlinx.coroutines.delay(10)

        viewModel.onDeleteBookmark(bookmark)

        verify(bookmarkStore).remove("1")
        val state = viewModel.uiState.first()
        assertEquals(0, state.bookmarks.size)
    }

    @Test
    fun `onConnectTunnel calls tunnelStatus startAuth`() = runTest {
        viewModel.onConnectTunnel()

        verify(tunnelStatusPort).startAuth()
    }

    @Test
    fun `onDisconnectTunnel calls tunnelStatus disconnect`() = runTest {
        viewModel.onDisconnectTunnel()

        verify(tunnelStatusPort).disconnect()
    }

    @Test
    fun `initial state shows start page`() = runTest {
        val state = viewModel.uiState.first()
        assertTrue(state.showStartPage)
        assertNull(state.currentUrl)
        assertEquals("", state.urlInput)
        assertFalse(state.isLoading)
        assertNull(state.loadError)
    }
}