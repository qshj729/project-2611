@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hubdeck.app.ui.browser

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * 浏览器屏路由入口（Hilt 版）。
 *
 * 页面不碰 NavController：所有导航动作通过回调交给装配壳（契约 C-12）。
 */
@Composable
fun BrowserRoute(
    onBack: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val viewModel: BrowserViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BrowserContent(
        uiState = uiState,
        onBack = onBack,
        onUrlInputChange = viewModel::onUrlInputChange,
        onSubmitUrl = viewModel::onSubmitUrl,
        onGoBack = viewModel::onGoBack,
        onGoForward = viewModel::onGoForward,
        onStopLoading = viewModel::onStopLoading,
        onReload = viewModel::onReload,
        onToggleBookmark = viewModel::onToggleBookmark,
        onOpenBookmark = viewModel::onOpenBookmark,
        onDeleteBookmark = viewModel::onDeleteBookmark,
        onOpenStartEntry = viewModel::onOpenStartEntry,
        onConnectTunnel = viewModel::onConnectTunnel,
        onDisconnectTunnel = viewModel::onDisconnectTunnel,
        onPageStarted = viewModel::onPageStarted,
        onPageFinished = viewModel::onPageFinished,
        onReceivedError = viewModel::onReceivedError,
        commands = viewModel.commands,
        modifier = modifier,
    )
}

