package com.glomopay.sdk.android

import com.glomopay.sdk.android.carousel.EducationCarouselContract
import com.glomopay.sdk.android.carousel.EducationCarouselState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EducationCarouselContractTest {
    @Test
    fun the_live_page_signal_shows_the_carousel() {
        // Exactly what glomopay-checkout lrs-carousel.event-emitter.ts sends.
        assertTrue(EducationCarouselContract.isShowSignal("""{"type":"lrs.has_education_steps","value":true}"""))
    }

    @Test
    fun anything_other_than_the_live_signal_leaves_the_carousel_hidden() {
        listOf(
            // The page never emits false; if it did, it must not change anything.
            """{"type":"lrs.has_education_steps","value":false}""",
            // The Flutter-style shape is not part of the contract.
            """{"event":"lrs.has_education_steps","hasContent":true}""",
            """{"type":"lrs.has_education_steps","value":"true"}""",
            """{"type":"lrs.has_education_steps"}""",
            """{"type":"payment.pending","value":true}""",
            """{"type":"lrs.has_education_steps",""",
            "not-json",
            "",
        ).forEach { assertFalse(EducationCarouselContract.isShowSignal(it), "Expected hidden for <$it>") }
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
}
