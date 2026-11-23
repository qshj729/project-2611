package app.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * 设置的读写入口（DataStore 实现）。
 *
 * 设置**语义**放在纯 Kotlin 的 [AppSettings] 里，JVM 单测可直接构造；
 * 这里只负责读写。
 */
interface SettingsStore {
    /** 设置流：任一项变化都会重新发射。 */
    fun observe(): Flow<AppSettings>

    /** 当前值（一次性读取）。 */
    suspend fun current(): AppSettings

    suspend fun setThemePreference(value: ThemePreference)

    suspend fun setAutoConnectTunnel(value: Boolean)

    suspend fun setOpenHomeOnLaunch(value: Boolean)

    suspend fun setUseTailnetProxy(value: Boolean)
}

class DataStoreSettingsStore(
    private val dataStore: DataStore<Preferences>,
) : SettingsStore {

    override fun observe(): Flow<AppSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toSettings() }

    override suspend fun current(): AppSettings = try {
        dataStore.data.first().toSettings()
    } catch (error: IOException) {
        AppSettings()
    }

    override suspend fun setThemePreference(value: ThemePreference) {
        dataStore.edit { prefs -> prefs[KEY_THEME_PREFERENCE] = value.raw }
    }

    override suspend fun setAutoConnectTunnel(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AUTO_CONNECT] = value }
    }

    override suspend fun setOpenHomeOnLaunch(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_OPEN_HOME_ON_LAUNCH] = value }
    }

    override suspend fun setUseTailnetProxy(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_USE_TAILNET_PROXY] = value }
    }

    companion object {
        internal val KEY_THEME_PREFERENCE = stringPreferencesKey("theme_preference")
        internal val KEY_AUTO_CONNECT = booleanPreferencesKey("auto_connect_tunnel")
        internal val KEY_OPEN_HOME_ON_LAUNCH = booleanPreferencesKey("open_home_on_launch")
        internal val KEY_USE_TAILNET_PROXY = booleanPreferencesKey("use_tailnet_proxy")
    }
}

internal fun Preferences.toSettings() = AppSettings(
    themePreference = ThemePreference.fromRaw(this[DataStoreSettingsStore.KEY_THEME_PREFERENCE]),
    autoConnectTunnel = this[DataStoreSettingsStore.KEY_AUTO_CONNECT] ?: true,
    openHomeOnLaunch = this[DataStoreSettingsStore.KEY_OPEN_HOME_ON_LAUNCH] ?: true,
    useTailnetProxy = this[DataStoreSettingsStore.KEY_USE_TAILNET_PROXY] ?: true,
)

private val Context.myAppsSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "myapps_settings",
)

object SettingsStores {
    fun create(context: Context): SettingsStore =
        DataStoreSettingsStore(context.applicationContext.myAppsSettingsDataStore)
}
