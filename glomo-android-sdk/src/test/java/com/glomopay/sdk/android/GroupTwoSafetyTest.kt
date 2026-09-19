package com.glomopay.sdk.android

import com.glomopay.sdk.android.bridge.GlomoPayEventRouter
import com.glomopay.sdk.android.state.CheckoutOpenFunnel
import com.glomopay.sdk.android.state.CheckoutOpenStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupTwoSafetyTest {
    @Test fun redirect_callbacks_do_not_rewind_open_funnel_and_timeout_reports_once() {
        val funnel = CheckoutOpenFunnel()
        assertTrue(funnel.advance(CheckoutOpenStep.URL_RESOLVED))
        assertTrue(funnel.advance(CheckoutOpenStep.NAVIGATION_FINISHED))
        assertFalse(funnel.advance(CheckoutOpenStep.NAVIGATION_STARTED))
        assertEquals("navigation_finished", funnel.timeout())
        assertNull(funnel.timeout())
        funnel.advance(CheckoutOpenStep.BRIDGE_READY)
        assertNull(funnel.timeout())
        val retry = CheckoutOpenFunnel()
        assertEquals("webview_created", retry.timeout())
    }

    @Test fun ready_bridge_prevents_timeout() {
        val funnel = CheckoutOpenFunnel()
        funnel.advance(CheckoutOpenStep.BRIDGE_READY)
        assertNull(funnel.timeout())
    }

    @Test fun window_open_is_validated_in_main_and_flow() {
        val opened = mutableListOf<String>()
        val router = GlomoPayEventRouter(null, false, {}, onWindowOpen = { opened += it })
        for (origin in listOf("main", "flow")) {
            for (url in listOf("javascript:alert(1)", "intent://bank", "https://", "https://user:pass@example.test", "https://bank.example bad", "//example.test")) {
                router.handleEnvelope(mapOf("type" to "window.open", "url" to url), origin)
            }
            router.handleEnvelope(mapOf("type" to "window.open", "url" to "https://bank.example/path"), origin)
        }
        assertEquals(listOf("https://bank.example/path", "https://bank.example/path"), opened)
    }

    @Test fun page_events_are_raw_sdk_events_are_namespaced_and_dependencies_are_advisory() {
        val events = mutableListOf<String>()
        var faults = 0
        val listener = object : GlomoPayListener {
            override fun onPaymentSuccess(payload: GlomoPayPayload) = Unit
            override fun onPaymentFailure(payload: GlomoPayPayload) = Unit
            override fun onSdkError(errors: List<SdkError>) { faults++ }
            override fun onUserJourneyCompleted(payload: GlomoPayUserJourneyPayload) = Unit
            override fun onConnectionError(error: ConnectionError) { faults++ }
            override fun onEvent(name: String, payload: Map<String, Any?>) { events += name }
        }
        var pageFailures = 0
        var ready = 0
        val router = GlomoPayEventRouter(listener, false, {}, onBridgeReady = { ready++ }, onDependenciesFailed = { pageFailures++ })
        router.handleEnvelope(mapOf("type" to "bridge.ready"), "flow")
        router.handleEnvelope(mapOf("type" to "bridge.ready"), "main")
        router.handleEnvelope(mapOf("type" to "message", "data" to mapOf("event" to "page.custom")))
        router.handleEnvelope(mapOf("type" to "dependencies.failed_to_load"))
        assertEquals(listOf("page.custom", "glomo_android_sdk.checkout.dependencies_failed"), events)
        assertEquals(0, faults)
        assertEquals(1, pageFailures)
        assertEquals(1, ready)
        router.stop()
        router.handle("not JSON")
        router.handleEnvelope(mapOf("type" to "bridge.ready"))
        assertEquals(0, faults)
        assertEquals(1, ready)
    }
}
