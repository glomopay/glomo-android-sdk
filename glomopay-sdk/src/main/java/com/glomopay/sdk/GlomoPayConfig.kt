package com.glomopay.sdk

/** Configuration required to start a GlomoPay checkout. */
public data class GlomoPayConfig public constructor(
    public val publicKey: String,
    public val orderId: String? = null,
    public val subscriptionId: String? = null,
    public val server: String? = null,
    public val devMode: Boolean = false,
) {
    public val checkoutId: String?
        get() = orderId ?: subscriptionId

    public val isSubscription: Boolean
        get() = !subscriptionId.isNullOrEmpty()

    public fun copyWith(
        publicKey: String = this.publicKey,
        orderId: String? = this.orderId,
        subscriptionId: String? = this.subscriptionId,
        server: String? = this.server,
        devMode: Boolean = this.devMode,
    ): GlomoPayConfig = GlomoPayConfig(publicKey, orderId, subscriptionId, server, devMode)
}
