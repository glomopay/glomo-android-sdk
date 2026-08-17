package com.glomopay.sdk.android

import com.glomopay.sdk.android.analytics.AnalyticsEvent
import com.glomopay.sdk.android.analytics.AnalyticsTransport
import com.glomopay.sdk.android.analytics.MixpanelAnalyticsTracker
import com.glomopay.sdk.android.analytics.MixpanelEventEncoder
import com.glomopay.sdk.android.analytics.MIXPANEL_TRACK_ENDPOINT
import com.glomopay.sdk.android.analytics.validateMixpanelResponse
import com.glomopay.sdk.android.monitoring.SdkErrorReporter
import org.json.JSONArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class MixpanelAnalyticsTrackerTest {
    @Test
    fun tracker_adds_the_approved_common_property_contract() {
        val events = mutableListOf<AnalyticsEvent>()
        val tracker = MixpanelAnalyticsTracker(
            config = GlomoPayConfig(
                publicKey = "test_public_key",
                orderId = "order_123",
                devMode = true,
            ),
            sessionId = "session-uuid",
            sdkVersion = "1.0.0",
            initialFlowType = "auto",
            transport = AnalyticsTransport { events += it },
            executor = java.util.concurrent.Executor(Runnable::run),
            clock = { 1_723_620_000_000L },
            deviceProperties = { mapOf(
                "device_os_version" to "14",
                "\$model" to "Pixel 8",
                "\$device" to "shiba",
                "\$wifi_enabled" to true,
                "\$cellular_enabled" to false,
                "\$app_namespace" to "com.example.merchant",
            ) },
        )

        tracker.updateFlowType("lrs")
        tracker.updateCheckoutUrl("https://checkout.glomopay.com/?orderId=order_123")
        tracker.track("Checkout Started")

        val properties = events.single().properties
        assertEquals("order_123", properties["distinct_id"])
        assertEquals("session-uuid", properties["session_id"])
        assertEquals("session-uuid", properties["\$insert_id"])
        assertEquals("Pixel 8", properties["\$model"])
        assertEquals(true, properties["\$wifi_enabled"])
        assertEquals(false, properties["\$cellular_enabled"])
        assertEquals("lrs", properties["flow_type"])
        assertEquals(true, properties["mock_mode"])
        assertEquals("android-sdk", properties["surface"])
        assertEquals(1_723_620_000_000L, properties["time"])
        assertEquals("2024-08-14T12:50:00.000+05:30", properties["timestamp"])
    }

    @Test
    fun subscription_flow_keeps_nullable_order_identity_without_fallback() {
        val events = mutableListOf<AnalyticsEvent>()
        val tracker = MixpanelAnalyticsTracker(
            config = GlomoPayConfig(publicKey = "test_public_key", subscriptionId = "sub_123"),
            sessionId = "session-uuid",
            sdkVersion = "1.0.0",
            initialFlowType = "standard",
            transport = AnalyticsTransport { events += it },
            executor = java.util.concurrent.Executor(Runnable::run),
            deviceProperties = { emptyMap() },
        )

        tracker.track("SDK Initialized")

        val properties = events.single().properties
        assertTrue(properties.containsKey("order_id"))
        assertEquals(null, properties["order_id"])
        assertEquals(null, properties["distinct_id"])
        assertEquals("sub_123", properties["subscription_id"])
        assertTrue(properties.containsKey("checkout_url"))
        assertEquals(null, properties["checkout_url"])
    }

    @Test
    fun encoder_creates_mixpanel_track_array_and_injects_token() {
        val payload = MixpanelEventEncoder.encode(
            AnalyticsEvent("SDK Initialized", mapOf(
                "distinct_id" to "order_1",
                "checkout_url" to null,
            )),
            "project-token",
        )

        val event = JSONArray(payload).getJSONObject(0)
        assertEquals("SDK Initialized", event.getString("event"))
        val properties = event.getJSONObject("properties")
        assertEquals("project-token", properties.getString("token"))
        assertTrue(properties.has("checkout_url"))
        assertTrue(properties.isNull("checkout_url"))
        assertFalse(payload.contains("Authorization"))
        assertEquals("https://api.mixpanel.com/track?ip=1", MIXPANEL_TRACK_ENDPOINT)
    }

    @Test
    fun transport_response_validation_rejects_http_and_ingestion_failures() {
        validateMixpanelResponse(200, "1")

        assertFailsWith<IllegalStateException> { validateMixpanelResponse(500, "") }
        assertFailsWith<IllegalStateException> { validateMixpanelResponse(200, "0") }
    }

    @Test
    fun transport_failure_is_reported_without_escaping_the_async_boundary() {
        val reporter = RecordingErrorReporter()
        val tracker = MixpanelAnalyticsTracker(
            config = GlomoPayConfig(publicKey = "test_public_key", orderId = "order_123"),
            sessionId = "session-uuid",
            sdkVersion = "1.0.0",
            initialFlowType = "standard",
            transport = AnalyticsTransport { error("HTTP 500") },
            executor = java.util.concurrent.Executor(Runnable::run),
            deviceProperties = { emptyMap() },
            errorReporter = reporter,
        )

        tracker.track("Checkout Started")

        assertEquals("mixpanel_delivery", reporter.operation)
        assertEquals("Checkout Started", reporter.context["event_name"])
        assertEquals(listOf("Checkout Started"), reporter.breadcrumbs)
    }

    private class RecordingErrorReporter : SdkErrorReporter {
        val breadcrumbs = mutableListOf<String>()
        var operation: String? = null
        var context: Map<String, Any?> = emptyMap()

        override fun addBreadcrumb(category: String, message: String, data: Map<String, Any?>) {
            breadcrumbs += message
        }

        override fun capture(operation: String, error: Throwable, context: Map<String, Any?>) {
            this.operation = operation
            this.context = context
        }

        override fun updateFlowType(flowType: String) = Unit
    }
}
