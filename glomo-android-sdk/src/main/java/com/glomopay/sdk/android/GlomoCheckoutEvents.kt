package com.glomopay.sdk.android

// Transcribed by hand from `checkout-events.schema.json`; re-check it when the schema changes.
// The string fields are open: the constant objects below name known values, not closed sets.

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
}

public object GlomoCheckoutCloseReasons {
    public const val USER_DISMISSED: String = "user_dismissed"
    public const val USER_CANCELLED: String = "user_cancelled"
    public const val COMPLETED: String = "completed"
    public const val ERROR: String = "error"
    public const val CONFIRMATION_TIMEOUT: String = "confirmation_timeout"
}

/** What the checkout is for. On LRS this is always the order. */
public class GlomoCheckoutResource internal constructor(
    public val type: String,
    public val id: String,
)

/** Position within a multi-part step, where the page reports one. 1-based. */
public class GlomoCheckoutSegment internal constructor(
    public val index: Int,
    public val total: Int,
)

/**
 * The customer reached a new stage, or a new step within one.
 *
 * Stages arrive in order; each stage and step pair arrives at most once. [rawPayload] holds the
 * payload as received, including fields added after this SDK was built.
 */
public class GlomoCheckoutStageChangedEvent internal constructor(
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
)

/** A null [lastStage] means the checkout closed before it finished opening. */
public class GlomoCheckoutClosedEvent internal constructor(
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
)
