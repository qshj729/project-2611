package com.hubdeck.app.core.tunnel

import android.content.Context
import android.util.Log
import com.hubdeck.app.core.model.TailnetState
import com.hubdeck.app.core.tailscale.TailnetRepository
import com.klethron.tsnet.NodeStatus
import com.klethron.tsnet.StateStore
import com.klethron.tsnet.TailnetNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.Duration

/**
 * Manages the tsnet node lifecycle and local proxy server.
 *
 * This is the central piece that wires together:
 * 1. TailnetNode (tsnet) - establishes the tailnet connection
 * 2. LocalProxyServer - handles HTTP/CONNECT proxy traffic
 * 3. ProxyConfigurator - applies WebView proxy rules
 * 4. State subscription - exposes tunnel state to UI (T3 status bar, T4 auth entry)
 *
 * Lifecycle (tsnet 0.14.0 real API):
 * - start() → TailnetNode.start(context, stateStore, authKey)
 * - awaitStatus(status, timeout) → NodeStatus
 * - NEEDS_LOGIN → node.login() → 轮询 awaitStatus 直到 loginUrl 出现 / 变成 RUNNING
 * - RUNNING → 启动 LocalProxyServer + 应用 WebView 代理规则
 * - 出错 / 掉线 → 回落到全 DIRECT（模式 C）
 *
 * All public methods are safe to call from any thread; internal work runs on IO dispatcher.
 */
