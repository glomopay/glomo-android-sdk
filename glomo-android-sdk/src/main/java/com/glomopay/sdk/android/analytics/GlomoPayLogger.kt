package com.glomopay.sdk.android.analytics

import android.util.Log

/** SDK logger matching Flutter's devMode logging gate. */
internal object GlomoPayLogger {
    private const val TAG = "GlomoPay"
    @Volatile var devMode: Boolean = false

    fun log(message: String) {
        if (devMode) Log.d(TAG, message)
    }

    fun info(message: String) {
        if (devMode) Log.i(TAG, message)
    }

    fun error(message: String, error: Throwable? = null) {
        if (devMode) Log.e(TAG, message, error)
    }
}
