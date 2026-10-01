package com.glomopay.sdk.android

import com.glomopay.sdk.android.ui.CaptureStore
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureStoreTest {
    private val cacheDir: File = Files.createTempDirectory("glomopay-cache").toFile()
    private val root = File(cacheDir, CaptureStore.DIRECTORY)

    @AfterTest
    fun cleanUp() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun captures_left_by_an_earlier_process_are_swept_when_a_checkout_opens() {
        // What a crash or kill mid-upload leaves behind, in both the old flat layout and the
        // per-checkout layout.
        val flat = File(root.apply { mkdirs() }, "capture-123.jpg").apply { writeText("kyc") }
        val nested = File(File(root, "checkout-from-dead-process").apply { mkdirs() }, "capture-456.jpg")
            .apply { writeText("kyc") }

        CaptureStore.open(root)

        assertFalse(flat.exists(), "stale flat capture survived")
        assertFalse(nested.exists(), "stale per-checkout capture survived")
        assertFalse(nested.parentFile!!.exists(), "stale checkout directory survived")
    }

    @Test
    fun a_capture_owned_by_a_live_checkout_survives_another_checkout_opening() {
        val active = CaptureStore.open(root)
        val inUse = active.newCapture().apply { writeText("being uploaded") }

        CaptureStore.open(root)

        assertTrue(inUse.exists(), "the capture currently in use was swept")
        active.close()
    }

    @Test
    fun closing_a_checkout_deletes_its_captures_and_makes_its_directory_sweepable() {
        val store = CaptureStore.open(root)
        val capture = store.newCapture().apply { writeText("kyc") }

        store.close()

        assertFalse(capture.exists())
        assertFalse(capture.parentFile!!.exists())
    }

    @Test
    fun an_unusable_capture_root_never_throws_into_checkout() {
        // A regular file where the directory should be: the sweep cannot list it and the
        // directory cannot be created.
        cacheDir.mkdirs()
        root.writeText("not a directory")

        val store = CaptureStore.open(root)

        // Fails the way the picker already handles, as camera unavailable, not with a crash.
        assertFailsWith<IOException> { store.newCapture() }
        store.close()
        assertTrue(root.isFile)
    }
}
