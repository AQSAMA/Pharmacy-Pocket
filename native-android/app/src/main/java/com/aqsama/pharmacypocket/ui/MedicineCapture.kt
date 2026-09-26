package com.aqsama.pharmacypocket.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/** Scan locally. The bundled ML Kit model works without a network connection. */
@Composable
@OptIn(ExperimentalGetImage::class)
fun MedicineCodeScanner(
    stickerOnly: Boolean,
    onCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val preview = remember { PreviewView(context) }
    val error = remember { mutableStateOf<String?>(null) }

    DisposableEffect(preview, lifecycle, stickerOnly) {
        val cameraProvider = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                if (stickerOnly) Barcode.FORMAT_QR_CODE else Barcode.FORMAT_ALL_FORMATS,
            ).build(),
        )
        val delivered = AtomicBoolean(false)
        val main = ContextCompat.getMainExecutor(context)
        var bound = false
        cameraProvider.addListener({
            if (!delivered.get() && !executor.isShutdown) {
                try {
                    val provider = cameraProvider.get()
                    val previewUseCase = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { frame ->
                        val media = frame.image
                        if (media == null || delivered.get()) {
                            frame.close()
                        } else {
                            val image = InputImage.fromMediaImage(media, frame.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener(main) { results ->
                                    val raw = results.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf(String::isNotEmpty) }
                                    if (raw != null && delivered.compareAndSet(false, true)) onCode(raw)
                                }
                                .addOnFailureListener(main) { error.value = "Could not read the code. Try again." }
                                .addOnCompleteListener(main) { frame.close() }
                        }
                    }
                    provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, previewUseCase, analysis)
                    bound = true
                } catch (exception: Exception) {
                    error.value = "Camera unavailable: ${exception.message ?: "unknown error"}"
                }
            }
        }, main)
        onDispose {
            delivered.set(true)
            if (bound) runCatching { cameraProvider.get().unbindAll() }
            executor.shutdown()
            scanner.close()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (stickerOnly) "Point at the official sticker QR" else "Point at a product code")
            AndroidView(factory = { preview }, modifier = Modifier.fillMaxWidth().height(360.dp))
            error.value?.let { Text(it) }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    }
}

/** Decodes and re-encodes a selected image; the original URI and metadata are never retained. */
fun prepareMedicinePhoto(context: Context, uri: Uri): ByteArray {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val sample = max(1, (max(info.size.width, info.size.height).toLong() + 1199L).div(1200L).toInt())
        decoder.setTargetSampleSize(sample)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    return try {
        for (quality in listOf(82, 68, 52, 36, 24)) {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            if (output.size() <= 256_000) return output.toByteArray()
        }
        throw IllegalArgumentException("This image cannot be reduced below 256 KB. Choose another photo.")
    } finally {
        bitmap.recycle()
    }
}
