package com.glomopay.sdk.android.analytics

import com.glomopay.sdk.android.security.DeviceComplianceResult

internal fun complianceAnalyticsProperties(result: DeviceComplianceResult): Map<String, Any?> {
    val skipped = result.checksSkipped
    return mapOf(
        "is_compliant" to result.isCompliant.takeUnless { skipped },
        "is_jailbroken" to result.isRooted.takeUnless { skipped },
        "checks_skipped" to skipped,
        "is_emulator" to result.isEmulator,
        "is_developer_mode_enabled" to result.isDeveloperModeEnabled,
        "is_debugger_attached" to null,
        "is_usb_debugging_enabled" to result.isUsbDebuggingEnabled,
        "has_test_keys" to result.hasTestKeys,
    )
}
