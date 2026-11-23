package com.glomopay.sdk.android

import com.glomopay.sdk.android.bridge.GlomoPayEventRouter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A payment stage change as `events-table.md` describes it, with every optional field populated.
 */
private fun stageChanged(vararg overrides: Pair<String, Any?>): Map<String, Any?> = buildMap {
    put("v", 1)
    put("method", "lrs")
    put("resource", mapOf("type" to "order", "id" to "order_abc123"))
    put("paymentId", "pay_abc123")
    put("journeyId", "jrn_abc123")
    put("at", "2026-11-23T06:00:00.000Z")
    put("flow", "payment")
    put("stage", "customer_auth")
    put("step", "otp")
    put("provider", "HDFC")
    put("journeyType", "native")
    overrides.forEach { (key, value) -> if (value == null) remove(key) else put(key, value) }
}

private fun stageEnvelope(payload: Map<String, Any?>?): Map<String, Any?> = mapOf(
    "type" to "message",
    "data" to buildMap<String, Any?> {
        put("type", "checkout.stage_changed")
        if (payload != null) put("payload", payload)
    },
)

private fun closedEnvelope(payload: Map<String, Any?>?): Map<String, Any?> = mapOf(
    "type" to "message",
    "data" to buildMap<String, Any?> {
        put("type", "checkout.closed")
        if (payload != null) put("payload", payload)
    },
)

class CheckoutStageEventsTest {
    private fun router(listener: StageRecordingListener) =
        GlomoPayEventRouter(listener, devMode = false, onComplete = {})

    @Test
    fun a_stage_change_reaches_the_merchant_with_every_field_the_page_sent() {
        val listener = StageRecordingListener()

        router(listener).handleEnvelope(stageEnvelope(stageChanged()))

        val event = listener.stages.single()
        assertEquals(1, event.version)
        assertEquals("customer_auth", event.stage)
        assertEquals("otp", event.step)
        assertEquals("HDFC", event.provider)
        assertEquals("native", event.journeyType)
        assertEquals("lrs", event.method)
        assertEquals("pay_abc123", event.paymentId)
        assertEquals("jrn_abc123", event.journeyId)
        assertEquals("order", event.resource.type)
        assertEquals("order_abc123", event.resource.id)
    }

    @Test
    fun an_unrecognised_stage_still_reaches_the_merchant() {
        val listener = StageRecordingListener()

        router(listener).handleEnvelope(stageEnvelope(stageChanged("stage" to "teleported")))

        assertEquals("teleported", listener.stages.single().stage)
    }

    @Test
    fun a_stage_change_with_no_payload_is_ignored_rather_than_delivered_empty() {
        val listener = StageRecordingListener()

        router(listener).handleEnvelope(stageEnvelope(null))

        assertTrue(listener.stages.isEmpty())
    }

    @Test
    fun every_stage_reaches_the_merchant_in_the_order_the_page_sent_them() {
        val listener = StageRecordingListener()
        val router = router(listener)

        router.handleEnvelope(stageEnvelope(stageChanged("stage" to "opened", "step" to "consent")))
        router.handleEnvelope(stageEnvelope(stageChanged("stage" to "method_selected", "step" to null)))
        router.handleEnvelope(stageEnvelope(stageChanged("stage" to "customer_auth", "step" to "otp")))

        assertEquals(listOf("opened", "method_selected", "customer_auth"), listener.stages.map { it.stage })
    }

    @Test
    fun a_page_sent_close_delivers_its_payload_and_still_terminates_as_before() {
        val listener = StageRecordingListener()
        val closed = mapOf(
            "v" to 1,
            "resource" to mapOf("type" to "order", "id" to "order_abc123"),
            "journeyId" to "jrn_abc123",
            "at" to "2026-11-23T06:05:00.000Z",
            "flow" to "payment",
            "lastStage" to "customer_auth",
            "lastStep" to "otp",
            "reason" to "user_cancelled",
        )

        router(listener).handleEnvelope(closedEnvelope(closed))

        val event = listener.closes.single()
        assertEquals("user_cancelled", event.reason)
        assertEquals("customer_auth", event.lastStage)
        assertEquals("otp", event.lastStep)
        assertEquals(listOf(TerminationSource.USER_DISMISS), listener.termination)
    }

    /**
     * The switch-off path. With checkout progress events off for the merchant the page still posts
     * the close, with nothing in it, so the pre-v1 behaviour has to survive unchanged.
     */
    @Test
    fun a_close_with_no_payload_terminates_exactly_as_it_did_before() {
        val listener = StageRecordingListener()

        router(listener).handleEnvelope(closedEnvelope(null))

        assertTrue(listener.closes.isEmpty())
        assertEquals(listOf(TerminationSource.USER_DISMISS), listener.termination)
    }

