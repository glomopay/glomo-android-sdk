package com.glomopay.sdk.android

import android.app.Activity
import com.glomopay.sdk.android.webview.CheckoutWebViewFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * API 33+ with a WebView provider that predates algorithmic darkening - the combination that
 * satisfies `isFeatureSupported` and still throws from the setter. Robolectric reproduces it: its
 * provider reports no features, so SDK 34 here is the stale-WebView device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CheckoutWebViewFactoryTest {
    private val host = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun a_web_view_provider_without_algorithmic_darkening_does_not_take_checkout_down() {
        // An uncaught setter would propagate out of create() and out of the activity's onCreate,
        // so returning a configured WebView at all is the assertion.
        val webView = CheckoutWebViewFactory.create(host)

        assertTrue(webView.settings.javaScriptEnabled)
    }
}
