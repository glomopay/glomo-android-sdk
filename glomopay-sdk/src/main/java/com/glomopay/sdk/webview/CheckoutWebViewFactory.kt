package com.glomopay.sdk.webview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

internal object CheckoutWebViewFactory {
    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, devMode: Boolean): WebView = WebView(context).apply {
        setBackgroundColor(android.graphics.Color.WHITE)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            // File uploads return content:// URIs from the Android picker.
            // WebView must be allowed to read those URIs after selection.
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        if (devMode) WebView.setWebContentsDebuggingEnabled(true)
    }

    fun clearSession(webView: WebView) {
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
        webView.clearSslPreferences()
        webView.loadUrl("about:blank")
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }
}
