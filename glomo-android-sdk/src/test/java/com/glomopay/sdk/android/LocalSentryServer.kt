package com.glomopay.sdk.android

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * A real HTTP/1.1 server on loopback standing in for Sentry's ingest endpoint.
 *
 * Built on java.net.ServerSocket because Android unit tests compile against android.jar, which does
 * not expose com.sun.net.httpserver. Deliberately small: one request per connection, then close.
 * A `Content-Encoding: gzip` body is decompressed, as Sentry's ingest does; [Request.rawBody] keeps
 * the bytes exactly as they arrived.
 */
internal class LocalSentryServer : AutoCloseable {
    class Request(
        val method: String,
        val path: String,
        private val headers: Map<String, String>,
        val rawBody: ByteArray,
    ) {
        val body: ByteArray =
            if (header("Content-Encoding") == "gzip") GZIPInputStream(rawBody.inputStream()).readBytes() else rawBody

        fun header(name: String): String? = headers[name.lowercase()]

        val lines: List<String>
            get() = String(body, Charsets.UTF_8).split('\n')

        val envelopeHeader: JSONObject get() = JSONObject(lines[0])
        val itemHeader: JSONObject get() = JSONObject(lines[1])
        val event: JSONObject get() = JSONObject(lines[2])
    }

    private class Response(val status: Int, val headers: Map<String, String>)

    private val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val pool = Executors.newCachedThreadPool()
    private val responses = ConcurrentLinkedQueue<Response>()
    val requests = LinkedBlockingQueue<Request>()

    /** When set, each request is recorded and then held until the latch opens. */
    @Volatile var hold: CountDownLatch? = null

    val port: Int get() = socket.localPort
    val dsn: String get() = "http://$PUBLIC_KEY@127.0.0.1:$port/$PROJECT_ID"

    init {
        pool.execute {
            while (!socket.isClosed) {
                val connection = try {
                    socket.accept()
                } catch (_: SocketException) {
                    break
                }
                pool.execute { handle(connection) }
            }
        }
    }

    fun respondNext(status: Int, headers: Map<String, String> = emptyMap()) {
        responses.add(Response(status, headers))
    }

    fun takeRequest(timeoutMillis: Long = 5_000): Request =
        requests.poll(timeoutMillis, TimeUnit.MILLISECONDS) ?: error("Expected a request to reach the server")

    override fun close() {
        hold?.let { while (it.count > 0) it.countDown() }
        socket.close()
        pool.shutdownNow()
    }

    private fun handle(connection: Socket) = runCatching {
        connection.use {
            val input = BufferedInputStream(it.getInputStream())
            val head = readHead(input).split("\r\n")
            val (method, target) = head.first().split(' ')
            val headers = head.drop(1).filter { line -> ':' in line }.associate { line ->
                line.substringBefore(':').trim().lowercase() to line.substringAfter(':').trim()
            }
            val length = headers["content-length"]?.toInt() ?: 0
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val count = input.read(body, read, length - read)
                if (count < 0) break
                read += count
            }
            requests.put(Request(method, target.substringBefore('?'), headers, body))

            hold?.await(10, TimeUnit.SECONDS)
            val response = responses.poll() ?: Response(200, emptyMap())
            val payload = """{"id":"accepted"}""".toByteArray()
            val reply = buildString {
                append("HTTP/1.1 ${response.status} Status\r\n")
                append("Content-Type: application/json\r\n")
                append("Content-Length: ${payload.size}\r\n")
                append("Connection: close\r\n")
                response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                append("\r\n")
            }
            it.getOutputStream().apply {
                write(reply.toByteArray(Charsets.US_ASCII))
                write(payload)
                flush()
            }
        }
    }

    private fun readHead(input: InputStream): String {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        while (matched < 4) {
            val byte = input.read()
            if (byte < 0) break
            buffer.write(byte)
            matched = when {
                byte == '\r'.code && (matched == 0 || matched == 2) -> matched + 1
                byte == '\n'.code && (matched == 1 || matched == 3) -> matched + 1
                byte == '\r'.code -> 1
                else -> 0
            }
        }
        return buffer.toString(Charsets.US_ASCII.name()).trimEnd()
    }

    companion object {
        // Synthetic credentials only.
        const val PUBLIC_KEY = "fakepublickey0123456789"
        const val PROJECT_ID = "4501111111111111"
    }
}
