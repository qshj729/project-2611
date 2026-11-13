package com.hubdeck.app.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFactory
import com.hubdeck.app.core.model.NetworkContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * 设置项的单测（DataStore 的持久化本身要 Android 运行时，这里测的是**语义**：
 * 默认值、手动锁定模式、序列化）。
 *
 * 使用 `preferencesDataStoreFactory` 创建内存/文件 DataStore，可在纯 JVM 上跑。
 */
class SettingsTest {

    @Test
    fun `network mode lock maps to network context`() {
        assertEquals(NetworkModeLock.AUTO, NetworkModeLock.fromRaw("auto"))
        assertEquals(NetworkModeLock.HOME, NetworkModeLock.fromRaw("HOME"))
        assertEquals(NetworkModeLock.AWAY, NetworkModeLock.fromRaw(" away "))

        assertEquals(NetworkContext.HOME, NetworkModeLock.HOME.toNetworkContext())
        assertEquals(NetworkContext.AWAY, NetworkModeLock.AWAY.toNetworkContext())
        assertNull("AUTO 表示没有锁定", NetworkModeLock.AUTO.toNetworkContext())
    }

    @Test
    fun `unknown network mode falls back to auto`() {
        assertEquals(NetworkModeLock.AUTO, NetworkModeLock.fromRaw("moon-phase"))
        assertEquals(NetworkModeLock.AUTO, NetworkModeLock.fromRaw(null))
    }

    @Test
    fun `default settings match the yaml defaults`() {
        val settings = HubDeckSettings()

        assertNull(settings.homeSsid)
        assertEquals(15, settings.probeIntervalMinutes)
        assertEquals(NetworkModeLock.AUTO, settings.networkModeLock)
    }

    @Test
    fun `preferences map onto the settings model`() = runTest {
        val dataStore = createTestDataStore()
        val store = DataStoreSettingsStore(dataStore)

        // Write test data
        dataStore.edit { prefs ->
            prefs[DataStoreSettingsStore.KEY_HOME_SSID] = "MyHomeWiFi"
            prefs[DataStoreSettingsStore.KEY_PROBE_INTERVAL_MINUTES] = 30
            prefs[DataStoreSettingsStore.KEY_NETWORK_MODE_LOCK] = "away"
            prefs[DataStoreSettingsStore.KEY_PREFERRED_DEVICE] = "pixel-8"
            prefs[DataStoreSettingsStore.KEY_MAGIC_DNS_SUFFIX] = "tailnet-xyz.ts.net"
        }

        val settings = store.current()

        assertEquals("MyHomeWiFi", settings.homeSsid)
        assertEquals(30, settings.probeIntervalMinutes)
        assertEquals(NetworkModeLock.AWAY, settings.networkModeLock)
        assertEquals("pixel-8", settings.preferredDevice)
        assertEquals("tailnet-xyz.ts.net", settings.magicDnsSuffix)
    }

    @Test
    fun `empty preferences fall back to defaults`() = runTest {
        val dataStore = createTestDataStore()
        val store = DataStoreSettingsStore(dataStore)

        val settings = store.current()

        assertEquals(HubDeckSettings(), settings)
    }

    @Test
    fun `settings survive the yaml round trip`() {
        val settings = HubDeckSettings(
            homeSsid = "MyHomeWiFi",
            probeIntervalMinutes = 30,
            networkModeLock = NetworkModeLock.HOME,
            preferredDevice = "pixel-8",
            magicDnsSuffix = "tailnet-xyz.ts.net",
        )

        val yaml = settings.toYamlSettings()
        val restored = yaml.toHubDeckSettings(
            preferredDevice = settings.preferredDevice,
            magicDnsSuffix = settings.magicDnsSuffix,
        )

        assertEquals(settings, restored)
        assertEquals("home", yaml.forceNetworkContext)
    }

    @Test
    fun `observe emits updated settings`() = runTest {
        val dataStore = createTestDataStore()
        val store = DataStoreSettingsStore(dataStore)

        // Initial value
        val initial = store.observe().first()
        assertEquals(HubDeckSettings(), initial)

        // Update
        dataStore.edit { prefs ->
            prefs[DataStoreSettingsStore.KEY_HOME_SSID] = "NewWiFi"
        }

        val updated = store.observe().first()
        assertEquals("NewWiFi", updated.homeSsid)
    }

    private fun createTestDataStore(): DataStore<Preferences> =
        preferencesDataStoreFactory.create(File.createTempFile("test", "preferences_pb"))
}