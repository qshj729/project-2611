package app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.core.data.config.InternalServicesConfig
import app.core.data.config.InternalServicesStore
import app.core.data.settings.AppSettings
import app.core.data.settings.SettingsStore
import app.core.data.settings.ThemePreference
import app.core.model.SecretStore
import app.core.model.TunnelState
import app.core.tunnel.TunnelStateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 测试用的 [SettingsViewModel] 实现。
 *
 * 实现 [SettingsViewModel] 接口，使用假存储实现，不依赖 Hilt、Context 或真实 TunnelManager。
 * 所有 StateFlow 暴露为 public，测试可直接断言或控制。
 */
class TestSettingsViewModel(
    private val settingsStore: SettingsStore,
    private val servicesStore: InternalServicesStore,
    private val secretStore: SecretStore,
    private val tunnelStateProvider: TunnelStateProvider,
) : ViewModel(), SettingsViewModel {

    private val _settings = MutableStateFlow<AppSettings>(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings

    private val _exportedYaml = MutableStateFlow<String?>(null)
    override val exportedYaml: StateFlow<String?> = _exportedYaml

    private val _importSummary = MutableStateFlow<String?>(null)
    override val importSummary: StateFlow<String?> = _importSummary

    private val _tunnelState = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    override val tunnelState: StateFlow<TunnelState> = _tunnelState

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
            tunnelStateProvider.tunnelState.collect { state -> _tunnelState.value = state }
        }
    }

    override fun exportServices() {
        viewModelScope.launch {
            _exportedYaml.value = servicesStore.exportYaml()
        }
    }

    override fun importServices(yaml: String) {
        viewModelScope.launch {
            try {
                val result = InternalServicesConfig.parse(yaml)
                servicesStore.replaceAll(result.services)
                _importSummary.value = if (result.rejected.isEmpty()) {
                    "已导入 ${result.services.size} 个服务"
                } else {
                    "导入 ${result.services.size} 个；拒绝 ${result.rejected.size} 个（标题为空 / 非 http(s) 地址）"
                }
            } catch (error: Exception) {
                _importSummary.value = "导入失败：${error.message ?: "无法解析 YAML"}"
            }
        }
    }

    override fun startTunnelAuthorization() {
        viewModelScope.launch {
            val authUrl = tunnelStateProvider.authUrl.first()
            if (!authUrl.isNullOrBlank()) {
                // 测试环境不打开浏览器，只记录被调用
                _tunnelState.value = TunnelState.Authorizing(loginUrl = authUrl)
            }
        }
    }

    override fun clearAuthKey() {
        viewModelScope.launch {
            secretStore.clearAuthKey()
            _tunnelState.value = TunnelState.Disconnected
        }
    }

    override fun setThemePreference(value: ThemePreference) {
        viewModelScope.launch { settingsStore.setThemePreference(value) }
    }

    override fun setAutoConnectTunnel(value: Boolean) {
        viewModelScope.launch { settingsStore.setAutoConnectTunnel(value) }
    }

    override fun setOpenHomeOnLaunch(value: Boolean) {
        viewModelScope.launch { settingsStore.setOpenHomeOnLaunch(value) }
    }

    override fun setUseTailnetProxy(value: Boolean) {
        viewModelScope.launch { settingsStore.setUseTailnetProxy(value) }
    }

    override fun openOpenSourceLicenses() {
        // 测试环境不打开浏览器
    }

    /** 测试辅助：直接设置设置值（跳过 Store）。 */
    fun setSettingsDirect(settings: AppSettings) {
        _settings.value = settings
    }

    /** 测试辅助：直接设置导出的 YAML。 */
    fun setExportedYamlDirect(yaml: String?) {
        _exportedYaml.value = yaml
    }

    /** 测试辅助：直接设置导入摘要。 */
    fun setImportSummaryDirect(summary: String?) {
        _importSummary.value = summary
    }

    /** 测试辅助：直接设置隧道状态。 */
    fun setTunnelStateDirect(state: TunnelState) {
        _tunnelState.value = state
    }
}