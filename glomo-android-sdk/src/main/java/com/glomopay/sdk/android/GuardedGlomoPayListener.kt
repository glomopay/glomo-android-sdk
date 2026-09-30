package com.glomopay.sdk.android

import com.glomopay.sdk.android.monitoring.NoOpSdkErrorReporter
import com.glomopay.sdk.android.monitoring.SdkErrorReporter

/** Merchant exceptions must never interrupt SDK cleanup or trigger another callback. */
internal class GuardedGlomoPayListener(
    private val delegate: GlomoPayListener,
    private val errorReporter: SdkErrorReporter = NoOpSdkErrorReporter,
) : GlomoPayListener {
    private inline fun deliver(callback: String, body: () -> Unit) {
        try { body() } catch (error: Throwable) { report(callback, error) }
    }

    /**
     * The exception type and stack are reported; its message is never read, logged
     * or sent, because merchant exception messages routinely carry customer data.
     */
    private fun report(callback: String, error: Throwable) {
        // Reporting must never throw back into the SDK path it is protecting.
        val type = error.javaClass.name
        runCatching {
            val frames = error.stackTrace.take(MAX_FRAMES).joinToString("\n") { "\tat $it" }
            android.util.Log.w(TAG, "Merchant $callback threw $type\n$frames")
        }
        runCatching {
            val sanitized = IllegalStateException("Merchant $callback threw $type").apply {
                stackTrace = error.stackTrace
            }
            errorReporter.capture(
                operation = "merchant_callback",
                error = sanitized,
                context = mapOf("callback" to callback, "exception_type" to type),
            )
        }
    }

    override fun onPaymentSuccess(payload: GlomoPayPayload) =
        deliver("onPaymentSuccess") { delegate.onPaymentSuccess(payload) }

    override fun onPaymentFailure(payload: GlomoPayPayload) =
        deliver("onPaymentFailure") { delegate.onPaymentFailure(payload) }

    override fun onSdkError(errors: List<SdkError>) =
        deliver("onSdkError") { delegate.onSdkError(errors) }

    override fun onUserJourneyCompleted(payload: GlomoPayUserJourneyPayload) =
        deliver("onUserJourneyCompleted") { delegate.onUserJourneyCompleted(payload) }

    override fun onConnectionError(error: ConnectionError) =
        deliver("onConnectionError") { delegate.onConnectionError(error) }

    override fun onPaymentTerminate(source: TerminationSource) =
        deliver("onPaymentTerminate") { delegate.onPaymentTerminate(source) }

    override fun onUserRefusedDevicePermissions(permission: String) =
        deliver("onUserRefusedDevicePermissions") { delegate.onUserRefusedDevicePermissions(permission) }

    @Suppress("DEPRECATION")
    override fun onEvent(name: String, payload: Map<String, Any?>) =
        deliver("onEvent") { delegate.onEvent(name, payload) }

    private companion object {
        const val TAG = "GlomoPay"
        const val MAX_FRAMES = 5
    }
}
