package com.glomopay.sdk.android

import com.glomopay.sdk.android.bridge.GlomoPayEventRouter
import com.glomopay.sdk.android.bridge.GlomoPayInjectionScripts
import com.glomopay.sdk.android.analytics.AnalyticsEvent
import com.glomopay.sdk.android.analytics.AnalyticsEvents
import com.glomopay.sdk.android.analytics.AnalyticsTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlomoPayEventRouterTest {
    @Test
    fun success_event_delivers_nested_payload_once() {
        val listener = RecordingListener()
        var result: GlomoPayResult? = null
        val router = GlomoPayEventRouter(listener, devMode = false, onComplete = { result = it })

        val success = mapOf<String, Any?>(
            "type" to "message",
            "data" to mapOf(
                "type" to "payment.success",
                "payload" to mapOf("orderId" to "order_1", "paymentId" to "pay_1", "signature" to "sig_1"),
            ),
        )
        router.handleEnvelope(success)
        router.handleEnvelope(success)

        assertTrue(listener.events.all { it.first == "payment.success" })
        assertEquals("order_1", listener.success.single().orderId)
        assertTrue(result is GlomoPayResult.Success)
    }

    @Test
    fun failure_requires_a_complete_payment_payload() {
        val listener = RecordingListener()
        val router = GlomoPayEventRouter(listener, devMode = false, onComplete = {})

        router.handleEnvelope(mapOf("type" to "message", "data" to mapOf(
            "type" to "payment.failure", "payload" to mapOf("orderId" to "order_1"),
        )))
        assertTrue(listener.failure.isEmpty())

        router.handleEnvelope(mapOf("type" to "message", "data" to mapOf(
            "type" to "payment.failed",
            "payload" to mapOf("orderId" to "order_1", "paymentId" to "pay_1", "signature" to "sig_1"),
        )))
        assertEquals("order_1", listener.failure.single().orderId)
    }

    @Test
    fun cancellation_and_dependency_errors_are_forwarded() {
        val listener = RecordingListener()
        val router = GlomoPayEventRouter(listener, devMode = false, onComplete = {})

        router.handleEnvelope(mapOf("type" to "message", "data" to mapOf("type" to "payment.cancelled")))
        router.handleEnvelope(mapOf("type" to "dependencies.failed_to_load", "message" to "LRS unavailable"))

        assertEquals(TerminationSource.USER_DISMISS, listener.termination.single())
        assertTrue(listener.events.any { it.first == "checkout.dependencies_failed" })
    }

    @Test
    fun injection_targets_the_native_bridge_and_event_envelope() {
        val script = GlomoPayInjectionScripts.main()

        assertTrue(script.contains("window.GlomoPayBridge.postMessage"))
        assertTrue(script.contains("type:'message'"))
        assertTrue(script.contains("window.fetch"))
        assertTrue(script.contains("XMLHttpRequest.prototype.open"))
        assertTrue(script.contains("type:'webview.error'"))
        assertTrue(script.contains("errorType:'js_error'"))
        assertTrue(script.contains("errorType:'unhandled_rejection'"))
    }

    @Test
    fun webview_error_is_tracked_with_its_webview_context() {
        val analytics = RecordingAnalytics()
        val router = GlomoPayEventRouter(
            listener = RecordingListener(),
            devMode = false,
            onComplete = {},
            analytics = analytics,
        )

        router.handleEnvelope(
            mapOf(
                "type" to "webview.error",
                "errorType" to "js_error",
                "message" to "Render failed",
            ),
            webViewType = "flow",
        )

        assertEquals(AnalyticsEvents.WEBVIEW_ERROR, analytics.events.single().name)
        assertEquals("js_error", analytics.events.single().properties["error_type"])
        assertEquals("flow", analytics.events.single().properties["webview_type"])
    }

    @Test
    fun education_step_events_are_tracked_from_bridge_messages() {
        val analytics = RecordingAnalytics()
        val router = GlomoPayEventRouter(
            listener = RecordingListener(),
            devMode = false,
            onComplete = {},
            analytics = analytics,
        )

        router.handleEnvelope(mapOf(
            "type" to "message",
            "data" to mapOf(
                "type" to "lrs.has_education_steps",
                "value" to true,
                "source" to "checkout",
            ),
        ))
        router.handleEnvelope(mapOf(
            "type" to "message",
            "data" to mapOf(
                "type" to "lrs.education_steps_failed",
                "reason" to "render_failed",
            ),
        ))

        assertEquals(
            listOf(AnalyticsEvents.EDUCATION_STEPS_SHOWN, AnalyticsEvents.EDUCATION_STEPS_FAILED),
            analytics.events.map { it.name },
        )
        assertEquals("checkout", analytics.events[0].properties["source"])
        assertEquals("render_failed", analytics.events[1].properties["reason"])
    }

    @Test
    fun flow_injection_uses_a_separate_bridge_and_is_idempotent() {
        val script = GlomoPayInjectionScripts.flow()

        assertTrue(script.contains("window.GlomoPayFlowBridge.postMessage"))
        assertTrue(script.contains("__glomo_GlomoPayFlowBridge_Injected__"))
        assertFalse(script.contains("window.GlomoPayBridge.postMessage"))
        assertTrue(script.contains("if (window[flag]) return;"))
    }

    @Test
    fun window_events_call_ui_callbacks() {
        val listener = RecordingListener()
        val uiEvents = mutableListOf<String>()
        val router = GlomoPayEventRouter(
            listener = listener,
            devMode = false,
            onComplete = {},
            onWindowOpen = { uiEvents += "open:$it" },
            onWindowClose = { uiEvents += "close" },
        )

        router.handleEnvelope(mapOf("type" to "window.open", "url" to "https://bank.example/3ds"))
        router.handleEnvelope(mapOf("type" to "window.close"))

        assertEquals(listOf("open:https://bank.example/3ds", "close"), uiEvents)
    }

    @Test
    fun pending_event_updates_payment_state_without_finishing_checkout() {
        var pending = 0
        var completed = 0
        val router = GlomoPayEventRouter(
            listener = RecordingListener(),
            devMode = false,
            onComplete = { completed++ },
            onPaymentPending = { pending++ },
        )

        router.handleEnvelope(mapOf("type" to "message", "data" to mapOf("type" to "payment.pending")))

        assertEquals(1, pending)
        assertEquals(0, completed)
    }

    @Test
    fun bank_transfer_completes_successfully() {
        var result: GlomoPayResult? = null
        val router = GlomoPayEventRouter(
            listener = RecordingListener(),
            devMode = false,
            onComplete = { result = it },
        )

        router.handleEnvelope(mapOf("type" to "message", "data" to mapOf(
            "type" to "payment.bank_transfer_submitted",
            "payload" to mapOf("orderId" to "order_1"),
        )))
        assertTrue(result is GlomoPayResult.Success)
    }

    @Test
    fun malformed_envelope_reports_an_sdk_error_without_crashing() {
        val listener = RecordingListener()
        val router = GlomoPayEventRouter(listener, devMode = false, onComplete = {})

        router.handle("{not-json")

        assertEquals(1, listener.sdkErrors.size)
    }

    @Test
    fun router_maps_redirect_payment_and_dependency_events_to_analytics() {
        val analytics = RecordingAnalytics()
        val router = GlomoPayEventRouter(
            listener = RecordingListener(),
            devMode = false,
            onComplete = {},
            analytics = analytics,
        )

        router.handleEnvelope(mapOf(
            "type" to "window.open",
            "url" to "https://bank.example/verify?phone=9876543210",
        ))
        router.handleEnvelope(mapOf(
            "type" to "message",
            "data" to mapOf("type" to "payment.pending", "paymentId" to "pay_1"),
        ))
        router.handleEnvelope(mapOf("type" to "dependencies.failed_to_load", "message" to "Unavailable"))

        assertEquals(
            listOf("Redirect Opened", "Payment Pending", "Checkout Dependencies Failed"),
            analytics.events.map { it.name },
        )
        assertEquals("https://bank.example", analytics.events.first().properties["url"])
        assertEquals("main", analytics.events.first().properties["source"])
        assertEquals("pay_1", analytics.events[1].properties["payment_id"])
    }

    @Test
    fun malformed_bridge_message_is_analytics_safe_and_truncated() {
        val analytics = RecordingAnalytics()
        val router = GlomoPayEventRouter(
            listener = RecordingListener(),
            devMode = false,
            onComplete = {},
            analytics = analytics,
        )

        router.handle("{not-json user@example.com")

        assertEquals("Invalid Message Received", analytics.events[0].name)
        assertTrue(analytics.events[0].properties["data"].toString().contains("[REDACTED]"))
        assertEquals("SDK Error", analytics.events[1].name)
    }

    private class RecordingListener : GlomoPayListener {
        val success = mutableListOf<GlomoPayPayload>()
        val failure = mutableListOf<GlomoPayPayload>()
        val termination = mutableListOf<TerminationSource>()
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        val sdkErrors = mutableListOf<List<SdkError>>()

        override fun onPaymentSuccess(payload: GlomoPayPayload) { success += payload }
        override fun onPaymentFailure(payload: GlomoPayPayload) { failure += payload }
        override fun onSdkError(errors: List<SdkError>) { sdkErrors += errors }
        override fun onConnectionError(error: ConnectionError) = Unit
        override fun onPaymentTerminate(source: TerminationSource) { termination += source }
        override fun onEvent(name: String, payload: Map<String, Any?>) { events += name to payload }
    }

    private class RecordingAnalytics : AnalyticsTracker {
        val events = mutableListOf<AnalyticsEvent>()

        override fun track(event: String, properties: Map<String, Any?>) {
            events += AnalyticsEvent(event, properties)
        }

        override fun updateFlowType(flowType: String) = Unit

        override fun updateCheckoutUrl(url: String) = Unit
    }
}