/**
 * 浏览器屏界面（纯函数式：状态进、回调出）。
 *
 * 无障碍要点：
 * - 地址栏有可见 label 与语义；
 * - 导航按钮有 contentDescription；
 * - 收藏/书签可被 TalkBack 朗读；
 * - 状态条三态文案清晰，焦点顺序合理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserContent(
    uiState: BrowserUiState,
    onBack: () -> Unit,
    onUrlInputChange: (String) -> Unit,
    onSubmitUrl: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onStopLoading: () -> Unit,
    onReload: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenBookmark: (Bookmark) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onOpenStartEntry: (StartPageEntry) -> Unit,
    onConnectTunnel: () -> Unit,
    onDisconnectTunnel: () -> Unit,
    onPageStarted: (String?) -> Unit,
    onPageFinished: (String?, Boolean, Boolean) -> Unit,
    onReceivedError: (String) -> Unit,
    commands: kotlinx.coroutines.channels.ReceiveChannel<BrowserCommand>,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val webViewClient = remember { BrowserWebViewClient(
        onPageStarted = onPageStarted,
        onPageFinished = onPageFinished,
        onReceivedError = onReceivedError,
    ) }

    // 用户正在输入的文本（与 currentUrl 解耦）
    val userInputText = remember { mutableStateOf(uiState.urlInput) }

    // WebView 实例引用，用于执行导航命令
    val webViewRef = remember { mutableStateOf<android.webkit.WebView?>(null) }

    // 处理授权 URL 打开
    LaunchedEffect(uiState.tunnelState) {
        when (val state = uiState.tunnelState) {
            is TunnelState.NeedsAuth -> {
                state.loginUrl?.let { url ->
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "需要授权才能访问内网服务",
                            actionLabel = "前往授权",
                            duration = androidx.compose.material3.SnackbarDuration.Indefinite,
                        )
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            openAuthUrl(context, url)
                        }
                    }
                }
            }
            else -> Unit
        }
    }

    // 消费 ViewModel 发来的导航命令
    LaunchedEffect(Unit) {
        scope.launch {
            while (true) {
                val cmd = commands.receive()
                val webView = webViewRef.value
                if (webView != null) {
                    when (cmd) {
                        is BrowserCommand.GoBack -> if (webView.canGoBack()) webView.goBack()
                        is BrowserCommand.GoForward -> if (webView.canGoForward()) webView.goForward()
                        is BrowserCommand.Reload -> webView.reload()
                        is BrowserCommand.StopLoading -> webView.stopLoading()
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BrowserTopAppBar(
                uiState = uiState,
                onBack = onBack,
                onUrlInputChange = { text ->
                    userInputText.value = text
                    onUrlInputChange(text)
                },
                onSubmitUrl = onSubmitUrl,
                onGoBack = onGoBack,
                onGoForward = onGoForward,
                onStopLoading = onStopLoading,
                onReload = onReload,
                onToggleBookmark = onToggleBookmark,
                isBookmarked = uiState.bookmarks.any { it.url == uiState.currentUrl },
                userInputText = userInputText,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // WebView 占满全屏
            AndroidWebView(
                modifier = Modifier.fillMaxSize(),
                url = if (uiState.showStartPage) null else uiState.currentUrl,
                webViewClient = webViewClient,
                webViewRef = webViewRef,
            )

            // 导航页（起始页）
            if (uiState.showStartPage && uiState.currentUrl == null) {
                StartPage(
                    modifier = Modifier.fillMaxSize(),
                    entries = uiState.startPageEntries,
                    bookmarks = uiState.bookmarks,
                    onOpenEntry = onOpenStartEntry,
                    onOpenBookmark = onOpenBookmark,
                    onDeleteBookmark = onDeleteBookmark,
                )
            }

            // 加载错误覆盖层
            uiState.loadError?.let { error ->
                ErrorOverlay(
                    modifier = Modifier.fillMaxSize(),
                    error = error,
                    onRetry = onReload,
                    onGoBack = onBack,
                )
            }
        }
    }

    // 底部状态条（三态）
    TunnelStatusBar(
        tunnelState = uiState.tunnelState,
        onConnect = { url ->
            url?.let { openAuthUrl(context, it) }
        },
        onDisconnect = onDisconnectTunnel,
    )
}

/** 顶部应用栏：地址栏 + 导航按钮 + 收藏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTopAppBar(
    uiState: BrowserUiState,
    onBack: () -> Unit,
    onUrlInputChange: (String) -> Unit,
    onSubmitUrl: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onStopLoading: () -> Unit,
    onReload: () -> Unit,
    onToggleBookmark: () -> Unit,
    isBookmarked: Boolean,
    userInputText: androidx.compose.runtime.MutableState<String>,
) {
    TopAppBar(
        title = {
            // 地址栏：显示用户正在输入的文本，不被 currentUrl 覆盖
            OutlinedTextField(
                value = userInputText.value,
                onValueChange = onUrlInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 8.dp),
                singleLine = true,
                label = { Text("地址栏，输入网址或搜索词") },
                leadingIcon = {
                    if (uiState.isLoading) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(start = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingIcon = {
                    if (uiState.isLoading) {
                        IconButton(onClick = onStopLoading) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "停止加载",
                            )
                        }
                    } else {
                        IconButton(onClick = onReload) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "重新加载",
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = { onSubmitUrl() },
                    onDone = { onSubmitUrl() },
                ),
                shape = RoundedCornerShape(20.dp),
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回上一页",
                )
            }
        },
        actions = {
            // 后退/前退按钮（地址栏旁）
            Row(
                modifier = Modifier.padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onGoBack,
                    enabled = uiState.canGoBack,
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "后退",
                    )
                }
                IconButton(
                    onClick = onGoForward,
                    enabled = uiState.canGoForward,
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "前进",
                    )
                }
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isBookmarked) "取消收藏" else "添加收藏",
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** 导航页（起始页）：常用入口 + 收藏。 */
@Composable
private fun StartPage(
    modifier: androidx.compose.ui.Modifier,
    entries: List<StartPageEntry>,
    bookmarks: List<Bookmark>,
    onOpenEntry: (StartPageEntry) -> Unit,
    onOpenBookmark: (Bookmark) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 常用入口
        if (entries.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "常用网站",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(entries) { entry ->
                        StartPageCard(
                            title = entry.title,
                            icon = entry.icon ?: "🌐",
                            onClick = { onOpenEntry(entry) },
                        )
                    }
                }
            }
        }

        // 收藏
        if (bookmarks.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "我的收藏",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(bookmarks) { bookmark ->
                        BookmarkCard(
                            bookmark = bookmark,
                            onClick = { onOpenBookmark(bookmark) },
                            onDelete = { onDeleteBookmark(bookmark) },
                        )
                    }
                }
            }
        }

        // 空状态
        if (entries.isEmpty() && bookmarks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "暂无常用网站或收藏",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 常用入口卡片。 */
@Composable
private fun StartPageCard(
    title: String,
    icon: String,
    onClick: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(80.dp)
            .height(96.dp)
            .fillMaxHeight()
            .semantics { contentDescription = "打开 $title" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = icon,
                fontSize = 28.sp,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 收藏卡片。 */
@Composable
private fun BookmarkCard(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics { contentDescription = "打开收藏：${bookmark.title}" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = bookmark.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除收藏：${bookmark.title}",
                )
            }
        }
    }
}

