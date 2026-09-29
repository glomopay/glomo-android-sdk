package com.glomopay.sdk.android

import com.glomopay.sdk.android.monitoring.HostAppVersion
import com.glomopay.sdk.android.monitoring.IsolatedSentryErrorReporter
import com.glomopay.sdk.android.monitoring.ReporterEnvironment
import com.glomopay.sdk.android.monitoring.NoOpSdkErrorReporter
import com.glomopay.sdk.android.monitoring.SdkErrorReporter
import com.glomopay.sdk.android.monitoring.SdkErrorReporterFactory
import com.glomopay.sdk.android.monitoring.SentryContexts
import com.glomopay.sdk.android.monitoring.SentryDsn
import com.glomopay.sdk.android.monitoring.SentryEnvelope
import com.glomopay.sdk.android.monitoring.SentryEnvelopeClient
import com.glomopay.sdk.android.monitoring.SentryRateLimiter
import com.glomopay.sdk.android.monitoring.SentrySendResult
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IsolatedSentryErrorReporterTest {
    private val server = LocalSentryServer()

    @AfterTest
    fun stopServer() = server.close()

    @Test
    fun capture_posts_a_sentry_envelope_to_the_endpoint_derived_from_the_dsn() {
        reporter().capture("mixpanel_delivery", IllegalStateException("boom"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/${LocalSentryServer.PROJECT_ID}/envelope/", request.path)
        assertEquals("application/x-sentry-envelope", request.header("Content-Type"))
        assertEquals(
            "Sentry sentry_version=7, sentry_client=glomo-android-sdk/1.2.3, " +
                "sentry_key=${LocalSentryServer.PUBLIC_KEY}",
            request.header("X-Sentry-Auth"),
        )
        assertEquals("glomo-android-sdk/1.2.3", request.header("User-Agent"))

        assertEquals(4, request.lines.size, "three lines plus the trailing newline")
        assertEquals("", request.lines[3])
        val event = request.event
        assertEquals(event.getString("event_id"), request.envelopeHeader.getString("event_id"))
        assertTrue(event.getString("event_id").matches(Regex("[0-9a-f]{32}")))
        assertTrue(request.envelopeHeader.getString("sent_at").matches(ISO_UTC))
        assertEquals(server.dsn, request.envelopeHeader.getString("dsn"))
        assertEquals("event", request.itemHeader.getString("type"))
        assertEquals(request.lines[2].toByteArray(Charsets.UTF_8).size, request.itemHeader.getInt("length"))
    }

    @Test
    fun event_carries_only_the_approved_fields() {
        val reporter = reporter(sessionId = "session-uuid", flowType = "auto")
        reporter.updateFlowType("standard")

        reporter.capture("mixpanel_delivery", IllegalStateException("customer user@example.com failed"))

        val event = server.takeRequest().event
        // Allowlist, not a denylist: no user, request, server_name, modules, threads or
        // debug_meta, and nothing else the reporter did not deliberately add.
        assertEquals(
            setOf(
                "event_id", "timestamp", "platform", "level", "logger", "release", "environment",
                "sdk", "tags", "extra", "exception", "contexts",
            ),
            event.keySet(),
        )
        assertEquals("error", event.getString("level"))
        assertEquals("com.glomopay.sdk.android", event.getString("logger"))
        assertEquals("java", event.getString("platform"))
        assertEquals("glomo-android-sdk@1.2.3", event.getString("release"))
        assertEquals("glomo-android-sdk", event.getString("environment"))
        assertTrue(event.getString("timestamp").matches(ISO_UTC))
        assertEquals(
            mapOf(
                "sdk_source" to "glomo-android-sdk",
                "operation" to "mixpanel_delivery",
                "flow_type" to "standard",
                "sdk_session_id" to "session-uuid",
                "dev_mode" to "false",
            ),
            event.getJSONObject("tags").toMap(),
        )
        assertEquals(mapOf<String, Any?>("session_id" to "session-uuid"), event.getJSONObject("extra").toMap())
    }

    @Test
    fun no_ip_inference_setting_and_no_user_object_is_sent() {
        reporter().capture("mixpanel_delivery", IllegalStateException("boom"))

        val request = server.takeRequest()
        // Exactly name and version: no sdk.settings, so no infer_ip in either direction.
        assertEquals(
            mapOf<String, Any?>("name" to "glomo-android-sdk", "version" to "1.2.3"),
            request.event.getJSONObject("sdk").toMap(),
        )
        assertFalse(request.event.has("user"))
        val wire = String(request.body, Charsets.UTF_8)
        listOf("infer_ip", "\"user\"", "\"email\"", "\"username\"", "ip_address", "{{auto}}").forEach {
            assertFalse(wire.contains(it), "<$it> reached the wire")
        }
    }

    @Test
    fun contexts_carry_only_the_approved_os_device_and_app_fields() {
        val contexts = SentryContexts(
            osVersion = "14",
            apiLevel = 34,
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 8",
            appVersion = "3.2.1",
            appBuild = "302010",
        )

        reporter(contexts = contexts).capture("mixpanel_delivery", IllegalStateException("boom"))

        val request = server.takeRequest()
        val sent = request.event.getJSONObject("contexts")
        assertEquals(setOf("os", "device", "app"), sent.keySet())
        assertEquals(
            mapOf<String, Any?>("type" to "os", "name" to "Android", "version" to "14", "api_level" to 34),
            sent.getJSONObject("os").toMap(),
        )
        assertEquals(
            mapOf<String, Any?>("type" to "device", "manufacturer" to "Google", "brand" to "google", "model" to "Pixel 8"),
            sent.getJSONObject("device").toMap(),
        )
        assertEquals(
            mapOf<String, Any?>("type" to "app", "app_version" to "3.2.1", "app_build" to "302010"),
            sent.getJSONObject("app").toMap(),
        )
        val wire = String(request.body, Charsets.UTF_8).lowercase()
        listOf(
            "android_id", "advertising", "ip_address", "device_name", "locale", "timezone", "battery",
            "memory", "screen", "package", "app_name", "app_identifier", "boot_time", "storage", "user",
        ).forEach { assertFalse(wire.contains(it), "<$it> reached the wire") }
    }

    @Test
    fun contexts_omit_missing_or_blank_fields() {
        reporter(contexts = SentryContexts(osVersion = " ", apiLevel = 0, model = "Pixel 8"))
            .capture("mixpanel_delivery", IllegalStateException("boom"))

        val sent = server.takeRequest().event.getJSONObject("contexts")
        assertEquals(setOf("os", "device"), sent.keySet())
        assertEquals(mapOf<String, Any?>("type" to "os", "name" to "Android"), sent.getJSONObject("os").toMap())
        assertEquals(mapOf<String, Any?>("type" to "device", "model" to "Pixel 8"), sent.getJSONObject("device").toMap())
    }

    @Test
    fun contexts_read_from_build_never_throw_when_build_fields_are_unavailable() {
        // JVM unit tests run against a stubbed android.os.Build, the worst case a real build can be.
        val contexts = SentryContexts.fromBuild(appVersion = null, appBuild = null)

        reporter(contexts = contexts).capture("mixpanel_delivery", IllegalStateException("boom"))

        // Fields Build could not supply are omitted, not serialised as "null": os keeps only its
        // constant name, and device, with nothing to carry, is left out entirely.
        val sent = server.takeRequest().event.getJSONObject("contexts")
        assertEquals(setOf("os"), sent.keySet())
        assertFalse(sent.getJSONObject("os").has("version"))
        assertFalse(sent.getJSONObject("os").has("api_level"))
        assertFalse(sent.has("device"))
    }

    @Test
    fun order_id_from_the_checkout_config_is_sent_as_a_tag() {
        reporter(orderId = " order_synthetic_42 ").capture("mixpanel_delivery", IllegalStateException("boom"))
        reporter(orderId = null).capture("mixpanel_delivery", IllegalStateException("boom"))

        assertEquals("order_synthetic_42", server.takeRequest().event.getJSONObject("tags").getString("order_id"))
        assertFalse(server.takeRequest().event.getJSONObject("tags").has("order_id"))
    }

    @Test
    fun envelopes_are_gzipped_on_the_wire_with_uncompressed_item_lengths() {
        val dsn = SentryDsn.parse(server.dsn) ?: error("invalid test DSN")
        val envelope = SentryEnvelope.event(
            eventId = "abcdef01234567890abcdef012345678",
            event = JSONObject().put("level", "error").put("note", "₹1,000 भुगतान ".repeat(50)),
            dsn = dsn,
            sentAtMillis = 0L,
        )

        val delivery = SentryEnvelopeClient(dsn, "glomo-android-sdk/1.2.3", executor = Executor(Runnable::run))
            .deliver(envelope)

        assertEquals(SentrySendResult.SENT, delivery.result)
        val request = server.takeRequest()
        assertEquals("gzip", request.header("Content-Encoding"))
        assertEquals(request.rawBody.size.toString(), request.header("Content-Length"))
        assertEquals(0x1f, request.rawBody[0].toInt() and 0xff)
        assertEquals(0x8b, request.rawBody[1].toInt() and 0xff)
        assertTrue(request.rawBody.size < envelope.size, "gzip did not shrink a repetitive envelope")
        assertTrue(request.body.contentEquals(envelope), "decompressed body differs from the envelope")
        assertEquals(request.lines[2].toByteArray(Charsets.UTF_8).size, request.itemHeader.getInt("length"))
    }

    @Test
    fun drops_from_a_429_and_its_rate_limit_window_are_reported_on_the_next_sent_event() {
        var now = 1_000_000L
        val reporter = reporter(rateLimiter = SentryRateLimiter { now })
        server.respondNext(429, mapOf("Retry-After" to "60"))

        reporter.capture("rejected_by_429", IllegalStateException("boom"))
        server.takeRequest()
        reporter.capture("dropped_in_window_1", IllegalStateException("boom"))
        reporter.capture("dropped_in_window_2", IllegalStateException("boom"))
        now += 61_000
        reporter.capture("first_after_window", IllegalStateException("boom"))
        reporter.capture("second_after_window", IllegalStateException("boom"))

        val first = server.takeRequest().event.getJSONObject("extra")
        assertEquals(3, first.getInt("dropped_since_last_send"))
        val second = server.takeRequest().event.getJSONObject("extra")
        assertFalse(second.has("dropped_since_last_send"), "count was not reset after a successful send")
    }

    @Test
    fun drops_reported_on_a_failed_send_are_carried_until_a_send_succeeds() {
        val reporter = reporter()
        server.respondNext(503)
        server.respondNext(503)

        reporter.capture("fails_1", IllegalStateException("boom"))
        reporter.capture("fails_2", IllegalStateException("boom"))
        reporter.capture("succeeds", IllegalStateException("boom"))
        reporter.capture("after", IllegalStateException("boom"))

        val counts = (1..4).map {
            server.takeRequest().event.getJSONObject("extra").optInt("dropped_since_last_send", 0)
        }
        // fails_2 reports fails_1; succeeds reports both 503s; after reports nothing.
        assertEquals(listOf(0, 1, 2, 0), counts)
    }

    @Test
    fun an_unreachable_endpoint_counts_as_a_drop() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val dsn = SentryDsn.parse("http://${LocalSentryServer.PUBLIC_KEY}@127.0.0.1:$closedPort/1")
            ?: error("invalid test DSN")
        val client = SentryEnvelopeClient(dsn, "glomo-android-sdk/1.2.3", executor = Executor(Runnable::run))

        val failed = client.deliverEvent("0".repeat(32), JSONObject().put("extra", JSONObject()))

        assertEquals(SentrySendResult.FAILED, failed.result)
        // The next event, wherever it goes, carries the count on its way out.
        val next = JSONObject().put("extra", JSONObject())
        client.deliverEvent("1".repeat(32), next)
        assertEquals(1, next.getJSONObject("extra").getInt("dropped_since_last_send"))
    }

    @Test
    fun context_outside_the_allowlist_never_reaches_the_wire() {
        reporter().capture(
            operation = "mixpanel_delivery",
            error = IllegalStateException("boom"),
            context = mapOf(
                "event_name" to "Payment Failure for user@example.com",
                "status_code" to 503,
                "customer_email" to "user@example.com",
                "checkout_url" to "https://bank.example/?account=98765432100",
                "order_id" to "order_synthetic_1",
                "fallback_type" to null,
            ),
        )

        val request = server.takeRequest()
        val extra = request.event.getJSONObject("extra").toMap()
        assertEquals(
            mapOf<String, Any?>(
                "session_id" to "session-uuid",
                "event_name" to "Payment Failure for [REDACTED]",
                "status_code" to 503,
            ),
            extra,
        )
        val wire = String(request.body, Charsets.UTF_8)
        listOf("user@example.com", "98765432100", "bank.example", "order_synthetic_1", "checkout_url")
            .forEach { assertFalse(wire.contains(it), "<$it> leaked onto the wire") }
    }

    @Test
    fun exception_keeps_the_stack_trace_but_not_the_original_message() {
        val error = IllegalStateException("customer user@example.com PAN ABCDE1234F failed")

        reporter().capture("webview_load", error)

        val request = server.takeRequest()
        val exception = request.event.getJSONObject("exception").getJSONArray("values").getJSONObject(0)
        assertEquals("RuntimeException", exception.getString("type"))
        assertEquals("java.lang", exception.getString("module"))
        assertEquals("webview_load failed (IllegalStateException)", exception.getString("value"))
        assertEquals(true, exception.getJSONObject("mechanism").getBoolean("handled"))

        val frames = exception.getJSONObject("stacktrace").getJSONArray("frames")
        assertEquals(error.stackTrace.size, frames.length())
        // Sentry wants the innermost frame last.
        val innermost = frames.getJSONObject(frames.length() - 1)
        assertEquals(error.stackTrace[0].className, innermost.getString("module"))
        assertEquals(error.stackTrace[0].methodName, innermost.getString("function"))
        assertEquals(error.stackTrace[0].lineNumber, innermost.getInt("lineno"))
        assertEquals("IsolatedSentryErrorReporterTest.kt", innermost.getString("filename"))
        assertTrue(innermost.getBoolean("in_app"))
        val junitFrame = (0 until frames.length()).map(frames::getJSONObject)
            .first { it.getString("module").startsWith("org.junit") }
        assertFalse(junitFrame.getBoolean("in_app"))

        val wire = String(request.body, Charsets.UTF_8)
        assertFalse(wire.contains("user@example.com"))
        assertFalse(wire.contains("ABCDE1234F"))
    }

    @Test
    fun breadcrumbs_are_sanitized_capped_at_thirty_and_drop_the_oldest() {
        val reporter = reporter()
        (1..35).forEach { step ->
            reporter.addBreadcrumb(
                category = "analytics",
                message = "step $step",
                data = mapOf("event_name" to "Step $step", "customer_email" to "user@example.com"),
            )
        }

        reporter.capture("mixpanel_delivery", IllegalStateException("boom"))

        val request = server.takeRequest()
        val values = request.event.getJSONObject("breadcrumbs").getJSONArray("values")
        assertEquals(30, values.length())
        assertEquals("step 6", values.getJSONObject(0).getString("message"))
        assertEquals("step 35", values.getJSONObject(29).getString("message"))
        val crumb = values.getJSONObject(29)
        assertEquals("analytics", crumb.getString("category"))
        assertEquals("info", crumb.getString("level"))
        assertTrue(crumb.getString("timestamp").matches(ISO_UTC))
        assertEquals(mapOf<String, Any?>("event_name" to "Step 35"), crumb.getJSONObject("data").toMap())
        assertFalse(String(request.body, Charsets.UTF_8).contains("user@example.com"))
    }

    @Test
    fun a_429_with_retry_after_drops_later_events_until_the_window_passes() {
        var now = 1_000_000L
        val reporter = reporter(rateLimiter = SentryRateLimiter { now })
        server.respondNext(429, mapOf("Retry-After" to "60"))

        reporter.capture("first", IllegalStateException("boom"))
        server.takeRequest()
        reporter.capture("second", IllegalStateException("boom"))
        assertNull(server.requests.poll(300, TimeUnit.MILLISECONDS), "rate-limited event was sent")

        now += 61_000
        reporter.capture("third", IllegalStateException("boom"))
        assertEquals("third", server.takeRequest().event.getJSONObject("tags").getString("operation"))
    }

    @Test
    fun a_rate_limit_header_on_a_success_drops_later_error_events() {
        val reporter = reporter()
        server.respondNext(200, mapOf("X-Sentry-Rate-Limits" to "60:error:organization"))

        reporter.capture("first", IllegalStateException("boom"))
        server.takeRequest()
        reporter.capture("second", IllegalStateException("boom"))

        assertNull(server.requests.poll(300, TimeUnit.MILLISECONDS), "rate-limited event was sent")
    }

    @Test
    fun a_rate_limit_for_another_category_does_not_drop_error_events() {
        val reporter = reporter()
        server.respondNext(200, mapOf("X-Sentry-Rate-Limits" to "60:transaction:organization"))

        reporter.capture("first", IllegalStateException("boom"))
        server.takeRequest()
        reporter.capture("second", IllegalStateException("boom"))

        assertEquals("second", server.takeRequest().event.getJSONObject("tags").getString("operation"))
    }

    @Test
    fun a_server_error_neither_throws_nor_blocks_the_next_event() {
        val reporter = reporter()
        server.respondNext(503)

        reporter.capture("first", IllegalStateException("boom"))
        server.takeRequest()
        reporter.capture("second", IllegalStateException("boom"))

        assertEquals("second", server.takeRequest().event.getJSONObject("tags").getString("operation"))
    }

    @Test
    fun an_unreachable_endpoint_never_throws() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val dsn = "http://${LocalSentryServer.PUBLIC_KEY}@127.0.0.1:$closedPort/1"

        reporter(dsn = dsn).capture("mixpanel_delivery", IllegalStateException("boom"))
    }

    @Test
    fun a_server_that_never_answers_is_abandoned_at_the_timeout() {
        server.hold = CountDownLatch(1)
        val reporter = reporter(timeoutMillis = 300)

        val started = System.nanoTime()
        reporter.capture("mixpanel_delivery", IllegalStateException("boom"))
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

        server.takeRequest()
        assertTrue(elapsedMillis < 5_000, "capture took ${elapsedMillis}ms")
    }

    @Test
    fun a_throwable_that_fails_to_describe_itself_never_throws_into_the_caller() {
        val hostile = object : IllegalStateException("boom") {
            override fun getStackTrace(): Array<StackTraceElement> = throw UnsupportedOperationException()
        }

        reporter().capture("mixpanel_delivery", hostile)
        reporter().addBreadcrumb("analytics", "ok", mapOf("event_name" to Double.NaN))
    }

    @Test
    fun factory_returns_the_no_op_reporter_for_a_blank_or_malformed_dsn() {
        listOf("", "   ", "not a dsn", "https://example.com/1", "https://key@example.com").forEach { dsn ->
            assertSame(NoOpSdkErrorReporter, factoryReporter(dsn), "Expected no-op for <$dsn>")
        }
    }

    @Test
    fun factory_returns_the_no_op_reporter_when_the_dsn_resource_is_missing() {
        val environment = TestEnvironment(dsn = { throw IllegalStateException("resource not found") })

        assertSame(NoOpSdkErrorReporter, factoryReporter(environment))
    }

    @Test
    fun factory_returns_the_no_op_reporter_when_the_sdk_version_resource_is_missing() {
        // Pins current behaviour: the version names the release, so without it nothing is sent.
        val environment = TestEnvironment(dsn = { server.dsn }, version = { throw IllegalStateException("resource not found") })

        assertSame(NoOpSdkErrorReporter, factoryReporter(environment))
    }

    @Test
    fun factory_reporter_reads_version_app_and_order_id_from_the_host() {
        val reporter = factoryReporter(
            TestEnvironment(dsn = { server.dsn }, app = { HostAppVersion("3.2.1", "302010") }),
            config = GlomoPayConfig(publicKey = "test_public_key", orderId = "order_synthetic_7"),
        )

        reporter.capture("mixpanel_delivery", IllegalStateException("boom"))

        val request = server.takeRequest()
        assertEquals("glomo-android-sdk/4.5.6", request.header("User-Agent"))
        assertEquals("glomo-android-sdk@4.5.6", request.event.getString("release"))
        assertEquals("order_synthetic_7", request.event.getJSONObject("tags").getString("order_id"))
        assertEquals(
            mapOf<String, Any?>("type" to "app", "app_version" to "3.2.1", "app_build" to "302010"),
            request.event.getJSONObject("contexts").getJSONObject("app").toMap(),
        )
    }

    @Test
    fun a_failed_package_lookup_still_gives_a_working_reporter_without_app_context() {
        val reporter = factoryReporter(
            TestEnvironment(dsn = { server.dsn }, app = { throw IllegalStateException("package not found") }),
        )
        assertIs<IsolatedSentryErrorReporter>(reporter)

        reporter.capture("mixpanel_delivery", IllegalStateException("boom"))

        val contexts = server.takeRequest().event.getJSONObject("contexts")
        assertTrue(contexts.has("os"))
        assertFalse(contexts.has("app"))
    }

    @Test
    fun factory_reporter_delivers_off_the_calling_thread() {
        server.hold = CountDownLatch(1)
        val reporter = factoryReporter(server.dsn)
        assertIs<IsolatedSentryErrorReporter>(reporter)

        reporter.capture("mixpanel_delivery", IllegalStateException("boom"))

        // The server is holding its response, so a synchronous send could not have returned yet.
        val request = server.takeRequest()
        assertEquals("mixpanel_delivery", request.event.getJSONObject("tags").getString("operation"))
        server.hold?.countDown()
    }

    @Test
    fun factory_reporter_drops_events_once_its_bounded_queue_is_full() {
        server.hold = CountDownLatch(1)
        val reporter = factoryReporter(server.dsn)

        reporter.capture("in_flight", IllegalStateException("boom"))
        server.takeRequest()
        repeat(SentryEnvelopeClient.MAX_QUEUED_EVENTS + 10) {
            reporter.capture("queued_$it", IllegalStateException("boom"))
        }
        server.hold?.countDown()

        val delivered = List(SentryEnvelopeClient.MAX_QUEUED_EVENTS) { server.takeRequest() }
        assertNull(server.requests.poll(500, TimeUnit.MILLISECONDS), "more events than the queue bound were sent")
        // The ten rejected events are reported by the first queued event to go out, then reset.
        val counts = delivered.map { it.event.getJSONObject("extra").optInt("dropped_since_last_send", 0) }
        assertEquals(listOf(10) + List(SentryEnvelopeClient.MAX_QUEUED_EVENTS - 1) { 0 }, counts)
    }

    /**
     * Opt-in delivery to a real Sentry project. Skipped unless GLOMO_SENTRY_LIVE_DSN is set. The
     * event is marked as test traffic (operation, tag, message, fake session, order and release) so
     * it can be found and resolved. Two drops are forced locally first, through a one-second rate
     * limit window, so the live event carries `dropped_since_last_send`; nothing extra reaches
     * Sentry. Run it with a single variant task, e.g. testDebugUnitTest, or it sends once per variant.
     */
    @Test
    fun live_delivery_to_a_real_sentry_project() {
        val liveDsn = System.getenv("GLOMO_SENTRY_LIVE_DSN").orEmpty()
        assumeTrue("GLOMO_SENTRY_LIVE_DSN not set", liveDsn.isNotBlank())
        val dsn = SentryDsn.parse(liveDsn) ?: error("GLOMO_SENTRY_LIVE_DSN is not a valid DSN")
        val sentAt = System.currentTimeMillis()
        val version = "0.0.0-delivery-test"
        IsolatedSentryErrorReporter(
            client = SentryEnvelopeClient(
                dsn = SentryDsn.parse(server.dsn) ?: error("invalid test DSN"),
                clientName = "glomo-android-sdk/$version",
                executor = Executor(Runnable::run),
            ),
            sdkVersion = version,
            sessionId = "delivery-test-$sentAt",
            initialFlowType = "auto",
            devMode = true,
            orderId = "order_delivery_test_$sentAt",
            contexts = SentryContexts.fromBuild(appVersion = null, appBuild = null),
        ).capture("delivery_test", IllegalStateException("delivery test"))
        val event = server.takeRequest().event
        event.getJSONObject("tags").put("delivery_test", "true")
        event.put("message", JSONObject().put("formatted", "GlomoPay SDK delivery test - safe to resolve"))
        val eventId = event.getString("event_id")

        var limiterNow = sentAt
        val limiter = SentryRateLimiter { limiterNow }
        limiter.update(429) { name -> if (name.equals("Retry-After", ignoreCase = true)) "1" else null }
        val live = SentryEnvelopeClient(
            dsn = dsn,
            clientName = "glomo-android-sdk/$version",
            executor = Executor(Runnable::run),
            rateLimiter = limiter,
        )
        repeat(2) { live.send("f".repeat(32), JSONObject()) }
        limiterNow += 2_000

        val delivery = live.deliverEvent(eventId, event)

        val ist = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS 'IST'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata") }
            .format(java.util.Date(sentAt))
        println(
            "delivery_test event_id=$eventId status=${delivery.statusCode} sent_at=$ist " +
                "dropped_since_last_send=${event.getJSONObject("extra").opt("dropped_since_last_send")}",
        )
        assertEquals(200, delivery.statusCode)
        assertEquals(2, event.getJSONObject("extra").getInt("dropped_since_last_send"))
        assertEquals(SentrySendResult.SENT, delivery.result)
        assertEquals(eventId, JSONObject(delivery.responseBody.orEmpty()).getString("id"))
    }

    private fun reporter(
        dsn: String = server.dsn,
        sessionId: String = "session-uuid",
        flowType: String = "auto",
        rateLimiter: SentryRateLimiter = SentryRateLimiter(),
        timeoutMillis: Int = SentryEnvelopeClient.TIMEOUT_MILLIS,
        contexts: SentryContexts = SentryContexts(),
        orderId: String? = null,
    ): SdkErrorReporter = IsolatedSentryErrorReporter(
        client = SentryEnvelopeClient(
            dsn = SentryDsn.parse(dsn) ?: error("invalid test DSN"),
            clientName = "glomo-android-sdk/1.2.3",
            executor = Executor(Runnable::run),
            rateLimiter = rateLimiter,
            timeoutMillis = timeoutMillis,
        ),
        sdkVersion = "1.2.3",
        sessionId = sessionId,
        initialFlowType = flowType,
        devMode = false,
        orderId = orderId,
        contexts = contexts,
    )

    private fun factoryReporter(dsn: String): SdkErrorReporter = factoryReporter(TestEnvironment(dsn = { dsn }))

    private fun factoryReporter(
        environment: ReporterEnvironment,
        config: GlomoPayConfig = GlomoPayConfig(publicKey = "test_public_key", orderId = "order_synthetic_1"),
    ): SdkErrorReporter = SdkErrorReporterFactory.create(
        environment = environment,
        config = config,
        sessionId = "session-uuid",
        flowType = "auto",
        devMode = false,
    )

    /** Stands in for the host app's resources and PackageManager, the one boundary a JVM test lacks. */
    private class TestEnvironment(
        private val dsn: () -> String,
        private val version: () -> String = { "4.5.6" },
        private val app: () -> HostAppVersion = { HostAppVersion(null, null) },
    ) : ReporterEnvironment {
        override fun sentryDsn(): String = dsn()

        override fun sdkVersion(): String = version()

        override fun hostAppVersion(): HostAppVersion = app()
    }

    private fun JSONObject.toMap(): Map<String, Any?> = keySet().associateWith { get(it) }

    private companion object {
        val ISO_UTC = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z""")
    }
}
