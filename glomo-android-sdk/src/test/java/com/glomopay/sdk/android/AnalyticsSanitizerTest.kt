package com.glomopay.sdk.android

import com.glomopay.sdk.android.analytics.AnalyticsSanitizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsSanitizerTest {
    @Test
    fun bank_redirect_url_keeps_only_https_scheme_and_lowercase_hostname() {
        val sanitized = AnalyticsSanitizer.navigationUrl(
            "https://user:pass@bank.example:8443/verify/ABCDE1234F?phone=9876543210#otp",
        )

        val bankSanitized = AnalyticsSanitizer.bankRedirectUrl(
            "https://user:pass@3DS.IN.Secure.Bank.COM:8443/verify/ABCDE1234F?phone=9876543210#otp",
        )

        assertEquals("https://bank.example:8443/verify/%5BREDACTED%5D", sanitized)
        assertEquals("https://3ds.in.secure.bank.com", bankSanitized)
    }

    @Test
    fun bank_redirect_url_rejects_non_https_or_malformed_urls() {
        assertEquals(null, AnalyticsSanitizer.bankRedirectUrl("http://bank.example/otp"))
        assertEquals(null, AnalyticsSanitizer.bankRedirectUrl("not-a-url"))
    }

    @Test
    fun pii_values_are_redacted_but_opaque_glomo_ids_are_preserved() {
        val sanitized = AnalyticsSanitizer.properties(mapOf(
            "order_id" to "order_6af743563",
            "message" to "user@example.com 9876543210 ABCDE1234F N1234567 ABC1234567",
            "customer_email" to "user@example.com",
        ))

        assertEquals("order_6af743563", sanitized["order_id"])
        assertEquals(
            "[REDACTED] [REDACTED] [REDACTED] [REDACTED] [REDACTED]",
            sanitized["message"],
        )
        assertFalse(sanitized.containsKey("customer_email"))
    }

    @Test
    fun nullable_compliance_signal_is_retained() {
        val sanitized = AnalyticsSanitizer.properties(mapOf("is_compliant" to null))

        assertTrue(sanitized.containsKey("is_compliant"))
        assertEquals(null, sanitized["is_compliant"])
    }

    @Test
    fun generated_iso_timestamp_is_not_mistaken_for_a_numeric_identifier() {
        val timestamp = "2026-08-17T14:33:19.153+05:30"

        val sanitized = AnalyticsSanitizer.properties(mapOf("timestamp" to timestamp))

        assertEquals(timestamp, sanitized["timestamp"])
    }
}
