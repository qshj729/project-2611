package com.hubdeck.app.core.data.settings

import androidx.datastore.preferences.core.Preferences
import com.hubdeck.app.core.model.NetworkContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 设置项的单测（DataStore 的持久化本身要 Android 运行时，这里测的是**语义**：
 * 默认值、手动锁定模式、序列化）。
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
    fun `preferences map onto the settings model`() {
        val preferences = preferencesOf(
            DataStoreSettingsStore.KEY_HOME_SSID to "MyHomeWiFi",
            DataStoreSettingsStore.KEY_PROBE_INTERVAL_MINUTES to 30,
            DataStoreSettingsStore.KEY_NETWORK_MODE_LOCK to "away",
            DataStoreSettingsStore.KEY_PREFERRED_DEVICE to "pixel-8",
            DataStoreSettingsStore.KEY_MAGIC_DNS_SUFFIX to "tailnet-xyz.ts.net",
        )

        val settings = preferences.toSettings()

        assertEquals("MyHomeWiFi", settings.homeSsid)
        assertEquals(30, settings.probeIntervalMinutes)
        assertEquals(NetworkModeLock.AWAY, settings.networkModeLock)
        assertEquals("pixel-8", settings.preferredDevice)
        assertEquals("tailnet-xyz.ts.net", settings.magicDnsSuffix)
    }

    @Test
    fun `empty preferences fall back to defaults`() {
        val settings = preferencesOf().toSettings()

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
}

/**
 * 构造一个内存里的 [Preferences]。
 *
 * 不用 `mutablePreferencesOf`：它在 datastore 1.1.1 里被标记为 `@RestrictTo(LIBRARY_GROUP)`，
 * 应用侧代码调用会编译失败（2026-11-11 R0 修复：`:core:data` 单测编译不过）。
 */
private fun preferencesOf(vararg pairs: Pair<Preferences.Key<*>, Any>): Preferences =
    object : Preferences {
        private val values: Map<Preferences.Key<*>, Any> = pairs.toMap()

        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: Preferences.Key<T>): T? = values[key] as T?

        override fun asMap(): Map<Preferences.Key<*>, Any> = values
    }
