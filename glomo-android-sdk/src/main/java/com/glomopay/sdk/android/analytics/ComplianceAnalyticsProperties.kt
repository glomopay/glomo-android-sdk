package com.glomopay.sdk.android.analytics

import com.glomopay.sdk.android.security.DeviceComplianceResult

internal fun complianceAnalyticsProperties(
    devMode: Boolean,
    result: DeviceComplianceResult,
): Map<String, Any?> {
    val skipped = devMode || result.checksSkipped
    return mapOf(
        "is_compliant" to result.isCompliant.takeUnless { skipped },
        "is_jailbroken" to result.isRooted.takeUnless { skipped },
        "is_emulator" to result.isEmulator.takeUnless { skipped },
        "is_developer_mode_enabled" to result.isDeveloperModeEnabled.takeUnless { skipped },
        "is_debugger_attached" to null,
        "is_usb_debugging_enabled" to result.isUsbDebuggingEnabled.takeUnless { skipped },
        "has_test_keys" to result.hasTestKeys.takeUnless { skipped },
    )
}
