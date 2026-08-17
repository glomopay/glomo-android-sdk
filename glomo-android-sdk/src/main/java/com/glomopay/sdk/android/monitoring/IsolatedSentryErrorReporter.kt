package com.glomopay.sdk.android.monitoring

import android.content.Context
import com.glomopay.sdk.android.GlomoPayConfig
import com.glomopay.sdk.android.R
import com.glomopay.sdk.android.analytics.AnalyticsSanitizer
import com.glomopay.sdk.android.analytics.GlomoPayLogger
import io.sentry.Breadcrumb
import io.sentry.IScope
import io.sentry.SentryClient
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.internal.debugmeta.ResourcesDebugMetaLoader
import io.sentry.util.DebugMetaPropertiesApplier
import java.util.ArrayDeque

internal fun interface SentryCaptureClient {
    fun capture(event: SentryEvent, scope: IScope)
}

internal class IsolatedSentryErrorReporter(
    private val options: SentryOptions,
    private val client: SentryCaptureClient,
    private val sessionId: String,
    initialFlowType: String,
    private val devMode: Boolean,
) : SdkErrorReporter {
    private val breadcrumbs = ArrayDeque<Breadcrumb>()
    @Volatile private var flowType = initialFlowType

    override fun updateFlowType(flowType: String) {
        this.flowType = flowType
    }

    override fun addBreadcrumb(category: String, message: String, data: Map<String, Any?>) {
        val breadcrumb = Breadcrumb().apply {
            this.category = AnalyticsSanitizer.text(category, 80)
            this.message = AnalyticsSanitizer.text(message, 200)
            level = SentryLevel.INFO
            sanitizeContext(data).forEach(::setData)
        }
        synchronized(breadcrumbs) {
            while (breadcrumbs.size >= MAX_BREADCRUMBS) breadcrumbs.removeFirst()
            breadcrumbs.addLast(breadcrumb)
        }
    }

    override fun capture(operation: String, error: Throwable, context: Map<String, Any?>) {
        runCatching {
            val safeError = RuntimeException("$operation failed (${error.javaClass.simpleName})").apply {
                stackTrace = error.stackTrace
            }
            val event = SentryEvent(safeError).apply {
                level = SentryLevel.ERROR
                logger = "com.glomopay.sdk.android"
                setTag("sdk_source", "glomo-android-sdk")
                setTag("operation", AnalyticsSanitizer.text(operation, 80))
                setTag("flow_type", flowType)
                setExtra("session_id", sessionId)
                sanitizeContext(context).forEach(::setExtra)
                synchronized(this@IsolatedSentryErrorReporter.breadcrumbs) {
                    this@IsolatedSentryErrorReporter.breadcrumbs.forEach(::addBreadcrumb)
                }
            }
            val scope = io.sentry.Scope(options).apply {
                setTag("sdk_source", "glomo-android-sdk")
                setTag("sdk_session_id", sessionId)
                setTag("flow_type", flowType)
                setTag("dev_mode", devMode.toString())
            }
            client.capture(event, scope)
        }.onFailure { GlomoPayLogger.error("Unable to report SDK failure to Sentry", it) }
    }

    private fun sanitizeContext(context: Map<String, Any?>): Map<String, Any> = buildMap {
        AnalyticsSanitizer.properties(context).forEach { (key, value) ->
            if (key in ALLOWED_CONTEXT_KEYS && value != null) put(key, value)
        }
    }

    private companion object {
        const val MAX_BREADCRUMBS = 30
        val ALLOWED_CONTEXT_KEYS = setOf(
            "event_name",
            "error_type",
            "status_code",
            "webview_type",
            "source",
            "fallback_type",
        )
    }
}

internal object SdkErrorReporterFactory {
    fun create(
        context: Context,
        config: GlomoPayConfig,
        sessionId: String,
        flowType: String,
    ): SdkErrorReporter {
        val dsn = context.getString(R.string.glomopay_sentry_dsn)
        if (dsn.isBlank()) return NoOpSdkErrorReporter

        return runCatching {
            val bundle = IsolatedSentryClientHolder.get(
                dsn = dsn,
                sdkVersion = context.getString(R.string.glomopay_sdk_version),
            )
            IsolatedSentryErrorReporter(
                options = bundle.options,
                client = bundle.captureClient,
                sessionId = sessionId,
                initialFlowType = flowType,
                devMode = config.devMode,
            )
        }.getOrElse {
            GlomoPayLogger.error("Unable to initialize isolated Sentry client", it)
            NoOpSdkErrorReporter
        }
    }
}

private data class IsolatedSentryClientBundle(
    val options: SentryOptions,
    val captureClient: SentryCaptureClient,
)

private object IsolatedSentryClientHolder {
    @Volatile private var bundle: IsolatedSentryClientBundle? = null

    fun get(dsn: String, sdkVersion: String): IsolatedSentryClientBundle =
        bundle ?: synchronized(this) {
            bundle ?: create(dsn, sdkVersion).also { bundle = it }
        }

    private fun create(dsn: String, sdkVersion: String): IsolatedSentryClientBundle {
        val options = createIsolatedSentryOptions(dsn, sdkVersion)
        val sentryClient = SentryClient(options)
        return IsolatedSentryClientBundle(
            options = options,
            captureClient = SentryCaptureClient { event, scope -> sentryClient.captureEvent(event, scope) },
        )
    }
}

internal fun createIsolatedSentryOptions(dsn: String, sdkVersion: String): SentryOptions {
    val options = SentryOptions().apply {
        this.dsn = dsn
        release = "glomo-android-sdk@$sdkVersion"
        environment = "glomo-android-sdk"
        sentryClientName = "glomo-android-sdk/$sdkVersion"
        isDebug = false
        isSendDefaultPii = false
        isEnableExternalConfiguration = false
        isEnableUncaughtExceptionHandler = false
        isEnableShutdownHook = false
        isEnableAutoSessionTracking = false
        isAttachThreads = false
        isAttachServerName = false
        isSendModules = false
        tracesSampleRate = 0.0
        profilesSampleRate = 0.0
        connectionTimeoutMillis = 10_000
        readTimeoutMillis = 10_000
        maxBreadcrumbs = 30
        maxQueueSize = 30
        addInAppInclude("com.glomopay.sdk.android")
        setBeforeSend { event, _ ->
            event.user = null
            event.request = null
            event.serverName = null
            event
        }
    }

    // Link final-app R8 mappings without importing the merchant's Sentry runtime configuration.
    DebugMetaPropertiesApplier.applyToOptions(
        options,
        ResourcesDebugMetaLoader(options.logger).loadDebugMeta(),
    )
    return options
}
