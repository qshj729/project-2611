package com.hubdeck.app.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hubdeck.app.core.data.importer.HubDeckYaml
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * 设置的读写入口（DataStore 实现）。
 *
 * 设计参考：`docs/design.md` §7.1（偏好走 DataStore）与 §6.5（手动锁定网络模式）。
 *
 * 之所以抽一层接口：DataStore 需要 `Context`（Android 侧），而设置**语义**（默认值、
 * 序列化、锁定值）要在 JVM 单测里能跑 —— 所以 [HubDeckSettings] 与 [NetworkModeLock]
 * 不依赖任何 Android 类型，测试直接用内存实现。
 */
interface SettingsStore {
    /** 设置流：任一项变化都会重新发射。 */
    fun observe(): Flow<HubDeckSettings>

    /** 当前值（一次性读取）。 */
    suspend fun current(): HubDeckSettings

    suspend fun setHomeSsid(value: String?)

    suspend fun setProbeIntervalMinutes(value: Int)

    suspend fun setProbeTimeoutMs(value: Int)

    suspend fun setProbeWifiOnly(value: Boolean)

    suspend fun setNetworkModeLock(value: NetworkModeLock)

    suspend fun setPreferredDevice(value: String?)

    suspend fun setMagicDnsSuffix(value: String?)

    suspend fun setThemePreference(value: ThemePreference)

    suspend fun setCardDensity(value: CardDensityPreference)
}

/**
 * DataStore 版实现。
 *
 * 文件名固定为 `hubdeck_settings`，跟数据库（`hubdeck.db`）分开，互不影响。
 */
class DataStoreSettingsStore(private val dataStore: DataStore<Preferences>) : SettingsStore {

    override fun observe(): Flow<HubDeckSettings> = dataStore.data
        // 读文件失败（一般是 IO 抖动）时退化成空设置，不让 UI 崩。
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toSettings() }

    override suspend fun current(): HubDeckSettings = try {
        dataStore.data.first().toSettings()
    } catch (error: IOException) {
        HubDeckSettings()
    }

    override suspend fun setHomeSsid(value: String?) {
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(KEY_HOME_SSID) else prefs[KEY_HOME_SSID] = value
        }
    }

    override suspend fun setProbeIntervalMinutes(value: Int) {
        val minutes = value.coerceIn(MIN_PROBE_INTERVAL_MINUTES, MAX_PROBE_INTERVAL_MINUTES)
        dataStore.edit { prefs -> prefs[KEY_PROBE_INTERVAL_MINUTES] = minutes }
    }

    override suspend fun setProbeTimeoutMs(value: Int) {
        val timeout = value.coerceIn(MIN_PROBE_TIMEOUT_MS, MAX_PROBE_TIMEOUT_MS)
        dataStore.edit { prefs -> prefs[KEY_PROBE_TIMEOUT_MS] = timeout }
    }

    override suspend fun setProbeWifiOnly(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_PROBE_WIFI_ONLY] = value }
    }

    override suspend fun setNetworkModeLock(value: NetworkModeLock) {
        dataStore.edit { prefs -> prefs[KEY_NETWORK_MODE_LOCK] = value.raw }
    }

    override suspend fun setPreferredDevice(value: String?) {
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(KEY_PREFERRED_DEVICE) else prefs[KEY_PREFERRED_DEVICE] = value
        }
    }

    override suspend fun setMagicDnsSuffix(value: String?) {
        dataStore.edit { prefs ->
            if (value.isNullOrBlank()) prefs.remove(KEY_MAGIC_DNS_SUFFIX) else prefs[KEY_MAGIC_DNS_SUFFIX] = value
        }
    }

    override suspend fun setThemePreference(value: ThemePreference) {
        dataStore.edit { prefs -> prefs[KEY_THEME_PREFERENCE] = value.raw }
    }

    override suspend fun setCardDensity(value: CardDensityPreference) {
        dataStore.edit { prefs -> prefs[KEY_CARD_DENSITY] = value.raw }
    }

    companion object {
        const val MIN_PROBE_INTERVAL_MINUTES = 1
        const val MAX_PROBE_INTERVAL_MINUTES = 24 * 60
        const val MIN_PROBE_TIMEOUT_MS = 500
        const val MAX_PROBE_TIMEOUT_MS = 30_000

        internal val KEY_HOME_SSID = stringPreferencesKey("home_ssid")
        internal val KEY_PROBE_INTERVAL_MINUTES = intPreferencesKey("probe_interval_minutes")
        internal val KEY_PROBE_TIMEOUT_MS = intPreferencesKey("probe_timeout_ms")
        internal val KEY_PROBE_WIFI_ONLY = androidx.datastore.preferences.core.booleanPreferencesKey("probe_wifi_only")
        internal val KEY_NETWORK_MODE_LOCK = stringPreferencesKey("network_mode_lock")
        internal val KEY_PREFERRED_DEVICE = stringPreferencesKey("preferred_device")
        internal val KEY_MAGIC_DNS_SUFFIX = stringPreferencesKey("magic_dns_suffix")
        internal val KEY_THEME_PREFERENCE = stringPreferencesKey("theme_preference")
        internal val KEY_CARD_DENSITY = stringPreferencesKey("card_density")
    }
}

