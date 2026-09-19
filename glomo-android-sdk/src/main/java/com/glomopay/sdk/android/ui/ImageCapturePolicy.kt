package com.glomopay.sdk.android.ui

internal object ImageCapturePolicy {
    fun acceptsImages(types: List<String>): Boolean {
        val accepted = types.flatMap { it.split(',') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        return accepted.isNotEmpty() && accepted.all {
            it.startsWith("image/") || it in setOf(".jpg", ".jpeg", ".png", ".webp", ".heic")
        }
    }
}
