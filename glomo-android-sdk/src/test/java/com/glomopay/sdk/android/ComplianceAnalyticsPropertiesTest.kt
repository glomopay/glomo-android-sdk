package com.glomopay.sdk.android

import com.glomopay.sdk.android.analytics.complianceAnalyticsProperties
import com.glomopay.sdk.android.security.DeviceComplianceResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComplianceAnalyticsPropertiesTest {
    @Test
    fun compliance_properties_preserve_observed_signals() {
        val properties = complianceAnalyticsProperties(result())
        assertEquals(false, properties["is_compliant"])
        assertEquals(true, properties["is_jailbroken"])
        assertEquals(false, properties["checks_skipped"])
        assertEquals(true, properties["is_developer_mode_enabled"])
    }

    @Test
    fun compliance_properties_include_android_signals() {
        val properties = complianceAnalyticsProperties(result())

        assertEquals(false, properties["is_compliant"])
        assertEquals(true, properties["is_jailbroken"])
        assertEquals(false, properties["is_emulator"])
        assertEquals(true, properties["is_developer_mode_enabled"])
        assertEquals(true, properties["is_usb_debugging_enabled"])
        assertEquals(true, properties["has_test_keys"])
        assertEquals(null, properties["is_debugger_attached"])
    }

    private fun result() = DeviceComplianceResult(
        isCompliant = false,
        isRooted = true,
        isEmulator = false,
        isDeveloperModeEnabled = true,
        isUsbDebuggingEnabled = true,
        hasTestKeys = true,
        checksSkipped = false,
    )

    private companion object {
        val EXPECTED_KEYS = setOf(
            "is_compliant",
            "is_jailbroken",
            "is_emulator",
            "is_developer_mode_enabled",
            "is_debugger_attached",
            "is_usb_debugging_enabled",
            "has_test_keys",
        )
    }
}
