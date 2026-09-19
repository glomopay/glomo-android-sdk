package com.glomopay.sdk.android.webview

import java.util.Locale

internal object FlowNavigationPolicy {
    fun allows(url: String): Boolean =
        url.substringBefore(':', "").lowercase(Locale.ROOT) in setOf("http", "https", "about", "blob", "data")
}
