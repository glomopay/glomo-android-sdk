package com.glomopay.sdk.android.monitoring

import android.content.Context
import com.glomopay.sdk.android.R
import androidx.core.content.pm.PackageInfoCompat

/**
 * What [SdkErrorReporterFactory] reads from the host app: the build-time DSN and SDK version
 * resources and the host app's own version. Any lookup may throw; the factory decides what a
 * failure means. Kept as a seam so the production construction path runs in JVM unit tests.
 */
internal interface ReporterEnvironment {
    fun sentryDsn(): String

    fun sdkVersion(): String

    fun hostAppVersion(): HostAppVersion
}

internal data class HostAppVersion(val versionName: String?, val versionCode: String?)

internal class AndroidReporterEnvironment(private val context: Context) : ReporterEnvironment {
    override fun sentryDsn(): String = context.getString(R.string.glomopay_sentry_dsn)

    override fun sdkVersion(): String = context.getString(R.string.glomopay_sdk_version)

    override fun hostAppVersion(): HostAppVersion {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return HostAppVersion(info.versionName, PackageInfoCompat.getLongVersionCode(info).toString())
    }
}