class TunnelManager(
    private val context: Context,
    private val tailnetRepository: TailnetRepository,
    private val proxyConfigurator: ProxyConfigurator,
    private val localProxyServer: LocalProxyServer,
    private val scope: CoroutineScope,
    /**
     * 节点启动器：生产环境用 tsnet 的 `TailnetNode.start`；
     * 单测注入假实现（JVM 单测跑不了 tsnet 的 native 库），返回 null 表示「不接真实节点」。
     */
    private val nodeStarter: (String) -> TailnetNode? = { authKey ->
        TailnetNode.start(context, createStateStore(), authKey)
    },
) : TunnelStateProvider {

    private val TAG = "TunnelManager"

    /**
     * tsnet 0.14.0 的 awaitStatus(previous, timeout) 接受 null 作为基线：
     * 表示"立即读一次当前状态"，而不是等待变化。
     * NodeStatus 的构造函数是包级私有（com.klethron.tsnet 内部用），外部无法构造基线对象。
     */
    private val initialStatus: NodeStatus? = null

    /** 当前隧道状态，供 UI 订阅。 */
    private val _tunnelState = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    override val tunnelState: StateFlow<TunnelState> = _tunnelState

    /** NEEDS_LOGIN 时供浏览器打开的授权链接。 */
    private val _authUrl = MutableStateFlow<String?>(null)
    override val authUrl: StateFlow<String?> = _authUrl

    /** 运行中的 tsnet 节点。 */
    @Volatile
    private var tailnetNode: TailnetNode? = null

    /** 节点健康检查协程。 */
    private var lifecycleJob: Job? = null

    /** 是否已经尝试过启动（避免重复起节点）。 */
    @Volatile
    private var startAttempted = false

    /** 若已保存 auth key，则启动隧道。 */
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

    /** 用给定 auth key 启动 tsnet 节点。 */
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
                // tsnet 0.14.0：节点由静态工厂创建，状态存储由 StateStore 提供。
                val node = nodeStarter(authKey)
                tailnetNode = node
                if (node == null) {
                    // 单测替身：没有真实节点，只驱动状态机。
                    Log.i(TAG, "nodeStarter returned no node (test double), skipping status handling")
                    return@launch
                }
                Log.i(TAG, "TailnetNode.start() returned")

                val status = node.awaitStatus(initialStatus, Duration.ofSeconds(15))
                Log.i(TAG, "awaitStatus() returned: ${status.state()}")

                handleStatus(node, status)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start tunnel", e)
                onNodeError(e.message ?: "Unknown error")
            } catch (e: LinkageError) {
                // tsnet 的 native 库缺失/架构不匹配：同样要落到错误态，不能让调用方崩。
                Log.e(TAG, "Failed to load tsnet native library", e)
                onNodeError(e.message ?: "tsnet native library unavailable")
            }
        }
    }

    /** 根据节点状态分发处理。 */
    private fun handleStatus(node: TailnetNode, status: NodeStatus) {
        when (status.state()) {
            NodeStatus.State.RUNNING -> onNodeRunning(node, status)
            NodeStatus.State.NEEDS_LOGIN -> onNodeNeedsLogin(node, status)
            else -> {
                Log.w(TAG, "Unexpected node state: ${status.state()}")
                onNodeError("Unexpected state: ${status.state()}")
            }
        }
    }

    /** 节点已连接（RUNNING）。 */
    private fun onNodeRunning(node: TailnetNode, status: NodeStatus) {
        scope.launch(Dispatchers.Main) {
            val addresses = status.addresses().orEmpty().map { it.toString() }
            val tailnetIps = addresses.filter { it.startsWith("100.") }
            val nodeName = addresses.firstOrNull { it.startsWith("100.") } ?: "unknown"
            val magicDnsSuffix = extractMagicDnsSuffix(addresses)

            Log.i(TAG, "Node RUNNING: name=$nodeName, ips=$tailnetIps")

            _tunnelState.value = TunnelState.Connected(
                nodeName = nodeName,
                tailnetIps = tailnetIps,
                magicDnsSuffix = magicDnsSuffix,
            )

            // 启动本地代理（把尾网流量转到 tsnet 通道）
            localProxyServer.start(context, node)

            // 给 WebView 应用代理规则
            val tailnetState = TailnetState(
                connected = true,
                magicDnsSuffix = magicDnsSuffix,
                tailnetName = extractTailnetName(addresses),
                selfIps = tailnetIps,
                backendState = "Running",
                source = com.hubdeck.app.core.model.TailnetSource.LOCAL_API,
            )
            proxyConfigurator.apply(context, tailnetState)

            // 开始轮询节点健康
            startHealthMonitoring(node, status)
        }
    }

    /** 节点需要授权（NEEDS_LOGIN）。 */
    private fun onNodeNeedsLogin(node: TailnetNode, status: NodeStatus) {
        val knownUrl: String? = status.loginUrl()
        Log.i(TAG, "Node NEEDS_LOGIN, loginUrl: $knownUrl")

        scope.launch(Dispatchers.Main) {
            if (!knownUrl.isNullOrBlank()) {
                _tunnelState.value = TunnelState.Authorizing(loginUrl = knownUrl)
                _authUrl.value = knownUrl
            }
        }

        scope.launch(Dispatchers.IO) {
            try {
                // tsnet 0.14.0：login() 不返回值，授权链接从状态里取。
                node.login()
            } catch (e: Exception) {
                Log.e(TAG, "node.login() failed", e)
                onNodeError("Login request failed: ${e.message}")
                return@launch
            }

            var current = status
            repeat(AUTH_POLL_ROUNDS) { round ->
                try {
                    val next = node.awaitStatus(current, Duration.ofSeconds(AUTH_POLL_INTERVAL_SECONDS))
                    current = next
                    val url: String? = next.loginUrl()
                    if (!url.isNullOrBlank()) {
                        scope.launch(Dispatchers.Main) {
                            _tunnelState.value = TunnelState.Authorizing(loginUrl = url)
                            _authUrl.value = url
                        }
                    }
                    when (next.state()) {
                        NodeStatus.State.RUNNING -> {
                            onNodeRunning(node, next)
                            return@launch
                        }
                        NodeStatus.State.STOPPED -> {
                            onNodeError("Node stopped while authorizing")
                            return@launch
                        }
                        else -> Log.d(TAG, "Still authorizing (round $round): ${next.state()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error while waiting for authorization", e)
                    onNodeError("Authorization error: ${e.message}")
                    return@launch
                }
            }
            onNodeError("Authorization timed out")
        }
    }

    /** 出错 → 回落全 DIRECT（模式 C）。 */
    private fun onNodeError(reason: String) {
        Log.w(TAG, "Tunnel error, falling back to DIRECT: $reason")
        scope.launch(Dispatchers.Main) {
            _tunnelState.value = TunnelState.Error(reason = reason)
            _authUrl.value = null
            proxyConfigurator.clearProxy(context)
            localProxyServer.stop()
            closeNode()
            lifecycleJob?.cancel()
            lifecycleJob = null
            startAttempted = false // 允许重试
        }
    }

    /** 周期性检查节点健康。 */
    private fun startHealthMonitoring(node: TailnetNode, initialStatus: NodeStatus) {
        lifecycleJob?.cancel()
        lifecycleJob = scope.launch(Dispatchers.IO) {
            var last = initialStatus
            while (true) {
                delay(HEALTH_CHECK_INTERVAL_MS)
                try {
                    val next = node.awaitStatus(last, Duration.ofSeconds(HEALTH_CHECK_TIMEOUT_SECONDS))
                    last = next
                    if (next.state() != NodeStatus.State.RUNNING) {
                        Log.w(TAG, "Node health check failed: state = ${next.state()}")
                        onNodeError("Node went offline: ${next.state()}")
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

    /** 停止隧道并清理。 */
    fun stop() {
        Log.i(TAG, "Stopping tunnel")
        lifecycleJob?.cancel()
        lifecycleJob = null
        localProxyServer.stop()
        closeNode()
        proxyConfigurator.clearProxy(context)
        _tunnelState.value = TunnelState.Disconnected
        _authUrl.value = null
        startAttempted = false
    }

    /** 重启隧道（例如 auth key 变更后）。 */
    fun restart(authKey: String) {
        stop()
        start(authKey)
    }

    /** 关闭并释放节点。 */
    private fun closeNode() {
        val node = tailnetNode
        tailnetNode = null
        if (node == null) return
        try {
            node.logout(Duration.ofSeconds(3))
        } catch (e: Exception) {
            Log.d(TAG, "logout failed", e)
        }
        try {
            node.close()
        } catch (e: Exception) {
            Log.d(TAG, "close failed", e)
        }
    }

    /** tsnet 0.14.0 需要一个 StateStore 持久化节点状态，这里用文件实现。 */
    private fun createStateStore(): StateStore = FileStateStore(File(context.filesDir, "tsnet-state"))

    /** 测试辅助：直接模拟节点已连接。 */
    internal fun onNodeRunningForTest(addresses: List<String>) {
        scope.launch(Dispatchers.Main) {
            val nodeName = addresses.firstOrNull { it.startsWith("100.") } ?: "unknown"
            val tailnetIps = addresses.filter { it.startsWith("100.") }

            _tunnelState.value = TunnelState.Connected(
                nodeName = nodeName,
                tailnetIps = tailnetIps,
                magicDnsSuffix = extractMagicDnsSuffix(addresses),
            )

            localProxyServer.start(context, tailnetNode)

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

    /** 测试辅助：直接模拟节点出错。 */
    internal fun onNodeErrorForTest(reason: String) {
        scope.launch(Dispatchers.Main) {
            _tunnelState.value = TunnelState.Error(reason = reason)
            _authUrl.value = null
            proxyConfigurator.clearProxy(context)
            localProxyServer.stop()
            closeNode()
            lifecycleJob?.cancel()
            lifecycleJob = null
            startAttempted = false
        }
    }

    private fun extractMagicDnsSuffix(addresses: List<String>): String? {
        // MagicDNS 域名形如 "host.tailnet-xyz.ts.net" → "tailnet-xyz.ts.net"
        return addresses.firstOrNull { it.endsWith(".ts.net") }?.let { fqdn ->
            val parts = fqdn.split(".")
            if (parts.size >= 3) parts.drop(1).joinToString(".") else null
        }
    }

    private fun extractTailnetName(addresses: List<String>): String? {
        return extractMagicDnsSuffix(addresses)?.removeSuffix(".ts.net")
    }

    companion object {
        private const val AUTH_POLL_ROUNDS = 60
        private const val AUTH_POLL_INTERVAL_SECONDS = 5L
        private const val HEALTH_CHECK_INTERVAL_MS = 30_000L
        private const val HEALTH_CHECK_TIMEOUT_SECONDS = 5L
    }
}

/**
 * 简单的文件型 [StateStore]：把 tsnet 的状态字节按 key 存到目录里。
 */
private class FileStateStore(private val dir: File) : StateStore {

    override fun read(key: String): ByteArray {
        val file = File(dir, key.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        if (!file.exists()) return ByteArray(0)
        return try {
            file.readBytes()
        } catch (e: Exception) {
            ByteArray(0)
        }
    }

    override fun write(key: String, bytes: ByteArray) {
        val file = File(dir, key.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        try {
            if (!dir.exists()) dir.mkdirs()
            file.writeBytes(bytes)
        } catch (e: Exception) {
            // 状态持久化失败不阻断隧道启动
        }
    }
}
