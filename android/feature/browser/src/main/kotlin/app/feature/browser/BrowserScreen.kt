package app.feature.browser

import android.content.Context
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.core.data.bookmarks.BookmarkStore
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
) {
    val context = LocalContext.current
    val runtime = GeckoRuntimeProvider.get(context)
    val geckoSession = remember { createSession(runtime) }

    var url by remember { mutableStateOf(initialUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showStartPage by remember { mutableStateOf(initialUrl.isBlank()) }

    // 载入目标地址（导航页跳转过来时）
    androidx.compose.runtime.LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank()) {
            loadUrl(geckoSession, initialUrl)
            onUrlConsumed()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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