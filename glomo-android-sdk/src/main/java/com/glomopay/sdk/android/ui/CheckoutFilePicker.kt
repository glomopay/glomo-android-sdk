package com.glomopay.sdk.android.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class CheckoutFilePicker(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit,
    private val onPermissionRefused: (String) -> Unit = {},
) {
    private var callback: ValueCallback<Array<Uri>>? = null
    private var cameraFile: File? = null
    private var cameraUri: Uri? = null
    private var dialog: AlertDialog? = null
    private val files = mutableListOf<File>()
    private var generation = 0
    private var awaitingResult = false

    fun open(value: ValueCallback<Array<Uri>>?, params: WebChromeClient.FileChooserParams?): Boolean {
        // Claim every request, including failures, to avoid WebView completing it twice.
        if (callback != null || awaitingResult) {
            value?.onReceiveValue(null)
            return true
        }
        callback = value ?: return true
        generation++
        val image = ImageCapturePolicy.acceptsImages(params?.acceptTypes.orEmpty().toList())
        if (image && params?.isCaptureEnabled == true) openCamera() else {
            dialog = AlertDialog.Builder(activity)
                .setTitle(com.glomopay.sdk.android.R.string.glomopay_upload_document)
                .setItems(arrayOf(activity.getString(com.glomopay.sdk.android.R.string.glomopay_camera), activity.getString(com.glomopay.sdk.android.R.string.glomopay_gallery), activity.getString(com.glomopay.sdk.android.R.string.glomopay_files))) { _, index ->
                    when (index) {
                        0 -> openCamera()
                        1 -> openGallery()
                        else -> openDocuments()
                    }
                }
                .setOnCancelListener { complete(null) }
                .show()
        }
        return true
    }

    private fun openDocuments() = launchPicker(Intent(Intent.ACTION_GET_CONTENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
    })

    /**
     * Gallery is an entry point the user chose, not a filter on what the bank accepts:
     * the Files entry stays unfiltered. ACTION_PICK with a wildcard type is unhandled
     * on several OEM galleries, so use the photo picker where it exists and fall back
     * to the documents picker rather than failing the upload.
     */
    @Suppress("DEPRECATION")
    private fun openGallery() {
        val intent = if (android.os.Build.VERSION.SDK_INT >= 33) {
            Intent(MediaStore.ACTION_PICK_IMAGES)
        } else {
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        }
        launchPicker(intent, fallback = ::openDocuments)
    }

    @Suppress("DEPRECATION")
    private fun openCamera() {
        try { openCameraWithPermission() }
        catch (_: Exception) { fail("camera_unavailable") }
    }

    @Suppress("DEPRECATION")
    private fun openCameraWithPermission() {
        // External camera apps own camera access. Request permission only if the host
        // already declares CAMERA, as Android otherwise rejects the capture intent.
        val declared = activity.packageManager.getPackageInfo(
            activity.packageName, PackageManager.GET_PERMISSIONS,
        ).requestedPermissions?.contains(Manifest.permission.CAMERA) == true
        if (declared && activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_CAMERA)
            awaitingResult = true
            return
        }
        try {
            val directory = File(activity.cacheDir, "glomopay-capture").apply { mkdirs() }
            val file = File.createTempFile("capture-", ".jpg", directory)
            files.add(file)
            cameraFile = file
            val uri = FileProvider.getUriForFile(activity, activity.packageName + ".glomopay.files", file)
            cameraUri = uri
            launchPicker(Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                clipData = ClipData.newRawUri("Camera output", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            })
        } catch (_: Exception) { fail("camera_unavailable") }
    }

    fun onPermissionResult(requestCode: Int, grants: IntArray) {
        if (requestCode != PERMISSION_CAMERA) return
        awaitingResult = false
        if (callback == null) return
        if (grants.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            openCamera()
            return
        }
        // A refusal is the user's choice, not an SDK fault: cancel the file input,
        // tell the host which permission was refused, and leave the checkout open.
        revokeCameraGrant()
        cameraFile?.delete()
        cameraFile = null
        complete(null)
        onPermissionRefused(Manifest.permission.CAMERA)
    }

    @Suppress("DEPRECATION")
    private fun launchPicker(intent: Intent, fallback: (() -> Unit)? = null) {
        try {
            activity.startActivityForResult(intent, REQUEST_PICKER)
            awaitingResult = true
        }
        catch (_: Exception) {
            if (fallback != null) fallback() else fail("picker_unavailable")
        }
    }

    fun onResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_PICKER) return
        awaitingResult = false
        if (callback == null) return
        revokeCameraGrant()
        if (resultCode != Activity.RESULT_OK) {
            cameraFile?.delete()
            cameraFile = null
            complete(null)
            return
        }
        val capture = cameraFile
        cameraFile = null
        if (capture == null) {
            complete(data?.data?.let { arrayOf(it) })
            return
        }
        val requestGeneration = generation
        scope.launch {
            try {
                val output = withContext(Dispatchers.IO) { resizeCapture(capture) }
                if (generation == requestGeneration && callback != null) {
                    complete(arrayOf(FileProvider.getUriForFile(activity, activity.packageName + ".glomopay.files", output)))
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == requestGeneration) fail("camera_processing_failed")
            }
        }.invokeOnCompletion { cause -> if (cause != null) capture.delete() }
    }

    private fun resizeCapture(file: File): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODE_DIMENSION) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }))
        val orientation = ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }
                8 -> setRotate(-90f)
            }
            val scale = minOf(1f, MAX_OUTPUT_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height))
            postScale(scale, scale)
        }
        val resized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        try {
            file.outputStream().use { check(resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) }
        } finally {
            if (resized !== bitmap) resized.recycle()
            bitmap.recycle()
        }
        return file
    }

    private fun fail(reason: String) {
        revokeCameraGrant()
        cameraFile?.delete()
        cameraFile = null
        complete(null)
        onError(reason)
    }

    private fun complete(uris: Array<Uri>?) {
        val pending = callback
        callback = null
        pending?.onReceiveValue(uris)
    }

    private fun revokeCameraGrant() {
        cameraUri?.let { activity.revokeUriPermission(it, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        cameraUri = null
    }

    fun cancel() {
        generation++
        dialog?.dismiss()
        dialog = null
        revokeCameraGrant()
        cameraFile?.delete()
        cameraFile = null
        complete(null)
    }

    fun destroy() {
        cancel()
        files.forEach { it.delete() }
        files.clear()
    }

    companion object {
        const val REQUEST_PICKER = 4101
        const val PERMISSION_CAMERA = 4102
        // Bank upload limits drove these values. Do not increase them without
        // reconfirming accepted dimensions and payload size with supported banks.
        private const val MAX_DECODE_DIMENSION = 4096
        private const val MAX_OUTPUT_DIMENSION = 2048
        private const val JPEG_QUALITY = 85
    }
}
