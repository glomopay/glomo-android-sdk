package com.glomopay.sdk.android

/** Input and payment validation rules mirrored from the Flutter SDK. */
public object Validator {
    public fun isValidPublicKey(key: String): Boolean = key.length > 5 &&
        (key.startsWith("live_") || key.startsWith("test_") || key.startsWith("mock_"))

    public fun isValidOrderId(id: String): Boolean = id.length > 6 && id.startsWith("order_")

    public fun isValidSubscriptionId(id: String): Boolean = id.length > 4 && id.startsWith("sub_")

    public fun validateCheckoutIdentifier(orderId: String?, subscriptionId: String?): String {
        val hasOrder = !orderId.isNullOrEmpty()
        val hasSubscription = !subscriptionId.isNullOrEmpty()
        if (hasOrder && hasSubscription) return "Provide either orderId or subscriptionId, not both."
        if (!hasOrder && !hasSubscription) return "Either orderId or subscriptionId is required."
        if (hasOrder && !isValidOrderId(orderId!!)) return "orderId must start with 'order_'."
        if (hasSubscription && !isValidSubscriptionId(subscriptionId!!)) return "subscriptionId must start with 'sub_'."
        return ""
    }

    public fun isValidUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    public fun isValidPaymentPayload(payload: GlomoPayPayload): Boolean =
        payload.orderId.isNotEmpty() &&
            !payload.paymentId.isNullOrEmpty() &&
            !payload.signature.isNullOrEmpty()

    public fun isValidBankTransferPayload(payload: GlomoPayPayload): Boolean = payload.orderId.isNotEmpty()
}
