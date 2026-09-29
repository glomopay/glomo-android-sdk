package com.glomopay.sdk.android.monitoring

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject

/**
 * Device, OS and host-app facts attached to Sentry events for triage.
 *
 * Deliberately a subset of what the SDK already sends to Mixpanel (see AndroidAnalyticsProperties):
 * no new data category. Never add ANDROID_ID, advertising id, IP, the user-set device name,
 * locale, timezone, battery, memory, screen, installed packages or screen names.
 */
internal data class SentryContexts(
    val osVersion: String? = null,
    val apiLevel: Int? = null,
    val manufacturer: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val appVersion: String? = null,
    val appBuild: String? = null,
) {
    fun toJson(): JSONObject {
        val os = JSONObject().put("type", "os").put("name", "Android")
            .putIfPresent("version", osVersion)
            .apply { apiLevel?.takeIf { it > 0 }?.let { put("api_level", it) } }
        val device = JSONObject().put("type", "device")
            .putIfPresent("manufacturer", manufacturer)
            .putIfPresent("brand", brand)
            .putIfPresent("model", model)
        val app = JSONObject().put("type", "app")
            .putIfPresent("app_version", appVersion)
            .putIfPresent("app_build", appBuild)
        return JSONObject().put("os", os).apply {
            if (device.length() > 1) put("device", device)
            if (app.length() > 1) put("app", app)
        }
    }

    private fun JSONObject.putIfPresent(key: String, value: String?): JSONObject = apply {
        value?.trim()?.takeIf { it.isNotEmpty() }?.let { put(key, it.take(MAX_VALUE_LENGTH)) }
    }

    companion object {
        private const val MAX_VALUE_LENGTH = 100

        /** Reads android.os.Build. Null-safe: any field may be missing on an unusual build. */
        fun fromBuild(appVersion: String?, appBuild: String?): SentryContexts = SentryContexts(
            osVersion = runCatching { Build.VERSION.RELEASE }.getOrNull(),
            apiLevel = runCatching { Build.VERSION.SDK_INT }.getOrNull(),
            manufacturer = runCatching { Build.MANUFACTURER }.getOrNull(),
            brand = runCatching { Build.BRAND }.getOrNull(),
            model = runCatching { Build.MODEL }.getOrNull(),
            appVersion = appVersion,
            appBuild = appBuild,
        )

        @Volatile private var cached: SentryContexts? = null

        /** Collected once per process; none of these values change while the app runs. */
        fun get(context: Context): SentryContexts = cached ?: collect(context).also { cached = it }

        private fun collect(context: Context): SentryContexts {
            val packageInfo = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()
            return fromBuild(
                appVersion = packageInfo?.versionName,
                appBuild = packageInfo?.let { runCatching { PackageInfoCompat.getLongVersionCode(it).toString() }.getOrNull() },
            )
        }
    }
}
