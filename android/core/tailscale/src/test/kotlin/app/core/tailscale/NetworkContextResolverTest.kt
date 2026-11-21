package app.core.tailscale

import app.core.model.NetworkContext
import app.core.model.TailnetSource
import app.core.model.TailnetState
import app.core.tailscale.NetworkContextResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkContextResolverTest {

    private val resolver = NetworkContextResolver()

    @Test
    fun `manual lock wins over every signal`() {
        val context = resolver.resolve(
            manualLock = NetworkContext.AWAY,
            tailnet = TailnetState(connected = true, source = TailnetSource.LOCAL_API),
            currentSsid = "HomeWifi",
            homeSsid = "HomeWifi",
        )
        assertEquals(NetworkContext.AWAY, context)
    }

    @Test
    fun `local api plus home ssid means home`() {
        val context = resolver.resolve(
            tailnet = TailnetState(connected = true, source = TailnetSource.LOCAL_API),
            currentSsid = "HomeWifi",
            homeSsid = "HomeWifi",
        )
        assertEquals(NetworkContext.HOME, context)
    }

    @Test
    fun `local api without home ssid means away`() {
        val context = resolver.resolve(
            tailnet = TailnetState(connected = true, source = TailnetSource.LOCAL_API),
            currentSsid = "CafeWifi",
            homeSsid = "HomeWifi",
        )
        assertEquals(NetworkContext.AWAY, context)
    }

    @Test
    fun `cellular without tailnet means away`() {
        val context = resolver.resolve(isCellular = true)
        assertEquals(NetworkContext.AWAY, context)
    }

    @Test
    fun `no signal at all stays unknown`() {
        val context = resolver.resolve()
        assertEquals(NetworkContext.UNKNOWN, context)
    }
}
