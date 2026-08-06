package com.glomopay.sdk.security

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.scottyab.rootbeer.RootBeer
import java.io.File

/** Android counterpart of the Flutter safe_device isJailBroken check. */
internal object DeviceComplianceChecker {
    fun check(context: Context, strict: Boolean): DeviceComplianceResult {
        val developerMode = readGlobalSetting(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)
        val usbDebugging = readGlobalSetting(context, Settings.Global.ADB_ENABLED)
        val emulator = isEmulator()
        val testKeys = hasTestKeys()

        if (!strict) {
            return DeviceComplianceResult(
                isCompliant = true,
                isRooted = false,
                isEmulator = emulator,
                isDeveloperModeEnabled = developerMode,
                isUsbDebuggingEnabled = usbDebugging,
                hasTestKeys = testKeys,
                checksSkipped = true,
            )
        }

        // safe_device's isJailBroken is the enforcement signal. Developer
        // options alone are reported for diagnostics but are not root evidence.
        val rooted = RootBeer(context).isRooted ||
            hasKnownRootFiles() ||
            hasSuBinary() ||
            testKeys

        return DeviceComplianceResult(
            isCompliant = !rooted,
            isRooted = rooted,
            isEmulator = emulator,
            isDeveloperModeEnabled = developerMode,
            isUsbDebuggingEnabled = usbDebugging,
            hasTestKeys = testKeys,
            checksSkipped = false,
        )
    }

    private fun readGlobalSetting(context: Context, key: String): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, key, 0) != 0
    }.getOrDefault(false)

    private fun hasTestKeys(): Boolean = Build.TAGS?.contains("test-keys") == true

    private fun hasKnownRootFiles(): Boolean = ROOT_PATHS.any { File(it).exists() }

    private fun hasSuBinary(): Boolean = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
        try {
            process.inputStream.bufferedReader().use { reader -> reader.readLine() != null }
        } finally {
            process.destroy()
        }
    }.getOrDefault(false)

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true) ||
            Build.PRODUCT.contains("google_sdk", ignoreCase = true)

    private val ROOT_PATHS = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
    )
}
