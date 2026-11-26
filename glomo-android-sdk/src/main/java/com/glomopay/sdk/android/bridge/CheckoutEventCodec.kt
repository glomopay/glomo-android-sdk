package com.glomopay.sdk.android.bridge

import com.glomopay.sdk.android.GlomoCheckoutClosedEvent
import com.glomopay.sdk.android.GlomoCheckoutCloseReasons
import com.glomopay.sdk.android.GlomoCheckoutResource
import com.glomopay.sdk.android.GlomoCheckoutSegment
import com.glomopay.sdk.android.GlomoCheckoutStageChangedEvent
import com.glomopay.sdk.android.GlomoCheckoutStages
import com.glomopay.sdk.android.TerminationSource

/** Decoding and close-reason rules for the checkout progress contract. */
internal const val GLOMO_CHECKOUT_EVENT_VERSION: Int = 1

/** In emission order. */
internal val PAYMENT_STAGES: List<String> = listOf(
    GlomoCheckoutStages.OPENED,
    GlomoCheckoutStages.METHOD_SELECTED,
    GlomoCheckoutStages.DETAILS_SUBMITTED,
    GlomoCheckoutStages.ATTEMPT_CREATED,
    GlomoCheckoutStages.CUSTOMER_AUTH,
    GlomoCheckoutStages.PROVIDER_HANDOFF,
    GlomoCheckoutStages.AWAITING_CONFIRMATION,
    GlomoCheckoutStages.CONFIRMED,
    GlomoCheckoutStages.FAILED,
)

/** Reserved in the contract; the LRS checkout does not send these. */
internal val KYC_STAGES: List<String> = listOf(
    GlomoCheckoutStages.OPENED,
    "in_progress",
    "submitted",
    "under_review",
    "action_required",
    "approved",
    "rejected",
    "expired",
    GlomoCheckoutStages.FAILED,
)

/**
 * No payload is the switch-off path and `{}` is what arrives when the flag drops mid-session;
 * neither is a v1 event.
 */
internal fun isDeliverablePayload(payload: Map<String, Any?>?): Boolean =
    payload != null &&
        payload.isNotEmpty() &&
        (payload["v"] as? Number)?.toInt() == GLOMO_CHECKOUT_EVENT_VERSION

/** Lenient like the web SDK: missing strings read as empty; [rawPayload] is authoritative. */
internal fun stageChangedFrom(payload: Map<String, Any?>): GlomoCheckoutStageChangedEvent =
    GlomoCheckoutStageChangedEvent(
        version = (payload["v"] as? Number)?.toInt() ?: 0,
        resource = resourceOf(payload["resource"]),
        journeyId = payload["journeyId"] as? String ?: "",
        at = payload["at"] as? String ?: "",
        flow = payload["flow"] as? String ?: "",
        stage = payload["stage"] as? String ?: "",
        method = payload["method"].nonEmptyString(),
        paymentId = payload["paymentId"].nonEmptyString(),
        step = payload["step"].nonEmptyString(),
        provider = payload["provider"].nonEmptyString(),
        journeyType = payload["journeyType"].nonEmptyString(),
        errorCode = payload["errorCode"].nonEmptyString(),
        segment = segmentOf(payload["segment"]),
        rawPayload = payload,
    )

internal fun closedFrom(payload: Map<String, Any?>): GlomoCheckoutClosedEvent =
    GlomoCheckoutClosedEvent(
        version = (payload["v"] as? Number)?.toInt() ?: 0,
        resource = resourceOf(payload["resource"]),
        journeyId = payload["journeyId"] as? String ?: "",
        at = payload["at"] as? String ?: "",
        flow = payload["flow"] as? String ?: "",
        reason = payload["reason"] as? String ?: "",
        method = payload["method"].nonEmptyString(),
        paymentId = payload["paymentId"].nonEmptyString(),
        lastStage = payload["lastStage"].nonEmptyString(),
        lastStep = payload["lastStep"].nonEmptyString(),
        rawPayload = payload,
    )

/** Whether the SDK may quote this stage in a close it writes itself. */
internal fun GlomoCheckoutStageChangedEvent.isRecognised(): Boolean {
    if (version != GLOMO_CHECKOUT_EVENT_VERSION) return false
    if (journeyId.isEmpty() || resource.id.isEmpty()) return false
    // Any flow other than `kyc` reads as payment; the two sets share only values that mean the
    // same in both.
    return stage in (if (flow == "kyc") KYC_STAGES else PAYMENT_STAGES)
}

/** Where the customer actually got to, which is not always a stage this version recognises. */
internal class CheckoutPosition(val stage: String, val step: String?)

/**
 * Copies this stage change's envelope so the close reconciles with the stage events the merchant
 * already has, and quotes [position] for how far the customer got.
 */
internal fun GlomoCheckoutStageChangedEvent.synthesisedClose(
    reason: String,
    closedAt: String,
    position: CheckoutPosition = CheckoutPosition(stage, step),
): GlomoCheckoutClosedEvent = GlomoCheckoutClosedEvent(
    version = version,
    resource = resource,
    journeyId = journeyId,
    at = closedAt,
    flow = flow,
    reason = reason,
    method = method,
    paymentId = paymentId,
    lastStage = position.stage,
    lastStep = position.step,
)

/**
 * The reason for a close the SDK writes on a dismissal, or null where the contract has none.
 *
 * Null for [TerminationSource.PROGRAMMATIC]: the merchant closed a checkout that reached no result
 * and `user_dismissed` would blame the customer for the host's call.
 */
internal fun closeReasonFor(
    source: TerminationSource,
    lastStage: String?,
): String? {
    if (source == TerminationSource.PROGRAMMATIC) return null
    if (source == TerminationSource.CONNECTION_ERROR) return GlomoCheckoutCloseReasons.ERROR
    return when (lastStage) {
        GlomoCheckoutStages.CONFIRMED -> GlomoCheckoutCloseReasons.COMPLETED
        GlomoCheckoutStages.FAILED -> GlomoCheckoutCloseReasons.ERROR
        else -> GlomoCheckoutCloseReasons.USER_DISMISSED
    }
}

private fun Any?.nonEmptyString(): String? = (this as? String)?.takeIf { it.isNotEmpty() }

private fun resourceOf(value: Any?): GlomoCheckoutResource {
    val map = value as? Map<*, *> ?: return GlomoCheckoutResource(type = "", id = "")
    return GlomoCheckoutResource(
        type = map["type"] as? String ?: "",
        id = map["id"] as? String ?: "",
    )
}

private fun segmentOf(value: Any?): GlomoCheckoutSegment? {
    val map = value as? Map<*, *> ?: return null
    val index = (map["index"] as? Number)?.toInt() ?: return null
    val total = (map["total"] as? Number)?.toInt() ?: return null
    return GlomoCheckoutSegment(index = index, total = total)
}
