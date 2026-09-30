package com.glomopay.sdk.android

public interface GlomoPayListener {
    public fun onPaymentSuccess(payload: GlomoPayPayload)

    public fun onPaymentFailure(payload: GlomoPayPayload)

    public fun onSdkError(errors: List<SdkError>)

    public fun onConnectionError(error: ConnectionError)

    /**
     * A non-payment journey completed, such as submitted bank-transfer details.
     *
     * Required on purpose, with no default implementation. The SDK cannot tell which
     * merchants have bank transfers enabled - the order decides that, server side - so
     * a default body would let a host upgrade, keep compiling, and silently stop hearing
     * about a journey it used to be told about through [onPaymentSuccess].
     *
     * Nothing here is verifiable as a payment: no money has moved.
     */
    public fun onUserJourneyCompleted(payload: GlomoPayUserJourneyPayload)

    public fun onPaymentTerminate(source: TerminationSource): Unit = Unit

    /**
     * A device permission the checkout asked for was refused by the user.
     * The checkout stays open; the bank's upload field is simply not satisfied.
     * [permission] is an android.Manifest.permission constant.
     */
    public fun onUserRefusedDevicePermissions(permission: String): Unit = Unit

    /** Page events are forwarded verbatim; SDK events use the glomo_android_sdk. prefix. */
    @Deprecated("Diagnostic events are not part of the integration contract and may be removed in a future major release.")
    public fun onEvent(name: String, payload: Map<String, Any?>): Unit = Unit
}
