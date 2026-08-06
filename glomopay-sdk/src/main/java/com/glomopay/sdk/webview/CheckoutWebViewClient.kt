package com.glomopay.sdk.webview

import android.net.http.SslError
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.glomopay.sdk.ConnectionError

@Suppress("DEPRECATION")
internal class CheckoutWebViewClient(
    private val onPageStartedCallback: (String) -> Unit,
    private val onPageFinishedCallback: (String) -> Unit,
    private val onUrlChangedCallback: (String) -> Unit,
    private val onErrorCallback: (ConnectionError) -> Unit,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        onUrlChangedCallback(request.url.toString())
        return false
    }

    @Suppress("DEPRECATION")
    @Deprecated("WebView compatibility callback")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        onUrlChangedCallback(url)
        return false
    }

    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        onUrlChangedCallback(url)
        onPageStartedCallback(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        onUrlChangedCallback(url)
        onPageFinishedCallback(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: android.webkit.WebResourceError,
    ) {
        if (request.isForMainFrame) {
            onErrorCallback(
                ConnectionError.fromWebResourceError(
                    description = error.description?.toString() ?: "WebView failed to load",
                    errorCode = error.errorCode,
                    failedUrl = request.url.toString(),
                ),
            )
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("WebView compatibility callback")
    override fun onReceivedError(
        view: WebView,
        errorCode: Int,
        description: String,
        failingUrl: String,
    ) {
        onErrorCallback(ConnectionError.fromWebResourceError(description, errorCode, failingUrl))
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
            onErrorCallback(ConnectionError.fromHttpStatus(errorResponse.statusCode, request.url.toString()))
        }
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        onErrorCallback(
            ConnectionError(
                type = com.glomopay.sdk.ConnectionErrorType.SSL_ERROR,
                message = "SSL certificate error",
                failedUrl = error.url,
            ),
        )
    }

    override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
        request.cancel()
    }

    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
        handler.cancel()
    }
}
