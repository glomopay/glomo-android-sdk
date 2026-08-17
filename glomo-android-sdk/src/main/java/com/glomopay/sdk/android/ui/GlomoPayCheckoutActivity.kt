package com.glomopay.sdk.android.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import android.webkit.ValueCallback
import com.glomopay.sdk.android.ConfigManager
import com.glomopay.sdk.android.ConnectionError
import com.glomopay.sdk.android.GlomoPayApiClient
import com.glomopay.sdk.android.GlomoPayConfig
import com.glomopay.sdk.android.GlomoPayResult
import com.glomopay.sdk.android.R
import com.glomopay.sdk.android.CheckoutSessionRegistry
import com.glomopay.sdk.android.SdkError
import com.glomopay.sdk.android.SdkErrorType
import com.glomopay.sdk.android.bridge.GlomoPayEventRouter
import com.glomopay.sdk.android.bridge.GlomoPayInjectionScripts
import com.glomopay.sdk.android.bridge.GlomoPayJavaScriptBridge
import com.glomopay.sdk.android.analytics.AnalyticsEvents
import com.glomopay.sdk.android.analytics.AnalyticsSanitizer
import com.glomopay.sdk.android.analytics.AnalyticsTracker
import com.glomopay.sdk.android.analytics.complianceAnalyticsProperties
import com.glomopay.sdk.android.analytics.GlomoPayLogger
import com.glomopay.sdk.android.analytics.NoOpAnalyticsTracker
import com.glomopay.sdk.android.monitoring.NoOpSdkErrorReporter
import com.glomopay.sdk.android.monitoring.SdkErrorReporter
import com.glomopay.sdk.android.Validator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.glomopay.sdk.android.security.CompliancePolicy
import com.glomopay.sdk.android.security.DeviceComplianceChecker
import com.glomopay.sdk.android.state.CheckoutUiState
import com.glomopay.sdk.android.state.withLoadingProgress
import com.glomopay.sdk.android.webview.CheckoutWebViewClient
import com.glomopay.sdk.android.webview.CheckoutWebViewFactory

/** Native main checkout host. JS events are attached in the bridge phase. */
public class GlomoPayCheckoutActivity : Activity() {
    private lateinit var webView: android.webkit.WebView
    private lateinit var loadingLabel: TextView
    private lateinit var config: GlomoPayConfig
    private var sessionId: String? = null
    private var listener: com.glomopay.sdk.android.GlomoPayListener? = null
    private var analytics: AnalyticsTracker = NoOpAnalyticsTracker
    private var errorReporter: SdkErrorReporter = NoOpSdkErrorReporter
    private lateinit var eventRouter: GlomoPayEventRouter
    private lateinit var rootView: FrameLayout
    private var flowWebView: android.webkit.WebView? = null
    private var flowOverlay: FrameLayout? = null
    private var flowLoadingLabel: TextView? = null
    private var mainErrorPanel: View? = null
    private var paymentInProgress = false
    private var currentUrl: String? = null
    private var lastMainAnalyticsUrl: String? = null
    private var lastRedirectAnalyticsUrl: String? = null
    private var uiState: CheckoutUiState = CheckoutUiState.Loading
    private var pendingFilePathCallback: ValueCallback<Array<Uri>>? = null
    private val checkoutScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = configFromIntent(intent)
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        val checkoutSession = CheckoutSessionRegistry.get(sessionId)
        listener = checkoutSession?.listener
        analytics = checkoutSession?.analytics ?: NoOpAnalyticsTracker
        errorReporter = checkoutSession?.errorReporter ?: NoOpSdkErrorReporter
        GlomoPayLogger.devMode = config.devMode
        val publicKeyError = if (Validator.isValidPublicKey(config.publicKey)) null else "Invalid Public Key format"
        val identifierError = Validator.validateCheckoutIdentifier(config.orderId, config.subscriptionId)
        if (publicKeyError != null || identifierError.isNotEmpty()) {
            val message = publicKeyError ?: identifierError
            analytics.track(AnalyticsEvents.SDK_VALIDATION_FAILED, mapOf(
                "failure_reason" to validationFailureReason(publicKeyError, identifierError),
                "error_message" to message,
            ))
            trackSdkError(SdkError(SdkErrorType.VALIDATION_ERROR, message))
            listener?.onSdkError(listOf(SdkError(SdkErrorType.VALIDATION_ERROR, message)))
            finishWith(GlomoPayResult.Failure(message, "VALIDATION_ERROR"))
            return
        }
        eventRouter = GlomoPayEventRouter(
            listener = listener,
            devMode = config.devMode,
            onComplete = ::finishWith,
            onWindowOpen = ::showFlow,
            onWindowClose = ::hideFlow,
            onPaymentPending = { paymentInProgress = true },
            analytics = analytics,
            errorReporter = errorReporter,
        )
        val strictCompliance = CompliancePolicy.requiresStrictCheck(config)
        val compliance = DeviceComplianceChecker.check(this, strictCompliance)
        analytics.track(
            AnalyticsEvents.DEVICE_COMPLIANCE_CHECKED,
            complianceAnalyticsProperties(config.devMode, compliance),
        )
        if (!compliance.isCompliant) {
            analytics.track(AnalyticsEvents.DEVICE_COMPLIANCE_BLOCKED, mapOf("block_reason" to "root_detected"))
            val error = SdkError(
                type = SdkErrorType.DEVICE_FORBIDDEN,
                message = "Device is rooted or jailbroken.",
            )
            trackSdkError(error)
            listener?.onSdkError(listOf(error))
            finishWith(GlomoPayResult.Failure("Device does not meet security requirements", "DEVICE_NON_COMPLIANT"))
            return
        }

