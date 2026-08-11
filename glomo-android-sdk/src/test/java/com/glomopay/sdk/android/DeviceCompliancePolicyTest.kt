package com.glomopay.sdk.android

import com.glomopay.sdk.android.security.CompliancePolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceCompliancePolicyTest {
    @Test
    fun live_non_dev_mode_requires_strict_compliance() {
        assertTrue(CompliancePolicy.requiresStrictCheck(GlomoPayConfig("live_key", orderId = "order_1")))
    }

    @Test
    fun test_and_dev_mode_do_not_require_strict_compliance() {
        assertFalse(CompliancePolicy.requiresStrictCheck(GlomoPayConfig("test_key", orderId = "order_1")))
        assertFalse(CompliancePolicy.requiresStrictCheck(GlomoPayConfig("mock_key", orderId = "order_1")))
        assertFalse(CompliancePolicy.requiresStrictCheck(GlomoPayConfig("live_key", orderId = "order_1", devMode = true)))
    }
}
