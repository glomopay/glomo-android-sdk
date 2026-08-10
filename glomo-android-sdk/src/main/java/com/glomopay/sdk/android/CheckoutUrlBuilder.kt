package com.glomopay.sdk.android

internal object CheckoutUrlBuilder {
    fun build(config: GlomoPayConfig, orderType: String = "standard"): String =
        ConfigManager.getCheckoutUrl(config, orderType)
}
