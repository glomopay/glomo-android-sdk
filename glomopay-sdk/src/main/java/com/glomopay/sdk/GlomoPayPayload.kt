package com.glomopay.sdk

/** Data delivered after a payment succeeds or fails. */
public data class GlomoPayPayload public constructor(
    public val orderId: String,
    public val paymentId: String? = null,
    public val signature: String? = null,
    public val rawResponse: Map<String, Any?>? = null,
) {
    public fun toMap(): Map<String, Any?> = mapOf(
        "orderId" to orderId,
        "paymentId" to paymentId,
        "signature" to signature,
        "rawResponse" to rawResponse,
    )

    public companion object {
        /** Supports nested checkout messages and legacy flat messages. */
        public fun fromMap(json: Map<String, Any?>): GlomoPayPayload {
            val nested = json["payload"] as? Map<*, *>
            val data = if (nested != null) {
                nested.entries.associate { it.key.toString() to it.value }
            } else {
                json
            }

            val explicitRawResponse = json["rawResponse"] as? Map<*, *>
            val raw = explicitRawResponse?.entries?.associate { it.key.toString() to it.value }
                ?: json

            return GlomoPayPayload(
                orderId = (data["orderId"] ?: data["order_id"] ?: "") as? String ?: "",
                paymentId = (data["paymentId"] ?: data["payment_id"]) as? String,
                signature = data["signature"] as? String,
                rawResponse = raw,
            )
        }
    }
}
