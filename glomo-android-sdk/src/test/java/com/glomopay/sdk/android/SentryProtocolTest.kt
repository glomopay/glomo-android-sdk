package com.glomopay.sdk.android

import com.glomopay.sdk.android.monitoring.SentryDsn
import com.glomopay.sdk.android.monitoring.SentryEnvelope
import com.glomopay.sdk.android.monitoring.SentryRateLimiter
import org.json.JSONObject
import java.util.TreeMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Synthetic keys only. Nothing here is a real credential.
private const val DE_REGION_DSN =
    "https://fakepublickey0123456789@o4500000000000000.ingest.de.sentry.io/4501111111111111"
private const val US_REGION_DSN =
    "https://fakepublickey0123456789@o4500000000000000.ingest.us.sentry.io/4501111111111111"

class SentryProtocolTest {
    @Test
    fun dsn_derives_the_envelope_endpoint_and_auth_header() {
        val dsn = assertNotNull(SentryDsn.parse(DE_REGION_DSN))

        assertEquals(
            "https://o4500000000000000.ingest.de.sentry.io/api/4501111111111111/envelope/",
            dsn.envelopeUrl,
        )
        assertEquals("fakepublickey0123456789", dsn.publicKey)
        assertEquals("4501111111111111", dsn.projectId)
        assertEquals(
            "Sentry sentry_version=7, sentry_client=glomo-android-sdk/1.0.0, " +
                "sentry_key=fakepublickey0123456789",
            dsn.authHeader("glomo-android-sdk/1.0.0"),
        )
    }

    @Test
    fun dsn_region_comes_from_the_host_never_from_a_constant() {
        val de = assertNotNull(SentryDsn.parse(DE_REGION_DSN))
        val us = assertNotNull(SentryDsn.parse(US_REGION_DSN))

        assertTrue(de.envelopeUrl.startsWith("https://o4500000000000000.ingest.de.sentry.io/"))
        assertTrue(us.envelopeUrl.startsWith("https://o4500000000000000.ingest.us.sentry.io/"))
    }

    @Test
    fun dsn_supports_self_hosted_port_path_prefix_and_legacy_secret() {
        val dsn = assertNotNull(SentryDsn.parse("  http://fakepublic:fakesecret@sentry.internal:9000/prefix/7  "))

        assertEquals("http://sentry.internal:9000/prefix/api/7/envelope/", dsn.envelopeUrl)
        assertTrue(dsn.authHeader("client/1").endsWith(", sentry_secret=fakesecret"))
    }

    @Test
    fun dsn_returns_null_for_unusable_input_instead_of_throwing() {
        listOf(
            null,
            "",
            "   ",
            "not a dsn",
            "ftp://key@example.com/1",
            "https://example.com/1",
            "https://key@example.com",
            "https://key@example.com/",
            "https://key@/1",
            "https://key@exa mple.com/1",
            "https://:secret@example.com/1",
        ).forEach { assertNull(SentryDsn.parse(it), "Expected null for <$it>") }
    }

    @Test
    fun dsn_never_prints_its_key() {
        val dsn = assertNotNull(SentryDsn.parse(DE_REGION_DSN))

        assertFalse(dsn.toString().contains("fakepublickey0123456789"))
    }

    @Test
    fun envelope_is_header_item_header_and_payload_on_separate_lines() {
        val dsn = assertNotNull(SentryDsn.parse(DE_REGION_DSN))
        val body = SentryEnvelope.event(
            eventId = "abcdef01234567890abcdef012345678",
            event = JSONObject().put("level", "error"),
            dsn = dsn,
            sentAtMillis = 1_787_659_200_000L,
        )

        val text = String(body, Charsets.UTF_8)
        assertTrue(text.endsWith("\n"))
        val lines = text.removeSuffix("\n").split('\n')
        assertEquals(3, lines.size)
        val header = JSONObject(lines[0])
        assertEquals("abcdef01234567890abcdef012345678", header.getString("event_id"))
        assertEquals("2026-08-25T12:00:00.000Z", header.getString("sent_at"))
        assertEquals(DE_REGION_DSN, header.getString("dsn"))
        val itemHeader = JSONObject(lines[1])
        assertEquals("event", itemHeader.getString("type"))
        assertEquals("application/json", itemHeader.getString("content_type"))
        assertEquals(lines[2].toByteArray(Charsets.UTF_8).size, itemHeader.getInt("length"))
        assertEquals("error", JSONObject(lines[2]).getString("level"))
    }

