package com.glomopay.sdk.security

import com.glomopay.sdk.ConfigManager
import com.glomopay.sdk.GlomoPayConfig

internal object CompliancePolicy {
    /** Matches Flutter controller: strict SafeDevice check only for live/non-dev sessions. */
    fun requiresStrictCheck(config: GlomoPayConfig): Boolean =
        ConfigManager.getMode(config.publicKey) == "live" && !config.devMode
}
