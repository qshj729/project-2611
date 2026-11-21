@file:OptIn(ExperimentalMaterial3Api::class)

package app.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.core.data.settings.CardDensityPreference
import app.core.data.settings.HubDeckSettings
import app.core.data.settings.NetworkModeLock
import app.core.data.settings.SettingsStore
import app.core.data.settings.ThemePreference
import app.core.data.importer.HomarrImporter
import app.core.data.importer.HubDeckYaml
import app.core.data.repo.ServiceRepository
import app.core.tunnel.TunnelManager
import app.core.tunnel.TunnelProvider
import app.core.tunnel.TunnelState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ServiceRepository,
    private val settingsStore: SettingsStore,
    private val tunnelManager: TunnelManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _settings = MutableStateFlow(HubDeckSettings())
    val settings: StateFlow<HubDeckSettings> = _settings

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
            settingsStore.observe().collect { settings ->
                _settings.value = settings
            }
        }
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
            val currentSettings = settings.value
            _exportedYaml.value = HubDeckYaml.export(
                services = services,
                magicDnsSuffix = currentSettings.magicDnsSuffix,
                homeSsid = currentSettings.homeSsid,
            )
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
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun setHomeSsid(value: String?) {
        viewModelScope.launch { settingsStore.setHomeSsid(value) }
    }

    fun setProbeIntervalMinutes(value: Int) {
        viewModelScope.launch { settingsStore.setProbeIntervalMinutes(value) }
    }

    fun setProbeTimeoutMs(value: Int) {
        viewModelScope.launch { settingsStore.setProbeTimeoutMs(value) }
    }

    fun setProbeWifiOnly(value: Boolean) {
        viewModelScope.launch { settingsStore.setProbeWifiOnly(value) }
    }

    fun setNetworkModeLock(value: NetworkModeLock) {
        viewModelScope.launch { settingsStore.setNetworkModeLock(value) }
    }

    fun setThemePreference(value: ThemePreference) {
        viewModelScope.launch { settingsStore.setThemePreference(value) }
    }

    fun setCardDensity(value: CardDensityPreference) {
        viewModelScope.launch { settingsStore.setCardDensity(value) }
    }

    fun openOpenSourceLicenses() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/qshj729/hubdeck/blob/main/LICENSE"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val exported by viewModel.exportedYaml.collectAsStateWithLifecycle()
    val importSummary by viewModel.importSummary.collectAsStateWithLifecycle()
    val tunnelState by viewModel.tunnelState.collectAsStateWithLifecycle()
    var homarrYaml by remember { mutableStateOf("") }
    var homarrYamlExpanded by remember { mutableStateOf(false) }
    var homeSsidText by remember { mutableStateOf(settings.homeSsid.orEmpty()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
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
            // 1. 内网连接
            ConnectionCard(viewModel = viewModel, tunnelState = tunnelState)

            // 2. 数据
            DataCard(viewModel = viewModel, exported = exported)

            // 3. 从 Homarr 导入
            HomarrImportCard(
                viewModel = viewModel,
                homarrYaml = homarrYaml,
                onHomarrYamlChange = { homarrYaml = it },
                homarrYamlExpanded = homarrYamlExpanded,
                onExpandedChange = { homarrYamlExpanded = it },
                importSummary = importSummary,
            )

            // 4. 网络模式
            NetworkModeCard(
                viewModel = viewModel,
                settings = settings,
                homeSsidText = homeSsidText,
                onHomeSsidTextChange = { homeSsidText = it }
            )

            // 5. 探测设置
            ProbeSettingsCard(viewModel = viewModel, settings = settings)

            // 6. 外观
            AppearanceCard(viewModel = viewModel, settings = settings)

            // 7. 凭据管理
            CredentialsCard(viewModel = viewModel)

            // 8. 关于
            AboutCard(viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionCard(
    viewModel: SettingsViewModel,
    tunnelState: TunnelState,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("内网连接", style = MaterialTheme.typography.titleMedium)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataCard(
    viewModel: SettingsViewModel,
    exported: String?,
) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomarrImportCard(
    viewModel: SettingsViewModel,
    homarrYaml: String,
    onHomarrYamlChange: (String) -> Unit,
    homarrYamlExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    importSummary: String?,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onExpandedChange(!homarrYamlExpanded) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("从 Homarr 导入", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (homarrYamlExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (homarrYamlExpanded) "收起" else "展开",
                )
            }

            if (homarrYamlExpanded) {
                OutlinedTextField(
                    value = homarrYaml,
                    onValueChange = onHomarrYamlChange,
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkModeCard(
    viewModel: SettingsViewModel,
    settings: HubDeckSettings,
    homeSsidText: String,
    onHomeSsidTextChange: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("网络模式", style = MaterialTheme.typography.titleMedium)
            Text(
                "家中 Wi-Fi 名称：用于判断「在家 / 在外」。未配置时依靠探测结果推断。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = homeSsidText,
                onValueChange = { newText ->
                    onHomeSsidTextChange(newText)
                    viewModel.setHomeSsid(newText)
                },
                label = { Text("家中 Wi-Fi SSID") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                trailingIcon = {
                    if (homeSsidText.isNotBlank()) {
                        IconButton(onClick = {
                            onHomeSsidTextChange("")
                            viewModel.setHomeSsid("")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "清除 SSID",
                            )
                        }
                    }
                },
            )

            Text("手动锁定网络模式", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NetworkModeLock.entries.forEach { mode ->
                    val isSelected = settings.networkModeLock == mode
                    androidx.compose.material3.RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.setNetworkModeLock(mode) },
                        modifier = Modifier.weight(1f, fill = true).semantics {
                            contentDescription = "${mode.raw} ${if (isSelected) "已选中" else "未选中"}"
                        },
                    )
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.weight(1f, fill = true)
                    ) {
                        Text(
                            text = when (mode) {
                                NetworkModeLock.AUTO -> "自动"
                                NetworkModeLock.HOME -> "强制在家"
                                NetworkModeLock.AWAY -> "强制在外"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProbeSettingsCard(
    viewModel: SettingsViewModel,
    settings: HubDeckSettings,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("探测设置", style = MaterialTheme.typography.titleMedium)
            Text(
                "配置周期性健康检查的行为。探测结果用于显示服务状态与网络模式判定。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 探测间隔
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("探测间隔", style = MaterialTheme.typography.bodyMedium)
                    Text("${settings.probeIntervalMinutes} 分钟", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Slider(
                    value = settings.probeIntervalMinutes.toFloat(),
                    onValueChange = { viewModel.setProbeIntervalMinutes(it.roundToInt()) },
                    valueRange = 1f..(24 * 60).toFloat(),
                    steps = (24 * 60 - 1),
                    modifier = Modifier.weight(1f),
                )
            }

            // 探测超时
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("探测超时", style = MaterialTheme.typography.bodyMedium)
                    Text("${settings.probeTimeoutMs} ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Slider(
                    value = settings.probeTimeoutMs.toFloat(),
                    onValueChange = { viewModel.setProbeTimeoutMs(it.roundToInt()) },
                    valueRange = 500f..30000f,
                    steps = 59,
                    modifier = Modifier.weight(1f),
                )
            }

            // 仅 Wi-Fi 探测
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("仅在 Wi-Fi 下探测", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.probeWifiOnly,
                    onCheckedChange = { viewModel.setProbeWifiOnly(it) },
                )
            }
            Text(
                "开启后，周期探测仅在连接 Wi-Fi 且充电时运行，省电。手动点击「刷新」不受此限制。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceCard(
    viewModel: SettingsViewModel,
    settings: HubDeckSettings,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("外观", style = MaterialTheme.typography.titleMedium)

            // 主题偏好
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
                            .clickable { viewModel.setThemePreference(theme) }
                            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.setThemePreference(theme) },
                        )
                        Spacer(modifier = Modifier.width(12.dp))
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

            // 卡片密度
            Text("卡片密度", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardDensityPreference.entries.forEach { density ->
                    val isSelected = settings.cardDensity == density
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setCardDensity(density) }
                            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.setCardDensity(density) },
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = when (density) {
                                CardDensityPreference.COMPACT -> "紧凑"
                                CardDensityPreference.NORMAL -> "标准"
                                CardDensityPreference.COMFORTABLE -> "舒适"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CredentialsCard(
    viewModel: SettingsViewModel,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("凭据管理", style = MaterialTheme.typography.titleMedium)
            Text(
                "本地加密存储的凭据（Local API secret、控制平面 token）。导出时默认不包含凭据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "凭据列表与管理界面将在后续版本提供。当前可通过 Tailscale 面板保存 Local API secret。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.openOpenSourceLicenses() },
                ) {
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
                androidx.compose.material3.TextButton(
                    onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/qshj729/hubdeck")
                        )
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                ) {
                    Text("GitHub")
                }
            }

            Text(
                "HubDeck —— 自托管服务聚合台。把散落的自建服务收进一个原生 Android 入口，并用 Tailscale 让它在任何网络下都能直达。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            )
        }
    }
}