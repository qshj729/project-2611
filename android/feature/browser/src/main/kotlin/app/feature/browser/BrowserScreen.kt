package app.feature.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.core.data.bookmarks.BookmarkStore
import app.ui.theme.MyAppsTheme
import androidx.compose.ui.tooling.preview.Preview
import app.core.model.TunnelState
import app.feature.browser.GeckoRuntimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    initialUrl: String,
    onUrlConsumed: () -> Unit = {},
    tunnelState: TunnelState = TunnelState.Disconnected,
    authUrl: String? = null,
) {
    val context = LocalContext.current
    val runtime = GeckoRuntimeProvider.get(context)
    val geckoSession = remember { createSession(runtime) }

    var url by remember { mutableStateOf(initialUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showStartPage by remember { mutableStateOf(initialUrl.isBlank()) }

    // 点击状态条授权按钮时打开外部浏览器
    val onAuthClick = remember(authUrl) {
        { url: String? ->
            url?.let { u ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(u))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            Unit
        }
    }

    // 载入目标地址（导航页跳转过来时）
    androidx.compose.runtime.LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank()) {
            loadUrl(geckoSession, initialUrl)
            onUrlConsumed()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TunnelStatusBar(
                tunnelState = tunnelState,
                authUrl = authUrl,
                onAuthClick = onAuthClick,
            )
            BrowserTopAppBar(
                url = url,
                isLoading = isLoading,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onBackClick = {
                    if (canGoBack) geckoSession.goBack()
                },
                onForwardClick = { geckoSession.goForward() },
                onReloadClick = {
                    if (isLoading) geckoSession.stop()
                    else geckoSession.reload()
                },
                onHomeClick = {
                    url = ""
                    showStartPage = true
                },
                onSubmitUrl = { newUrl ->
                    url = newUrl
                    showStartPage = false
                    loadUrl(geckoSession, newUrl)
                },
                onUrlChange = { newUrl ->
                    url = newUrl
                },
            )

            if (showStartPage) {
                StartPage(onUrlClick = { u ->
                    url = u
                    showStartPage = false
                    loadUrl(geckoSession, u)
                })
            } else {
                AndroidGeckoView(
                    modifier = Modifier.fillMaxSize(),
                    session = geckoSession,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTopAppBar(
    url: String,
    isLoading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onReloadClick: () -> Unit,
    onHomeClick: () -> Unit,
    onSubmitUrl: (String) -> Unit,
    onUrlChange: (String) -> Unit,
) {
    TopAppBar(
        modifier = Modifier.fillMaxWidth(),
        title = {
            TextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                singleLine = true,
                isError = false,
                label = { Text("地址栏") },
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick, enabled = canGoBack || true) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "后退",
                    tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        },
        actions = {
            IconButton(onClick = onForwardClick, enabled = canGoForward) {
                Icon(Icons.Default.ArrowForward, "前进", tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onReloadClick) {
                Icon(if (isLoading) Icons.Default.Close else Icons.Default.Refresh, if (isLoading) "停止" else "刷新")
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onHomeClick) {
                Icon(Icons.Default.Home, "导航页")
            }
        },
    )
}

@Composable
private fun StartPage(onUrlClick: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("常用入口", style = MaterialTheme.typography.titleMedium)
        // TODO: 从 BookmarkStore 读取
        StartPageCard("GitHub", "https://github.com", "🐙", onUrlClick)
        StartPageCard("Google", "https://google.com", "🔍", onUrlClick)
        StartPageCard("Stack Overflow", "https://stackoverflow.com", "📚", onUrlClick)
        StartPageCard("MDN Web Docs", "https://developer.mozilla.org", "📖", onUrlClick)
        StartPageCard("Tailscale", "https://tailscale.com", "🔐", onUrlClick)
    }
}

@Composable
private fun StartPageCard(title: String, url: String, icon: String, onUrlClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onUrlClick(url) },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = icon, fontSize = 28.sp, modifier = Modifier.padding(end = 16.dp))
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AndroidGeckoView(
    modifier: Modifier,
    session: org.mozilla.geckoview.GeckoSession,
) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { ctx ->
            org.mozilla.geckoview.GeckoView(ctx).apply {
                setSession(session)
                setBackgroundColor(android.graphics.Color.WHITE)
            }
        },
        update = { view ->
            view.setSession(session)
        },
    )
}

private fun createSession(runtime: org.mozilla.geckoview.GeckoRuntime): org.mozilla.geckoview.GeckoSession {
    val session = org.mozilla.geckoview.GeckoSession()
    session.open(runtime)
    session.settings.allowJavascript = true
    return session
}

private fun loadUrl(session: org.mozilla.geckoview.GeckoSession, url: String) {
    val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    session.loadUri(normalized)
}

/**
 * 隧道状态到 UI 显示的映射（纯函数，便于单测）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelStatusBar(
    tunnelState: TunnelState,
    authUrl: String?,
    onAuthClick: (String?) -> Unit,
) {
    val ui = remember(tunnelState) { tunnelStateToUi(tunnelState) }
    val colors = tunnelStateToColors(tunnelState)

    // 只有非 Disconnected 状态才显示状态条，避免公网直连时的视觉干扰
    if (tunnelState !is TunnelState.Disconnected) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.backgroundColor,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clickable(enabled = ui.isClickable, onClick = { onAuthClick(authUrl) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = ui.icon,
                    contentDescription = null,
                    tint = colors.iconColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = ui.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textColor,
                    modifier = Modifier.weight(1f),
                )
                if (ui.showChevron) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "点击授权",
                        tint = colors.iconColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * 隧道状态到纯 UI 属性的映射（纯函数，无副作用，便于单测）。
 * 不包含颜色，颜色由 [tunnelStateToColors] 在 Composable 上下文中解析。
 */
data class TunnelStatusUi(
    val text: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val isClickable: Boolean,
    val showChevron: Boolean,
)

/**
 * 纯函数：将 TunnelState 映射为可测试的 UI 属性（文案、图标、可点击性）。
 */
fun tunnelStateToUi(state: TunnelState): TunnelStatusUi = when (state) {
    is TunnelState.Connected -> TunnelStatusUi(
        text = "内网已连接（${state.nodeName}）",
        icon = Icons.Default.Wifi,
        isClickable = false,
        showChevron = false,
    )
    is TunnelState.Starting -> TunnelStatusUi(
        text = "正在连接内网…",
        icon = Icons.Default.HourglassEmpty,
        isClickable = false,
        showChevron = false,
    )
    is TunnelState.Authorizing -> TunnelStatusUi(
        text = "需授权才能连接内网",
        icon = Icons.Default.Help,
        isClickable = true,
        showChevron = true,
    )
    is TunnelState.Error -> TunnelStatusUi(
        text = "连接失败：${state.reason}",
        icon = Icons.Default.Error,
        isClickable = true,
        showChevron = true,
    )
    TunnelState.Disconnected -> TunnelStatusUi(
        text = "",
        icon = Icons.Default.WifiOff,
        isClickable = false,
        showChevron = false,
    )
}

/**
 * 隧道状态到颜色的映射（需在 Composable 上下文中调用）。
 */
@Composable
fun tunnelStateToColors(state: TunnelState): TunnelStatusColors = when (state) {
    is TunnelState.Connected -> TunnelStatusColors(
        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
        textColor = MaterialTheme.colorScheme.onPrimaryContainer,
        iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    is TunnelState.Starting -> TunnelStatusColors(
        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
        textColor = MaterialTheme.colorScheme.onSecondaryContainer,
        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
    is TunnelState.Authorizing -> TunnelStatusColors(
        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
        textColor = MaterialTheme.colorScheme.onTertiaryContainer,
        iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
    is TunnelState.Error -> TunnelStatusColors(
        backgroundColor = MaterialTheme.colorScheme.errorContainer,
        textColor = MaterialTheme.colorScheme.onErrorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
    )
    TunnelState.Disconnected -> TunnelStatusColors(
        backgroundColor = MaterialTheme.colorScheme.surface,
        textColor = MaterialTheme.colorScheme.onSurface,
        iconColor = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * 隧道状态对应的颜色值。
 */
data class TunnelStatusColors(
    val backgroundColor: androidx.compose.ui.graphics.Color,
    val textColor: androidx.compose.ui.graphics.Color,
    val iconColor: androidx.compose.ui.graphics.Color,
)

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "TunnelStatusBar - Connected", showBackground = true)
@Composable
fun TunnelStatusBarPreviewConnected() {
    MyAppsTheme {
        TunnelStatusBar(
            tunnelState = TunnelState.Connected(
                nodeName = "my-device",
                tailnetIps = listOf("100.1.2.3"),
                magicDnsSuffix = "tailnet-xyz.ts.net",
            ),
            authUrl = null,
            onAuthClick = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "TunnelStatusBar - Starting", showBackground = true)
@Composable
fun TunnelStatusBarPreviewStarting() {
    MyAppsTheme {
        TunnelStatusBar(
            tunnelState = TunnelState.Starting,
            authUrl = null,
            onAuthClick = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "TunnelStatusBar - Authorizing", showBackground = true)
@Composable
fun TunnelStatusBarPreviewAuthorizing() {
    MyAppsTheme {
        TunnelStatusBar(
            tunnelState = TunnelState.Authorizing(loginUrl = "https://login.tailscale.com/abc123"),
            authUrl = "https://login.tailscale.com/abc123",
            onAuthClick = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "TunnelStatusBar - Error", showBackground = true)
@Composable
fun TunnelStatusBarPreviewError() {
    MyAppsTheme {
        TunnelStatusBar(
            tunnelState = TunnelState.Error(reason = "Authentication expired"),
            authUrl = "https://login.tailscale.com/reauth",
            onAuthClick = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "TunnelStatusBar - Disconnected (hidden)", showBackground = true)
@Composable
fun TunnelStatusBarPreviewDisconnected() {
    MyAppsTheme {
        TunnelStatusBar(
            tunnelState = TunnelState.Disconnected,
            authUrl = null,
            onAuthClick = {},
        )
    }
}