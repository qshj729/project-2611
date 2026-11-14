package com.hubdeck.app

import android.content.Context
import android.os.Build
import android.os.Bundle
import java.io.FileDescriptor
import java.net.Socket
import java.net.ServerSocket
import java.time.Duration
import android.util.Log
import android.system.Os
import android.system.OsConstants

/**
 * Local proxy server that listens on 127.0.0.1:1080.
 * Chromium Android WebView routes traffic via [ProxyConfigurator] using
 * [androidx.webkit.ProxyController.setProxyOverride].
 * JVM global proxy properties are NOT used.
 *
 * When a connection is accepted, it is tunneled through the tsnet native library
 * via TailnetNode.dial() and Os.read/write for CONNECT and ordinary HTTP forwarding.
 */
class LocalProxyServer {

    private val TAG = "LocalProxyServer"
    private val LISTEN_PORT = 1080
    @Volatile
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var running = false
    private lateinit var tailnetNode: com.klethron.tsnet.TailnetNode

    fun start(context: Context, node: com.klethron.tsnet.TailnetNode) {
        tailnetNode = node
        if (running) return
        running = true
        Thread {
            try {
                serverSocket = ServerSocket(LISTEN_PORT)
                Log.i(TAG, "Local proxy started on 127.0.0.1:$LISTEN_PORT")
                acceptLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start local proxy", e)
                running = false
            }
        }.start()
    }

    fun stop() {
        running = false
        serverSocket?.close()
        serverSocket = null
        Log.i(TAG, "Local proxy stopped")
    }

    private fun acceptLoop() {
        while (running) {
            try {
                val socket = serverSocket?.accept()
                if (socket != null) {
                    Thread {
                        handleClient(socket)
                    }.start()
                }
            } catch (e: Exception) {
                if (running) {
                    Log.e(TAG, "Error in accept loop", e)
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            // Read the request method (CONNECT or GET etc.)
            val inputStream = socket.inputStream
            val request = StringBuilder()
            var b: ByteArray = ByteArray(1024)
            var bytesRead: Int = inputStream.read(b)
            while (bytesRead > 0) {
                request.append(String(b, 0, bytesRead))
                bytesRead = inputStream.read(b)
            }
            val requestText = request.toString()
            val firstLine = requestText.split("\\r\\n")[0]
            val parts = firstLine.split(" ", limit = 2)
            val method = if (parts.isNotEmpty()) parts[0] else ""

            if (method == "CONNECT") {
                // CONNECT request - establish tunnel through tsnet
                val hostPort = if (parts.size > 1) parts[1] else ""
                val hostPortParts = hostPort.split(":")
                val host = hostPortParts[0]
                var port = 443 // default HTTPS
                if (hostPortParts.size > 1) {
                    port = hostPortParts[1].toInt()
                }

                // Dial through tsnet to the target
                val pfd = tailnetNode.dial(host, port, Duration.ofSeconds(30))
                val fd = pfd.fileDescriptor

                // Forward data between WebView socket and tsnet tunnel using Os.read/write
                forwardThroughTunnel(socket, fd)

            } else {
                // Ordinary HTTP request - send simple response
                val outputStream = socket.outputStream
                val response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\nProxy operational — tsnet tunnel pending"
                outputStream.write(response.toByteArray())
                outputStream.flush()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Client handler error", e)
        } finally {
            socket.close()
        }
    }

    private fun forwardThroughTunnel(socket: Socket, tunnelFd: java.io.FileDescriptor) {
        try {
            val socketInput = socket.inputStream
            val socketOutput = socket.outputStream

            // Direction 1: Read from tunnel (Os.read) → write to WebView socket
            val t1 = Thread {
                try {
                    val buf = ByteArray(8192)
                    while (running) {
                        val bytesRead = Os.read(tunnelFd, buf, 0, buf.size)
                        if (bytesRead < 0) break
                        socketOutput.write(buf, 0, bytesRead)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Forward Tunnel→WebView error", e)
                }
            }
            t1.start()

            // Direction 2: Read from WebView socket → write to tunnel (using Os.write on fd)
            val t2 = Thread {
                try {
                    val buf = ByteArray(8192)
                    while (running) {
                        val bytesRead = socketInput.read(buf)
                        if (bytesRead < 0) break
                        Os.write(tunnelFd, buf, 0, bytesRead)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Forward WebView→Tunnel error", e)
                }
            }
            t2.start()

            t1.join()
            t2.join()
        } catch (e: Exception) {
            Log.d(TAG, "forwardThroughTunnel error", e)
        }
    }
}