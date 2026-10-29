package com.glomopay.sdk.android

import android.app.Activity
import com.glomopay.sdk.android.analytics.AnalyticsFactory
import com.glomopay.sdk.android.analytics.NoOpAnalyticsTracker
import com.glomopay.sdk.android.monitoring.SdkErrorReporter
import com.glomopay.sdk.android.monitoring.SdkErrorReporterFactory
import com.glomopay.sdk.android.ui.GlomoPayCheckoutActivity
import com.glomopay.sdk.android.webview.CheckoutWebViewFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val DARKENING_UNSUPPORTED = "webview_algorithmic_darkening_unsupported"

private class RecordingErrorReporter : SdkErrorReporter {
    val captures = mutableListOf<Pair<String, Map<String, Any?>>>()

    override fun addBreadcrumb(category: String, message: String, data: Map<String, Any?>) = Unit

    override fun capture(operation: String, error: Throwable, context: Map<String, Any?>) {
        captures += operation to context
    }

    override fun updateFlowType(flowType: String) = Unit
}

/**
 * API 33+ with a WebView provider that predates algorithmic darkening - the combination that
 * satisfies `isFeatureSupported` and still throws from the setter. Robolectric reproduces it: its
 * provider reports no features, so SDK 34 here is the stale-WebView device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CheckoutWebViewFactoryTest {
    private val host = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val config = GlomoPayConfig(
        publicKey = "test_public_key",
        orderId = "order_123",
        appColorScheme = GlomoColorScheme.LIGHT,
    )

    @AfterTest
    fun tearDown() {
        AnalyticsFactory.trackerFactory = null
        SdkErrorReporterFactory.reporterFactory = null
    }

    @Test
    fun a_web_view_provider_without_algorithmic_darkening_does_not_take_checkout_down() {
        // An uncaught setter would propagate out of create() and out of the activity's onCreate,
        // so returning a configured WebView at all is the assertion.
        val webView = CheckoutWebViewFactory.create(host)

        assertTrue(webView.settings.javaScriptEnabled)
    }

    @Test
    fun the_rejection_carries_the_web_view_that_hit_it() {
        var reported: UnsupportedOperationException? = null

        CheckoutWebViewFactory.create(host) { reported = it }

        assertTrue(reported is UnsupportedOperationException)
    }

    @Test
    fun a_rejected_setting_is_reported_once_per_checkout_not_once_per_web_view() {
        val reporter = RecordingErrorReporter()
        SdkErrorReporterFactory.reporterFactory = { _, _ -> reporter }
        AnalyticsFactory.trackerFactory = { _, _ -> NoOpAnalyticsTracker }

        GlomoPaySdk.startCheckout(host, config, NoOpListener, orderType = "lrs")
        val intent = requireNotNull(shadowOf(host).nextStartedActivity)
        Robolectric.buildActivity(GlomoPayCheckoutActivity::class.java, intent).setup()

        // An lrs checkout builds the main WebView and the education carousel, and both hit the
        // rejection, so this pins the dedupe rather than counting to one by accident. Dropping
        // the guard in the activity makes it two.
        val reports = reporter.captures.filter { it.first == DARKENING_UNSUPPORTED }
        assertEquals(1, reports.size)
        assertEquals("main", reports.single().second["webview_type"])
    }

    private object NoOpListener : GlomoPayListener {
        override fun onPaymentSuccess(payload: GlomoPayPayload) = Unit
        override fun onPaymentFailure(payload: GlomoPayPayload) = Unit
        override fun onSdkError(errors: List<SdkError>) = Unit
        override fun onConnectionError(error: ConnectionError) = Unit
        override fun onUserJourneyCompleted(payload: GlomoPayUserJourneyPayload) = Unit
    }
}
