package app.feature.settings

import app.core.data.settings.AppSettings
import app.core.data.settings.SettingsStore
import app.core.data.settings.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [SettingsStore] 的假实现，用于 JVM 单测。
 * 状态保存在内存 MutableStateFlow 里，所有操作同步完成。
 */
class FakeSettingsStore : SettingsStore {

    private val _settings = MutableStateFlow(AppSettings())

    override fun observe(): Flow<AppSettings> = _settings

    override suspend fun current(): AppSettings = _settings.value

    override suspend fun setThemePreference(value: ThemePreference) {
        _settings.value = _settings.value.copy(themePreference = value)
    }

    override suspend fun setAutoConnectTunnel(value: Boolean) {
        _settings.value = _settings.value.copy(autoConnectTunnel = value)
    }

    override suspend fun setOpenHomeOnLaunch(value: Boolean) {
        _settings.value = _settings.value.copy(openHomeOnLaunch = value)
    }

    override suspend fun setUseTailnetProxy(value: Boolean) {
        _settings.value = _settings.value.copy(useTailnetProxy = value)
    }

    /** 测试辅助：直接设置完整配置。 */
    fun setSettings(settings: AppSettings) {
        _settings.value = settings
    }
}