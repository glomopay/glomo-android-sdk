package com.glomopay.sdk.android

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONArray
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The page's message sequences, replayed verbatim into a real checkout.
 *
 * Router-level tests cannot stand in for these: the Activity stops the router at every ending, so
 * anything a test drives through the router alone asserts a state production never reaches.
 */
private fun sequence(name: String): List<String> {
    val url = requireNotNull(CheckoutStageEventsActivityTest::class.java.classLoader)
        .getResource("checkout-sequences/$name.json")
    val array = JSONArray(requireNotNull(url).readText())
    return (0 until array.length()).map { array.getJSONObject(it).toString() }
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

    private fun replay(name: String, listener: StageRecordingListener): RecordedCheckout {
        val checkout = RecordedCheckout.open(host, config, listener)
        sequence(name).forEach(checkout::postRaw)
        return checkout
    }

    /** The contract's own invariant, asserted everywhere rather than in one test. */
    private fun StageRecordingListener.assertCloseIsLastAndSingle() {
        assertTrue(log.count { it.startsWith("closed:") } <= 1, "more than one close: $log")
        if (log.any { it.startsWith("closed:") }) {
            assertTrue(log.last().startsWith("closed:"), "close was not the last event: $log")
        }
    }

    @Test
    fun a_successful_payment_ends_with_a_completed_close_after_the_outcome() {
        val listener = StageRecordingListener()
        val checkout = replay("success", listener)

        assertEquals(
            listOf("stage:awaiting_confirmation", "stage:confirmed", "success", "closed:completed:confirmed"),
            listener.log,
        )
        listener.assertCloseIsLastAndSingle()
        assertTrue(checkout.activity.isFinishing)
    }

    /**
     * Today's page order, pinned as it is rather than as it should be: the page posts
     * `payment.failure` before the `failed` stage, the Activity finishes on the outcome, and the
     * stage behind it never arrives. Delete this with its fixture once the page sends `failed`
     * first - `decline-fixed` below is the same journey in that order.
     */
    @Test
    fun a_declined_payment_closes_as_an_error_quoting_the_stage_that_reached_the_sdk() {
        val listener = StageRecordingListener()
        replay("decline-current", listener)

        assertEquals(
            listOf("stage:awaiting_confirmation", "failure", "closed:error:awaiting_confirmation"),
            listener.log,
        )
        listener.assertCloseIsLastAndSingle()
    }

    @Test
    fun a_declined_payment_quotes_the_failed_stage_once_the_page_sends_it_first() {
        val listener = StageRecordingListener()
        replay("decline-fixed", listener)

        assertEquals(
            listOf("stage:awaiting_confirmation", "stage:failed", "failure", "closed:error:failed"),
            listener.log,
        )
        listener.assertCloseIsLastAndSingle()
    }

    @Test
    fun a_host_closing_the_checkout_from_on_payment_success_still_gets_exactly_one_close() {
        val listener = StageRecordingListener()
        val checkout = RecordedCheckout.open(host, config, listener)
        listener.onSuccess = { checkout.handle.close() }

        sequence("success").forEach(checkout::postRaw)

        assertEquals(
            listOf("stage:awaiting_confirmation", "stage:confirmed", "success", "closed:completed:confirmed"),
            listener.log,
        )
        listener.assertCloseIsLastAndSingle()
    }

    @Test
    fun a_page_cancel_reports_the_termination_and_then_the_page_s_own_reason() {
        val listener = StageRecordingListener()
        replay("page-cancel", listener)

        assertEquals(
            listOf("stage:customer_auth", "terminate:USER_DISMISS", "closed:user_cancelled:customer_auth"),
            listener.log,
        )
        listener.assertCloseIsLastAndSingle()
    }

    @Test
    fun a_close_with_no_payload_is_the_flag_being_off_and_reports_only_the_termination() {
        val listener = StageRecordingListener()
        replay("flag-off-close", listener)

        assertEquals(listOf("terminate:USER_DISMISS"), listener.log)
    }

    @Test
    fun empty_payloads_deliver_neither_callback() {
        val listener = StageRecordingListener()
        replay("empty-payload", listener)

        assertTrue(listener.log.none { it.startsWith("stage:") || it.startsWith("closed:") }, "${listener.log}")
    }

    @Test
    @Suppress("DEPRECATION")
    fun a_back_press_after_a_confirmed_stage_closes_as_completed() {
        val listener = StageRecordingListener()
        val checkout = RecordedCheckout.open(host, config, listener)
        checkout.post(stageEnvelope(stageChanged("stage" to "confirmed", "step" to null)))

        checkout.activity.onBackPressed()

        assertEquals("closed:completed:confirmed", listener.log.last())
        listener.assertCloseIsLastAndSingle()
    }

    @Test
    fun leaving_the_sdk_s_own_error_screen_closes_as_an_error() {
        val listener = StageRecordingListener()
        val checkout = RecordedCheckout.open(host, config, listener)
        checkout.post(stageEnvelope(stageChanged()))

        checkout.timeOut()
        checkout.tap("Cancel")

        assertEquals("closed:error:customer_auth", listener.log.last())
        listener.assertCloseIsLastAndSingle()
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
        listener.assertCloseIsLastAndSingle()
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

    @Test
    @Suppress("DEPRECATION")
    fun a_close_quotes_a_stage_the_sdk_does_not_recognise_when_the_page_sent_it_last() {
        val listener = StageRecordingListener()
        val checkout = RecordedCheckout.open(host, config, listener)
        checkout.post(stageEnvelope(stageChanged()))
        checkout.post(stageEnvelope(stageChanged("stage" to "teleported", "step" to null)))

        checkout.activity.onBackPressed()

        val closed = listener.closes.single()
        assertEquals("teleported", closed.lastStage)
        assertEquals(null, closed.lastStep)
        assertEquals("jrn_abc123", closed.journeyId, "the envelope still comes from a validated payload")
    }
}
