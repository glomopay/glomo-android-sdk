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
            EducationCarouselContract.parseHasContent(
                """{"type":"lrs.has_education_steps","value":true}""",
            ),
        )
    }

    @Test
    fun flutter_event_and_has_content_payload_remains_supported() {
        assertEquals(
            false,
            EducationCarouselContract.parseHasContent(
                """{"event":"lrs.has_education_steps","hasContent":false}""",
            ),
        )
    }

    @Test
    fun unrelated_or_incomplete_messages_do_not_change_state() {
        assertNull(EducationCarouselContract.parseHasContent("""{"type":"payment.pending"}"""))
        assertNull(EducationCarouselContract.parseHasContent("""{"type":"lrs.has_education_steps"}"""))
        assertNull(EducationCarouselContract.parseHasContent("not-json"))
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
        assertTrue(script.contains("parsed.type || parsed.event"))
        assertTrue(script.contains("parsed.value"))
        assertTrue(script.contains("parsed.hasContent"))
    }
}
