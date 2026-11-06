package com.glomopay.sdk.android.webview

import com.glomopay.sdk.android.ConfigManager
import java.util.Locale

internal object FlowNavigationPolicy {
    fun allows(url: String): Boolean =
        url.substringBefore(':', "").lowercase(Locale.ROOT) in setOf("http", "https", "about", "blob", "data")
}

/**
 * Top-level navigation policy for the main checkout WebView.
 *
 * The main WebView hosts `GlomoPayBridge`, so any document it lands on can speak to the SDK. It
 * only ever loads GlomoPay's own checkout page, which is an assumption worth enforcing rather than
 * stating - the flow WebView is where a bank gets to decide where to go next, and it has its own
 * policy above.
 *
 * `about:` and `blob:` stay allowed: those are the WebView's own intermediate documents, not a
 * navigation anyone chose, and blocking them breaks a load rather than securing one.
 */
internal class MainNavigationPolicy(private val trustedOrigins: Set<String>) {
    fun allows(url: String): Boolean {
        val scheme = url.substringBefore(':', "").lowercase(Locale.ROOT)
        if (scheme == "about" || scheme == "blob") return true
        return ConfigManager.isTrustedPageOrigin(url, trustedOrigins)
    }
}
