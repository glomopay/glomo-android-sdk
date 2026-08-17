package com.glomopay.sdk.android

import com.glomopay.sdk.android.monitoring.IsolatedSentryErrorReporter
import com.glomopay.sdk.android.monitoring.SentryCaptureClient
import com.glomopay.sdk.android.monitoring.createIsolatedSentryOptions
import io.sentry.IScope
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.protocol.Request
import io.sentry.protocol.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IsolatedSentryErrorReporterTest {
    @Test
    fun capture_replaces_sensitive_exception_text_and_limits_context() {
        val capture = RecordingSentryCapture()
        val reporter = IsolatedSentryErrorReporter(
            options = SentryOptions(),
            client = capture,
            sessionId = "session-uuid",
            initialFlowType = "auto",
            devMode = false,
        )
        reporter.addBreadcrumb(
            category = "analytics",
            message = "Payment Failure",
            data = mapOf("event_name" to "Payment Failure", "customer_email" to "user@example.com"),
        )

        reporter.updateFlowType("standard")
        reporter.capture(
            operation = "mixpanel_delivery",
            error = IllegalStateException("customer user@example.com failed"),
            context = mapOf(
                "event_name" to "Payment Failure",
                "customer_email" to "user@example.com",
                "checkout_url" to "https://bank.example/?account=123456789",
            ),
        )

        val event = capture.event ?: error("Expected a Sentry event")
        assertEquals("mixpanel_delivery", event.getTag("operation"))
        assertEquals("standard", event.getTag("flow_type"))
        assertEquals("session-uuid", event.getExtra("session_id"))
        assertEquals("Payment Failure", event.getExtra("event_name"))
        assertFalse(event.extras.orEmpty().containsKey("customer_email"))
        assertFalse(event.extras.orEmpty().containsKey("checkout_url"))
        assertFalse(event.throwable?.message.orEmpty().contains("user@example.com"))
        assertEquals(1, event.breadcrumbs?.size)
    }

    @Test
    fun isolated_options_disable_global_and_pii_features() {
        val options = createIsolatedSentryOptions(
            dsn = "https://public@example.invalid/1",
            sdkVersion = "1.0.0",
        )

        assertFalse(options.isSendDefaultPii)
        assertFalse(options.isEnableExternalConfiguration)
        assertFalse(options.isEnableUncaughtExceptionHandler)
        assertFalse(options.isEnableShutdownHook)
        assertFalse(options.isEnableAutoSessionTracking)
        assertFalse(options.isAttachThreads)
        assertEquals(0.0, options.tracesSampleRate)
        assertEquals(0.0, options.profilesSampleRate)
        assertEquals("12345678-1234-1234-1234-123456789abc", options.proguardUuid)

        val event = SentryEvent().apply {
            user = User().apply { email = "user@example.com" }
            request = Request().apply { url = "https://merchant.example/private" }
            serverName = "merchant-server"
        }
        val filtered = options.beforeSend?.execute(event, io.sentry.Hint())
        assertNull(filtered?.user)
        assertNull(filtered?.request)
        assertNull(filtered?.serverName)
    }

    private class RecordingSentryCapture : SentryCaptureClient {
        var event: SentryEvent? = null
        var scope: IScope? = null

        override fun capture(event: SentryEvent, scope: IScope) {
            this.event = event
            this.scope = scope
        }
    }
}
