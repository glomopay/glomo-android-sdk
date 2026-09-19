package com.glomopay.sdk.android.webview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

internal object CheckoutWebViewFactory {
    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    fun create(context: Context): WebView = WebView(context).apply {
        setBackgroundColor(android.graphics.Color.WHITE)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // File uploads return content:// URIs from the Android picker.
            // WebView must be allowed to read those URIs after selection.
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        // A fresh WebView starts with empty navigation/form state. Shared cookies,
        // WebStorage and the process-wide disk cache belong to the embedding app.
    }

    fun clearSession(webView: WebView) {
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
        webView.clearSslPreferences()
        webView.loadUrl("about:blank")
        // CookieManager is process-wide; clearing it would erase merchant/bank sessions.
        // Android has no per-WebView cookie clearing API.
    }
}