    @Test
    fun envelope_declares_byte_length_not_character_length() {
        val dsn = assertNotNull(SentryDsn.parse(DE_REGION_DSN))
        val body = SentryEnvelope.event(
            eventId = "abcdef01234567890abcdef012345678",
            // Multi-byte on purpose: Relay slices items by the declared length.
            event = JSONObject().put("note", "₹1,000 भुगतान"),
            dsn = dsn,
            sentAtMillis = 0L,
        )

        val lines = String(body, Charsets.UTF_8).split('\n')
        val declared = JSONObject(lines[1]).getInt("length")
        assertEquals(lines[2].toByteArray(Charsets.UTF_8).size, declared)
        // Guards the guard: if the payload drifts back to ASCII the counts coincide.
        assertNotEquals(lines[2].length, declared)
    }

    @Test
    fun rate_limiter_limits_only_the_named_categories_until_the_window_passes() {
        var now = 1_000_000L
        val limiter = SentryRateLimiter { now }
        assertFalse(limiter.isLimited("error"))

        limiter.update(429, headers("x-sentry-rate-limits" to "60:error:organization"))

        assertTrue(limiter.isLimited("error"))
        assertFalse(limiter.isLimited("transaction"))
        now += 61_000
        assertFalse(limiter.isLimited("error"))
    }

    @Test
    fun rate_limiter_handles_several_categories_fractional_seconds_and_longest_quota() {
        var now = 0L
        val limiter = SentryRateLimiter { now }

        limiter.update(
            200,
            headers("X-Sentry-Rate-Limits" to "30:error:organization, 2700.0:error;session:organization:quota"),
        )

        now += 60_000
        assertTrue(limiter.isLimited("error"))
        assertTrue(limiter.isLimited("session"))
    }

    @Test
    fun rate_limiter_empty_category_list_limits_everything() {
        val limiter = SentryRateLimiter { 0L }

        limiter.update(200, headers("x-sentry-rate-limits" to "120::organization"))

        assertTrue(limiter.isLimited("error"))
        assertTrue(limiter.isLimited("session"))
    }

    @Test
    fun rate_limiter_falls_back_to_retry_after_on_a_bare_429() {
        var now = 0L
        val limiter = SentryRateLimiter { now }

        limiter.update(429, headers("Retry-After" to "45"))

        assertTrue(limiter.isLimited("error"))
        now += 46_000
        assertFalse(limiter.isLimited("error"))
    }

    @Test
    fun rate_limiter_accepts_an_http_date_retry_after() {
        // 2026-08-25T12:00:00Z
        var now = 1_787_659_200_000L
        val limiter = SentryRateLimiter { now }

        limiter.update(429, headers("retry-after" to "Tue, 25 Aug 2026 12:02:00 GMT"))

        assertTrue(limiter.isLimited("error"))
        now += 3 * 60_000
        assertFalse(limiter.isLimited("error"))
    }

    @Test
    fun rate_limiter_backs_off_on_a_429_with_no_usable_header() {
        var now = 0L
        val limiter = SentryRateLimiter { now }

        limiter.update(429, headers("retry-after" to "gibberish"))

        assertTrue(limiter.isLimited("error"))
        now += 59_000
        assertTrue(limiter.isLimited("error"))
        now += 2_000
        assertFalse(limiter.isLimited("error"))
    }

    @Test
    fun rate_limiter_falls_back_to_retry_after_when_the_rate_limit_header_parses_to_nothing() {
        var now = 0L
        val limiter = SentryRateLimiter { now }

        limiter.update(429, headers("X-Sentry-Rate-Limits" to ":error:organization", "Retry-After" to "30"))

        assertTrue(limiter.isLimited("error"))
        now += 31_000
        assertFalse(limiter.isLimited("error"))
    }

    @Test
    fun rate_limiter_ignores_an_unparseable_rate_limit_header_on_a_success() {
        val limiter = SentryRateLimiter { 0L }

        limiter.update(200, headers("X-Sentry-Rate-Limits" to ":error:organization"))

        assertFalse(limiter.isLimited("error"))
    }

    @Test
    fun rate_limiter_ignores_non_429_responses_without_headers() {
        val limiter = SentryRateLimiter { 0L }

        limiter.update(200, headers())
        limiter.update(503, headers())

        assertFalse(limiter.isLimited("error"))
    }

    private fun headers(vararg pairs: Pair<String, String>): (String) -> String? {
        val map = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).apply { putAll(pairs) }
        return { map[it] }
    }
}
