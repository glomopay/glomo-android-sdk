package com.glomopay.sdk.android

import com.glomopay.sdk.android.bridge.GlomoPayEventRouter
import com.glomopay.sdk.android.webview.FlowNavigationPolicy
import com.glomopay.sdk.android.ui.ImageCapturePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupOneSafetyTest {
    @Test fun flow_blocks_external_intents_and_script_schemes() {
        listOf("upi://pay", "intent://bank", "javascript:alert(1)", "file:///tmp/a", "content://a", "tel:123", "relative").forEach {
            assertFalse(FlowNavigationPolicy.allows(it), it)
        }
        listOf("https://example.test", "HTTP://example.test", "about:blank", "blob:https://example.test/id", "data:text/html,test").forEach {
            assertTrue(FlowNavigationPolicy.allows(it), it)
        }
    }

    @Test fun capture_is_only_automatic_for_image_compatible_accept_types() {
        assertTrue(ImageCapturePolicy.acceptsImages(listOf("image/*")))
        assertFalse(ImageCapturePolicy.acceptsImages(listOf("application/pdf, .jpg")))
        assertFalse(ImageCapturePolicy.acceptsImages(listOf("application/pdf")))
        assertFalse(ImageCapturePolicy.acceptsImages(emptyList()))
    }

    @Test fun throwing_merchant_does_not_interrupt_any_callback_or_checkout_completion() {
        var attempts = 0
        val merchant = object : GlomoPayListener {
            fun throws(): Nothing { attempts++; throw IllegalStateException("synthetic callback failure") }
            override fun onPaymentSuccess(payload: GlomoPayPayload) = throws()
            override fun onPaymentFailure(payload: GlomoPayPayload) = throws()
            override fun onSdkError(errors: List<SdkError>) = throws()
            override fun onUserJourneyCompleted(payload: GlomoPayUserJourneyPayload) = throws()
            override fun onConnectionError(error: ConnectionError) = throws()
            override fun onPaymentTerminate(source: TerminationSource) = throws()
            override fun onUserRefusedDevicePermissions(permission: String) = throws()
            override fun onEvent(name: String, payload: Map<String, Any?>) = throws()
        }
        val guarded = GuardedGlomoPayListener(merchant)
        guarded.onPaymentSuccess(GlomoPayPayload("order_test"))
        guarded.onPaymentFailure(GlomoPayPayload("order_test"))
        guarded.onSdkError(emptyList())
        guarded.onConnectionError(ConnectionError(ConnectionErrorType.TIMEOUT, "timeout"))
        guarded.onPaymentTerminate(TerminationSource.BACK_BUTTON)
        guarded.onUserRefusedDevicePermissions("android.permission.CAMERA")
        guarded.onUserJourneyCompleted(
            GlomoPayUserJourneyPayload(GlomoPayUserJourneyType.BANK_TRANSFER, "order_test"),
        )
        guarded.onEvent("test", emptyMap())
        assertEquals(8, attempts)
        var completions = 0
        val router = GlomoPayEventRouter(merchant, false, { completions++ })
        val event = mapOf("type" to "message", "data" to mapOf(
            "type" to "payment.success", "orderId" to "order_test", "paymentId" to "pay_test", "signature" to "synthetic",
        ))
        router.handleEnvelope(event)
        router.handleEnvelope(event)
        assertEquals(1, completions)
    }

    @Test fun merchant_failure_is_reported_by_type_without_its_message() {
        val captured = mutableListOf<Pair<Throwable, Map<String, Any?>>>()
        val reporter = object : com.glomopay.sdk.android.monitoring.SdkErrorReporter {
            override fun addBreadcrumb(category: String, message: String, data: Map<String, Any?>) = Unit
            override fun capture(operation: String, error: Throwable, context: Map<String, Any?>) {
                captured += error to (context + ("operation" to operation))
            }
            override fun updateFlowType(flowType: String) = Unit
        }
        val merchant = object : GlomoPayListener {
            override fun onPaymentSuccess(payload: GlomoPayPayload) =
                throw IllegalArgumentException("order for user@example.com failed")
            override fun onPaymentFailure(payload: GlomoPayPayload) = Unit
            override fun onSdkError(errors: List<SdkError>) = Unit
            override fun onUserJourneyCompleted(payload: GlomoPayUserJourneyPayload) = Unit
            override fun onConnectionError(error: ConnectionError) = Unit
        }

        GuardedGlomoPayListener(merchant, reporter).onPaymentSuccess(GlomoPayPayload("order_test"))

        assertEquals(1, captured.size)
        val (error, context) = captured.single()
        assertEquals("merchant_callback", context["operation"])
        assertEquals("onPaymentSuccess", context["callback"])
        assertEquals("java.lang.IllegalArgumentException", context["exception_type"])
        // The merchant's own message routinely carries customer data and must not travel.
        assertFalse(error.message.orEmpty().contains("user@example.com"))
        assertTrue(error.stackTrace.isNotEmpty())
    }
}
