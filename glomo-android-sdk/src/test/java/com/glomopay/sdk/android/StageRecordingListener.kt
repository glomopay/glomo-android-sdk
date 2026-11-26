package com.glomopay.sdk.android

/**
 * Records what a merchant's listener would see.
 *
 * [log] is the ordered one and the one to assert on where ordering is the point: the close is the
 * last event of every checkout, which separate per-callback lists cannot express.
 */
internal class StageRecordingListener : GlomoPayListener {
    val stages = mutableListOf<GlomoCheckoutStageChangedEvent>()
    val closes = mutableListOf<GlomoCheckoutClosedEvent>()
    val termination = mutableListOf<TerminationSource>()
    val success = mutableListOf<GlomoPayPayload>()
    val log = mutableListOf<String>()

    /** Lets a test do from `onPaymentSuccess` what a merchant does there - navigate away, close. */
    var onSuccess: (() -> Unit)? = null

    override fun onPaymentSuccess(payload: GlomoPayPayload) {
        success += payload
        log += "success"
        onSuccess?.invoke()
    }

    override fun onPaymentFailure(payload: GlomoPayPayload) { log += "failure" }
    override fun onSdkError(errors: List<SdkError>) = Unit
    override fun onConnectionError(error: ConnectionError) = Unit
    override fun onUserJourneyCompleted(payload: GlomoPayUserJourneyPayload) { log += "journey_completed" }

    override fun onPaymentTerminate(source: TerminationSource) {
        termination += source
        log += "terminate:$source"
    }

    override fun onCheckoutStageChanged(event: GlomoCheckoutStageChangedEvent) {
        stages += event
        log += "stage:${event.stage}"
    }

    override fun onCheckoutClosed(event: GlomoCheckoutClosedEvent) {
        closes += event
        log += "closed:${event.reason}:${event.lastStage}"
    }
}
