package com.glomopay.sdk.android.security

import com.glomopay.sdk.android.ConfigManager
import com.glomopay.sdk.android.GlomoPayConfig

internal object CompliancePolicy {
    /** Only an SDK-owner internal build can bypass live-device enforcement. */
    fun requiresStrictCheck(config: GlomoPayConfig, internalBuild: Boolean = com.glomopay.sdk.android.BuildConfig.GLOMO_INTERNAL_BUILD): Boolean =
        ConfigManager.getMode(config.publicKey) == "live" && !internalBuild
}
