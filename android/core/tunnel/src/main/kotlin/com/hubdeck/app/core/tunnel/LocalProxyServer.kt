package com.hubdeck.app.core.tunnel

import android.content.Context
import android.util.Log
import com.klethron.tsnet.TailnetNode
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Local proxy server that listens on 127.0.0.1:1080.
 * Chromium Android WebView routes traffic via [ProxyConfigurator] using
 * [androidx.webkit.ProxyController.setProxyOverride].
 * JVM global proxy properties are NOT used.
 *
 * Routing logic (matches core:network ProxyRule.decideProxy):
 * - CONNECT requests: always dial through tsnet (for HTTPS tunneling)
 * - Ordinary HTTP requests:
 *     - Tailnet targets (100.64.0.0/10, *.ts.net) → forward through tsnet tunnel
 *     - Public targets (everything else) → direct connection (no tunnel)
 *
 * This fixes the previous behavior where non-CONNECT requests returned
 * a fixed "Proxy operational — tsnet tunnel pending" response.
 *
 * tsnet 0.14.0 API: uses socket-based forwarding model.
 * - Old (0.4.0): `node.dial(host, port, timeout)` → ParcelFileDescriptor → Os.read/write
 * - New (0.14.0): `node.connect(host, port)` → Socket → socket.inputStream/outputStream
 *   Retained methods: start, awaitStatus, login, logout, close, controlUrl
 *   Removed: dial, nativeDial
 *   New: open, connect, bind, fromSocket, localBridge, nativeSetRoots
 */
class LocalProxyServer {

    private val TAG = "LocalProxyServer"
    private val LISTEN_PORT = 1080
    @Volatile
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var running = false
    private var tailnetNode: TailnetNode? = null

    // Thread pool for handling client connections
    private val executor = Executors.newFixedThreadPool(16) as ThreadPoolExecutor