    @Test
    fun the_legacy_cancel_names_do_not_fabricate_a_close_event() {
        for (eventName in listOf("payment.cancelled", "cancelled")) {
            val listener = StageRecordingListener()

            router(listener).handleEnvelope(
                mapOf("type" to "message", "data" to mapOf("type" to eventName, "payload" to mapOf("foo" to "bar"))),
            )

            assertTrue(listener.closes.isEmpty(), "$eventName must not produce a v1 close")
            assertEquals(listOf(TerminationSource.USER_DISMISS), listener.termination)
        }
    }

    @Test
    fun a_native_dismissal_writes_the_close_quoting_the_last_stage() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged()))

        router.deliverNativeClose(GlomoCheckoutCloseReasons.USER_DISMISSED)

        val event = listener.closes.single()
        assertEquals("user_dismissed", event.reason)
        assertEquals("customer_auth", event.lastStage)
        assertEquals("otp", event.lastStep)
        assertEquals("jrn_abc123", event.journeyId)
        assertEquals("order_abc123", event.resource.id)
        assertTrue(event.at.isNotEmpty())
        assertTrue(event.at != "2026-11-23T06:00:00.000Z", "the close is timestamped, not the stage")
    }

    /**
     * The dismissal path is reachable from more than one place - terminateCheckout and the destroy
     * that never went through it - and neither sets the router's terminal flag, so the one-close
     * rule here rests on its own latch rather than on the terminal guard above it.
     */
    @Test
    fun the_close_is_written_once_however_many_times_the_dismissal_path_fires() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged()))

        router.deliverNativeClose(GlomoCheckoutCloseReasons.USER_DISMISSED)
        router.deliverNativeClose(GlomoCheckoutCloseReasons.USER_DISMISSED)

        assertEquals(1, listener.closes.size)
    }

    @Test
    fun a_native_dismissal_stays_silent_when_no_stage_ever_arrived() {
        val listener = StageRecordingListener()

        router(listener).deliverNativeClose(GlomoCheckoutCloseReasons.USER_DISMISSED)

        assertTrue(listener.closes.isEmpty())
    }

    @Test
    fun a_native_dismissal_stays_silent_when_the_only_stage_was_unrecognised() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged("stage" to "teleported")))

        router.deliverNativeClose(GlomoCheckoutCloseReasons.USER_DISMISSED)

        assertEquals(1, listener.stages.size)
        assertTrue(listener.closes.isEmpty())
    }

    @Test
    fun a_native_dismissal_adds_nothing_after_the_page_already_closed() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged()))
        router.handleEnvelope(
            closedEnvelope(
                mapOf(
                    "v" to 1,
                    "resource" to mapOf("type" to "order", "id" to "order_abc123"),
                    "journeyId" to "jrn_abc123",
                    "at" to "2026-11-23T06:05:00.000Z",
                    "flow" to "payment",
                    "reason" to "user_cancelled",
                ),
            ),
        )

        router.deliverNativeClose(GlomoCheckoutCloseReasons.USER_DISMISSED)

        assertEquals("user_cancelled", listener.closes.single().reason)
    }

    @Test
    fun a_native_dismissal_adds_nothing_after_a_payment_outcome_was_delivered() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged()))
        router.handleEnvelope(
            mapOf(
                "type" to "message",
                "data" to mapOf(
                    "type" to "payment.success",
                    "payload" to mapOf("orderId" to "order_abc123", "paymentId" to "pay_1", "signature" to "sig"),
                ),
            ),
        )

        router.deliverNativeClose(GlomoCheckoutCloseReasons.USER_DISMISSED)

        assertEquals(1, listener.success.size)
        assertTrue(listener.closes.isEmpty())
    }

    /**
     * A checkout closed by host code has no honest reason in the contract - `merchant_closed` was
     * dropped before it shipped - so nothing is sent.
     */
    @Test
    fun a_close_with_no_contract_reason_says_nothing() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged()))

        router.deliverNativeClose(
            GlomoCheckoutCloseReasons.forTermination(TerminationSource.PROGRAMMATIC),
        )

        assertTrue(listener.closes.isEmpty())
    }

    @Test
    fun each_termination_source_maps_to_the_only_honest_contract_reason() {
        assertEquals(
            "user_dismissed",
            GlomoCheckoutCloseReasons.forTermination(TerminationSource.USER_DISMISS),
        )
        assertEquals(
            "user_dismissed",
            GlomoCheckoutCloseReasons.forTermination(TerminationSource.BACK_BUTTON),
        )
        assertEquals(
            "error",
            GlomoCheckoutCloseReasons.forTermination(TerminationSource.CONNECTION_ERROR),
        )
        assertNull(GlomoCheckoutCloseReasons.forTermination(TerminationSource.PROGRAMMATIC))
    }

    @Test
    fun a_recognised_kyc_stage_is_remembered_even_though_lrs_never_sends_one() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged("flow" to "kyc", "stage" to "under_review", "step" to null)))

        router.deliverNativeClose(GlomoCheckoutCloseReasons.USER_DISMISSED)

        assertEquals("under_review", listener.closes.single().lastStage)
    }

    @Test
    fun a_kyc_only_stage_on_the_payment_flow_is_forwarded_but_not_remembered() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged("stage" to "under_review")))

        router.deliverNativeClose(GlomoCheckoutCloseReasons.USER_DISMISSED)

        assertEquals("under_review", listener.stages.single().stage)
        assertTrue(listener.closes.isEmpty())
    }
}

