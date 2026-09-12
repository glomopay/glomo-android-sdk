package com.glomopay.sdk.android

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class GlomoPayApiClientTest {
    @Test
    fun http_error_message_includes_status_but_not_response_body() {
        val secretBody = """{"error":"order not found","customer_email":"user@example.com"}"""
        val error = assertFailsWith<IOException> {
            mapOrderFetchResponse(404, secretBody)
        }
        assertEquals("Failed to load order. Status: 404", error.message)
        assertFalse(error.message.orEmpty().contains(secretBody))
        assertFalse(error.message.orEmpty().contains("user@example.com"))
        assertFalse(error.message.orEmpty().contains("Body"))
    }

    @Test
    fun wrapped_fetch_error_keeps_status_and_omits_body() {
        val inner = assertFailsWith<IOException> {
            mapOrderFetchResponse(500, """{"payload":"should-not-leak"}""")
        }
        val wrapped = IOException("Network error fetching order: ${inner.message}", inner)
        assertEquals("Network error fetching order: Failed to load order. Status: 500", wrapped.message)
        assertFalse(wrapped.message.orEmpty().contains("should-not-leak"))
    }

    @Test
    fun success_response_is_parsed() {
        val order = mapOrderFetchResponse(200, """{"id":"order_123","type":"standard"}""")
        assertEquals("order_123", order["id"])
        assertEquals("standard", order["type"])
    }
}
