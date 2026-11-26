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
     * The customer reached a new checkout stage, or a new step within one.
     *
     * LRS only, and only when checkout progress events are enabled for the merchant. `stage` and
     * the other string fields are open: new values can arrive without a version bump.
     */
    public fun onCheckoutStageChanged(event: GlomoCheckoutStageChangedEvent): Unit = Unit

    /**
     * The checkout closed, carrying the last stage the page reported and why it ended.
     *
     * At most once per checkout, under the same conditions as [onCheckoutStageChanged]. No further
     * progress events follow it - no stage changes, and no second close. Not guaranteed: a
     * checkout closed from host code before any stage arrived sends none.
     *
     * Not a payment outcome. After a dismissal the payment can still complete on the server, so
     * read that from the payment status API or your webhook; `completed` means the customer
     * finished what the checkout asked of them, not that the payment settled.
     *
     * On a close the page does not send, the SDK writes this event itself once it has seen a stage
     * it recognises. Where [onPaymentTerminate] also fires, it comes first - it does not fire once
     * a payment has reported success or failure.
     */
    public fun onCheckoutClosed(event: GlomoCheckoutClosedEvent): Unit = Unit

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
