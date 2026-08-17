package com.glomopay.sdk.android.monitoring

internal interface SdkErrorReporter {
    fun addBreadcrumb(category: String, message: String, data: Map<String, Any?> = emptyMap())

    fun capture(operation: String, error: Throwable, context: Map<String, Any?> = emptyMap())

    fun updateFlowType(flowType: String)
}

internal object NoOpSdkErrorReporter : SdkErrorReporter {
    override fun addBreadcrumb(category: String, message: String, data: Map<String, Any?>) = Unit

    override fun capture(operation: String, error: Throwable, context: Map<String, Any?>) = Unit

    override fun updateFlowType(flowType: String) = Unit
}
