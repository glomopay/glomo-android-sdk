package com.glomopay.sdk.android

import android.content.Context
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
        val sessionId = CheckoutSessionRegistry.put(listener)
        val intent = GlomoPayCheckoutActivity.createIntent(context, config, orderType)
            .putExtra(GlomoPayCheckoutActivity.EXTRA_SESSION_ID, sessionId)
        if (context !is android.app.Activity) intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

internal object CheckoutSessionRegistry {
    private val sessions = ConcurrentHashMap<String, GlomoPayListener>()

    fun put(listener: GlomoPayListener): String {
        val id = UUID.randomUUID().toString()
        sessions[id] = listener
        return id
    }

    fun get(id: String?): GlomoPayListener? = id?.let { sessions[it] }

    fun remove(id: String?) {
        if (id != null) sessions.remove(id)
    }
}
