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
    private var reachedStep: CheckoutOpenStep? = null
    private var timeoutReported = false

    /** The reporting default before any open-funnel event has fired. */
    val lastStep: CheckoutOpenStep
        get() = reachedStep ?: CheckoutOpenStep.WEB_VIEW_CREATED

    fun advance(step: CheckoutOpenStep): Boolean {
        reachedStep?.let { if (step.ordinal <= it.ordinal) return false }
        reachedStep = step
        return true
    }

    fun timeout(): String? {
        if (timeoutReported || lastStep == CheckoutOpenStep.BRIDGE_READY) return null
        timeoutReported = true
        return lastStep.value
    }
}
