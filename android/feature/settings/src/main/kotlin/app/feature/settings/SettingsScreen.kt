@file:OptIn(ExperimentalMaterial3Api::class)

package app.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.core.data.config.InternalServicesConfig
import app.core.data.config.InternalServicesStore
import app.core.data.settings.AppSettings
import app.core.data.settings.SettingsStore
import app.core.data.settings.ThemePreference
import app.core.model.SecretStore
import app.core.model.TunnelState
import app.core.tunnel.TunnelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val servicesStore: InternalServicesStore,
    private val secretStore: SecretStore,
    private val tunnelManager: TunnelManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    private val _exportedYaml = MutableStateFlow<String?>(null)
    val exportedYaml: StateFlow<String?> = _exportedYaml

    private val _importSummary = MutableStateFlow<String?>(null)
    val importSummary: StateFlow<String?> = _importSummary

    private val _tunnelState = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val tunnelState: StateFlow<TunnelState> = _tunnelState

    init {
        observeSettings()
        observeTunnelState()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsStore.observe().collect { settings -> _settings.value = settings }
        }
    }

    private fun observeTunnelState() {
        viewModelScope.launch {
            tunnelManager.tunnelState.collect { state -> _tunnelState.value = state }
        }
    }

    fun exportServices() {
        viewModelScope.launch {
            _exportedYaml.value = servicesStore.exportYaml()
        }
    }

    fun importServices(yaml: String) {
        viewModelScope.launch {
            runCatching { InternalServicesConfig.parse(yaml) }
                .onSuccess { result ->
                    servicesStore.replaceAll(result.services)
                    _importSummary.value = if (result.rejected.isEmpty()) {
                        "已导入 ${result.services.size} 个服务"
                    } else {
                        "导入 ${result.services.size} 个；拒绝 ${result.rejected.size} 个（标题为空 / 非 http(s) 地址）"
                    }
                }
                .onFailure { error ->
                    _importSummary.value = "导入失败：${error.message ?: "无法解析 YAML"}"
                }
        }
    }

    fun startTunnelAuthorization() {
        viewModelScope.launch {
            val authUrl = tunnelManager.authUrl.first()
            if (!authUrl.isNullOrBlank()) openInBrowser(authUrl)
        }
    }

    fun clearAuthKey() {
        viewModelScope.launch {
            secretStore.clearAuthKey()
            tunnelManager.stop()
        }
    }

    private fun openInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun setThemePreference(value: ThemePreference) {
        viewModelScope.launch { settingsStore.setThemePreference(value) }
    }

    fun setAutoConnectTunnel(value: Boolean) {
        viewModelScope.launch { settingsStore.setAutoConnectTunnel(value) }
    }

    fun setOpenHomeOnLaunch(value: Boolean) {
        viewModelScope.launch { settingsStore.setOpenHomeOnLaunch(value) }
    }

    fun setUseTailnetProxy(value: Boolean) {
        viewModelScope.launch { settingsStore.setUseTailnetProxy(value) }
    }

    fun openOpenSourceLicenses() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/qshj765/project-2611/blob/main/LICENSE"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val exported by viewModel.exportedYaml.collectAsStateWithLifecycle()
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()
    val tunnelState by viewModel.tunnelState.collectAsStateWithLifecycle()

    var servicesYaml by remember { mutableStateOf("") }
    var servicesYamlExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectionCard(viewModel = viewModel, tunnelState = tunnelState)

            ServicesCard(
                viewModel = viewModel,
                exported = exported,
                importSummary = importSummary,
                servicesYaml = servicesYaml,
                onServicesYamlChange = { servicesYaml = it },
                servicesYamlExpanded = servicesYamlExpanded,
                onExpandedChange = { servicesYamlExpanded = it },
            )

            TunnelSettingsCard(viewModel = viewModel, settings = settings)

            AppearanceCard(viewModel = viewModel, settings = settings)

            AboutCard(viewModel = viewModel)
        }
    }
}

