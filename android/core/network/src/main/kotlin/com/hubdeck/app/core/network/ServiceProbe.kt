package com.hubdeck.app.core.network

import com.hubdeck.app.core.model.Endpoint
import com.hubdeck.app.core.model.HealthMode
import com.hubdeck.app.core.model.ProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Reachability probing.
 *
 * Design reference: docs/design.md §6.6.
 *
 * The probe is *advisory*: a failure never prevents the user from opening the
 * service, it only annotates the card with a reason such as "DNS 解析失败".
 */
interface ServiceProbe {
    suspend fun probe(endpoint: Endpoint, mode: HealthMode, path: String, timeoutMs: Int): ProbeResult
}

class DefaultServiceProbe(
    private val client: OkHttpClient,
) : ServiceProbe {

    override suspend fun probe(
        endpoint: Endpoint,
        mode: HealthMode,
        path: String,
        timeoutMs: Int,
    ): ProbeResult = withContext(Dispatchers.IO) {
        when (mode) {
            HealthMode.NONE -> ProbeResult(reachable = false, reason = "未启用探测")
            HealthMode.TCP -> probeTcp(endpoint, timeoutMs)
            HealthMode.HTTP -> probeHttp(endpoint, path, timeoutMs)
        }
    }

    private suspend fun probeTcp(endpoint: Endpoint, timeoutMs: Int): ProbeResult {
        val host = endpoint.host?.takeIf { it.isNotBlank() } ?: return ProbeResult(false, reason = "未配置主机名")
        val port = endpoint.port ?: return ProbeResult(false, reason = "未配置端口")
        return try {
            val started = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            val latency = ((System.nanoTime() - started) / 1_000_000).toInt()
            ProbeResult(reachable = true, latencyMs = latency)
        } catch (e: SocketTimeoutException) {
            ProbeResult(reachable = false, reason = "连接超时")
        } catch (e: ConnectException) {
            ProbeResult(reachable = false, reason = "连接被拒绝")
        } catch (e: UnknownHostException) {
            ProbeResult(reachable = false, reason = "DNS 解析失败")
        } catch (e: Exception) {
            ProbeResult(reachable = false, reason = e.message ?: "连接失败")
        }
    }

    private suspend fun probeHttp(endpoint: Endpoint, path: String, timeoutMs: Int): ProbeResult {
        val host = endpoint.host?.takeIf { it.isNotBlank() } ?: return ProbeResult(false, reason = "未配置主机名")
        val url = buildProbeUrl(endpoint, path)
        return try {
            val started = System.nanoTime()
            val request = Request.Builder().url(url).head().build()
            val call = client.newCall(request)
            // 用 OkHttp 自己的 call timeout，不用协程超时包住阻塞调用：
            // 阻塞 IO 不响应协程取消，withTimeout 只会把调用挂到 socket 超时才返回。
            call.timeout().timeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            val code = call.execute().use { it.code }
            val latency = ((System.nanoTime() - started) / 1_000_000).toInt()
            when (code) {
                in 200..399 -> ProbeResult(reachable = true, latencyMs = latency)
                401, 403 -> ProbeResult(reachable = true, latencyMs = latency, reason = "需要登录")
                else -> ProbeResult(reachable = false, reason = "HTTP $code")
            }
        } catch (e: InterruptedIOException) {
            ProbeResult(reachable = false, reason = "请求超时")
        } catch (e: ConnectException) {
            ProbeResult(reachable = false, reason = "连接失败")
        } catch (e: UnknownHostException) {
            ProbeResult(reachable = false, reason = "DNS 解析失败")
        } catch (e: Exception) {
            ProbeResult(reachable = false, reason = e.message ?: "请求失败")
        }
    }
}

/**
 * 探测用的实际 URL：scheme 取自端点模板，host/port 取自端点配置。
 *
 * 抽成顶层函数是为了能直接单测（https 场景无法用明文 MockWebServer 验证握手）。
 */
internal fun buildProbeUrl(endpoint: Endpoint, path: String): String {
    val host = endpoint.host?.takeIf { it.isNotBlank() } ?: return ""
    val scheme = if (endpoint.urlTemplate.startsWith("https")) "https" else "http"
    return buildString {
        append(scheme).append("://").append(host)
        val port = endpoint.port
        if (port != null) append(':').append(port)
        append(if (path.startsWith('/')) path else "/$path")
    }
}
