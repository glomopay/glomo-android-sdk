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
     * LRS checkouts only, and only once checkout progress events are switched on for the merchant.
     * Until then this never fires and the checkout behaves exactly as it did before.
     *
     * Relayed from the checkout page untouched. Treat [GlomoCheckoutStageChangedEvent.stage] and
     * the other open strings as open: new values can appear without a new payload version, and are
     * forwarded rather than filtered.
     *
     * Defaulted so an existing Kotlin implementation keeps compiling. A Java implementation of this
     * interface must add the method by hand - see MIGRATION.md.
     */
    public fun onCheckoutStageChanged(event: GlomoCheckoutStageChangedEvent): Unit = Unit

    /**
     * The checkout closed, carrying the furthest stage it reached and why it ended.
     *
     * Delivered at most once per checkout, and only under the same conditions as
     * [onCheckoutStageChanged]. It does not replace [onPaymentTerminate], which still reports every
     * termination unchanged - this adds where the customer stopped.
     *
     * When the customer dismisses the checkout natively the page never runs its own close path, so
     * the SDK writes the event itself with reason `user_dismissed`. It only does so once it has
     * seen a stage it recognises, because an absent `lastStage` means "closed before the checkout
     * opened" and the SDK will not assert that when it simply does not know.
     *
     * Defaulted for the same reason as [onCheckoutStageChanged].
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
