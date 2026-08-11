package com.glomopay.sdk.android

public sealed interface GlomoPayResult {
    public data class Success public constructor(
        public val payload: Map<String, Any?> = emptyMap(),
    ) : GlomoPayResult

    public data class Failure public constructor(
        public val message: String,
        public val code: String? = null,
    ) : GlomoPayResult

    public data object Cancelled : GlomoPayResult
}
