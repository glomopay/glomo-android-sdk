package com.glomopay.sdk.android.carousel

import org.json.JSONObject

internal enum class EducationCarouselState {
    PENDING,
    HAS_CONTENT,
    NO_CONTENT,
}

internal data class EducationCarouselLayout(
    val showCarousel: Boolean,
    val carouselWeight: Float,
    val paymentWeight: Float,
)

internal object EducationCarouselContract {
    const val EVENT_NAME = "lrs.has_education_steps"

    fun parseAvailabilitySignal(rawMessage: String): Boolean? = runCatching {
        val message = JSONObject(rawMessage)
        val data = message.keys().asSequence().associateWith { key ->
            message.opt(key).takeUnless { it == JSONObject.NULL }
        }
        availabilitySignal(data)
    }.getOrNull()

    fun availabilitySignal(data: Map<String, Any?>): Boolean? {
        if (data["event"] != EVENT_NAME) return null
        return data["hasContent"] as? Boolean
    }

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
