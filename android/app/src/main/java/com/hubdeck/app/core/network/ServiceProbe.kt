package com.hubdeck.app.core.network

import com.hubdeck.app.core.model.Endpoint
import com.hubdeck.app.core.model.HealthMode
import com.hubdeck.app.core.model.ProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException

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
            val result = withTimeoutOrNull(timeoutMs.toLong()) {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                    true
                }
            }
            val latency = ((System.nanoTime() - started) / 1_000_000).toInt()
            if (result == true) ProbeResult(reachable = true, latencyMs = latency)
            else ProbeResult(reachable = false, reason = "连接超时")
        } catch (e: UnknownHostException) {
            ProbeResult(reachable = false, reason = "DNS 解析失败")
        } catch (e: Exception) {
            ProbeResult(reachable = false, reason = e.message ?: "连接失败")
        }
    }

    private suspend fun probeHttp(endpoint: Endpoint, path: String, timeoutMs: Int): ProbeResult {
        val host = endpoint.host?.takeIf { it.isNotBlank() } ?: return ProbeResult(false, reason = "未配置主机名")
        val port = endpoint.port
        val scheme = if (endpoint.urlTemplate.startsWith("https")) "https" else "http"
        val url = buildString {
            append(scheme).append("://").append(host)
            if (port != null) append(':').append(port)
            append(if (path.startsWith('/')) path else "/$path")
        }
        return try {
            val started = System.nanoTime()
            val response = withTimeoutOrNull(timeoutMs.toLong()) {
                val request = Request.Builder().url(url).head().build()
                client.newCall(request).execute().use { it.code }
            }
            val latency = ((System.nanoTime() - started) / 1_000_000).toInt()
            when (response) {
                null -> ProbeResult(reachable = false, reason = "请求超时")
                in 200..399 -> ProbeResult(reachable = true, latencyMs = latency)
                401, 403 -> ProbeResult(reachable = true, latencyMs = latency, reason = "需要登录")
                else -> ProbeResult(reachable = false, reason = "HTTP $response")
            }
        } catch (e: UnknownHostException) {
            ProbeResult(reachable = false, reason = "DNS 解析失败")
        } catch (e: Exception) {
            ProbeResult(reachable = false, reason = e.message ?: "请求失败")
        }
    }
}
