package com.hubdeck.app

import android.content.Context
import android.util.Log
import com.hubdeck.app.core.model.TailnetState
import com.hubdeck.app.core.tailscale.TailnetRepository
import com.klethron.tsnet.TailnetNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the tsnet node lifecycle and local proxy server.
 *
 * This is the central piece that wires together:
 * 1. TailnetNode (tsnet) - establishes the tailnet connection
 * 2. LocalProxyServer - handles HTTP/CONNECT proxy traffic
 * 3. ProxyConfigurator - applies WebView proxy rules
 * 4. State subscription - exposes tunnel state to UI (T3/T4)
 *
 * Lifecycle:
 * - start() → TailnetNode.start() → awaitStatus() → NEEDS_LOGIN → loginUrl() → user authorizes → RUNNING
 * - On RUNNING: start LocalProxyServer, apply proxy rules for tailnet targets
 * - On auth failure / node offline: fallback to all DIRECT (Mode C)
 *
 * All public methods are safe to call from any thread; internal work runs on IO dispatcher.
 */
class TunnelManager(
    private val context: Context,
    private val tailnetRepository: TailnetRepository,
    private val proxyConfigurator: ProxyConfigurator,
    private val localProxyServer: LocalProxyServer,
    private val scope: CoroutineScope,
) {

    private val TAG = "TunnelManager"

    /** Current tunnel state, exposed for UI subscription (T3 status bar, T4 auth entry). */
    private val _tunnelState = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val tunnelState: StateFlow<TunnelState> = _tunnelState

    /** Auth URL when node is in NEEDS_LOGIN state. */
    private val _authUrl = MutableStateFlow<String?>(null)
    val authUrl: StateFlow<String?> = _authUrl

    /** The running tsnet node. */
    @Volatile
    private var tailnetNode: TailnetNode? = null

    /** Background job for node lifecycle monitoring. */
    private var lifecycleJob: Job? = null

    /** Whether we've attempted to start the node (prevents duplicate starts). */
    @Volatile
    private var startAttempted = false

    /** Start the tunnel if we have an auth key saved. */
    fun startIfConfigured() {
        if (startAttempted) {
            Log.d(TAG, "Tunnel start already attempted, skipping")
            return
        }
        val prefs = context.getSharedPreferences("hubdeck_prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("tailscale_auth_key_saved", null)
        if (savedKey != null && savedKey != "placeholder-key-no-env" && savedKey.isNotBlank()) {
            start(savedKey)
        } else {
            Log.i(TAG, "No auth key saved, tunnel not started")
            _tunnelState.value = TunnelState.Disconnected
        }
    }

    /** Start the tsnet node with the given auth key. */
    fun start(authKey: String) {
        if (startAttempted) {
            Log.d(TAG, "Tunnel start already attempted, skipping")
            return
        }
        startAttempted = true
        Log.i(TAG, "Starting tunnel with auth key")

        _tunnelState.value = TunnelState.Starting
        _authUrl.value = null

        scope.launch(Dispatchers.IO) {
            try {
                // Create and configure the TailnetNode
                val node = TailnetNode()
                tailnetNode = node

                // Start the node with the auth key
                node.start(authKey)
                Log.i(TAG, "TailnetNode.start() called")

                // Wait for status - this will block until RUNNING or NEEDS_LOGIN
                val status = node.awaitStatus()
                Log.i(TAG, "TailnetNode.awaitStatus() returned: ${status.state}")

                when (status.state) {
                    "RUNNING" -> {
                        onNodeRunning(node, status)
                    }
                    "NEEDS_LOGIN" -> {
                        onNodeNeedsLogin(node, status)
                    }
                    else -> {
                        Log.w(TAG, "Unexpected node state: ${status.state}")
                        onNodeError("Unexpected state: ${status.state}")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start tunnel", e)
                onNodeError(e.message ?: "Unknown error")
            }
        }
    }

    /** Handle successful node startup (RUNNING state). */
    private fun onNodeRunning(node: TailnetNode, status: TailnetNode.NodeStatus) {
        scope.launch(Dispatchers.Main) {
            // Extract node info
            val addresses = status.addresses ?: emptyList()
            val nodeName = addresses.firstOrNull { it.startsWith("100.") } ?: "unknown"
            val tailnetIps = addresses.filter { it.startsWith("100.") }

            Log.i(TAG, "Node RUNNING: name=$nodeName, ips=$tailnetIps")

            // Update tunnel state
            _tunnelState.value = TunnelState.Connected(
                nodeName = nodeName,
                tailnetIps = tailnetIps,
                magicDnsSuffix = extractMagicDnsSuffix(addresses),
            )

            // Start the local proxy server
            localProxyServer.start(context, node)

            // Apply proxy configuration for tailnet targets
            val tailnetState = TailnetState(
                connected = true,
                magicDnsSuffix = extractMagicDnsSuffix(addresses),
                tailnetName = extractTailnetName(addresses),
                selfIps = tailnetIps,
                backendState = "Running",
                source = com.hubdeck.app.core.model.TailnetSource.LOCAL_API,
            )
            proxyConfigurator.apply(context, tailnetState)

            // Start monitoring node health
            startHealthMonitoring(node)
        }
    }

    /** Handle NEEDS_LOGIN state - need user to authorize via login URL. */
    private fun onNodeNeedsLogin(node: TailnetNode, status: TailnetNode.NodeStatus) {
        val loginUrl = node.loginUrl()
        Log.i(TAG, "Node NEEDS_LOGIN, loginUrl: $loginUrl")

        scope.launch(Dispatchers.Main) {
            _tunnelState.value = TunnelState.Authorizing(loginUrl = loginUrl)
            _authUrl.value = loginUrl
        }

        // Continue waiting for authorization to complete
        scope.launch(Dispatchers.IO) {
            try {
                // awaitStatus() will block until the user authorizes or times out
                val newStatus = node.awaitStatus()
                Log.i(TAG, "Post-auth awaitStatus() returned: ${newStatus.state}")

                if (newStatus.state == "RUNNING") {
                    onNodeRunning(node, newStatus)
                } else {
                    onNodeError("Authorization failed or timed out, state: ${newStatus.state}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error waiting for authorization", e)
                onNodeError("Authorization error: ${e.message}")
            }
        }
    }

    /** Handle node error - fallback to DIRECT mode (Mode C). */
    private fun onNodeError(reason: String) {
        Log.w(TAG, "Tunnel error, falling back to DIRECT: $reason")
        scope.launch(Dispatchers.Main) {
            _tunnelState.value = TunnelState.Error(reason = reason)
            _authUrl.value = null
            // Fallback: clear proxy rules → everything DIRECT
            proxyConfigurator.clearProxy(context)
            localProxyServer.stop()
            tailnetNode?.close()
            tailnetNode = null
            lifecycleJob?.cancel()
            lifecycleJob = null
            startAttempted = false // Allow retry
        }
    }

    /** Start periodic health checks on the node. */
    private fun startHealthMonitoring(node: TailnetNode) {
        lifecycleJob?.cancel()
        lifecycleJob = scope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    delay(30_000) // Check every 30 seconds
                    // Use withTimeoutOrNull to avoid blocking indefinitely on awaitStatus()
                    val status = withTimeoutOrNull(5_000) { node.awaitStatus() }
                    if (status == null) {
                        Log.w(TAG, "Health check timed out")
                        onNodeError("Health check timed out")
                        break
                    }
                    if (status.state != "RUNNING") {
                        Log.w(TAG, "Node health check failed: state = ${status.state}")
                        onNodeError("Node went offline: ${status.state}")
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Health check exception", e)
                    onNodeError("Health check failed: ${e.message}")
                    break
                }
            }
        }
    }

    /** Stop the tunnel and clean up. */
    fun stop() {
        Log.i(TAG, "Stopping tunnel")
        lifecycleJob?.cancel()
        lifecycleJob = null
        localProxyServer.stop()
        tailnetNode?.logout()
        tailnetNode?.close()
        tailnetNode = null
        proxyConfigurator.clearProxy(context)
        _tunnelState.value = TunnelState.Disconnected
        _authUrl.value = null
        startAttempted = false
    }

    /** Restart the tunnel (e.g., after auth key change). */
    fun restart(authKey: String) {
        stop()
        start(authKey)
    }

    /** Test helper: simulate node running with given addresses. */
    internal fun onNodeRunningForTest(addresses: List<String>) {
        scope.launch(Dispatchers.Main) {
            val nodeName = addresses.firstOrNull { it.startsWith("100.") } ?: "unknown"
            val tailnetIps = addresses.filter { it.startsWith("100.") }

            _tunnelState.value = TunnelState.Connected(
                nodeName = nodeName,
                tailnetIps = tailnetIps,
                magicDnsSuffix = extractMagicDnsSuffix(addresses),
            )

            localProxyServer.start(context, object : com.klethron.tsnet.TailnetNode() {})

            val tailnetState = TailnetState(
                connected = true,
                magicDnsSuffix = extractMagicDnsSuffix(addresses),
                tailnetName = extractTailnetName(addresses),
                selfIps = tailnetIps,
                backendState = "Running",
                source = com.hubdeck.app.core.model.TailnetSource.LOCAL_API,
            )
            proxyConfigurator.apply(context, tailnetState)
        }
    }

    /** Test helper: simulate node error. */
    internal fun onNodeErrorForTest(reason: String) {
        scope.launch(Dispatchers.Main) {
            _tunnelState.value = TunnelState.Error(reason = reason)
            _authUrl.value = null
            proxyConfigurator.clearProxy(context)
            localProxyServer.stop()
            tailnetNode?.close()
            tailnetNode = null
            lifecycleJob?.cancel()
            lifecycleJob = null
            startAttempted = false
        }
    }

    private fun extractMagicDnsSuffix(addresses: List<String>): String? {
        // MagicDNS suffix is typically the tailnet domain, e.g., "tailnet-xyz.ts.net"
        // In tsnet 0.4.0, addresses might include the MagicDNS FQDN
        return addresses.firstOrNull { it.endsWith(".ts.net") }?.let { fqdn ->
            // Extract suffix from FQDN like "host.tailnet-xyz.ts.net" -> "tailnet-xyz.ts.net"
            val parts = fqdn.split(".")
            if (parts.size >= 3) parts.drop(1).joinToString(".") else null
        }
    }

    private fun extractTailnetName(addresses: List<String>): String? {
        return extractMagicDnsSuffix(addresses)?.removeSuffix(".ts.net")
    }
}

/**
 * Tunnel state exposed to UI for subscription (T3 status bar, T4 auth entry).
 */
sealed interface TunnelState {
    data class Disconnected : TunnelState
    data class Starting : TunnelState
    data class Authorizing(val loginUrl: String) : TunnelState
    data class Connected(
        val nodeName: String,
        val tailnetIps: List<String>,
        val magicDnsSuffix: String?,
    ) : TunnelState
    data class Error(val reason: String) : TunnelState
}