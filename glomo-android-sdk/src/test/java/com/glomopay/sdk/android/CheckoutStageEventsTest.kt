package com.glomopay.sdk.android

import com.glomopay.sdk.android.bridge.GlomoPayEventRouter
import com.glomopay.sdk.android.bridge.closedFrom
import com.glomopay.sdk.android.bridge.stageChangedFrom
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

private fun outcomeEnvelope(type: String): Map<String, Any?> = mapOf(
    "type" to "message",
    "data" to mapOf(
        "type" to type,
        "payload" to mapOf("orderId" to "order_abc123", "paymentId" to "pay_1", "signature" to "sig"),
    ),
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

    /** Checkout progress events off: the page posts the close with no payload. */
    @Test
    fun a_close_with_no_payload_reports_only_the_termination() {
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

        router.deliverNativeClose(TerminationSource.USER_DISMISS)

        val event = listener.closes.single()
        assertEquals("user_dismissed", event.reason)
        assertEquals("customer_auth", event.lastStage)
        assertEquals("otp", event.lastStep)
        assertEquals("jrn_abc123", event.journeyId)
        assertEquals("order_abc123", event.resource.id)
        assertTrue(event.at.isNotEmpty())
        assertTrue(event.at != "2026-11-23T06:00:00.000Z", "the close is timestamped, not the stage")
    }

    @Test
    fun the_close_is_written_once_however_many_times_the_dismissal_path_fires() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged()))

        router.deliverNativeClose(TerminationSource.USER_DISMISS)
        router.deliverNativeClose(TerminationSource.USER_DISMISS)

        assertEquals(1, listener.closes.size)
    }

    @Test
    fun a_native_dismissal_stays_silent_when_no_stage_ever_arrived() {
        val listener = StageRecordingListener()

        router(listener).deliverNativeClose(TerminationSource.USER_DISMISS)

        assertTrue(listener.closes.isEmpty())
    }

    @Test
    fun a_native_dismissal_stays_silent_when_the_only_stage_was_unrecognised() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged("stage" to "teleported")))

        router.deliverNativeClose(TerminationSource.USER_DISMISS)

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

        router.deliverNativeClose(TerminationSource.USER_DISMISS)

        assertEquals("user_cancelled", listener.closes.single().reason)
    }

    @Test
    fun a_close_the_host_asked_for_says_nothing() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged()))

        router.deliverNativeClose(TerminationSource.PROGRAMMATIC)

        assertTrue(listener.closes.isEmpty())
    }

    @Test
    fun a_successful_payment_closes_as_completed() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged()))

        router.handleEnvelope(outcomeEnvelope("payment.success"))

        assertEquals(1, listener.success.size)
        val closed = listener.closes.single()
        assertEquals(GlomoCheckoutCloseReasons.COMPLETED, closed.reason)
        assertEquals("customer_auth", closed.lastStage)
    }

    @Test
    fun a_failed_payment_closes_as_an_error() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged()))

        router.handleEnvelope(outcomeEnvelope("payment.failure"))

        assertEquals(GlomoCheckoutCloseReasons.ERROR, listener.closes.single().reason)
    }

    @Test
    fun a_dismissal_after_an_outcome_does_not_turn_it_into_an_abandonment() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged()))
        router.handleEnvelope(outcomeEnvelope("payment.success"))

        router.deliverNativeClose(TerminationSource.USER_DISMISS)

        assertEquals(listOf(GlomoCheckoutCloseReasons.COMPLETED), listener.closes.map { it.reason })
    }

    @Test
    fun a_submitted_bank_transfer_closes_as_completed() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged()))

        router.handleEnvelope(outcomeEnvelope("payment.bank_transfer_submitted"))

        assertEquals(GlomoCheckoutCloseReasons.COMPLETED, listener.closes.single().reason)
    }

    /**
     * The outcome lands while the only stage so far is one the SDK will not quote, so no close is
     * written at the outcome itself and the reason has to be read off the outcome later.
     */
    @Test
    fun an_outcome_outranks_the_stage_that_arrives_after_it() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged("stage" to "teleported")))
        router.handleEnvelope(outcomeEnvelope("payment.success"))
        assertTrue(listener.closes.isEmpty(), "nothing to quote back yet")

        router.handleEnvelope(stageEnvelope(stageChanged("stage" to "awaiting_confirmation", "step" to null)))
        router.deliverNativeClose(TerminationSource.USER_DISMISS)

        assertEquals(GlomoCheckoutCloseReasons.COMPLETED, listener.closes.single().reason)
    }

    /** The page reports the outcome first, so the terminal guard used to swallow this stage. */
    @Test
    fun the_failed_stage_still_reaches_the_merchant_after_the_outcome() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged()))
        router.handleEnvelope(outcomeEnvelope("payment.failure"))

        router.handleEnvelope(stageEnvelope(stageChanged("stage" to "failed", "step" to null)))

        assertEquals(listOf("customer_auth", "failed"), listener.stages.map { it.stage })
    }

    @Test
    fun a_confirmed_stage_closes_as_completed_without_any_outcome() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged("stage" to "confirmed", "step" to null)))

        router.deliverNativeClose(TerminationSource.USER_DISMISS)

        assertEquals(GlomoCheckoutCloseReasons.COMPLETED, listener.closes.single().reason)
    }

    @Test
    fun a_failed_stage_closes_as_an_error_without_any_outcome() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged("stage" to "failed", "step" to null)))

        router.deliverNativeClose(TerminationSource.USER_DISMISS)

        assertEquals(GlomoCheckoutCloseReasons.ERROR, listener.closes.single().reason)
    }

    @Test
    fun the_sdks_own_error_screen_closes_as_an_error() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged()))

        router.deliverNativeClose(TerminationSource.CONNECTION_ERROR)

        assertEquals(GlomoCheckoutCloseReasons.ERROR, listener.closes.single().reason)
    }

    @Test
    fun an_empty_stage_payload_is_not_a_v0_event() {
        val listener = StageRecordingListener()

        router(listener).handleEnvelope(stageEnvelope(emptyMap()))

        assertTrue(listener.stages.isEmpty())
    }

    @Test
    fun an_empty_close_payload_is_not_a_v0_event() {
        val listener = StageRecordingListener()

        router(listener).handleEnvelope(closedEnvelope(emptyMap()))

        assertTrue(listener.closes.isEmpty())
        assertEquals(listOf(TerminationSource.USER_DISMISS), listener.termination)
    }

    @Test
    fun a_payload_stamped_with_another_contract_version_is_dropped() {
        val listener = StageRecordingListener()
        val router = router(listener)

        router.handleEnvelope(stageEnvelope(stageChanged("v" to 2)))
        router.handleEnvelope(closedEnvelope(stageChanged("v" to 2, "reason" to "user_cancelled")))

        assertTrue(listener.stages.isEmpty())
        assertTrue(listener.closes.isEmpty())
    }

    @Test
    fun a_recognised_kyc_stage_is_remembered_even_though_lrs_never_sends_one() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged("flow" to "kyc", "stage" to "under_review", "step" to null)))

        router.deliverNativeClose(TerminationSource.USER_DISMISS)

        assertEquals("under_review", listener.closes.single().lastStage)
    }

    @Test
    fun a_kyc_only_stage_on_the_payment_flow_is_forwarded_but_not_remembered() {
        val listener = StageRecordingListener()
        val router = router(listener)
        router.handleEnvelope(stageEnvelope(stageChanged("stage" to "under_review")))

        router.deliverNativeClose(TerminationSource.USER_DISMISS)

        assertEquals("under_review", listener.stages.single().stage)
        assertTrue(listener.closes.isEmpty())
    }
}

