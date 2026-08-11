package com.glomopay.sdk.android

public enum class CheckoutStatus {
    READY,
    VALIDATING,
    PAYMENT_IN_PROGRESS,
    PAYMENT_SUCCESSFUL,
    PAYMENT_FAILED,
    PAYMENT_CANCELLED,
}
