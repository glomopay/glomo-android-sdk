package com.glomopay.sdk

internal object CheckoutUrlBuilder {
    fun build(config: GlomoPayConfig, orderType: String = "standard"): String =
        ConfigManager.getCheckoutUrl(config, orderType)
}
