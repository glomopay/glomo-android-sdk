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

    fun parseHasContent(rawMessage: String): Boolean? = runCatching {
        val message = JSONObject(rawMessage)
        val data = message.keys().asSequence().associateWith { key ->
            message.opt(key).takeUnless { it == JSONObject.NULL }
        }
        hasContent(data)
    }.getOrNull()

    fun hasContent(data: Map<String, Any?>): Boolean? {
        val eventName = data["type"]?.toString() ?: data["event"]?.toString()
        if (eventName != EVENT_NAME) return null
        return booleanValue(data["value"] ?: data["hasContent"])
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

    private fun booleanValue(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when {
            value.equals("true", ignoreCase = true) -> true
            value.equals("false", ignoreCase = true) -> false
            else -> null
        }
        else -> null
    }
}
