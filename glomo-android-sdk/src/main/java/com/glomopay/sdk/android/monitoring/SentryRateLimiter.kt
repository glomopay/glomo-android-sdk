package com.glomopay.sdk.android.monitoring

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Tracks Sentry ingestion rate limits from `X-Sentry-Rate-Limits` and, for a bare 429,
 * `Retry-After`.
 *
 * Anything caught by an active limit is dropped, never queued: a payment flow must not build a
 * telemetry backlog inside a merchant's app.
 */
internal class SentryRateLimiter(private val clock: () -> Long = System::currentTimeMillis) {
    private val blockedUntil = HashMap<String, Long>()

    @Synchronized
    fun isLimited(category: String): Boolean {
        val now = clock()
        return isBlocked(ALL_CATEGORIES, now) || isBlocked(category, now)
    }

    /** [header] looks up a response header by name, case-insensitively. */
    @Synchronized
    fun update(statusCode: Int, header: (String) -> String?) {
        val limits = header("X-Sentry-Rate-Limits")
        // A header that parses to no quota at all must not suppress the 429 back-off below.
        if (!limits.isNullOrBlank() && applyLimits(limits) > 0) return
        if (statusCode == 429) block(ALL_CATEGORIES, retryAfterMillis(header("Retry-After")))
    }

    private fun isBlocked(key: String, now: Long): Boolean {
        val until = blockedUntil[key] ?: return false
        if (until > now) return true
        blockedUntil.remove(key)
        return false
    }

    private fun block(key: String, durationMillis: Long) {
        val until = clock() + durationMillis
        if (until > (blockedUntil[key] ?: Long.MIN_VALUE)) blockedUntil[key] = until
    }

    /**
     * Parses `retry_after:categories:scope:reason:namespaces`; empty categories means all. Returns
     * the number of quotas applied, 0 when none parsed.
     */
    private fun applyLimits(header: String): Int {
        var applied = 0
        header.split(',').forEach { quota ->
            val parts = quota.split(':')
            val seconds = parseSeconds(parts.first()) ?: return@forEach
            val categories = parts.getOrNull(1)?.trim().orEmpty()
            if (categories.isEmpty()) {
                block(ALL_CATEGORIES, seconds * 1_000)
            } else {
                categories.split(';')
                    .map { it.trim().lowercase(Locale.US) }
                    .filter { it.isNotEmpty() }
                    .forEach { block(it, seconds * 1_000) }
            }
            applied++
        }
        return applied
    }

    private fun retryAfterMillis(value: String?): Long {
        if (value == null) return DEFAULT_BACKOFF_MILLIS
        parseSeconds(value)?.let { return it * 1_000 }
        val date = runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("GMT") }
                .parse(value.trim())
        }.getOrNull() ?: return DEFAULT_BACKOFF_MILLIS
        return (date.time - clock()).coerceAtLeast(0)
    }

    private fun parseSeconds(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // Sentry sends fractional seconds such as `2700.0`.
        return trimmed.substringBefore('.').toLongOrNull()?.takeIf { it >= 0 }
    }

    companion object {
        const val ERROR_CATEGORY: String = "error"
        private const val ALL_CATEGORIES = "__all__"
        private const val DEFAULT_BACKOFF_MILLIS = 60_000L
    }
}
