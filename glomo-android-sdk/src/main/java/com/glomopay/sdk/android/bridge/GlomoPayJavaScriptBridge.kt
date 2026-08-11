package com.glomopay.sdk.android.bridge

import android.webkit.JavascriptInterface

internal class GlomoPayJavaScriptBridge(
    private val onMessage: (String) -> Unit,
) {
    @JavascriptInterface
    fun postMessage(message: String) = onMessage(message)
}
