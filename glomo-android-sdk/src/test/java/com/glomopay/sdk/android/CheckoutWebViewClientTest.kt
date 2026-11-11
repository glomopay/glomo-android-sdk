package com.glomopay.sdk.android

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import com.glomopay.sdk.android.webview.CheckoutWebViewClient
import com.glomopay.sdk.android.webview.MainNavigationPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CheckoutWebViewClientTest {
    private val trusted = setOf("https://checkout.glomopay.com")

    private fun request(url: String, isForMainFrame: Boolean) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame(): Boolean = isForMainFrame
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = true
        override fun getMethod(): String = "GET"
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }

    private fun client(blocked: MutableList<String>) = CheckoutWebViewClient(
        onPageStartedCallback = {},
        onPageFinishedCallback = {},
        onUrlChangedCallback = {},
        onErrorCallback = {},
        onNavigationBlocked = { blocked += it },
        navigationPolicy = MainNavigationPolicy(trusted)::allows,
    )

    private fun webView(): WebView = WebView(RuntimeEnvironment.getApplication())

    @Test
    fun a_top_level_navigation_off_a_glomo_origin_is_blocked() {
        val blocked = mutableListOf<String>()

        val overridden = client(blocked)
            .shouldOverrideUrlLoading(webView(), request("https://netbanking.bank.example/2fa", true))

        assertTrue(overridden)
        assertEquals(listOf("https://netbanking.bank.example/2fa"), blocked)
    }

    /**
     * The checkout page embeds third-party frames - the card fields, the server-to-server frame,
     * 3-D Secure - and `shouldOverrideUrlLoading` fires for those too. Blocking them stops the
     * page working rather than securing it; what the policy is about is where the document goes.
     */
    @Test
    fun a_subframe_on_a_third_party_host_still_loads() {
        val blocked = mutableListOf<String>()

        val overridden = client(blocked)
            .shouldOverrideUrlLoading(webView(), request("https://js.skyflowapi.example/field", false))

        assertFalse(overridden)
        assertTrue(blocked.isEmpty())
    }

    @Test
    fun a_glomo_domain_the_config_never_named_still_navigates() {
        val blocked = mutableListOf<String>()

        val overridden = client(blocked)
            .shouldOverrideUrlLoading(webView(), request("https://checkout.glomo.one/pay", true))

        assertFalse(overridden)
        assertTrue(blocked.isEmpty())
    }
}
