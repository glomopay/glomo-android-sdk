package com.glomopay.sdk.android.analytics

internal data class AnalyticsEvent(
    val name: String,
    val properties: Map<String, Any?>,
)