@Composable
private fun ConnectionCard(
    viewModel: SettingsViewModel,
    tunnelState: TunnelState,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("内网连接", style = MaterialTheme.typography.titleMedium)
            Text(
                "通过内置 Tailscale 隧道访问 tailnet 内网服务，无需另装系统级 Tailscale App。\n" +
                    "首次使用请点下方按钮，在浏览器中完成一次性授权。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            )

            when (tunnelState) {
                is TunnelState.Connected -> {
                    Text(
                        "状态：已连接（${tunnelState.nodeName}）",
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
                        "状态：等待授权…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                    Row(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                        Button(onClick = { viewModel.startTunnelAuthorization() }) {
                            Text("打开授权链接")
                        }
                    }
                }

                is TunnelState.Starting -> {
                    Text(
                        "状态：正在连接…",
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
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Text("清除凭据")
                        }
                    }
                }

                TunnelState.Disconnected -> {
                    Text(
                        "状态：未连接内网",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    )
                    Text(
                        "当前为公网直连模式。完成授权后，内网地址才会经隧道访问。",
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
}

@Composable
private fun ServicesCard(
    viewModel: SettingsViewModel,
    exported: String?,
    importSummary: String?,
    servicesYaml: String,
    onServicesYamlChange: (String) -> Unit,
    servicesYamlExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("内网服务清单", style = MaterialTheme.typography.titleMedium)
            Text(
                "以 YAML 形式存储，可导入导出；导航页直接读取这份文件渲染分组卡片。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                Button(onClick = { viewModel.exportServices() }) {
                    Text("导出")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onExpandedChange(true) }) {
                    Text("导入")
                }
            }
            exported?.let { yaml ->
                Text(
                    text = yaml.take(800),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                )
            }
            if (servicesYamlExpanded) {
                OutlinedTextField(
                    value = servicesYaml,
                    onValueChange = onServicesYamlChange,
                    label = { Text("粘贴 services.yaml") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Button(
                    onClick = { viewModel.importServices(servicesYaml) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("确认导入")
                }
            }
            importSummary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TunnelSettingsCard(
    viewModel: SettingsViewModel,
    settings: AppSettings,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("隧道设置", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("启动时自动连接隧道", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.autoConnectTunnel,
                    onCheckedChange = { viewModel.setAutoConnectTunnel(it) },
                )
            }
            Text(
                "开启后，App 启动时若检测到已保存的授权凭据，会自动尝试连接隧道。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("启动时显示内网导航页", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.openHomeOnLaunch,
                    onCheckedChange = { viewModel.setOpenHomeOnLaunch(it) },
                )
            }
            Text(
                "关闭后直接进入浏览器页。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("内网流量走隧道代理", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.useTailnetProxy,
                    onCheckedChange = { viewModel.setUseTailnetProxy(it) },
                )
            }
            Text(
                "关闭后所有流量直连，tailnet 目标将无法访问（除非系统另装 Tailscale App）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AppearanceCard(
    viewModel: SettingsViewModel,
    settings: AppSettings,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("外观", style = MaterialTheme.typography.titleMedium)
            Text("主题", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemePreference.entries.forEach { theme ->
                    val isSelected = settings.themePreference == theme
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setThemePreference(theme) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.setThemePreference(theme) },
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = when (theme) {
                                ThemePreference.SYSTEM -> "跟随系统"
                                ThemePreference.LIGHT -> "浅色模式"
                                ThemePreference.DARK -> "深色模式"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutCard(
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("关于", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("版本", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "0.1.0 (开发版)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("开源协议", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.openOpenSourceLicenses() }) {
                    Text("Apache-2.0")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("源代码", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/qshj765/project-2611"),
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                ) {
                    Text("GitHub")
                }
            }

            Text(
                "MyApps —— 自用的 Tailscale 套壳浏览器：一个 App 取代「浏览器 + Tailscale App」。" +
                    "内置 GeckoView 引擎，书签 / 标签 / 下载 / 历史 / 无痕都由引擎提供；" +
                    "内网地址经本地隧道代理（PAC）访问，公网直连。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            )
        }
    }
}
