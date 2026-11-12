package com.glomopay.sdk.android

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.view.View
import kotlinx.coroutines.CompletableDeferred
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

    @Test
    fun the_carousel_follows_the_flag_on_an_lrs_order() {
        // Deliberate, and pinned here so it cannot be read as an accident: the carousel is a
        // Glomo page on a Glomo origin, so it is gated exactly like the main WebView. The
        // carousel *host* - the FrameLayout around it - is the surface the ticket keeps light,
        // and this PR does not touch it. iOS #18 gates the carousel WebView the same way.
        val checkout = openWithFlag(
            mapOf("lrs" to mapOf("id" to "lrs_1"), "dark_mode_enabled" to true),
            GlomoColorScheme.DARK,
        )
        checkout.post(mapOf("type" to "window.open", "url" to "https://bank.example/3ds"))

        val carousel = requireNotNull(checkout.carouselWebView) { "carousel was never attached" }
        assertEquals(CheckoutSurfaceColors.DARK_BACKGROUND, shadowOf(carousel).backgroundColor)
    }

    @Test
    fun the_carousel_stays_light_for_a_flag_off_merchant() {
        val checkout = openWithFlag(
            mapOf("lrs" to mapOf("id" to "lrs_1"), "dark_mode_enabled" to false),
            GlomoColorScheme.DARK,
        )
        checkout.post(mapOf("type" to "window.open", "url" to "https://bank.example/3ds"))

        val carousel = requireNotNull(checkout.carouselWebView) { "carousel was never attached" }
        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, shadowOf(carousel).backgroundColor)
    }

    @Test
    fun a_failed_order_fetch_stays_light() {
        // The flag is unknown when the fetch does not answer, and unknown means light. A fetch
        // that fails must not be able to leave a dark surface behind either.
        GlomoPayApiClient.fetchOverride = { throw GlomoPayTransportError(java.io.IOException("offline")) }
        val checkout = RecordedCheckout.open(host, config(GlomoColorScheme.DARK), orderType = "auto")
        checkout.idleUntil { checkout.named(AnalyticsEvents.SDK_API_BACKEND).isNotEmpty() }

        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, webViewColor(checkout))
        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, colorOf(checkout.loadingLabel))
        assertEquals(CheckoutSurfaceColors.LIGHT_LABEL, checkout.loadingLabel.currentTextColor)
    }

    @Test
    fun a_timed_out_order_fetch_stays_light() {
        GlomoPayApiClient.fetchOverride = { throw GlomoPayRequestTimeout(java.net.SocketTimeoutException()) }
        val checkout = RecordedCheckout.open(host, config(GlomoColorScheme.DARK), orderType = "auto")
        checkout.idleUntil { checkout.named(AnalyticsEvents.SDK_API_BACKEND).isNotEmpty() }

        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, webViewColor(checkout))
        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, colorOf(checkout.loadingLabel))
    }

    @Test
    fun the_surface_is_light_until_the_flag_arrives() {
        // The in-flight state, and the reason the CHANGELOG promises a brief light loading screen
        // rather than none: the flag cannot be known before the response is.
        val response = CompletableDeferred<Pair<Int, Map<String, Any?>>>()
        GlomoPayApiClient.fetchOverride = { response.await() }
        val checkout = RecordedCheckout.open(host, config(GlomoColorScheme.DARK), orderType = "auto")

        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, webViewColor(checkout))
        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, colorOf(checkout.loadingLabel))

        response.complete(200 to mapOf("orderType" to "standard", "dark_mode_enabled" to true))
        checkout.idleUntil { webViewColor(checkout) == CheckoutSurfaceColors.DARK_BACKGROUND }

        assertEquals(CheckoutSurfaceColors.DARK_BACKGROUND, webViewColor(checkout))
        assertEquals(CheckoutSurfaceColors.DARK_BACKGROUND, colorOf(checkout.loadingLabel))
    }

    @Test
    fun a_live_scheme_change_repaints_a_flag_on_checkout() {
        // The host changing its theme mid-checkout. `applyColorScheme` is the only path that runs
        // then, so if it does not re-paint, every surface keeps the scheme the checkout opened on.
        val checkout = openWithFlag(
            mapOf("orderType" to "standard", "dark_mode_enabled" to true),
            GlomoColorScheme.LIGHT,
        )
        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, webViewColor(checkout))

        checkout.activity.setAppColorScheme(GlomoColorScheme.DARK)

        assertEquals(CheckoutSurfaceColors.DARK_BACKGROUND, webViewColor(checkout))
        assertEquals(CheckoutSurfaceColors.DARK_BACKGROUND, colorOf(checkout.loadingLabel))
        assertEquals(CheckoutSurfaceColors.DARK_LABEL, checkout.loadingLabel.currentTextColor)
    }

    @Test
    fun a_live_scheme_change_leaves_a_flag_off_checkout_light() {
        // The request still reaches the page - what the SDK paints is the separate decision.
        val checkout = openWithFlag(
            mapOf("orderType" to "standard", "dark_mode_enabled" to false),
            GlomoColorScheme.LIGHT,
        )

        checkout.activity.setAppColorScheme(GlomoColorScheme.DARK)

        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, webViewColor(checkout))
        assertEquals(CheckoutSurfaceColors.LIGHT_BACKGROUND, colorOf(checkout.loadingLabel))
        assertEquals(CheckoutSurfaceColors.LIGHT_LABEL, checkout.loadingLabel.currentTextColor)
    }
}