/** DataStore 里的键值 → 设置模型。 */
internal fun Preferences.toSettings() = HubDeckSettings(
    homeSsid = this[DataStoreSettingsStore.KEY_HOME_SSID],
    probeIntervalMinutes = this[DataStoreSettingsStore.KEY_PROBE_INTERVAL_MINUTES]
        ?: HubDeckSettings.DEFAULT_PROBE_INTERVAL_MINUTES,
    probeTimeoutMs = this[DataStoreSettingsStore.KEY_PROBE_TIMEOUT_MS]
        ?: HubDeckSettings.DEFAULT_PROBE_TIMEOUT_MS,
    probeWifiOnly = this[DataStoreSettingsStore.KEY_PROBE_WIFI_ONLY]
        ?: HubDeckSettings.DEFAULT_PROBE_WIFI_ONLY,
    networkModeLock = NetworkModeLock.fromRaw(this[DataStoreSettingsStore.KEY_NETWORK_MODE_LOCK]),
    preferredDevice = this[DataStoreSettingsStore.KEY_PREFERRED_DEVICE],
    magicDnsSuffix = this[DataStoreSettingsStore.KEY_MAGIC_DNS_SUFFIX],
    themePreference = ThemePreference.fromRaw(this[DataStoreSettingsStore.KEY_THEME_PREFERENCE]),
    cardDensity = CardDensityPreference.fromRaw(this[DataStoreSettingsStore.KEY_CARD_DENSITY]),
)

/** DataStore 实例（进程内单例，交给 DataStore 自己保证）。 */
private val Context.hubDeckSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hubdeck_settings",
)

/** 装配入口：`:app` 里 `SettingsStores.create(context)`。 */
object SettingsStores {
    fun create(context: Context): SettingsStore =
        DataStoreSettingsStore(context.applicationContext.hubDeckSettingsDataStore)
}

/** 设置 ↔ `hubdeck.yaml` 的 `settings` 段互转（导出 / 导入都要用）。 */
fun HubDeckSettings.toYamlSettings(): HubDeckYaml.Settings = HubDeckYaml.Settings(
    homeSsid = homeSsid,
    probeIntervalMinutes = probeIntervalMinutes,
    forceNetworkContext = networkModeLock.raw,
)

fun HubDeckYaml.Settings.toHubDeckSettings(
    preferredDevice: String? = null,
    magicDnsSuffix: String? = null,
): HubDeckSettings = HubDeckSettings(
    homeSsid = homeSsid,
    probeIntervalMinutes = probeIntervalMinutes,
    networkModeLock = NetworkModeLock.fromRaw(forceNetworkContext),
    preferredDevice = preferredDevice,
    magicDnsSuffix = magicDnsSuffix,
)