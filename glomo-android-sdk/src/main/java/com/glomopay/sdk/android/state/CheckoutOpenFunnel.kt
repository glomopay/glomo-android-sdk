package com.glomopay.sdk.android.state

internal enum class CheckoutOpenStep(val value: String, val event: String) {
    WEB_VIEW_CREATED("webview_created", "Checkout WebView Created"),
    URL_RESOLVED("url_resolved", "Checkout URL Resolved"),
    NAVIGATION_STARTED("navigation_started", "Checkout Navigation Started"),
    NAVIGATION_FINISHED("navigation_finished", "Checkout Navigation Finished"),
    BRIDGE_READY("bridge_ready", "Checkout Bridge Ready"),
}

/** Tracks one open attempt; redirect callbacks cannot move the funnel backwards. */
internal class CheckoutOpenFunnel {
    var lastStep: CheckoutOpenStep = CheckoutOpenStep.WEB_VIEW_CREATED
        private set
    private var timeoutReported = false

    fun advance(step: CheckoutOpenStep): Boolean {
        if (step.ordinal <= lastStep.ordinal) return false
        lastStep = step
        return true
    }

    fun timeout(): String? {
        if (timeoutReported || lastStep == CheckoutOpenStep.BRIDGE_READY) return null
        timeoutReported = true
        return lastStep.value
    }
}
