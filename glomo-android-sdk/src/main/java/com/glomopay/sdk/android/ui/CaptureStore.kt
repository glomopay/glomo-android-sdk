package com.glomopay.sdk.android.ui

import com.glomopay.sdk.android.analytics.GlomoPayLogger
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Where camera captures (KYC document photos) live on disk, one subdirectory per checkout.
 *
 * Normal paths delete a capture once it is used or abandoned, but a crash, a process death or a
 * kill mid-upload skips them. So [open] sweeps the capture root before the checkout can take a
 * photo: every entry not owned by a checkout still live in this process is deleted. Ownership is
 * the per-checkout subdirectory, so "stale" is unambiguous: anything left by an earlier process,
 * or by a checkout that ended without cleaning up, is not registered and goes.
 *
 * Nothing here throws into checkout. A sweep that fails is logged and skipped; a store whose
 * directory cannot be used still opens, and [newCapture] then fails with an IOException the
 * caller already handles as "camera unavailable".
 */
internal class CaptureStore private constructor(private val directory: File) {
    /** A new, empty file for the camera app to write into. */
    @Throws(IOException::class)
    fun newCapture(): File {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Capture directory unavailable")
        }
        return File.createTempFile("capture-", ".jpg", directory)
    }

    /** Deletes this checkout's captures and releases its directory. Never throws. */
    fun close() {
        runCatching { directory.deleteRecursively() }
            .onFailure { GlomoPayLogger.error("Unable to delete checkout captures", it) }
        live.remove(directory.absolutePath)
    }

    companion object {
        const val DIRECTORY: String = "glomopay-capture"

        /** Directories owned by checkouts that are still open in this process. */
        private val live: MutableSet<String> = ConcurrentHashMap.newKeySet()

        /** Claims a directory for one checkout, then sweeps everything under [root] it does not own. */
        fun open(root: File): CaptureStore {
            val directory = File(root, "checkout-" + UUID.randomUUID())
            // Registered before the sweep, so a concurrent open can never sweep it.
            live.add(directory.absolutePath)
            sweep(root)
            return CaptureStore(directory)
        }

        private fun sweep(root: File) {
            runCatching {
                root.listFiles()?.forEach { entry ->
                    if (entry.absolutePath in live) return@forEach
                    runCatching { entry.deleteRecursively() }
                        .onFailure { GlomoPayLogger.error("Unable to delete a stale capture", it) }
                }
            }.onFailure { GlomoPayLogger.error("Unable to sweep stale captures", it) }
        }
    }
}
