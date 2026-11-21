package app.core.tailscale

import app.core.model.Endpoint
import app.core.model.EndpointScope
import app.core.model.NetworkContext
import app.core.model.Service

/**
 * Decides which endpoint of a service should be opened for the current network context.
 *
 * Design reference: docs/tailscale-integration.md §5.2 (ADR-003, ADR-004).
 *
 * Rules:
 *  1. scope preference depends on the network context (away -> tailnet first);
 *  2. inside a scope, endpoints that were reachable last time win over unreachable ones;
 *  3. if the preferred endpoint is known to be unreachable we fall back to the next candidate;
 *  4. we never refuse to open a service because everything looks unreachable -
 *     the probe may simply be blocked by the network.
 */
object EndpointResolver {

    data class Resolution(
        val endpoint: Endpoint?,
        /** True when we had to deviate from the preferred scope (UI shows a hint). */
        val fellBack: Boolean,
    )

    fun scopeOrder(context: NetworkContext): List<EndpointScope> = when (context) {
        NetworkContext.AWAY -> listOf(EndpointScope.TAILNET, EndpointScope.PUBLIC, EndpointScope.LAN)
        NetworkContext.HOME -> listOf(EndpointScope.LAN, EndpointScope.TAILNET, EndpointScope.PUBLIC)
        NetworkContext.UNKNOWN -> listOf(EndpointScope.TAILNET, EndpointScope.LAN, EndpointScope.PUBLIC)
    }

    fun resolve(
        service: Service,
        context: NetworkContext,
        reachability: Map<String, Boolean> = emptyMap(),
    ): Resolution {
        val endpoints = service.endpoints
        if (endpoints.isEmpty()) return Resolution(null, fellBack = false)

        val order = scopeOrder(context)
        val scopeRank: (Endpoint) -> Int = { order.indexOf(it.scope).takeIf { index -> index >= 0 } ?: order.size }
        val withinScope = compareBy<Endpoint>({ if (reachability[it.id] == true) 0 else 1 }, { if (it.isPrimary) 0 else 1 }, { it.id })

        // 1. the endpoint we would use in the current context
        val preferred = endpoints.filter { it.scope == order.first() }
            .sortedWith(withinScope)
            .firstOrNull()

        // 2. the preferred endpoint is fine as long as we have no evidence that it is down
        if (preferred != null && reachability[preferred.id] != false) {
            return Resolution(preferred, fellBack = false)
        }

        // 3. it is known to be unreachable -> prefer any endpoint that is known to work
        val reachableAlternative = endpoints.filter { reachability[it.id] == true }
            .sortedWith(compareBy(scopeRank).then(withinScope))
            .firstOrNull()
        if (reachableAlternative != null) {
            return Resolution(reachableAlternative, fellBack = true)
        }

        // 4. nothing is known to be reachable -> keep the context preference
        val fallback = endpoints.sortedWith(compareBy(scopeRank).then(withinScope)).first()
        return Resolution(fallback, fellBack = preferred != null && fallback.id != preferred.id)
    }
}

/**
 * Renders the final URL of an endpoint.
 *
 * A tailnet endpoint may store a short MagicDNS name (`jellyfin`); when the tailnet
 * suffix is known we expand it to the FQDN (`jellyfin.tailnet-xyz.ts.net`).
 */
object EndpointUrlRenderer {

    fun render(endpoint: Endpoint, magicDnsSuffix: String? = null): String {
        var host = endpoint.host.orEmpty().trim()
        if (endpoint.scope == EndpointScope.TAILNET &&
            magicDnsSuffix != null &&
            host.isNotEmpty() &&
            !host.contains('.') &&
            !host.contains(':')
        ) {
            host = "$host.$magicDnsSuffix"
        }

        val template = if (endpoint.port == null) {
            // drop the whole ":{port}" segment, keeping the scheme separator intact
            endpoint.urlTemplate.replace(":{port}", "").replace("{port}", "")
        } else {
            endpoint.urlTemplate
        }

        var url = template
            .replace("{host}", host)
            .replace("{port}", endpoint.port?.toString().orEmpty())

        if (url.endsWith(":")) url = url.dropLast(1)
        return url.trim()
    }
}
