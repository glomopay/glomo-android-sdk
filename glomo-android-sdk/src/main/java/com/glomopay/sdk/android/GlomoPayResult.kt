package com.glomopay.sdk.android

internal sealed interface GlomoPayResult {
    public data class Success public constructor(
        public val payload: Map<String, Any?> = emptyMap(),
    ) : GlomoPayResult

    public data class Failure public constructor(
        public val message: String,
        public val code: String? = null,
    ) : GlomoPayResult

    /** A completed non-payment journey, such as submitted bank-transfer details. */
    public data class JourneyCompleted public constructor(
        public val payload: Map<String, Any?> = emptyMap(),
    ) : GlomoPayResult

    public data object Cancelled : GlomoPayResult
}
