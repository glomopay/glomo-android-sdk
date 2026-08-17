package com.glomopay.sdk.android.sampleApp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CheckoutFormStateTest {
    @Test
    fun order_form_maps_to_sdk_config() {
        val config = CheckoutFormState(
            publicKey = " test_key ",
            identifier = " order_1 ",
            devMode = true,
        ).toConfig()

        assertEquals("test_key", config.publicKey)
        assertEquals("order_1", config.orderId)
        assertEquals(null, config.subscriptionId)
        assertEquals(true, config.devMode)
    }

    @Test
    fun subscription_form_maps_without_order_id() {
        val config = CheckoutFormState("test_key", "sub_1", false).toConfig()

        assertEquals(null, config.orderId)
        assertEquals("sub_1", config.subscriptionId)
    }

    @Test
    fun form_rejects_missing_public_key() {
        assertFailsWith<IllegalArgumentException> {
            CheckoutFormState("", "order_1", false).toConfig()
        }
    }

    @Test
    fun form_rejects_missing_identifier() {
        assertFailsWith<IllegalArgumentException> {
            CheckoutFormState("test_key", "", false).toConfig()
        }
    }
}
