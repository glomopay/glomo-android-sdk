package com.glomopay.sdk.android

import android.content.Context
import com.glomopay.sdk.android.analytics.AnalyticsEvents
import com.glomopay.sdk.android.analytics.AnalyticsFactory
import com.glomopay.sdk.android.analytics.AnalyticsTracker
import com.glomopay.sdk.android.monitoring.SdkErrorReporter
import com.glomopay.sdk.android.monitoring.SdkErrorReporterFactory
import com.glomopay.sdk.android.ui.GlomoPayCheckoutActivity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Public entry point for starting the native checkout. */
public object GlomoPaySdk {
    public fun startCheckout(
        context: Context,
        config: GlomoPayConfig,
        listener: GlomoPayListener,
        orderType: String = "auto",
    ): Unit {
        val sessionId = UUID.randomUUID().toString()
        val errorReporter = SdkErrorReporterFactory.create(
            context.applicationContext,
            config,
            sessionId,
            orderType,
        )
        val analytics = AnalyticsFactory.create(
            context.applicationContext,
            config,
            sessionId,
            orderType,
            errorReporter,
        )
        CheckoutSessionRegistry.put(sessionId, listener, analytics, errorReporter)
        analytics.track(AnalyticsEvents.SDK_INITIALIZED)
        val intent = GlomoPayCheckoutActivity.createIntent(context, config, orderType)
            .putExtra(GlomoPayCheckoutActivity.EXTRA_SESSION_ID, sessionId)
        if (context !is android.app.Activity) intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

internal data class CheckoutSession(
    val listener: GlomoPayListener,
    val analytics: AnalyticsTracker,
    val errorReporter: SdkErrorReporter,
)

internal object CheckoutSessionRegistry {
    private val sessions = ConcurrentHashMap<String, CheckoutSession>()

    fun put(
        id: String,
        listener: GlomoPayListener,
        analytics: AnalyticsTracker,
        errorReporter: SdkErrorReporter,
    ) {
        sessions[id] = CheckoutSession(listener, analytics, errorReporter)
    }

    fun get(id: String?): CheckoutSession? = id?.let { sessions[it] }

    fun remove(id: String?) {
        if (id != null) sessions.remove(id)
    }
}
