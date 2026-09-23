package com.glomopay.sdk.android.state

internal enum class CheckoutOpenStep(val value: String) {
    WEB_VIEW_CREATED("webview_created"),
    URL_RESOLVED("url_resolved"),
    NAVIGATION_STARTED("navigation_started"),
    NAVIGATION_FINISHED("navigation_finished"),
    BRIDGE_READY("bridge_ready"),
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
