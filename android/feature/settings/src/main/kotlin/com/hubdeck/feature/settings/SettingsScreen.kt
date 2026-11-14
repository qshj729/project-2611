@file:OptIn(ExperimentalMaterial3Api::class)

package com.hubdeck.feature.settings

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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hubdeck.app.core.tunnel.TunnelManager
import com.hubdeck.app.core.tunnel.TunnelProvider
import com.hubdeck.app.core.tunnel.TunnelState
import com.hubdeck.app.core.data.importer.HomarrImporter
import com.hubdeck.app.core.data.importer.HubDeckYaml
import com.hubdeck.app.core.data.repo.ServiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ServiceRepository,
    private val tunnelManager: TunnelManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    private val _exportedYaml = MutableStateFlow<String?>(null)
    val exportedYaml: StateFlow<String?> = _exportedYaml

    private val _importSummary = MutableStateFlow<String?>(null)
    val importSummary: StateFlow<String?> = _importSummary

    private val _tunnelState = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val tunnelState: StateFlow<TunnelState> = _tunnelState

    init {
        observeTunnelState()
    }

    private fun observeTunnelState() {
        viewModelScope.launch {
            tunnelManager.tunnelState.collect { state ->
                _tunnelState.value = state
            }
        }
    }

    fun export() {
        viewModelScope.launch {
            val services = repository.observeServices().firstOrNull() ?: emptyList()
            _exportedYaml.value = HubDeckYaml.export(services)
        }
    }

    fun importHomarr(yaml: String) {
        viewModelScope.launch {
            runCatching { HomarrImporter.parse(yaml) }
                .onSuccess { result ->
                    result.services.forEach { repository.upsert(it) }
                    _importSummary.value = "已导入 ${result.services.size} 个服务" +
                        if (result.warnings.isEmpty()) "" else "；${result.warnings.size} 条提示"
                }
                .onFailure { error ->
                    _importSummary.value = "导入失败：${error.message ?: "无法解析 YAML"}"
                }
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
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val exported by viewModel.exportedYaml.collectAsStateWithLifecycle()
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()
    val tunnelState by viewModel.tunnelState.collectAsStateWithLifecycle()
    var homarrYaml by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack, contentDescription = "返回")
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
            // Intranet Connection Section
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("内网连接", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "通过 Tailscale 连接 tailnet 内网服务。无需配置端口转发或公网 IP。\n" +
                            "授权方式：点击下方按钮，在浏览器中完成一次性授权即可。\n" +
                            "注意：不支持 Auth Key 自动上线，只有浏览器授权这一条路。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )

                    when (tunnelState) {
                        is TunnelState.Connected -> {
                            Text(
                                "状态：已连接 (${tunnelState.nodeName})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                            )
                            Text(
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
                            Text(
                                "状态：等待授权中...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                            )
                            Text(
                                "请在浏览器中完成授权，授权完成后将自动连接。",
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
                            Text(
                                "状态：正在连接...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                            )
                        }
                        is TunnelState.Error -> {
                            Text(
                                "状态：连接失败 - ${tunnelState.reason}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
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
                            Text(
                                "状态：未连接内网",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                            )
                            Text(
                                "当前为公网直连模式，可正常访问公网服务。点击下方按钮开始授权以启用内网访问。",
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
                    Text("数据", style = MaterialTheme.typography.titleMedium)
                    Text("目录以 hubdeck.yaml 为准，可随时导出备份。")
                    Button(onClick = { viewModel.export() }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("导出 hubdeck.yaml")
                    }
                    exported?.let { yaml ->
                        Text(
                            text = yaml.take(800),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        )
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("从 Homarr 导入", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = homarrYaml,
                        onValueChange = { homarrYaml = it },
                        label = { Text("粘贴 Homarr config.yaml") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    Button(
                        onClick = { viewModel.importHomarr(homarrYaml) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("导入")
                    }
                    importSummary?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("网络", style = MaterialTheme.typography.titleMedium)
                    Text("家中 Wi-Fi 名称：用于判断「在家 / 在外」。未配置时依靠探测结果推断。")
                    Text("Tailscale secret 与探测参数见 roadmap M2。")
                }
            }
        }
    }
}