package com.glomopay.sdk.android.monitoring

import com.glomopay.sdk.android.analytics.GlomoPayLogger
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal enum class SentrySendResult { SENT, RATE_LIMITED, REJECTED, FAILED }

/**
 * Minimal client for Sentry's HTTP envelope endpoint. Deliberately not an SDK: it installs no
 * uncaught-exception handler, shutdown hook or session tracking, and only sends events Glomo code
 * hands it.
 *
 * Delivery runs on a single background thread behind a bounded queue. When the queue is full, or a
 * rate limit is active, the event is dropped. Nothing is retried or persisted.
 */
internal class SentryEnvelopeClient(
    private val dsn: SentryDsn,
    private val clientName: String,
    private val executor: Executor = boundedExecutor(),
    private val rateLimiter: SentryRateLimiter = SentryRateLimiter(),
    private val timeoutMillis: Int = TIMEOUT_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Queues [event] for delivery. Never throws and never blocks on the network. */
    fun send(eventId: String, event: JSONObject) {
        runCatching {
            if (rateLimiter.isLimited(SentryRateLimiter.ERROR_CATEGORY)) {
                GlomoPayLogger.log("Sentry event dropped: rate limited")
                return
            }
            executor.execute {
                runCatching { deliver(SentryEnvelope.event(eventId, event, dsn, clock())) }
                    .onFailure { GlomoPayLogger.error("Unable to deliver SDK failure to Sentry", it) }
            }
        }.onFailure { GlomoPayLogger.error("Unable to queue SDK failure for Sentry", it) }
    }

    /** Posts one serialised envelope on the calling thread. Never throws. */
    fun deliver(envelope: ByteArray): SentrySendResult {
        if (rateLimiter.isLimited(SentryRateLimiter.ERROR_CATEGORY)) {
            GlomoPayLogger.log("Sentry event dropped: rate limited")
            return SentrySendResult.RATE_LIMITED
        }
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(dsn.envelopeUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMillis
                readTimeout = timeoutMillis
                doOutput = true
                useCaches = false
                instanceFollowRedirects = false
                setFixedLengthStreamingMode(envelope.size)
                setRequestProperty("Content-Type", SentryEnvelope.CONTENT_TYPE)
                setRequestProperty("X-Sentry-Auth", dsn.authHeader(clientName))
                setRequestProperty("User-Agent", clientName)
            }
            connection.outputStream.use { it.write(envelope) }
            val status = connection.responseCode
            val response = connection
            rateLimiter.update(status) { name -> response.getHeaderField(name) }
            drain(connection, status)
            when {
                status in 200..299 -> SentrySendResult.SENT
                status == 429 -> SentrySendResult.RATE_LIMITED
                else -> SentrySendResult.REJECTED
            }.also { if (it != SentrySendResult.SENT) GlomoPayLogger.log("Sentry returned HTTP $status") }
        } catch (error: Throwable) {
            GlomoPayLogger.error("Sentry delivery failed", error)
            SentrySendResult.FAILED
        } finally {
            connection?.disconnect()
        }
    }

    /** Reads and discards the (small) response body so the connection is released. */
    private fun drain(connection: HttpURLConnection, status: Int) {
        runCatching {
            (if (status in 200..299) connection.inputStream else connection.errorStream)?.use { stream ->
                val buffer = ByteArray(1_024)
                var remaining = MAX_RESPONSE_BYTES
                while (remaining > 0) {
                    val read = stream.read(buffer, 0, minOf(buffer.size, remaining))
                    if (read < 0) break
                    remaining -= read
                }
            }
        }
    }

    companion object {
        const val TIMEOUT_MILLIS: Int = 10_000
        const val MAX_QUEUED_EVENTS: Int = 30
        private const val MAX_RESPONSE_BYTES = 16 * 1_024
        private const val IDLE_THREAD_SECONDS = 30L

        fun boundedExecutor(): Executor = ThreadPoolExecutor(
            1,
            1,
            IDLE_THREAD_SECONDS,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(MAX_QUEUED_EVENTS),
            { runnable -> Thread(runnable, "GlomoPay-Sentry").apply { isDaemon = true } },
            { _, _ -> GlomoPayLogger.log("Sentry event dropped: queue full") },
        ).apply { allowCoreThreadTimeOut(true) }
    }
}
