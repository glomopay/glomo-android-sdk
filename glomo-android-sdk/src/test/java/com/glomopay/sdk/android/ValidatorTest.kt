package com.glomopay.sdk.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidatorTest {
    @Test
    fun public_key_prefix_and_length_rules_match_flutter() {
        assertTrue(Validator.isValidPublicKey("live_12345"))
        assertTrue(Validator.isValidPublicKey("test_12345"))
        assertTrue(Validator.isValidPublicKey("mock_12345"))
        assertFalse(Validator.isValidPublicKey("live_"))
        assertFalse(Validator.isValidPublicKey("prod_12345"))
    }

    @Test
    fun order_and_subscription_id_rules_match_flutter() {
        assertTrue(Validator.isValidOrderId("order_123"))
        assertFalse(Validator.isValidOrderId("order_"))
        assertFalse(Validator.isValidOrderId("sub_123"))
        assertTrue(Validator.isValidSubscriptionId("sub_123"))
        assertFalse(Validator.isValidSubscriptionId("sub_"))
        assertFalse(Validator.isValidSubscriptionId("order_123"))
    }

    @Test
    fun identifier_validation_requires_exactly_one_valid_identifier() {
        assertEquals("Either orderId or subscriptionId is required.", Validator.validateCheckoutIdentifier(null, null))
        assertEquals("Provide either orderId or subscriptionId, not both.", Validator.validateCheckoutIdentifier("order_123", "sub_123"))
        assertEquals("orderId must start with 'order_'.", Validator.validateCheckoutIdentifier("bad_123", null))
        assertEquals("subscriptionId must start with 'sub_'.", Validator.validateCheckoutIdentifier(null, "bad_123"))
        assertEquals("", Validator.validateCheckoutIdentifier("order_123", null))
        assertEquals("", Validator.validateCheckoutIdentifier(null, "sub_123"))
    }

    @Test
    fun url_validation_accepts_http_and_https_only() {
        assertTrue(Validator.isValidUrl("https://example.com"))
        assertTrue(Validator.isValidUrl("HTTP://example.com"))
        assertFalse(Validator.isValidUrl("ftp://example.com"))
        assertFalse(Validator.isValidUrl("example.com"))
    }

    @Test
    fun payment_payload_requires_payment_id_and_signature() {
        val valid = GlomoPayPayload("order_1", "pay_1", "sig_1")
        assertTrue(Validator.isValidPaymentPayload(valid))
        assertFalse(Validator.isValidPaymentPayload(valid.copy(paymentId = null)))
        assertFalse(Validator.isValidPaymentPayload(valid.copy(signature = "")))
        assertFalse(Validator.isValidPaymentPayload(valid.copy(orderId = "")))
    }

    @Test
    fun bank_transfer_requires_only_order_id() {
        assertTrue(Validator.isValidBankTransferPayload(GlomoPayPayload("order_1")))
        assertFalse(Validator.isValidBankTransferPayload(GlomoPayPayload("")))
    }
}
