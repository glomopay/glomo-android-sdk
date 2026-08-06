package com.glomopay.sdk.security

public data class DeviceComplianceResult public constructor(
    public val isCompliant: Boolean,
    public val isRooted: Boolean,
    public val isEmulator: Boolean,
    public val isDeveloperModeEnabled: Boolean,
    public val isUsbDebuggingEnabled: Boolean,
    public val hasTestKeys: Boolean,
    public val checksSkipped: Boolean,
)
