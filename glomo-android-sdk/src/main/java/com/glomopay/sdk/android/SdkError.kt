package com.glomopay.sdk.android

public enum class SdkErrorType {
    VALIDATION_ERROR,
    DEVICE_FORBIDDEN,
    NETWORK_ERROR,
    UNKNOWN,
}

public data class SdkError public constructor(
    public val type: SdkErrorType,
    public val message: String,
    public val field: String? = null,
)