/** 加载错误覆盖层。 */
@Composable
private fun ErrorOverlay(
    modifier: androidx.compose.ui.Modifier,
    error: String,
    onRetry: () -> Unit,
    onGoBack: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "无法打开页面",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    androidx.compose.material3.Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("重试")
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = onGoBack,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("返回")
                    }
                }
            }
        }
    }
}

/** 状态条展示数据：把隧道三态映射成状态条需要的五个字段。 */
private data class TunnelStatusBarUi(
    val label: String,
    val description: String,
    val actionLabel: String,
    val actionEnabled: Boolean,
    val isConnected: Boolean,
)

/** 底部状态条（三态）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelStatusBar(
    tunnelState: TunnelState,
    onConnect: (String?) -> Unit,
    onDisconnect: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val (label, description, actionLabel, actionEnabled, isConnected) = when (val state = tunnelState) {
        TunnelState.Disconnected -> TunnelStatusBarUi(
            label = "未连接内网",
            description = "点击连接以访问 tailnet 服务",
            actionLabel = "连接",
            actionEnabled = true,
            isConnected = false,
        )
        is TunnelState.Connected -> TunnelStatusBarUi(
            label = "${state.nodeName} · ${state.tailnetIp}",
            description = "已连接到 ${state.tailnetName ?: "tailnet"}，点击断开",
            actionLabel = "断开",
            actionEnabled = true,
            isConnected = true,
        )
        is TunnelState.NeedsAuth -> TunnelStatusBarUi(
            label = "授权中或失败：${state.reason}",
            description = state.loginUrl?.let { url -> "点击前往授权：$url" } ?: "请检查网络后重试",
            actionLabel = "授权",
            actionEnabled = state.loginUrl != null,
            isConnected = false,
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        border = if (isConnected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (actionEnabled) {
                androidx.compose.material3.Button(
                    onClick = {
                        when (val state = tunnelState) {
                            TunnelState.Disconnected -> onConnect(null)
                            is TunnelState.Connected -> onDisconnect()
                            is TunnelState.NeedsAuth -> onConnect(state.loginUrl)
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                        contentColor = if (isConnected) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/** 在外部浏览器打开授权 URL。 */
private fun openAuthUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/** WebView 客户端回调。 */
class BrowserWebViewClient(
    val onPageStarted: (String?) -> Unit,
    val onPageFinished: (String?, Boolean, Boolean) -> Unit,
    val onReceivedError: (String) -> Unit,
) : android.webkit.WebViewClient() {

    override fun onPageStarted(view: android.webkit.WebView, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }

    override fun onPageFinished(view: android.webkit.WebView, url: String) {
        super.onPageFinished(view, url)
        onPageFinished(url, view.canGoBack(), view.canGoForward())
    }

    override fun onReceivedError(
        view: android.webkit.WebView,
        request: android.webkit.WebResourceRequest?,
        error: android.webkit.WebResourceError,
    ) {
        super.onReceivedError(view, request, error)
        onReceivedError(error.description.toString())
    }
}

