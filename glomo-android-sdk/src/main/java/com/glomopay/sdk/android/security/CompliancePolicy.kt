package com.glomopay.sdk.android.security

import com.glomopay.sdk.android.ConfigManager
import com.glomopay.sdk.android.GlomoPayConfig

internal object CompliancePolicy {
    /** Matches Flutter controller: strict SafeDevice check only for live/non-dev sessions. */
    fun requiresStrictCheck(config: GlomoPayConfig): Boolean =
        ConfigManager.getMode(config.publicKey) == "live" && !config.devMode
}
