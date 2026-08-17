package com.glomopay.sdk.android

import com.glomopay.sdk.android.bridge.GlomoPayInjectionScripts
import com.glomopay.sdk.android.carousel.EducationCarouselContract
import com.glomopay.sdk.android.carousel.EducationCarouselState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EducationCarouselContractTest {
    @Test
    fun documented_type_and_value_payload_is_supported() {
        assertEquals(
            true,
            EducationCarouselContract.parseAvailabilitySignal(
                """{"type":"lrs.has_education_steps","value":true}""",
            ),
        )
    }

    @Test
    fun legacy_and_explicit_false_payloads_are_ignored() {
        assertNull(EducationCarouselContract.parseAvailabilitySignal(
            """{"event":"lrs.has_education_steps","hasContent":true}""",
        ))
        assertNull(EducationCarouselContract.parseAvailabilitySignal(
            """{"type":"lrs.has_education_steps","value":false}""",
        ))
    }

    @Test
    fun unrelated_or_incomplete_messages_do_not_change_state() {
        assertNull(EducationCarouselContract.parseAvailabilitySignal("""{"type":"payment.pending"}"""))
        assertNull(EducationCarouselContract.parseAvailabilitySignal("""{"type":"lrs.has_education_steps"}"""))
        assertNull(EducationCarouselContract.parseAvailabilitySignal("not-json"))
    }

    @Test
    fun carousel_is_visible_only_for_lrs_content() {
        val visible = EducationCarouselContract.layout(
            state = EducationCarouselState.HAS_CONTENT,
            isLrsOrder = true,
            isSubscription = false,
        )
        val pending = EducationCarouselContract.layout(
            state = EducationCarouselState.PENDING,
            isLrsOrder = true,
            isSubscription = false,
        )
        val standard = EducationCarouselContract.layout(
            state = EducationCarouselState.HAS_CONTENT,
            isLrsOrder = false,
            isSubscription = false,
        )
        val subscription = EducationCarouselContract.layout(
            state = EducationCarouselState.HAS_CONTENT,
            isLrsOrder = true,
            isSubscription = true,
        )

        assertTrue(visible.showCarousel)
        assertEquals(15f, visible.carouselWeight)
        assertEquals(85f, visible.paymentWeight)
        assertFalse(pending.showCarousel)
        assertEquals(100f, pending.paymentWeight)
        assertFalse(standard.showCarousel)
        assertFalse(subscription.showCarousel)
    }

    @Test
    fun carousel_injection_intercepts_early_and_late_messages() {
        val script = GlomoPayInjectionScripts.carousel()

        assertTrue(script.contains("window.postMessage = function"))
        assertTrue(script.contains("window.addEventListener('message'"))
        assertTrue(script.contains("window.GlomoCarousel.postMessage"))
        assertTrue(script.contains("parsed.type !== 'lrs.has_education_steps'"))
        assertTrue(script.contains("parsed.value !== true"))
        assertFalse(script.contains("parsed.event"))
        assertFalse(script.contains("parsed.hasContent"))
    }
}