/** Android WebView 组件。 */
@Composable
fun AndroidWebView(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    url: String?,
    webViewClient: BrowserWebViewClient,
    webViewRef: androidx.compose.runtime.MutableState<android.webkit.WebView?>
) {
    val context = LocalContext.current

    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = {
            android.webkit.WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                this.webViewClient = webViewClient
            }.also { webViewRef.value = it }
        },
        update = { it.webViewClient = webViewClient },
    )

    // URL 变化时加载新 URL，不销毁 WebView 实例
    DisposableEffect(url) {
        url?.let {
            webViewRef.value?.loadUrl(it)
        }
        onDispose {}
    }

    // 组件离开组合时清理
    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.let {
                it.stopLoading()
                it.clearHistory()
                // 注意：不调用 destroy()，避免复用已销毁的 WebView 导致崩溃
                // WebView 会随 Activity 生命周期自动清理
            }
            webViewRef.value = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
private fun BrowserContentPreview() {
    MaterialTheme {
        BrowserContent(
            uiState = BrowserUiState(
                urlInput = "https://github.com",
                showStartPage = true,
                startPageEntries = BrowserViewModel.DEFAULT_START_ENTRIES,
                bookmarks = listOf(
                    Bookmark("1", "GitHub", "https://github.com"),
                    Bookmark("2", "Google", "https://google.com"),
                ),
                tunnelState = TunnelState.Disconnected,
            ),
            onBack = {},
            onUrlInputChange = {},
            onSubmitUrl = {},
            onGoBack = {},
            onGoForward = {},
            onStopLoading = {},
            onReload = {},
            onToggleBookmark = {},
            onOpenBookmark = {},
            onDeleteBookmark = {},
            onOpenStartEntry = {},
            onConnectTunnel = {},
            onDisconnectTunnel = {},
            onPageStarted = {},
            onPageFinished = { _, _, _ -> },
            onReceivedError = {},
            commands = kotlinx.coroutines.channels.Channel(kotlinx.coroutines.channels.Channel.UNLIMITED),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
private fun BrowserContentPreview_Connected() {
    MaterialTheme {
        BrowserContent(
            uiState = BrowserUiState(
                urlInput = "https://nas.tailnet.ts.net",
                currentUrl = "https://nas.tailnet.ts.net",
                showStartPage = false,
                isLoading = false,
                canGoBack = true,
                canGoForward = false,
                tunnelState = TunnelState.Connected(
                    nodeName = "pixel-8",
                    tailnetIp = "100.101.102.103",
                    tailnetName = "my-tailnet",
                ),
            ),
            onBack = {},
            onUrlInputChange = {},
            onSubmitUrl = {},
            onGoBack = {},
            onGoForward = {},
            onStopLoading = {},
            onReload = {},
            onToggleBookmark = {},
            onOpenBookmark = {},
            onDeleteBookmark = {},
            onOpenStartEntry = {},
            onConnectTunnel = {},
            onDisconnectTunnel = {},
            onPageStarted = {},
            onPageFinished = { _, _, _ -> },
            onReceivedError = {},
            commands = kotlinx.coroutines.channels.Channel(kotlinx.coroutines.channels.Channel.UNLIMITED),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
private fun BrowserContentPreview_NeedsAuth() {
    MaterialTheme {
        BrowserContent(
            uiState = BrowserUiState(
                urlInput = "https://nas.tailnet.ts.net",
                showStartPage = false,
                tunnelState = TunnelState.NeedsAuth(
                    reason = "Tailscale 登录已过期",
                    loginUrl = "https://login.tailscale.com/start?device=...",
                ),
            ),
            onBack = {},
            onUrlInputChange = {},
            onSubmitUrl = {},
            onGoBack = {},
            onGoForward = {},
            onStopLoading = {},
            onReload = {},
            onToggleBookmark = {},
            onOpenBookmark = {},
            onDeleteBookmark = {},
            onOpenStartEntry = {},
            onConnectTunnel = {},
            onDisconnectTunnel = {},
            onPageStarted = {},
            onPageFinished = { _, _, _ -> },
            onReceivedError = {},
            commands = kotlinx.coroutines.channels.Channel(kotlinx.coroutines.channels.Channel.UNLIMITED),
        )
    }
}