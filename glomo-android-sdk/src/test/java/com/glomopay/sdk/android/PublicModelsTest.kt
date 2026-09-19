package com.glomopay.sdk.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicModelsTest {
    @Test
    fun config_exposes_flutter_helpers() {
        val config = GlomoPayConfig(publicKey = "live_key", subscriptionId = "sub_123")

        assertEquals("sub_123", config.checkoutId)
        assertTrue(config.isSubscription)
        assertEquals("sub_123", config.copyWith(server = "https://example.test").checkoutId)
    }

    @Test
    fun payload_reads_nested_checkout_message() {
        val payload = GlomoPayPayload.fromMap(
            mapOf(
                "type" to "payment.success",
                "orderId" to "outer-order",
                "payload" to mapOf(
                    "orderId" to "order_123",
                    "paymentId" to "pay_123",
                    "signature" to "sig_123",
                ),
            ),
        )

        assertEquals("order_123", payload.orderId)
        assertEquals("pay_123", payload.paymentId)
        assertEquals("sig_123", payload.signature)
        assertEquals("payment.success", payload.rawResponse?.get("type"))
    }

    @Test
    fun payload_reads_legacy_flat_and_snake_case_fields() {
        val payload = GlomoPayPayload.fromMap(
            mapOf("order_id" to "order_777", "payment_id" to "pay_777"),
        )

        assertEquals("order_777", payload.orderId)
        assertEquals("pay_777", payload.paymentId)
        assertNull(payload.signature)
    }

    @Test
    fun payload_defaults_missing_order_id_to_empty_string() {
        assertEquals("", GlomoPayPayload.fromMap(emptyMap()).orderId)
    }

    @Test
    fun payload_prefers_nested_fields_and_keeps_raw_response() {
        val payload = GlomoPayPayload.fromMap(
            mapOf(
                "orderId" to "outer",
                "paymentId" to "outer-payment",
                "payload" to mapOf("order_id" to "nested", "payment_id" to "nested-payment"),
                "status" to "success",
            ),
        )

        assertEquals("nested", payload.orderId)
        assertEquals("nested-payment", payload.paymentId)
        assertEquals("success", payload.rawResponse?.get("status"))
    }

    @Test
    fun result_types_preserve_success_failure_and_cancelled_contracts() {
        val success = GlomoPayResult.Success(mapOf("orderId" to "order_1"))
        val failure = GlomoPayResult.Failure("failed", "NETWORK")

        assertEquals("order_1", (success as GlomoPayResult.Success).payload["orderId"])
        assertEquals("NETWORK", failure.code)
        val cancelled: GlomoPayResult = GlomoPayResult.Cancelled
        assertEquals(GlomoPayResult.Cancelled, cancelled)
    }

    @Test
    fun user_journey_type_members_are_append_only() {
        // Pinned on purpose: hosts and stored records key off member order, so an
        // inserted member would silently re-label every journey already recorded.
        assertEquals(listOf("BANK_TRANSFER"), GlomoPayUserJourneyType.entries.map { it.name })
        assertEquals(0, GlomoPayUserJourneyType.BANK_TRANSFER.ordinal)
    }

    @Test
    fun user_journey_payload_keeps_the_page_response_verbatim() {
        val json = mapOf<String, Any?>(
            "order_id" to "order_1",
            "senderAccountNumber" to "000123456789",
            "transaction_reference" to "utr_1",
            "status" to "",
            "extra" to mapOf("bank" to "example"),
        )

        val payload = GlomoPayUserJourneyPayload.fromMap(GlomoPayUserJourneyType.BANK_TRANSFER, json)

        assertEquals("order_1", payload.orderId)
        assertEquals("000123456789", payload.senderAccountNumber)
        assertEquals("utr_1", payload.transactionReference)
        // Empty strings are absent, not values.
        assertEquals(null, payload.status)
        assertEquals(json, payload.rawResponse)
        assertEquals("BANK_TRANSFER", payload.toMap()["journeyType"])
    }

    @Test
    fun connection_error_matches_flutter_recoverability() {
        assertTrue(ConnectionError.fromWebResourceError("offline", -2).isRecoverable)
        assertTrue(ConnectionError.fromWebResourceError("timeout", -7).isRecoverable)
        assertTrue(ConnectionError.fromHttpStatus(503).isRecoverable)
        assertFalse(ConnectionError.fromHttpStatus(404).isRecoverable)
        assertEquals("Page Not Found", ConnectionError.fromHttpStatus(404).message)
    }

}
