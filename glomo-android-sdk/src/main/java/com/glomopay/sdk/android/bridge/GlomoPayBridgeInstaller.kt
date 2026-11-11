package com.glomopay.sdk.android.bridge

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Who sent a bridge message, as the WebView attributes it.
 *
 * [origin] is null when the sender could not be attributed, which is never trusted.
 */
internal data class BridgeMessageSender(
    val origin: String?,
    val isMainFrame: Boolean,
)

/**
 * Installs a bridge on a WebView, preferring the framework's own frame attribution.
 *
 * [WebViewCompat.addWebMessageListener] reports the sending frame's origin and whether it is the
 * main frame, which `addJavascriptInterface` cannot - that one is exposed to every frame with no
 * way to tell them apart.
 *
 * **`allowedOriginRules` is `"*"` on purpose, and is not the gate.** Narrowing it to GlomoPay
 * hosts would leave `window.<name>` undefined on a bank page, and `GlomoPayInjectionScripts.build`
 * routes every message type through that one object - so `window.open`, `window.close` and
 * `console` would silently stop working there. A bank calling `window.close()` is how the overlay
 * dismisses after a 3-D Secure challenge. The channel stays open to everyone; the router decides
 * what each sender is allowed to say.
 */
internal object GlomoPayBridgeInstaller {

    /** Whether the WebView provider on this device can attribute a message to its frame. */
    fun supportsFrameAttribution(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    /**
     * Returns true when the listener was installed with frame attribution.
     *
     * When it returns false the caller has installed nothing and must fall back to
     * `addJavascriptInterface`, where the best available answer is the WebView's own URL - the
     * top-level document's, which says nothing about a subframe.
     */
    fun install(
        webView: WebView,
        name: String,
        onMessage: (String, BridgeMessageSender) -> Unit,
    ): Boolean {
        if (!supportsFrameAttribution()) return false
        WebViewCompat.addWebMessageListener(
            webView,
            name,
            setOf("*"),
        ) { _, message, sourceOrigin, isMainFrame, _ ->
            val raw = message.data ?: return@addWebMessageListener
            onMessage(raw, BridgeMessageSender(originOf(sourceOrigin), isMainFrame))
        }
        return true
    }

    /**
     * `sourceOrigin` arrives as a `Uri` with no path. `Uri.getPort()` is -1 when absent, and the
     * scheme's default port is dropped so both spellings of one origin compare equal.
     */
    private fun originOf(sourceOrigin: Uri?): String? {
        val scheme = sourceOrigin?.scheme?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = sourceOrigin.host?.lowercase() ?: return null
        val defaultPort = if (scheme == "https") 443 else 80
        val port = sourceOrigin.port.takeIf { it >= 0 && it != defaultPort }?.let { ":$it" } ?: ""
        return "$scheme://$host$port"
    }
}
