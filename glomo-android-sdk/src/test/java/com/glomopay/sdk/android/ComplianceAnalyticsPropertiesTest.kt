package com.glomopay.sdk.android

import com.glomopay.sdk.android.analytics.complianceAnalyticsProperties
import com.glomopay.sdk.android.security.DeviceComplianceResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComplianceAnalyticsPropertiesTest {
    @Test
    fun dev_mode_sends_all_compliance_signals_as_explicit_nulls() {
        val properties = complianceAnalyticsProperties(devMode = true, result = result())

        EXPECTED_KEYS.forEach { key ->
            assertTrue(properties.containsKey(key))
            assertEquals(null, properties[key])
        }
    }

    @Test
    fun strict_mode_sends_android_compliance_signals() {
        val properties = complianceAnalyticsProperties(devMode = false, result = result())

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
