package com.glomopay.sdk.bridge

import com.glomopay.sdk.GlomoPayPayload
import com.glomopay.sdk.GlomoPayResult
import com.glomopay.sdk.GlomoPayListener
import com.glomopay.sdk.SdkError
import com.glomopay.sdk.SdkErrorType
import com.glomopay.sdk.TerminationSource
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

internal class GlomoPayEventRouter(
    private val listener: GlomoPayListener?,
    private val devMode: Boolean,
    private val onComplete: (GlomoPayResult) -> Unit,
    private val onWindowOpen: (String) -> Unit = {},
    private val onWindowClose: () -> Unit = {},
    private val onPaymentPending: () -> Unit = {},
) {
    private val terminalDelivered = AtomicBoolean(false)

    fun handle(rawMessage: String) {
        try {
            val envelope = JSONObject(rawMessage).toMap()
            handleEnvelope(envelope)
        } catch (error: Exception) {
            listener?.onSdkError(listOf(SdkError(SdkErrorType.UNKNOWN, error.message ?: "Unable to parse WebView event")))
        }
    }

    fun handleEnvelope(envelope: Map<String, Any?>) {
        try {
            when (envelope["type"]?.toString()) {
                "console" -> handleConsole(envelope)
                "window.open" -> {
                    val url = envelope["url"] as? String ?: return
                    onWindowOpen(url)
                    emit("redirect.started", mapOf("url" to url))
                }
                "window.close" -> {
                    onWindowClose()
                    emit("redirect.completed", emptyMap())
                }
                "message" -> handlePaymentEvent(envelope["data"] as? Map<*, *>)
                "dependencies.failed_to_load" -> emitDependencyError(envelope["message"]?.toString())
                "file.input" -> emit("file.requested", envelope)
            }
        } catch (error: Exception) {
            listener?.onSdkError(listOf(SdkError(SdkErrorType.UNKNOWN, error.message ?: "Unable to parse WebView event")))
        }
    }

    private fun handleConsole(envelope: Map<String, Any?>) {
        if (!devMode) return
        val level = envelope["level"]?.toString() ?: "log"
        val message = envelope["message"]?.toString() ?: ""
        android.util.Log.println(android.util.Log.DEBUG, "GlomoPayJS/$level", message)
        if (level == "error" && (message.startsWith("ApiError:") || message.contains("LRS information not found"))) {
            emitDependencyError(message, "js_console_error")
        }
    }

    private fun handlePaymentEvent(rawData: Map<*, *>?) {
        if (rawData == null) return
        val data = rawData.entries.associate { it.key.toString() to it.value }
        val eventName = (data["type"] as? String)?.takeIf { it.isNotEmpty() }
            ?: data["event"]?.toString()
            ?: data["status"]?.toString()
        if (eventName != null) emit(eventName, data)

        val payloadData = ((data["payload"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value })
            ?.let { data + it } ?: data

        when (eventName) {
            "payment.success", "success" -> {
                val payload = GlomoPayPayload.fromMap(payloadData)
                if (isValidPaymentPayload(payload)) completeSuccess(payload)
            }
            "payment.bank_transfer_submitted" -> {
                val payload = GlomoPayPayload.fromMap(payloadData)
                if (payload.orderId.isNotEmpty()) {
                    completeSuccess(payload)
                }
            }
            "payment.failure", "payment.failed", "failed", "payment.error" -> {
                val payload = GlomoPayPayload.fromMap(payloadData)
                if (isValidPaymentPayload(payload)) completeFailure(payload)
            }
            "payment.pending", "pending" -> onPaymentPending()
            "payment.cancelled", "cancelled" -> completeCancelled(TerminationSource.USER_DISMISS)
            "checkout.closed" -> completeCancelled(TerminationSource.USER_DISMISS)
            "dependencies.failed_to_load" -> emitDependencyError(data["message"]?.toString(), "postMessage")
        }
    }

    private fun completeSuccess(payload: GlomoPayPayload) {
        if (!terminalDelivered.compareAndSet(false, true)) return
        listener?.onPaymentSuccess(payload)
        onComplete(GlomoPayResult.Success(payload.toMap()))
    }

    private fun completeFailure(payload: GlomoPayPayload) {
        if (!terminalDelivered.compareAndSet(false, true)) return
        listener?.onPaymentFailure(payload)
        onComplete(GlomoPayResult.Failure("Payment failed"))
    }

    private fun completeCancelled(source: TerminationSource) {
        if (!terminalDelivered.compareAndSet(false, true)) return
        listener?.onPaymentTerminate(source)
        onComplete(GlomoPayResult.Cancelled)
    }

    private fun emit(name: String, data: Map<String, Any?>) {
        listener?.onEvent(name, data)
    }

    private fun emitDependencyError(message: String?, source: String = "bridge") {
        emit("checkout.dependencies_failed", mapOf(
            "message" to (message ?: "Checkout dependencies failed to load"),
            "source" to source,
        ))
    }

    private fun isValidPaymentPayload(payload: GlomoPayPayload): Boolean =
        payload.orderId.isNotEmpty() && !payload.paymentId.isNullOrEmpty() && !payload.signature.isNullOrEmpty()

    private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key ->
        when (val value = get(key)) {
            JSONObject.NULL -> null
            is JSONObject -> value.toMap()
            is JSONArray -> value.toList()
            else -> value
        }
    }

    private fun JSONArray.toList(): List<Any?> = (0 until length()).map { index ->
        when (val value = get(index)) {
            JSONObject.NULL -> null
            is JSONObject -> value.toMap()
            is JSONArray -> value.toList()
            else -> value
        }
    }
}
