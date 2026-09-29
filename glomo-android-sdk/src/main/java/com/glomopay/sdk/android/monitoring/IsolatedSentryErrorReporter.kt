package com.glomopay.sdk.android.monitoring

import android.content.Context
import com.glomopay.sdk.android.GlomoPayConfig
import com.glomopay.sdk.android.R
import com.glomopay.sdk.android.analytics.AnalyticsSanitizer
import com.glomopay.sdk.android.analytics.GlomoPayLogger
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Reports failures Glomo code explicitly captures to Glomo's Sentry project over the envelope
 * endpoint, with no Sentry SDK on the classpath.
 *
 * Events are built from an allowlist: the fields below are the only ones ever sent. There is no
 * user, request, server name, module list, thread dump or debug-meta, device/OS context is limited
 * to [SentryContexts], and the original exception message and cause chain never leave the device.
 */
internal class IsolatedSentryErrorReporter(
    private val client: SentryEnvelopeClient,
    private val sdkVersion: String,
    private val sessionId: String,
    initialFlowType: String,
    private val devMode: Boolean,
    private val contexts: SentryContexts = SentryContexts(),
    private val clock: () -> Long = System::currentTimeMillis,
) : SdkErrorReporter {
    private val breadcrumbs = ArrayDeque<JSONObject>()
    @Volatile private var flowType = initialFlowType

    override fun updateFlowType(flowType: String) {
        this.flowType = flowType
    }

    override fun addBreadcrumb(category: String, message: String, data: Map<String, Any?>) {
        runCatching {
            val breadcrumb = JSONObject()
                .put("timestamp", SentryEnvelope.isoTimestamp(clock()))
                .put("category", AnalyticsSanitizer.text(category, 80))
                .put("message", AnalyticsSanitizer.text(message, 200))
                .put("level", "info")
            val safeData = sanitizeContext(data)
            if (safeData.length() > 0) breadcrumb.put("data", safeData)
            synchronized(breadcrumbs) {
                while (breadcrumbs.size >= MAX_BREADCRUMBS) breadcrumbs.removeFirst()
                breadcrumbs.addLast(breadcrumb)
            }
        }.onFailure { GlomoPayLogger.error("Unable to record SDK breadcrumb", it) }
    }

    override fun capture(operation: String, error: Throwable, context: Map<String, Any?>) {
        runCatching {
            val eventId = UUID.randomUUID().toString().replace("-", "")
            client.send(eventId, buildEvent(eventId, operation, error, context))
        }.onFailure { GlomoPayLogger.error("Unable to report SDK failure to Sentry", it) }
    }

    private fun buildEvent(
        eventId: String,
        operation: String,
        error: Throwable,
        context: Map<String, Any?>,
    ): JSONObject {
        val currentFlowType = flowType
        val tags = JSONObject()
            .put("sdk_source", SDK_NAME)
            .put("operation", AnalyticsSanitizer.text(operation, 80))
            .put("flow_type", currentFlowType)
            .put("sdk_session_id", sessionId)
            .put("dev_mode", devMode.toString())
        val extra = sanitizeContext(context).put("session_id", sessionId)
        val crumbs = synchronized(breadcrumbs) { JSONArray(breadcrumbs.toList()) }

        return JSONObject()
            .put("event_id", eventId)
            .put("timestamp", SentryEnvelope.isoTimestamp(clock()))
            .put("platform", "java")
            .put("level", "error")
            .put("logger", LOGGER)
            .put("release", "$SDK_NAME@$sdkVersion")
            .put("environment", SDK_NAME)
            .put("sdk", JSONObject().put("name", SDK_NAME).put("version", sdkVersion))
            .put("tags", tags)
            .put("extra", extra)
            .put("contexts", contexts.toJson())
            .put("exception", JSONObject().put("values", JSONArray().put(exception(operation, error))))
            .apply { if (crumbs.length() > 0) put("breadcrumbs", JSONObject().put("values", crumbs)) }
    }

    /**
     * The exception is replaced by a synthetic one named after the operation, keeping only the
     * original stack trace. The original message and causes may carry customer data.
     */
    private fun exception(operation: String, error: Throwable): JSONObject {
        val frames = JSONArray()
        // Sentry orders frames oldest first; Java orders them innermost first.
        error.stackTrace.take(MAX_FRAMES).asReversed().forEach { element ->
            frames.put(
                JSONObject()
                    .put("module", element.className)
                    .put("function", element.methodName)
                    .apply {
                        element.fileName?.let { put("filename", it) }
                        if (element.lineNumber >= 0) put("lineno", element.lineNumber)
                        if (element.isNativeMethod) put("native", true)
                    }
                    .put("in_app", element.className.startsWith(IN_APP_PACKAGE)),
            )
        }
        return JSONObject()
            .put("type", "RuntimeException")
            .put("module", "java.lang")
            .put("value", "$operation failed (${error.javaClass.simpleName})")
            .put("mechanism", JSONObject().put("type", "generic").put("handled", true))
            .apply { if (frames.length() > 0) put("stacktrace", JSONObject().put("frames", frames)) }
    }

    private fun sanitizeContext(context: Map<String, Any?>): JSONObject = JSONObject().apply {
        AnalyticsSanitizer.properties(context).forEach { (key, value) ->
            if (key in ALLOWED_CONTEXT_KEYS && value != null) put(key, jsonValue(value))
        }
    }

    /** org.json rejects non-finite numbers; send those as text rather than lose the event. */
    private fun jsonValue(value: Any): Any = when (value) {
        is Double -> if (value.isFinite()) value else value.toString()
        is Float -> if (value.isFinite()) value else value.toString()
        is Boolean, is Number, is String -> value
        else -> value.toString()
    }

    private companion object {
        const val SDK_NAME = "glomo-android-sdk"
        const val LOGGER = "com.glomopay.sdk.android"
        const val IN_APP_PACKAGE = "com.glomopay.sdk.android"
        const val MAX_BREADCRUMBS = 30
        const val MAX_FRAMES = 100
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
    ): SdkErrorReporter = runCatching {
        create(
            dsn = context.getString(R.string.glomopay_sentry_dsn),
            sdkVersion = context.getString(R.string.glomopay_sdk_version),
            sessionId = sessionId,
            flowType = flowType,
            devMode = com.glomopay.sdk.android.BuildConfig.GLOMO_INTERNAL_BUILD,
            contexts = { SentryContexts.get(context) },
        )
    }.getOrElse {
        GlomoPayLogger.error("Unable to initialize SDK error reporting", it)
        NoOpSdkErrorReporter
    }

    /** A blank or malformed DSN yields the no-op reporter; this never throws. */
    fun create(
        dsn: String,
        sdkVersion: String,
        sessionId: String,
        flowType: String,
        devMode: Boolean,
        contexts: () -> SentryContexts = { SentryContexts() },
    ): SdkErrorReporter = runCatching {
        val parsed = SentryDsn.parse(dsn) ?: return NoOpSdkErrorReporter
        IsolatedSentryErrorReporter(
            client = SentryClientHolder.get(parsed, sdkVersion),
            sdkVersion = sdkVersion,
            sessionId = sessionId,
            initialFlowType = flowType,
            devMode = devMode,
            // Collected only once a usable DSN exists, and then once per process.
            contexts = runCatching(contexts).getOrDefault(SentryContexts()),
        )
    }.getOrElse {
        GlomoPayLogger.error("Unable to initialize SDK error reporting", it)
        NoOpSdkErrorReporter
    }
}

/**
 * One client per DSN for the process, so its delivery thread, queue bound and rate-limit state are
 * shared by every checkout rather than reset per session. Sentry counts limits per project.
 */
private object SentryClientHolder {
    private val clients = ConcurrentHashMap<String, SentryEnvelopeClient>()

    fun get(dsn: SentryDsn, sdkVersion: String): SentryEnvelopeClient =
        clients.computeIfAbsent(dsn.value) {
            SentryEnvelopeClient(dsn = dsn, clientName = "glomo-android-sdk/$sdkVersion")
        }
}
