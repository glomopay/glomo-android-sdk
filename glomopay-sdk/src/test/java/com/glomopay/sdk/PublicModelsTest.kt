package com.glomopay.sdk

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
        assertTrue(config.copyWith(devMode = true).devMode)
        assertEquals("sub_123", config.copyWith(devMode = true).checkoutId)
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
    fun connection_error_matches_flutter_recoverability() {
        assertTrue(ConnectionError.fromWebResourceError("offline", -2).isRecoverable)
        assertTrue(ConnectionError.fromWebResourceError("timeout", -7).isRecoverable)
        assertTrue(ConnectionError.fromHttpStatus(503).isRecoverable)
        assertFalse(ConnectionError.fromHttpStatus(404).isRecoverable)
        assertEquals("Page Not Found", ConnectionError.fromHttpStatus(404).message)
    }

    @Test
    fun checkout_status_has_same_order_as_flutter() {
        assertEquals(
            listOf(
                CheckoutStatus.READY,
                CheckoutStatus.VALIDATING,
                CheckoutStatus.PAYMENT_IN_PROGRESS,
                CheckoutStatus.PAYMENT_SUCCESSFUL,
                CheckoutStatus.PAYMENT_FAILED,
                CheckoutStatus.PAYMENT_CANCELLED,
            ),
            CheckoutStatus.entries,
        )
    }
}
