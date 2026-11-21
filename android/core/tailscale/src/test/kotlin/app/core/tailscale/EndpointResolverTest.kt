package app.core

import app.core.model.Endpoint
import app.core.model.EndpointScope
import app.core.model.NetworkContext
import app.core.model.Service
import app.core.tailscale.EndpointResolver
import app.core.tailscale.EndpointUrlRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointResolverTest {

    private fun service(): Service = Service(
        id = "svc",
        name = "Jellyfin",
        endpoints = listOf(
            Endpoint("lan", "svc", EndpointScope.LAN, "http://{host}:{port}", "192.168.1.10", 8096, isPrimary = true),
            Endpoint("tail", "svc", EndpointScope.TAILNET, "https://{host}", "jellyfin", null, isPrimary = true),
        ),
    )

    @Test
    fun `away prefers the tailnet endpoint`() {
        val resolution = EndpointResolver.resolve(service(), NetworkContext.AWAY)
        assertEquals("tail", resolution.endpoint?.id)
        assertFalse(resolution.fellBack)
    }

    @Test
    fun `home prefers the lan endpoint`() {
        val resolution = EndpointResolver.resolve(service(), NetworkContext.HOME)
        assertEquals("lan", resolution.endpoint?.id)
    }

    @Test
    fun `falls back when the preferred endpoint is known to be unreachable`() {
        val resolution = EndpointResolver.resolve(
            service = service(),
            context = NetworkContext.AWAY,
            reachability = mapOf("tail" to false, "lan" to true),
        )
        assertEquals("lan", resolution.endpoint?.id)
        assertTrue(resolution.fellBack)
    }

    @Test
    fun `renders magic dns names with the tailnet suffix`() {
        val endpoint = Endpoint("tail", "svc", EndpointScope.TAILNET, "https://{host}", "jellyfin", null)
        assertEquals(
            "https://jellyfin.tailnet-xyz.ts.net",
            EndpointUrlRenderer.render(endpoint, "tailnet-xyz.ts.net"),
        )
    }

    @Test
    fun `keeps lan hosts untouched`() {
        val endpoint = Endpoint("lan", "svc", EndpointScope.LAN, "http://{host}:{port}", "192.168.1.10", 8096)
        assertEquals(
            "http://192.168.1.10:8096",
            EndpointUrlRenderer.render(endpoint, "tailnet-xyz.ts.net"),
        )
    }

    @Test
    fun `drops the port placeholder when no port is configured`() {
        val endpoint = Endpoint("lan", "svc", EndpointScope.LAN, "http://{host}:{port}/admin", "nas", null)
        assertEquals("http://nas/admin", EndpointUrlRenderer.render(endpoint, null))
    }
}
