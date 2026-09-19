package com.glomopay.sdk.android

/**
 * A non-payment journey the customer completed inside checkout.
 *
 * Append new members; never insert. Hosts and tests pin member order, and an
 * inserted member silently re-labels every journey recorded against an ordinal.
 */
public enum class GlomoPayUserJourneyType {
    /** The customer submitted bank-transfer details. No money has moved yet. */
    BANK_TRANSFER,
}

/**
 * Deliberately not a [GlomoPayPayload]. That type's `paymentId` and `signature` are
 * the fields a host verifies a payment with, and neither exists for a journey.
 * Reusing it invited exactly the signature check that can never pass, which is how
 * a submitted bank transfer used to be reported as a completed payment.
 */
public data class GlomoPayUserJourneyPayload public constructor(
    public val journeyType: GlomoPayUserJourneyType,
    public val orderId: String,
    public val senderAccountNumber: String? = null,
    public val transactionReference: String? = null,
    public val status: String? = null,
    public val rawResponse: Map<String, Any?>? = null,
) {
    public fun toMap(): Map<String, Any?> = mapOf(
        "journeyType" to journeyType.name,
        "orderId" to orderId,
        "senderAccountNumber" to senderAccountNumber,
        "transactionReference" to transactionReference,
        "status" to status,
        "rawResponse" to rawResponse,
    )

    public companion object {
        /**
         * Fields are read coercively, in both camelCase and snake_case, because the page
         * has sent both. A cast would throw inside the delivery path after the one-result
         * latch is spent, losing the journey entirely.
         */
        public fun fromMap(
            journeyType: GlomoPayUserJourneyType,
            json: Map<String, Any?>,
        ): GlomoPayUserJourneyPayload = GlomoPayUserJourneyPayload(
            journeyType = journeyType,
            orderId = read(json, "orderId", "order_id").orEmpty(),
            senderAccountNumber = read(json, "senderAccountNumber", "sender_account_number"),
            transactionReference = read(json, "transactionReference", "transaction_reference"),
            status = read(json, "status", "status"),
            rawResponse = json,
        )

        private fun read(json: Map<String, Any?>, camelCase: String, snakeCase: String): String? =
            (json[camelCase] ?: json[snakeCase])?.toString()?.takeIf { it.isNotEmpty() }
    }
}
