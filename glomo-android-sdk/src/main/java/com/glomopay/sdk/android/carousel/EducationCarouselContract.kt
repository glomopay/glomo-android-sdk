package com.glomopay.sdk.android.carousel

import org.json.JSONObject

/** Hidden until the page signals content. There is no "no content" signal, so no third state. */
internal enum class EducationCarouselState {
    PENDING,
    HAS_CONTENT,
}

internal data class EducationCarouselLayout(
    val showCarousel: Boolean,
    val carouselWeight: Float,
    val paymentWeight: Float,
)

/**
 * The education page's one signal: `{type: 'lrs.has_education_steps', value: true}`, from
 * glomopay-checkout `lrs-carousel.event-emitter.ts`. The page never emits `false` ("absence of
 * event signals 'no content' to native SDKs", `lrs-education-carousel.tsx:18`), matching the RN
 * SDK. So the carousel shows on that exact message and stays hidden otherwise, with no fallback.
 */
internal object EducationCarouselContract {
    const val EVENT_NAME = "lrs.has_education_steps"

    fun isShowSignal(rawMessage: String): Boolean = runCatching {
        val message = JSONObject(rawMessage)
        isShowSignal(message.keys().asSequence().associateWith { key -> message.opt(key) })
    }.getOrDefault(false)

    fun isShowSignal(data: Map<String, Any?>): Boolean =
        data["type"] == EVENT_NAME && data["value"] == true

    fun layout(
        state: EducationCarouselState,
        isLrsOrder: Boolean,
        isSubscription: Boolean,
    ): EducationCarouselLayout {
        val showCarousel = isLrsOrder && !isSubscription && state == EducationCarouselState.HAS_CONTENT
        return if (showCarousel) {
            EducationCarouselLayout(true, carouselWeight = 15f, paymentWeight = 85f)
        } else {
            EducationCarouselLayout(false, carouselWeight = 0f, paymentWeight = 100f)
        }
    }

}
