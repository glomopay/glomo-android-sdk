package com.glomopay.sdk.android.monitoring

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Sentry's newline-delimited envelope: an envelope header, then an item header and payload per item.
 *
 * Item headers declare the payload length in UTF-8 bytes. Relay slices items by that length, so a
 * character count would make it mis-parse the envelope and drop it without an error.
 */
internal object SentryEnvelope {
    const val CONTENT_TYPE: String = "application/x-sentry-envelope"

    fun event(eventId: String, event: JSONObject, dsn: SentryDsn, sentAtMillis: Long): ByteArray {
        val header = JSONObject()
            .put("event_id", eventId)
            .put("sent_at", isoTimestamp(sentAtMillis))
            .put("dsn", dsn.value)
        val payload = event.toString().toByteArray(Charsets.UTF_8)
        val itemHeader = JSONObject()
            .put("type", "event")
            .put("content_type", "application/json")
            .put("length", payload.size)
        return ByteArrayOutputStream().apply {
            writeLine(header.toString().toByteArray(Charsets.UTF_8))
            writeLine(itemHeader.toString().toByteArray(Charsets.UTF_8))
            writeLine(payload)
        }.toByteArray()
    }

    /** ISO 8601 UTC with milliseconds, the form Sentry uses for `timestamp` and `sent_at`. */
    fun isoTimestamp(timeMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(timeMillis))

    private fun ByteArrayOutputStream.writeLine(bytes: ByteArray) {
        write(bytes)
        write('\n'.code)
    }
}
