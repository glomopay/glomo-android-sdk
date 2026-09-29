package com.glomopay.sdk.android.monitoring

import com.glomopay.sdk.android.analytics.GlomoPayLogger
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

internal enum class SentrySendResult { SENT, RATE_LIMITED, REJECTED, FAILED }

/** Outcome of one POST. [statusCode] and [responseBody] are null when no response arrived. */
internal data class SentryDelivery(
    val result: SentrySendResult,
    val statusCode: Int? = null,
    val responseBody: String? = null,
)

/**
 * Minimal client for Sentry's HTTP envelope endpoint. Deliberately not an SDK: it installs no
 * uncaught-exception handler, shutdown hook or session tracking, and only sends events Glomo code
 * hands it.
 *
 * Delivery runs on a single background thread behind a bounded queue. When the queue is full, a
 * rate limit is active, or a delivery fails, the event is dropped. Nothing is retried or persisted,
 * but every drop is counted and reported on the next event that gets through, as
 * `extra.dropped_since_last_send`, so a gap in the project is visible rather than silent.
 */
internal class SentryEnvelopeClient(
    private val dsn: SentryDsn,
    private val clientName: String,
    private val executor: Executor = boundedExecutor(),
    private val rateLimiter: SentryRateLimiter = SentryRateLimiter(),
    private val timeoutMillis: Int = TIMEOUT_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Events discarded since the last successful send and not yet reported to Sentry. */
    private val dropped = AtomicInteger()

    /** Queues [event] for delivery. Never throws and never blocks on the network. */
    fun send(eventId: String, event: JSONObject) {
        runCatching {
            if (rateLimiter.isLimited(SentryRateLimiter.ERROR_CATEGORY)) {
                recordDrop("rate limited")
                return
            }
            try {
                executor.execute { deliverEvent(eventId, event) }
            } catch (_: RejectedExecutionException) {
                recordDrop("queue full")
            }
        }.onFailure { GlomoPayLogger.error("Unable to queue SDK failure for Sentry", it) }
    }

    /**
     * Serialises [event] with the pending drop count and posts it on the calling thread. The count
     * is subtracted only once Sentry accepts the event, so drops reported on a failed send are not
     * lost; the failed event itself is then counted too. Never throws.
     */
    fun deliverEvent(eventId: String, event: JSONObject): SentryDelivery {
        val pending = dropped.get()
        val delivery = runCatching {
            if (pending > 0) {
                val extra = event.optJSONObject("extra") ?: JSONObject().also { event.put("extra", it) }
                extra.put(DROPPED_SINCE_LAST_SEND, pending)
            }
            deliver(SentryEnvelope.event(eventId, event, dsn, clock()))
        }.getOrElse {
            GlomoPayLogger.error("Unable to deliver SDK failure to Sentry", it)
            SentryDelivery(SentrySendResult.FAILED)
        }
        if (delivery.result == SentrySendResult.SENT) {
            if (pending > 0) dropped.addAndGet(-pending)
        } else {
            recordDrop(delivery.result.name.lowercase())
        }
        return delivery
    }

    private fun recordDrop(reason: String) {
        dropped.incrementAndGet()
        GlomoPayLogger.log("Sentry event dropped: $reason")
    }

    /**
     * Posts one serialised envelope on the calling thread, gzip-compressed. The envelope's item
     * `length` headers stay the uncompressed byte counts; they describe the items, not the HTTP
     * body. Never throws.
     */
    fun deliver(envelope: ByteArray): SentryDelivery {
        if (rateLimiter.isLimited(SentryRateLimiter.ERROR_CATEGORY)) {
            GlomoPayLogger.log("Sentry event dropped: rate limited")
            return SentryDelivery(SentrySendResult.RATE_LIMITED)
        }
        var connection: HttpURLConnection? = null
        return try {
            val body = gzip(envelope)
            connection = (URL(dsn.envelopeUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMillis
                readTimeout = timeoutMillis
                doOutput = true
                useCaches = false
                instanceFollowRedirects = false
                setFixedLengthStreamingMode(body.size)
                setRequestProperty("Content-Type", SentryEnvelope.CONTENT_TYPE)
                setRequestProperty("Content-Encoding", "gzip")
                setRequestProperty("X-Sentry-Auth", dsn.authHeader(clientName))
                setRequestProperty("User-Agent", clientName)
            }
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val response = connection
            rateLimiter.update(status) { name -> response.getHeaderField(name) }
            val responseBody = readBody(connection, status)
            val result = when {
                status in 200..299 -> SentrySendResult.SENT
                status == 429 -> SentrySendResult.RATE_LIMITED
                else -> SentrySendResult.REJECTED
            }
            if (result != SentrySendResult.SENT) GlomoPayLogger.log("Sentry returned HTTP $status")
            SentryDelivery(result, status, responseBody)
        } catch (error: Throwable) {
            GlomoPayLogger.error("Sentry delivery failed", error)
            SentryDelivery(SentrySendResult.FAILED)
        } finally {
            connection?.disconnect()
        }
    }

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    /** Reads the (small) response body, bounded, so the connection is released. Never logged. */
    private fun readBody(connection: HttpURLConnection, status: Int): String? = runCatching {
        (if (status in 200..299) connection.inputStream else connection.errorStream)?.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1_024)
            var remaining = MAX_RESPONSE_BYTES
            while (remaining > 0) {
                val read = stream.read(buffer, 0, minOf(buffer.size, remaining))
                if (read < 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
            out.toString(Charsets.UTF_8.name())
        }
    }.getOrNull()

    companion object {
        const val TIMEOUT_MILLIS: Int = 10_000
        const val MAX_QUEUED_EVENTS: Int = 30
        const val DROPPED_SINCE_LAST_SEND: String = "dropped_since_last_send"
        private const val MAX_RESPONSE_BYTES = 16 * 1_024
        private const val IDLE_THREAD_SECONDS = 30L

        /** A full queue rejects with RejectedExecutionException, which [send] counts as a drop. */
        fun boundedExecutor(): Executor = ThreadPoolExecutor(
            1,
            1,
            IDLE_THREAD_SECONDS,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(MAX_QUEUED_EVENTS),
            { runnable -> Thread(runnable, "GlomoPay-Sentry").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        ).apply { allowCoreThreadTimeOut(true) }
    }
}