class CheckoutEventParsingTest {
    @Test
    fun the_segment_and_the_raw_payload_survive_parsing() {
        val payload = stageChanged("segment" to mapOf("index" to 2, "total" to 3), "errorCode" to null)

        val event = stageChangedFrom(payload)

        assertEquals(2, event.segment?.index)
        assertEquals(3, event.segment?.total)
        assertEquals(payload, event.rawPayload)
    }

    @Test
    fun a_malformed_payload_parses_to_empties_rather_than_throwing() {
        val event = stageChangedFrom(mapOf("stage" to "opened"))

        assertEquals(0, event.version)
        assertEquals("", event.journeyId)
        assertEquals("", event.resource.id)
        assertEquals("opened", event.stage)
        assertNull(event.step)
    }

    @Test
    fun an_empty_optional_reads_as_absent_rather_than_as_an_empty_string() {
        val event = stageChangedFrom(stageChanged("step" to "", "provider" to ""))

        assertNull(event.step)
        assertNull(event.provider)
    }

    @Test
    fun a_segment_missing_a_bound_is_dropped_rather_than_half_built() {
        val event = stageChangedFrom(stageChanged("segment" to mapOf("index" to 2)))

        assertNull(event.segment)
    }

    @Test
    fun a_closed_payload_parses_its_optional_last_step() {
        val event = closedFrom(
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
