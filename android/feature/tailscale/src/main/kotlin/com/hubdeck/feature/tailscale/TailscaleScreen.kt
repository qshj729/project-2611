@file:OptIn(ExperimentalMaterial3Api::class)

package com.hubdeck.feature.tailscale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hubdeck.app.core.model.TailnetDevice
import com.hubdeck.app.core.model.TailnetState
import com.hubdeck.app.core.tailscale.TailnetRepository
import com.hubdeck.app.core.tunnel.ProxyConfigurator
import com.hubdeck.app.core.tunnel.TunnelManager
import com.hubdeck.app.core.tunnel.TunnelProvider
import com.hubdeck.app.core.tunnel.TunnelState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TailscaleViewModel @Inject constructor(
    private val tailnetRepository: TailnetRepository,
    private val proxyConfigurator: ProxyConfigurator,
    private val tunnelManager: TunnelManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    private val _state = MutableStateFlow(TailnetState())
    val state: StateFlow<TailnetState> = _state

    private val _devices = MutableStateFlow<List<TailnetDevice>>(emptyList())
    val devices: StateFlow<List<TailnetDevice>> = _devices

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _tunnelState = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val tunnelState: StateFlow<TunnelState> = _tunnelState

    init {
        observeTunnelState()
        refresh()
    }

    private fun observeTunnelState() {
        viewModelScope.launch {
            tunnelManager.tunnelState.collect { state ->
                _tunnelState.value = state
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val newState = tailnetRepository.refreshLocalState()
            _state.value = newState
            // Apply proxy configuration based on tailnet state
            proxyConfigurator.apply(context, newState)
            val tailnet = newState.tailnetName
            _devices.value = if (tailnet.isNullOrBlank()) emptyList() else tailnetRepository.devices(tailnet)
        }
    }

    fun saveLocalApiSecret(secret: String) {
        viewModelScope.launch {
            tailnetRepository.storeLocalApiSecret(secret)
            _message.value = "已保存本机 Local API secret，正在重新读取状态…"
            refresh()
        }
    }

    fun startTunnelAuthorization() {
        viewModelScope.launch {
            val authUrl = tunnelManager.authUrl.firstOrNull()
            authUrl?.let { url ->
                openInBrowser(url)
            }
        }
    }

    fun clearAuthKey() {
        TunnelProvider.clearAuthKey(context)
        tunnelManager.stop()
    }

    private fun openInBrowser(url: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TailscaleScreen(
    onBack: () -> Unit,
    viewModel: TailscaleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val tunnelState by viewModel.tunnelState.collectAsStateWithLifecycle()
    var secret by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Tailscale") }, navigationIcon = {
            androidx.compose.material3.IconButton(onClick = onBack) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                    contentDescription = "返回"
                )
            }
        }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Tunnel Status Section
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("内网连接状态", style = MaterialTheme.typography.titleMedium)
                    
                    when (tunnelState) {
                        is TunnelState.Connected -> {
                            Row(
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.VpnKey,
                                    contentDescription = "已连接",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Modifier.width(8.dp))
                                Text(
                                    "已连接到 tailnet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                "节点：${tunnelState.nodeName}\n" +
                                    "MagicDNS 后缀：${tunnelState.magicDnsSuffix ?: "-"}\n" +
                                    "节点 IP：${tunnelState.tailnetIps.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                            )
                            Row(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                                Button(onClick = { viewModel.clearAuthKey() }) {
                                    Text("断开连接并清除凭据")
                                }
                            }
                        }
                        is TunnelState.Authorizing -> {
                            Row(
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = androidx.compose.foundation.layout.Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Modifier.width(8.dp))
                                Text(
                                    "等待授权中...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            Text(
                                "请在浏览器中完成授权，授权完成后将自动连接。\n" +
                                    "授权链接：${tunnelState.loginUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                            )
                            Row(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                                Button(onClick = { viewModel.startTunnelAuthorization() }) {
                                    Text("打开授权链接")
                                }
                            }
                        }
                        is TunnelState.Starting -> {
                            Row(
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = androidx.compose.foundation.layout.Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Modifier.width(8.dp))
                                Text(
                                    "正在连接...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        is TunnelState.Error -> {
                            Row(
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Error,
                                    contentDescription = "错误",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Modifier.width(8.dp))
                                Text(
                                    "连接失败",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Text(
                                "原因：${tunnelState.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                            )
                            Row(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                                Button(onClick = { viewModel.startTunnelAuthorization() }) {
                                    Text("重新授权")
                                }
                                Button(
                                    onClick = { viewModel.clearAuthKey() },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Text("清除凭据")
                                }
                            }
                        }
                        is TunnelState.Disconnected -> {
                            Row(
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.VpnKeyOff,
                                    contentDescription = "未连接",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.foundation.layout.Modifier.width(8.dp))
                                Text(
                                    "未连接内网",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "当前为公网直连模式，可正常访问公网服务。\n" +
                                    "点击下方按钮开始授权以启用内网访问。\n" +
                                    "注意：不支持 Auth Key 自动上线，只有浏览器授权这一条路。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                            )
                            Row(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                                Button(onClick = { viewModel.startTunnelAuthorization() }) {
                                    Text("开始授权连接内网")
                                }
                            }
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("连接状态（Local API）", style = MaterialTheme.typography.titleMedium)
                    Text(if (state.connected) "已加入 tailnet" else "未检测到本机 tailnet 连接")
                    Text("后端状态：${state.backendState ?: "-"}")
                    Text("MagicDNS 后缀：${state.magicDnsSuffix ?: "-"}")
                    Text("本机地址：${state.selfIps.joinToString(", ").ifBlank { "-" }}")
                    Text("来源：${state.source}")
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Local API secret（可选）", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "仅用于读取本机 tailnet 状态，不提供也能正常使用。\n" +
                            "获取方式：在任意已登录的电脑执行 `tailscale debug prefs`，复制其中的 Local API Secret。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = { Text("Local API Secret") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    Button(
                        onClick = { viewModel.saveLocalApiSecret(secret.trim()); secret = "" },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("保存并刷新")
                    }
                }
            }

            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Text("tailnet 设备", style = MaterialTheme.typography.titleMedium)
            if (devices.isEmpty()) {
                Text("未获取到设备列表（需要控制平面 OAuth 凭据，roadmap M3.1）。")
            } else {
                devices.forEach { device ->
                    Text("· ${device.name} ${if (device.online) "在线" else "离线"} ${device.dnsName.orEmpty()}")
                }
            }
        }
    }
}