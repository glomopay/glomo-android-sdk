package com.glomopay.sdk.android

public interface GlomoPayListener {
    public fun onPaymentSuccess(payload: GlomoPayPayload)

    public fun onPaymentFailure(payload: GlomoPayPayload)

    public fun onSdkError(errors: List<SdkError>)

    public fun onConnectionError(error: ConnectionError)

    public fun onPaymentTerminate(source: TerminationSource): Unit = Unit

    public fun onEvent(name: String, payload: Map<String, Any?>): Unit = Unit
}
