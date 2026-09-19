package com.glomopay.sdk.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class GlomoPayApiClientTest {
    private val client = GlomoPayApiClient("pk_test_123")

    @Test
    fun mapOrderFetchResponse_parses_success_body() {
        val response = client.mapOrderFetchResponse(
            status = 200,
            body = """{"id":"order_1","metadata":{"flow":"lrs"},"items":["one",null]}""",
        )

        assertEquals("order_1", response["id"])
        assertEquals(mapOf("flow" to "lrs"), response["metadata"])
        assertEquals(listOf("one", null), response["items"])
    }

    @Test
    fun mapOrderFetchResponse_keeps_http_error_body_out_of_exception() {
        val error = assertFailsWith<GlomoPayHttpStatusError> {
            client.mapOrderFetchResponse(
                status = 500,
                body = """{"customerEmail":"user@example.com","paymentId":"pay_real"}""",
            )
        }

        assertEquals(500, error.statusCode)
        assertEquals("Failed to load order. Status: 500", error.message)
        assertFalse(error.message.orEmpty().contains("user@example.com"))
        assertFalse(error.message.orEmpty().contains("pay_real"))
    }

    @Test
    fun mapOrderFetchResponse_accepts_any_2xx_as_the_backend_answering() {
        val created = client.mapOrderFetchResponse(status = 201, body = """{"id":"order_1"}""")
        assertEquals("order_1", created["id"])

        // 204 answers with no body: a broken contract, not a status or connectivity fault.
        assertFailsWith<GlomoPayMalformedResponse> { client.mapOrderFetchResponse(status = 204, body = "") }
    }

    @Test
    fun mapOrderFetchResponse_treats_3xx_and_4xx_as_status_faults() {
        assertEquals(302, assertFailsWith<GlomoPayHttpStatusError> {
            client.mapOrderFetchResponse(status = 302, body = "")
        }.statusCode)
        assertEquals(404, assertFailsWith<GlomoPayHttpStatusError> {
            client.mapOrderFetchResponse(status = 404, body = "")
        }.statusCode)
    }

    @Test
    fun mapOrderFetchResponse_reports_malformed_success_body() {
        val error = assertFailsWith<GlomoPayMalformedResponse> {
            client.mapOrderFetchResponse(status = 200, body = "{not-json")
        }
        assertEquals(null, error.cause)
        assertEquals("Malformed order response.", error.message)
    }
}
