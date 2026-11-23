package com.glomopay.sdk.android

/**
 * The v1 checkout progress contract, as the checkout page sends it.
 *
 * Hand-written from `checkout-events.schema.json` and `events-table.md` in the checkout monorepo.
 * That package publishes no Kotlin and ships no fixtures, so this is a transcription rather than a
 * generated binding - when the schema moves, this file has to be re-read against it.
 *
 * `stage`, `step`, `provider`, `method` and `reason` are plain strings on purpose. The contract
 * types them as open strings so a new bank, screen or close reason reaches a merchant instead of
 * failing their validator, and an enum here would turn each of those additions into a crash or a
 * dropped event in every host already in the field. [GlomoCheckoutStages] and friends name today's
 * values for comparison; none of them is a closed set.
 */

/** The payload version this SDK understands. */
public const val GLOMO_CHECKOUT_EVENT_VERSION: Int = 1

/** Payment-flow stages, in the order the page emits them. */
public object GlomoCheckoutStages {
    public const val OPENED: String = "opened"
    public const val METHOD_SELECTED: String = "method_selected"
    public const val DETAILS_SUBMITTED: String = "details_submitted"
    public const val ATTEMPT_CREATED: String = "attempt_created"
    public const val CUSTOMER_AUTH: String = "customer_auth"
    public const val PROVIDER_HANDOFF: String = "provider_handoff"
    public const val AWAITING_CONFIRMATION: String = "awaiting_confirmation"
    public const val CONFIRMED: String = "confirmed"
    public const val FAILED: String = "failed"

    /** In emission order. The page only ever moves forward through these. */
    public val PAYMENT: List<String> = listOf(
        OPENED,
        METHOD_SELECTED,
        DETAILS_SUBMITTED,
        ATTEMPT_CREATED,
        CUSTOMER_AUTH,
        PROVIDER_HANDOFF,
        AWAITING_CONFIRMATION,
        CONFIRMED,
        FAILED,
    )

    /** Reserved in the contract; the LRS checkout does not send these today. */
    public val KYC: List<String> = listOf(
        OPENED,
        "in_progress",
        "submitted",
        "under_review",
        "action_required",
        "approved",
        "rejected",
        "expired",
        "failed",
    )
}

/**
 * Why a checkout closed.
 *
 * `merchant_closed` was dropped from the contract before it shipped, which is why a checkout closed
 * by host code reports no close event at all rather than borrowing one of these.
 */
public object GlomoCheckoutCloseReasons {
    public const val USER_DISMISSED: String = "user_dismissed"
    public const val USER_CANCELLED: String = "user_cancelled"
    public const val COMPLETED: String = "completed"
    public const val ERROR: String = "error"
    public const val CONFIRMATION_TIMEOUT: String = "confirmation_timeout"

    /**
     * The reason to report for a close this SDK writes itself, or null when the contract has no
     * honest value for it.
     *
     * [TerminationSource.PROGRAMMATIC] returns null: the merchant closed the checkout, so none of
     * the five reasons describes it, and the merchant already knows. Reporting `user_dismissed`
     * there would blame the customer for the host's own call.
     */
    public fun forTermination(source: TerminationSource): String? = when (source) {
        TerminationSource.USER_DISMISS -> USER_DISMISSED
        TerminationSource.BACK_BUTTON -> USER_DISMISSED
        TerminationSource.CONNECTION_ERROR -> ERROR
        TerminationSource.PROGRAMMATIC -> null
    }
}

/** What the checkout is for. On LRS this is always the order. */
public data class GlomoCheckoutResource public constructor(
    public val type: String,
    public val id: String,
)

/** Position within a multi-part step, where the page reports one. 1-based. */
public data class GlomoCheckoutSegment public constructor(
    public val index: Int,
    public val total: Int,
)

/**
 * The customer reached a new stage, or a new step within the current stage.
 *
 * Every field is relayed from the page; the SDK derives none of them. Stages arrive in order and
 * never repeat, so a drop-off funnel can be built by appending.
 *
 * [rawPayload] carries the payload exactly as it arrived, including any field added to the contract
 * after this SDK was built. Read it when a value you need has no typed field yet.
 */
public data class GlomoCheckoutStageChangedEvent public constructor(
    public val version: Int,
    public val resource: GlomoCheckoutResource,
    public val journeyId: String,
    public val at: String,
    public val flow: String,
    public val stage: String,
    public val method: String? = null,
    public val paymentId: String? = null,
    public val step: String? = null,
    public val provider: String? = null,
    public val journeyType: String? = null,
    public val errorCode: String? = null,
    public val segment: GlomoCheckoutSegment? = null,
    public val rawPayload: Map<String, Any?>? = null,
) {
    public companion object {
        /**
         * Parses leniently and never fails.
         *
         * A payload that violates the contract is still delivered, with missing strings read as
         * empty: the merchant comparing this against the web SDK should see one contract, and the
         * web SDK forwards contract-violating payloads unchanged. [rawPayload] is always the
         * authority on what actually arrived.
         */
        public fun fromMap(payload: Map<String, Any?>): GlomoCheckoutStageChangedEvent =
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
    }
}

/**
 * The checkout closed, with the furthest point it reached.
 *
 * Delivered at most once per checkout. [lastStage] is absent when the checkout closed before it
 * finished opening - so an absent value is a claim in its own right, not merely a missing field.
 */
public data class GlomoCheckoutClosedEvent public constructor(
    public val version: Int,
    public val resource: GlomoCheckoutResource,
    public val journeyId: String,
    public val at: String,
    public val flow: String,
    public val reason: String,
    public val method: String? = null,
    public val paymentId: String? = null,
    public val lastStage: String? = null,
    public val lastStep: String? = null,
    public val rawPayload: Map<String, Any?>? = null,
) {
    public companion object {
        /** Parses leniently and never fails, for the same reason as the stage event above. */
        public fun fromMap(payload: Map<String, Any?>): GlomoCheckoutClosedEvent =
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