    fun start(context: Context, node: TailnetNode) {
        tailnetNode = node
        if (running) return
        running = true
        Thread({
            try {
                serverSocket = ServerSocket(LISTEN_PORT)
                serverSocket?.setSoTimeout(1000) // Allow periodic check of running flag
                Log.i(TAG, "Local proxy started on 127.0.0.1:$LISTEN_PORT")
                acceptLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start local proxy", e)
                running = false
            }
        }).start()
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.d(TAG, "Error closing server socket", e)
        }
        serverSocket = null
        executor.shutdown()
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
        Log.i(TAG, "Local proxy stopped")
    }

    private fun acceptLoop() {
        while (running) {
            try {
                val socket = serverSocket?.accept()
                if (socket != null) {
                    executor.execute { handleClient(socket) }
                }
            } catch (e: java.net.SocketTimeoutException) {
                // Expected - allows checking running flag
            } catch (e: Exception) {
                if (running) {
                    Log.e(TAG, "Error in accept loop", e)
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 30000 // 30s timeout for client operations
            val inputStream = socket.inputStream
            val outputStream = socket.outputStream

            // Read the request line and headers
            val requestLine = readLine(inputStream) ?: return
            val parts = requestLine.split(" ", limit = 3)
            if (parts.size < 2) return

            val method = parts[0]
            val target = parts[1]

            // Read headers (we need Host header for HTTP/1.1)
            var hostHeader: String? = null
            var line: String?
            while (true) {
                line = readLine(inputStream)
                if (line == null || line.isEmpty()) break
                if (line.lowercase().startsWith("host:")) {
                    hostHeader = line.substring(5).trim()
                }
            }

            when (method) {
                "CONNECT" -> handleConnect(socket, target, outputStream)
                "GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "PATCH" -> {
                    handleHttpRequest(socket, method, target, hostHeader, inputStream, outputStream)
                }
                else -> {
                    sendErrorResponse(outputStream, 501, "Not Implemented", "Method $method not supported")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Client handler error", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun handleConnect(socket: Socket, hostPort: String, outputStream: java.io.OutputStream) {
        val hostPortParts = hostPort.split(":")
        val host = hostPortParts[0]
        val port = if (hostPortParts.size > 1) hostPortParts[1].toIntOrNull() ?: 443 else 443

        Log.d(TAG, "CONNECT $host:$port")

        val node = tailnetNode ?: run {
            sendErrorResponse(outputStream, 503, "Service Unavailable", "Tunnel not ready")
            return
        }

        try {
            // Connect through tsnet to the target (tsnet 0.14.0 API: connect returns Socket)
            val tunnelSocket = node.connect(host, port)

            // Send 200 Connection Established
            val response = "HTTP/1.1 200 Connection Established\r\n\r\n"
            outputStream.write(response.toByteArray())
            outputStream.flush()

            // Forward data between WebView socket and tsnet tunnel
            forwardBidirectional(socket, tunnelSocket)

        } catch (e: Exception) {
            Log.e(TAG, "CONNECT connect failed for $host:$port", e)
            sendErrorResponse(outputStream, 502, "Bad Gateway", "Failed to connect to target: ${e.message}")
        }
    }

    private fun handleHttpRequest(
        socket: Socket,
        method: String,
        requestTarget: String,
        hostHeader: String?,
        inputStream: java.io.InputStream,
        outputStream: java.io.OutputStream,
    ) {
        // Determine the target host and port
        val (targetHost, targetPort) = parseTarget(requestTarget, hostHeader) ?: run {
            sendErrorResponse(outputStream, 400, "Bad Request", "Cannot determine target host")
            return
        }

        val isTailnetTarget = isTailnetHost(targetHost)

        Log.d(TAG, "$method $targetHost:$targetPort (tailnet=$isTailnetTarget)")

        val node = tailnetNode
        if (isTailnetTarget) {
            // Tailnet target: forward through tsnet tunnel
            if (node == null) {
                sendErrorResponse(outputStream, 503, "Service Unavailable", "Tunnel not ready for tailnet target")
                return
            }
            forwardHttpThroughTunnel(socket, method, requestTarget, targetHost, targetPort, inputStream, outputStream, node)
        } else {
            // Public target: direct connection
            forwardHttpDirect(socket, method, requestTarget, targetHost, targetPort, inputStream, outputStream)
        }
    }

    private fun forwardHttpThroughTunnel(
        socket: Socket,
        method: String,
        requestTarget: String,
        targetHost: String,
        targetPort: Int,
        inputStream: java.io.InputStream,
        outputStream: java.io.OutputStream,
        node: TailnetNode,
    ) {
        var tunnelSocket: Socket? = null
        try {
            // Connect through tsnet (tsnet 0.14.0 API: connect returns Socket)
            tunnelSocket = node.connect(targetHost, targetPort)

            val tunnelOut = tunnelSocket.outputStream
            val tunnelIn = tunnelSocket.inputStream

            // Send request line
            val requestLine = "$method $requestTarget HTTP/1.1\r\n"
            tunnelOut.write(requestLine.toByteArray())

            // Send Host header if not present in requestTarget
            if (!requestTarget.startsWith("http")) {
                val hostHeader = "Host: $targetHost:$targetPort\r\n"
                tunnelOut.write(hostHeader.toByteArray())
            }

            // Send Connection: close to ensure server closes after response
            val closeHeader = "Connection: close\r\n\r\n"
            tunnelOut.write(closeHeader.toByteArray())
            tunnelOut.flush()

            // Now forward bidirectionally
            forwardBidirectional(socket, tunnelSocket!!)

        } catch (e: Exception) {
            Log.e(TAG, "HTTP tunnel forward failed for $targetHost:$targetPort", e)
            sendErrorResponse(outputStream, 502, "Bad Gateway", "Tunnel error: ${e.message}")
        } finally {
            try {
                tunnelSocket?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun forwardHttpDirect(
        socket: Socket,
        method: String,
        requestTarget: String,
        targetHost: String,
        targetPort: Int,
        inputStream: java.io.InputStream,
        outputStream: java.io.OutputStream,
    ) {
        var targetSocket: Socket? = null
        try {
            targetSocket = Socket()
            targetSocket.connect(java.net.InetSocketAddress(targetHost, targetPort), 10000)
            targetSocket.soTimeout = 30000

            val targetOut = targetSocket.outputStream
            val targetIn = targetSocket.inputStream

            // Send request line
            val requestLine = "$method $requestTarget HTTP/1.1\r\n"
            targetOut.write(requestLine.toByteArray())

            // Send Host header
            val hostHeader = "Host: $targetHost:$targetPort\r\n"
            targetOut.write(hostHeader.toByteArray())

            // Send Connection: close
            val closeHeader = "Connection: close\r\n\r\n"
            targetOut.write(closeHeader.toByteArray())
            targetOut.flush()

            // Forward bidirectionally
            forwardBidirectional(socket, targetSocket!!)

        } catch (e: Exception) {
            Log.e(TAG, "Direct HTTP forward failed for $targetHost:$targetPort", e)
            sendErrorResponse(outputStream, 502, "Bad Gateway", "Direct connection failed: ${e.message}")
        } finally {
            try {
                targetSocket?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun forwardBidirectional(clientSocket: Socket, targetSocket: Socket) {
        val clientIn = clientSocket.inputStream
        val clientOut = clientSocket.outputStream
        val targetIn = targetSocket.inputStream
        val targetOut = targetSocket.outputStream

        val t1 = Thread({
            try {
                val buf = ByteArray(8192)
                while (running) {
                    val bytesRead = clientIn.read(buf)
                    if (bytesRead < 0) break
                    targetOut.write(buf, 0, bytesRead)
                    targetOut.flush()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Forward Client→Target error", e)
            }
        })

        val t2 = Thread({
            try {
                val buf = ByteArray(8192)
                while (running) {
                    val bytesRead = targetIn.read(buf)
                    if (bytesRead < 0) break
                    clientOut.write(buf, 0, bytesRead)
                    clientOut.flush()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Forward Target→Client error", e)
            }
        })

        t1.start()
        t2.start()
        try {
            t1.join()
            t2.join()
        } catch (e: InterruptedException) {
            // Ignore
        }
    }

    private fun readLine(inputStream: java.io.InputStream): String? {
        val buffer = StringBuilder()
        var lastChar = 0
        while (true) {
            val b = inputStream.read()
            if (b == -1) return if (buffer.isNotEmpty()) buffer.toString() else null
            if (b == 10) { // \n
                if (lastChar == 13) { // \r
                    buffer.setLength(buffer.length - 1)
                }
                return buffer.toString()
            }
            buffer.append(b.toChar())
            lastChar = b
        }
    }

    private fun parseTarget(requestTarget: String, hostHeader: String?): Pair<String, Int>? {
        // requestTarget can be:
        // - Absolute form: "http://host:port/path" (for proxy requests)
        // - Origin form: "/path" (for direct requests, need Host header)
        // - Authority form: "host:port" (for CONNECT)

        return if (requestTarget.startsWith("http://") || requestTarget.startsWith("https://")) {
            try {
                val url = URL(requestTarget)
                val host = url.host
                val port = if (url.port != -1) url.port else (if (url.protocol == "https") 443 else 80)
                if (host.isNotEmpty()) Pair(host, port) else null
            } catch (e: Exception) {
                null
            }
        } else {
            // Origin form - need Host header
            hostHeader?.let { hh ->
                val parts = hh.split(":")
                val host = parts[0]
                val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 80 else 80
                Pair(host, port)
            }
        }
    }

    /** Check if a host is a tailnet target (100.64.0.0/10 or *.ts.net). Internal for testing. */
    internal fun isTailnetHost(host: String): Boolean {
        // Check if host is a tailnet IP (100.64.0.0/10)
        if (isTailnetIp(host)) return true
        // Check if host is a MagicDNS FQDN (*.ts.net)
        if (host.lowercase().endsWith(".ts.net")) return true
        return false
    }

    /** Check if a host string is an IPv4 address in the tailnet range 100.64.0.0/10. Internal for testing. */
    internal fun isTailnetIp(host: String): Boolean {
        return try {
            val addr = InetAddress.getByName(host)
            val bytes = addr.address
            bytes.size == 4 && (bytes[0].toInt() and 0xFF) == 100 && (bytes[1].toInt() and 0xFF) in 64..127
        } catch (e: Exception) {
            false
        }
    }

    private fun sendErrorResponse(
        outputStream: java.io.OutputStream,
        code: Int,
        status: String,
        message: String,
    ) {
        try {
            val body = "Proxy Error: $message"
            val response = "HTTP/1.1 $code $status\r\nContent-Type: text/plain\r\nContent-Length: ${body.length}\r\n\r\n$body"
            outputStream.write(response.toByteArray())
            outputStream.flush()
        } catch (e: Exception) {
            Log.d(TAG, "Failed to send error response", e)
        }
    }
}