class CheckoutEventParsingTest {
    @Test
    fun the_segment_and_the_raw_payload_survive_parsing() {
        val payload = stageChanged("segment" to mapOf("index" to 2, "total" to 3), "errorCode" to null)

        val event = GlomoCheckoutStageChangedEvent.fromMap(payload)

        assertEquals(2, event.segment?.index)
        assertEquals(3, event.segment?.total)
        assertEquals(payload, event.rawPayload)
    }

    @Test
    fun a_malformed_payload_parses_to_empties_rather_than_throwing() {
        val event = GlomoCheckoutStageChangedEvent.fromMap(mapOf("stage" to "opened"))

        assertEquals(0, event.version)
        assertEquals("", event.journeyId)
        assertEquals("", event.resource.id)
        assertEquals("opened", event.stage)
        assertNull(event.step)
    }

    @Test
    fun an_empty_optional_reads_as_absent_rather_than_as_an_empty_string() {
        val event = GlomoCheckoutStageChangedEvent.fromMap(stageChanged("step" to "", "provider" to ""))

        assertNull(event.step)
        assertNull(event.provider)
    }

    @Test
    fun a_segment_missing_a_bound_is_dropped_rather_than_half_built() {
        val event = GlomoCheckoutStageChangedEvent.fromMap(stageChanged("segment" to mapOf("index" to 2)))

        assertNull(event.segment)
    }

    @Test
    fun a_closed_payload_parses_its_optional_last_step() {
        val event = GlomoCheckoutClosedEvent.fromMap(
            mapOf(
                "v" to 1,
                "resource" to mapOf("type" to "order", "id" to "order_1"),
                "journeyId" to "jrn_1",
                "at" to "2026-11-23T06:05:00.000Z",
                "flow" to "payment",
                "reason" to "confirmation_timeout",
            ),
        )

        assertEquals("confirmation_timeout", event.reason)
        assertNull(event.lastStage)
        assertNull(event.lastStep)
    }
}

private class StageRecordingListener : GlomoPayListener {
    val stages = mutableListOf<GlomoCheckoutStageChangedEvent>()
    val closes = mutableListOf<GlomoCheckoutClosedEvent>()
    val termination = mutableListOf<TerminationSource>()
    val success = mutableListOf<GlomoPayPayload>()

    override fun onPaymentSuccess(payload: GlomoPayPayload) { success += payload }
    override fun onPaymentFailure(payload: GlomoPayPayload) = Unit
    override fun onSdkError(errors: List<SdkError>) = Unit
    override fun onConnectionError(error: ConnectionError) = Unit
    override fun onUserJourneyCompleted(payload: GlomoPayUserJourneyPayload) = Unit
    override fun onPaymentTerminate(source: TerminationSource) { termination += source }
    override fun onCheckoutStageChanged(event: GlomoCheckoutStageChangedEvent) { stages += event }
    override fun onCheckoutClosed(event: GlomoCheckoutClosedEvent) { closes += event }
}

/**
 * The Activity half: a back press has to reach the router's synthesised close through the real
 * wiring, not just through a direct call on the router.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CheckoutStageEventsActivityTest {
    private val host = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
    private val config = GlomoPayConfig(publicKey = "test_public_key", orderId = "order_abc123")

    @AfterTest
    fun tearDown() {
        RecordedCheckout.reset()
    }

    @Test
    @Suppress("DEPRECATION")
    fun a_back_press_reports_the_last_stage_the_customer_reached() {
        val listener = StageRecordingListener()
        val checkout = RecordedCheckout.open(host, config, listener)
        checkout.post(stageEnvelope(stageChanged()))

        checkout.activity.onBackPressed()

        val closed = listener.closes.single()
        assertEquals("user_dismissed", closed.reason)
        assertEquals("customer_auth", closed.lastStage)
        assertEquals("otp", closed.lastStep)
        assertEquals(listOf(TerminationSource.BACK_BUTTON), listener.termination)
    }

    @Test
    @Suppress("DEPRECATION")
    fun a_back_press_with_no_stage_behind_it_reports_only_the_termination() {
        val listener = StageRecordingListener()
        val checkout = RecordedCheckout.open(host, config, listener)

        checkout.activity.onBackPressed()

        assertTrue(listener.closes.isEmpty())
        assertEquals(listOf(TerminationSource.BACK_BUTTON), listener.termination)
    }
}