        buildContentView()
        loadCheckout()
    }

    private fun buildContentView() {
        webView = CheckoutWebViewFactory.create(this, config.devMode).apply {
            webViewClient = CheckoutWebViewClient(
                onPageStartedCallback = { url ->
                    currentUrl = url
                    analytics.track(AnalyticsEvents.NAVIGATION_STARTED, navigationProperties(url))
                    mainErrorPanel?.visibility = View.GONE
                    updateState(CheckoutUiState.Loading)
                },
                onPageFinishedCallback = { url ->
                    currentUrl = url
                    analytics.track(AnalyticsEvents.NAVIGATION_FINISHED, navigationProperties(url))
                    updateState(CheckoutUiState.Content)
                    evaluateInjection()
                },
                onUrlChangedCallback = { url ->
                    currentUrl = url
                    if (lastMainAnalyticsUrl != url) {
                        lastMainAnalyticsUrl = url
                        analytics.track(AnalyticsEvents.NAVIGATION_URL_CHANGE, navigationProperties(url))
                    }
                },
                onErrorCallback = ::handleConnectionError,
            )
            addJavascriptInterface(GlomoPayJavaScriptBridge { raw ->
                runOnUiThread { eventRouter.handle(raw) }
            }, "GlomoPayBridge")
        }

        rootView = FrameLayout(this)
        rootView.addView(webView, FrameLayout.LayoutParams(-1, -1))

        loadingLabel = TextView(this).apply {
            text = getString(R.string.glomopay_loading_checkout)
            setTextColor(Color.DKGRAY)
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.CENTER
            visibility = View.VISIBLE
        }
        rootView.addView(loadingLabel, FrameLayout.LayoutParams(-1, -1))

        mainErrorPanel = createErrorPanel()
        rootView.addView(mainErrorPanel, FrameLayout.LayoutParams(-1, -1))

        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
                updateState(uiState.withLoadingProgress(newProgress))
            }

            override fun onShowFileChooser(
                view: android.webkit.WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean = openFileChooser(filePathCallback, fileChooserParams)
        }
        setContentView(rootView)
    }

    private fun loadCheckout() {
        val requestedType = intent.getStringExtra(EXTRA_ORDER_TYPE) ?: "auto"
        val orderId = config.orderId
        if (requestedType != "auto" || config.isSubscription || orderId.isNullOrEmpty()) {
            openCheckout(requestedType.takeUnless { it == "auto" } ?: "standard")
            return
        }

        checkoutScope.launch {
            analytics.track(AnalyticsEvents.ORDER_TYPE_DETECTION_STARTED)
            val detectedType = try {
                val order = withContext(Dispatchers.IO) {
                    GlomoPayApiClient(config.publicKey, config.devMode).fetchOrder(orderId)
                }
                ConfigManager.detectOrderType(order).also { resolved ->
                    analytics.updateFlowType(resolved)
                    errorReporter.updateFlowType(resolved)
                    analytics.track(AnalyticsEvents.ORDER_TYPE_RESOLVED, mapOf("resolved_type" to resolved))
                }
            } catch (error: Exception) {
                analytics.updateFlowType("standard")
                errorReporter.updateFlowType("standard")
                analytics.track(AnalyticsEvents.ORDER_TYPE_DETECTION_FAILED, mapOf(
                    "error" to error.javaClass.simpleName,
                    "fallback_type" to "standard",
                ))
                errorReporter.capture(
                    operation = "order_type_detection",
                    error = error,
                    context = mapOf("fallback_type" to "standard"),
                )
                "standard"
            }
            openCheckout(detectedType)
        }
    }

    private fun openCheckout(orderType: String) {
        val url = ConfigManager.getCheckoutUrl(config, orderType)
        analytics.updateFlowType(orderType)
        errorReporter.updateFlowType(orderType)
        analytics.updateCheckoutUrl(url)
        analytics.track(AnalyticsEvents.CHECKOUT_URL_RESOLVED, mapOf("url" to url))
        analytics.track(AnalyticsEvents.CHECKOUT_STARTED)
        currentUrl = url
        webView.loadUrl(url)
    }

    private fun updateState(state: CheckoutUiState) {
        uiState = state
        when (state) {
            CheckoutUiState.Loading -> {
                loadingLabel.text = getString(R.string.glomopay_loading_checkout)
                loadingLabel.visibility = View.VISIBLE
            }
            is CheckoutUiState.LoadingProgress -> {
                loadingLabel.text = if (state.progress > 0) {
                    getString(R.string.glomopay_loading_checkout_progress, state.progress)
                } else {
                    getString(R.string.glomopay_loading_checkout)
                }
                loadingLabel.visibility = View.VISIBLE
            }
            CheckoutUiState.Content -> {
                loadingLabel.visibility = View.GONE
            }
            is CheckoutUiState.Error -> {
                loadingLabel.text = state.connectionError.message
                loadingLabel.visibility = View.VISIBLE
            }
        }
    }

    private fun handleConnectionError(error: ConnectionError) {
        analytics.track(AnalyticsEvents.CONNECTION_ERROR, mapOf(
            "error_code" to (error.errorCode ?: error.statusCode)?.toString(),
            "error_description" to error.message,
            "url" to error.failedUrl?.let(AnalyticsSanitizer::navigationUrl),
            "is_recoverable" to error.isRecoverable,
        ))
        error.statusCode?.let { statusCode ->
            analytics.track(AnalyticsEvents.WEBVIEW_HTTP_ERROR, mapOf(
                "status_code" to statusCode,
                "url" to error.failedUrl?.let(AnalyticsSanitizer::navigationUrl),
                "webview_type" to "main",
            ))
        }
        errorReporter.capture(
            operation = "main_webview_connection",
            error = IllegalStateException(error.type.name),
            context = mapOf(
                "error_type" to error.type.name,
                "status_code" to error.statusCode,
                "webview_type" to "main",
            ),
        )
        updateState(CheckoutUiState.Error(error))
        mainErrorPanel?.visibility = View.VISIBLE
        loadingLabel.visibility = View.GONE
        listener?.onConnectionError(error)
        val sdkError = com.glomopay.sdk.android.SdkError(
            com.glomopay.sdk.android.SdkErrorType.NETWORK_ERROR,
            "${error.message} (${error.errorCode ?: error.statusCode ?: "unknown"})",
        )
        trackSdkError(sdkError)
        listener?.onSdkError(listOf(sdkError))
    }

    private fun evaluateInjection() {
        webView.evaluateJavascript("window.__glomoDevMode__ = ${config.devMode};", null)
        webView.evaluateJavascript(GlomoPayInjectionScripts.main(), null)
    }

    private fun createErrorPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(24), dp(24), dp(24))
        setBackgroundColor(Color.WHITE)
        visibility = View.GONE

        val title = TextView(this@GlomoPayCheckoutActivity).apply {
            text = "Connection Error"
            textSize = 20f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }
        val message = TextView(this@GlomoPayCheckoutActivity).apply {
            text = "Unable to load checkout. Please check your connection and try again."
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(24))
        }
        val actions = LinearLayout(this@GlomoPayCheckoutActivity).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        val retry = Button(this@GlomoPayCheckoutActivity).apply {
            text = "Retry"
            setOnClickListener { retryMainCheckout() }
        }
        val cancel = Button(this@GlomoPayCheckoutActivity).apply {
            text = "Cancel"
            setOnClickListener { cancelCheckout() }
        }
        actions.addView(retry)
        actions.addView(cancel)
        addView(title)
        addView(message)
        addView(actions)
    }

    private fun retryMainCheckout() {
        mainErrorPanel?.visibility = View.GONE
        updateState(CheckoutUiState.Loading)
        webView.loadUrl(currentUrl ?: ConfigManager.getCheckoutUrl(config, intent.getStringExtra(EXTRA_ORDER_TYPE) ?: "standard"))
    }

    private fun cancelCheckout() {
        analytics.track(AnalyticsEvents.PAYMENT_TERMINATED, mapOf("termination_source" to "user_dismiss"))
        listener?.onPaymentTerminate(com.glomopay.sdk.android.TerminationSource.USER_DISMISS)
        finishWith(GlomoPayResult.Cancelled)
    }

    private fun showFlow(url: String) {
        hideFlow()
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.glomopay_ic_chevron_back)
            contentDescription = getString(R.string.glomopay_back)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { handleFlowBack() }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        layout.addView(toolbar, LinearLayout.LayoutParams(-1, dp(48)))

        val content = FrameLayout(this)
        val flow = CheckoutWebViewFactory.create(this, config.devMode)
        val flowLoading = TextView(this).apply {
            text = getString(R.string.glomopay_opening_secure_page)
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        flowWebView = flow
        flowLoadingLabel = flowLoading
        flow.webViewClient = CheckoutWebViewClient(
            onPageStartedCallback = { pageUrl ->
                analytics.track(AnalyticsEvents.REDIRECT_PAGE_STARTED, bankNavigationProperties(pageUrl))
                listener?.onEvent("flow.pageStarted", mapOf("url" to pageUrl))
                flowLoading.text = getString(R.string.glomopay_opening_secure_page)
                flowLoading.visibility = View.VISIBLE
            },
            onPageFinishedCallback = { pageUrl ->
                analytics.track(AnalyticsEvents.REDIRECT_PAGE_FINISHED, bankNavigationProperties(pageUrl))
                listener?.onEvent("flow.pageFinished", mapOf("url" to pageUrl))
                flowLoading.visibility = View.GONE
                flow.evaluateJavascript("window.__glomoDevMode__ = ${config.devMode};", null)
                flow.evaluateJavascript(GlomoPayInjectionScripts.flow(), null)
            },
            onUrlChangedCallback = { pageUrl ->
                if (lastRedirectAnalyticsUrl != pageUrl) {
                    lastRedirectAnalyticsUrl = pageUrl
                    analytics.track(AnalyticsEvents.REDIRECT_URL_CHANGE, bankNavigationProperties(pageUrl))
                }
                listener?.onEvent("flow.urlChange", mapOf("url" to pageUrl))
            },
            onErrorCallback = { error ->
                analytics.track(AnalyticsEvents.CONNECTION_ERROR, mapOf(
                    "error_code" to (error.errorCode ?: error.statusCode)?.toString(),
                    "error_description" to error.message,
                    "url" to error.failedUrl?.let(AnalyticsSanitizer::bankRedirectUrl),
                    "is_recoverable" to error.isRecoverable,
                ))
                error.statusCode?.let { statusCode ->
                    analytics.track(AnalyticsEvents.WEBVIEW_HTTP_ERROR, mapOf(
                        "status_code" to statusCode,
                        "url" to error.failedUrl?.let(AnalyticsSanitizer::bankRedirectUrl),
                        "webview_type" to "flow",
                    ))
                }
                errorReporter.capture(
                    operation = "redirect_webview_connection",
                    error = IllegalStateException(error.type.name),
                    context = mapOf(
                        "error_type" to error.type.name,
                        "status_code" to error.statusCode,
                        "webview_type" to "flow",
                    ),
                )
                flowLoading.text = error.message
                flowLoading.visibility = View.VISIBLE
                listener?.onEvent("flow.error", mapOf(
                    "type" to error.type.toString(),
                    "message" to error.message,
                    "errorCode" to error.errorCode,
                ))
            },
        )
        flow.addJavascriptInterface(GlomoPayJavaScriptBridge { raw ->
            runOnUiThread { eventRouter.handle(raw, "flow") }
        }, "GlomoPayFlowBridge")
        flow.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
                val progress = newProgress.coerceIn(0, 100)
                flowLoading.text = if (progress > 0) {
                    getString(R.string.glomopay_opening_secure_page_progress, progress)
                } else {
                    getString(R.string.glomopay_opening_secure_page)
                }
            }

            override fun onShowFileChooser(
                view: android.webkit.WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean = openFileChooser(filePathCallback, fileChooserParams)
        }
        content.addView(flow, FrameLayout.LayoutParams(-1, -1))
        content.addView(flowLoading, FrameLayout.LayoutParams(-1, -1))
        layout.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        overlay.addView(layout, FrameLayout.LayoutParams(-1, -1))
        flowOverlay = overlay
        rootView.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        flow.loadUrl(url)
    }

    private fun openFileChooser(
        callback: ValueCallback<Array<Uri>>?,
        params: android.webkit.WebChromeClient.FileChooserParams?
    ): Boolean {
        val acceptTypes = params?.acceptTypes?.filter { it.isNotBlank() }?.joinToString(",")
        pendingFilePathCallback?.onReceiveValue(null)
        pendingFilePathCallback = callback

        return try {
            val fileIntent = params?.createIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            val chooser = Intent.createChooser(fileIntent, "Select file")
            startActivityForResult(chooser, REQUEST_FILE_CHOOSER)
            true
        } catch (error: Exception) {
            analytics.track(AnalyticsEvents.FILE_PICKER_ERROR, mapOf(
                "accept_types" to (acceptTypes ?: ""),
                "error_message" to (error.message ?: "Unable to open file picker"),
                "picker_method" to "system_document_picker",
            ))
            errorReporter.capture(
                operation = "file_picker",
                error = error,
                context = mapOf("source" to "system_document_picker"),
            )
            pendingFilePathCallback?.onReceiveValue(null)
            pendingFilePathCallback = null
            false
        }
    }

    @Deprecated("Use Activity Result APIs when this Activity is migrated to ComponentActivity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_FILE_CHOOSER) return

        val callback = pendingFilePathCallback ?: return
        pendingFilePathCallback = null
        callback.onReceiveValue(
            android.webkit.WebChromeClient.FileChooserParams.parseResult(resultCode, data)
        )
    }

    private fun hideFlow() {
        flowOverlay?.let { rootView.removeView(it) }
        flowWebView?.let {
            CheckoutWebViewFactory.clearSession(it)
            it.destroy()
        }
        flowOverlay = null
        flowWebView = null
        flowLoadingLabel = null
        lastRedirectAnalyticsUrl = null
    }

    @Suppress("DEPRECATION")
    private fun handleFlowBack() {
        val flow = flowWebView ?: return
        if (paymentInProgress) return
        if (flow.canGoBack()) {
            flow.goBack()
        } else {
            hideFlow()
            analytics.track(AnalyticsEvents.REDIRECT_CLOSED, mapOf("source" to "flow"))
            listener?.onEvent("redirect.completed", emptyMap())
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use OnBackInvokedDispatcher on newer Android versions")
    override fun onBackPressed() {
        if (flowWebView != null) {
            handleFlowBack()
        } else if (paymentInProgress) {
            return
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            analytics.track(AnalyticsEvents.PAYMENT_TERMINATED, mapOf("termination_source" to "back_button"))
            listener?.onPaymentTerminate(com.glomopay.sdk.android.TerminationSource.BACK_BUTTON)
            finishWith(GlomoPayResult.Cancelled)
        }
    }

    override fun onDestroy() {
        checkoutScope.cancel()
        if (::webView.isInitialized) {
            hideFlow()
            CheckoutWebViewFactory.clearSession(webView)
            webView.destroy()
        }
        CheckoutSessionRegistry.remove(sessionId)
        super.onDestroy()
    }

    private fun finishWith(result: GlomoPayResult) {
        setResult(if (result is GlomoPayResult.Success) RESULT_OK else RESULT_CANCELED)
        finish()
    }

    private fun navigationProperties(url: String): Map<String, Any?> =
        mapOf("url" to AnalyticsSanitizer.navigationUrl(url))

    private fun bankNavigationProperties(url: String): Map<String, Any?> =
        mapOf("url" to AnalyticsSanitizer.bankRedirectUrl(url))

    private fun trackSdkError(error: SdkError) {
        val serializedError = JSONObject(mapOf(
            "type" to error.type.toString(),
            "message" to AnalyticsSanitizer.text(error.message, 500),
        )).toString()
        analytics.track(AnalyticsEvents.SDK_ERROR, mapOf(
            "error_count" to 1,
            "errors" to "[$serializedError]",
        ))
    }

    private fun validationFailureReason(publicKeyError: String?, identifierError: String): String = when {
        publicKeyError != null -> "invalid_public_key"
        identifierError.contains("both", ignoreCase = true) -> "both_ids_provided"
        identifierError.contains("subscription", ignoreCase = true) -> "invalid_subscription_id"
        else -> "missing_order_id"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun configFromIntent(intent: Intent): GlomoPayConfig = GlomoPayConfig(
        publicKey = requireNotNull(intent.getStringExtra(EXTRA_PUBLIC_KEY)),
        orderId = intent.getStringExtra(EXTRA_ORDER_ID),
        subscriptionId = intent.getStringExtra(EXTRA_SUBSCRIPTION_ID),
        server = intent.getStringExtra(EXTRA_SERVER),
        devMode = intent.getBooleanExtra(EXTRA_DEV_MODE, false),
    )

    public companion object {
        public const val EXTRA_PUBLIC_KEY: String = "com.glomopay.sdk.android.PUBLIC_KEY"
        public const val EXTRA_ORDER_ID: String = "com.glomopay.sdk.android.ORDER_ID"
        public const val EXTRA_SUBSCRIPTION_ID: String = "com.glomopay.sdk.android.SUBSCRIPTION_ID"
        public const val EXTRA_SERVER: String = "com.glomopay.sdk.android.SERVER"
        public const val EXTRA_DEV_MODE: String = "com.glomopay.sdk.android.DEV_MODE"
        public const val EXTRA_ORDER_TYPE: String = "com.glomopay.sdk.android.ORDER_TYPE"
        public const val EXTRA_SESSION_ID: String = "com.glomopay.sdk.android.SESSION_ID"
        private const val REQUEST_FILE_CHOOSER = 4101

        public fun createIntent(
            context: Context,
            config: GlomoPayConfig,
            orderType: String = "auto",
        ): Intent = Intent(context, GlomoPayCheckoutActivity::class.java).apply {
            putExtra(EXTRA_PUBLIC_KEY, config.publicKey)
            putExtra(EXTRA_ORDER_ID, config.orderId)
            putExtra(EXTRA_SUBSCRIPTION_ID, config.subscriptionId)
            putExtra(EXTRA_SERVER, config.server)
            putExtra(EXTRA_DEV_MODE, config.devMode)
            putExtra(EXTRA_ORDER_TYPE, orderType)
        }
    }
}
