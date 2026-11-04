package com.glomopay.sdk.android

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.view.View
import com.glomopay.sdk.android.analytics.AnalyticsEvents
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What the checkout paints before the page's first paint.
 *
 * The SDK knows the scheme the host asked for the moment it starts, but not whether the merchant
 * will have it honoured - the page stays light for a merchant whose `dark_mode_enabled` flag is
 * off, whatever the URL said. Painting from the request alone therefore flashes dark in front of a
 * light page. These cover the gate, both ways, through the real order-fetch path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreloadBackgroundTest {
    private val host = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun config(scheme: GlomoColorScheme) = GlomoPayConfig(
        publicKey = "test_public_key",
        orderId = "order_123",
        appColorScheme = scheme,
    )

    @AfterTest
    fun tearDown() {
        RecordedCheckout.reset()
    }

    /** Robolectric's WebView shadow records the colour instead of building a drawable for it. */
    private fun webViewColor(checkout: RecordedCheckout): Int = shadowOf(checkout.webView).backgroundColor

    private fun colorOf(view: View): Int = (view.background as ColorDrawable).color

    /** Opens on the `auto` path so the flag arrives the way it does in production. */
    private fun openWithFlag(order: Map<String, Any?>, scheme: GlomoColorScheme): RecordedCheckout {
        GlomoPayApiClient.fetchOverride = { 200 to order }
        val checkout = RecordedCheckout.open(host, config(scheme), orderType = "auto")
        checkout.idleUntil { checkout.named(AnalyticsEvents.SDK_API_BACKEND).isNotEmpty() }
        return checkout
    }

    @Test
    fun the_flag_on_and_dark_requested_paints_dark() {
        val checkout = openWithFlag(
            mapOf("orderType" to "standard", "dark_mode_enabled" to true),
            GlomoColorScheme.DARK,
        )

        assertEquals(CheckoutSurfaceColors.DARK_BACKGROUND, webViewColor(checkout))
        assertEquals(CheckoutSurfaceColors.DARK_BACKGROUND, colorOf(checkout.loadingLabel))
        assertEquals(CheckoutSurfaceColors.DARK_LABEL, checkout.loadingLabel.currentTextColor)
    }

    @Test
    fun the_flag_on_and_light_requested_stays_light() {
        val checkout = openWithFlag(
            mapOf("orderType" to "standard", "dark_mode_enabled" to true),
            GlomoColorScheme.LIGHT,
        )

        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, webViewColor(checkout))
        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, colorOf(checkout.loadingLabel))
    }

    @Test
    fun the_flag_off_and_dark_requested_stays_light() {
        // The regression this ticket exists for. Without the gate this paints dark in front of a
        // page that renders light, for every merchant who has not switched dark mode on.
        val checkout = openWithFlag(
            mapOf("orderType" to "standard", "dark_mode_enabled" to false),
            GlomoColorScheme.DARK,
        )

        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, webViewColor(checkout))
        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, colorOf(checkout.loadingLabel))
        assertEquals(CheckoutSurfaceColors.LIGHT_LABEL, checkout.loadingLabel.currentTextColor)
    }

    @Test
    fun a_response_carrying_no_flag_stays_light() {
        // What an older or proxied response looks like. Absent is not permission.
        val checkout = openWithFlag(mapOf("orderType" to "standard"), GlomoColorScheme.DARK)

        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, webViewColor(checkout))
    }

    @Test
    fun a_path_that_never_fetches_stays_light() {
        // An explicit order type skips the order fetch entirely, so the flag is never known.
        // Light is the correct answer, and this pins it: those merchants keep the flash until the
        // fetch is reworked, which is deliberately not part of this change.
        val checkout = RecordedCheckout.open(host, config(GlomoColorScheme.DARK), orderType = "lrs")

        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, webViewColor(checkout))
        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, colorOf(checkout.loadingLabel))
    }
}
