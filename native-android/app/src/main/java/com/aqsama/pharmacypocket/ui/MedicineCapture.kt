package com.aqsama.pharmacypocket.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.aqsama.pharmacypocket.data.CodeKind
import com.aqsama.pharmacypocket.data.MedicineCode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The same camera stays open for codes and package photos. Callbacks acknowledge durable saves. */
@Composable
fun MedicineCameraScreen(
    title: String,
    existingCodes: Set<String>,
    onCode: (MedicineCode, (String) -> Unit) -> Unit,
    onPhoto: (ByteArray, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var allowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(Unit) { if (!allowed) request.launch(Manifest.permission.CAMERA) }

    var message by remember { mutableStateOf("Point at a barcode or QR, or take a photo") }
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var saving by remember { mutableStateOf(false) }
    val latestCapturedFile by rememberUpdatedState(capturedFile)
    val seen = remember { mutableSetOf<String>() }
    val currentCodes by rememberUpdatedState(existingCodes)
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnPhoto by rememberUpdatedState(onPhoto)
    val handling = remember { AtomicBoolean(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    fun receive(code: MedicineCode) {
        if (capturedFile != null || saving || !handling.compareAndSet(false, true)) return
        if (code.value in currentCodes || code.value in seen) {
            message = "Code already added"
            handling.set(false)
            return
        }
        currentOnCode(code) { result ->
            if (result.startsWith("Saved") || result.startsWith("Added")) seen.add(code.value)
            message = result
            handling.set(false)
        }
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (capturedFile != null) {
                        capturedFile?.delete()
                        capturedFile = null
                    } else onDismiss() }, enabled = !saving) { Text(if (capturedFile == null) "Close" else "Retake") }
                    Text(title, modifier = Modifier.weight(1f), maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                }
                if (!allowed) {
                    Column(Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center) {
                        Text("Camera access is needed to scan and photograph a package.")
                        Button(onClick = { request.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        LiveMedicineCamera(
                            enabled = capturedFile == null && !saving,
                            onCaptureReady = { imageCapture = it },
                            onDetected = ::receive,
                            onError = { message = it },
                        )
                        capturedFile?.let { file ->
                            MedicinePhotoCrop(
                                file = file,
                                saving = saving,
                                onAccept = { bytes ->
                                    saving = true
                                    currentOnPhoto(bytes) { result ->
                                        saving = false
                                        message = result
                                        if (result.startsWith("Saved") || result.startsWith("Added")) {
                                            file.delete()
                                            capturedFile = null
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Text(
                        message,
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (capturedFile == null) {
                        Button(
                            onClick = {
                                val capture = imageCapture ?: return@Button
                                val file = File(context.cacheDir, "medicine_capture/${UUID.randomUUID()}.jpg")
                                file.parentFile?.mkdirs()
                                capture.takePicture(
                                    ImageCapture.OutputFileOptions.Builder(file).build(),
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                            capturedFile = file
                                            message = "Frame the package and save"
                                        }
                                        override fun onError(exception: ImageCaptureException) {
                                            file.delete()
                                            message = "Could not take photo. Try again."
                                        }
                                    },
                                )
                            },
                            enabled = imageCapture != null && !saving,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 18.dp),
                        ) { Text("●  Take photo") }
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { latestCapturedFile?.delete() } }
}

@Composable
private fun LiveMedicineCamera(
    enabled: Boolean,
    onCaptureReady: (ImageCapture?) -> Unit,
    onDetected: (MedicineCode) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentDetected by rememberUpdatedState(onDetected)
    val currentError by rememberUpdatedState(onError)
    val currentReady by rememberUpdatedState(onCaptureReady)
    DisposableEffect(lifecycle, previewView) {
        val future = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build(),
        )
        val main = ContextCompat.getMainExecutor(context)
        var useCases: List<androidx.camera.core.UseCase> = emptyList()
        var disposed = false
        future.addListener({
            if (!disposed) try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val photo = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { frame ->
                    val media = frame.image
                    if (media == null || !currentEnabled) {
                        frame.close()
                    } else {
                        scanner.process(InputImage.fromMediaImage(media, frame.imageInfo.rotationDegrees))
                            .addOnSuccessListener(main) { results ->
                                if (!disposed && currentEnabled) results.firstOrNull { !it.rawValue.isNullOrEmpty() }?.let { barcode ->
                                    currentDetected(MedicineCode(
                                        if (barcode.format == Barcode.FORMAT_QR_CODE) CodeKind.PRICE_STICKER_QR else CodeKind.BARCODE,
                                        barcode.rawValue.orEmpty(),
                                    ))
                                }
                            }
                            .addOnCompleteListener(main) { frame.close() }
                    }
                }
                useCases = listOf(preview, photo, analysis)
                provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, *useCases.toTypedArray())
                currentReady(photo)
            } catch (error: Exception) {
                currentError("Camera unavailable: ${error.message ?: "try again"}")
            }
        }, main)
        onDispose {
            disposed = true
            currentReady(null)
            if (future.isDone) runCatching { future.get().unbind(*useCases.toTypedArray()) }
            executor.shutdown()
            scanner.close()
        }
    }
    androidx.compose.ui.viewinterop.AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun MedicinePhotoCrop(
    file: File,
    saving: Boolean,
    onAccept: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var square by remember(file) { mutableStateOf(false) }
    var horizontal by remember(file) { mutableStateOf(0.5f) }
    var vertical by remember(file) { mutableStateOf(0.5f) }
    var error by remember(file) { mutableStateOf<String?>(null) }
    val bitmap = remember(file) {
        runCatching { decodeMedicineBitmap(context, Uri.fromFile(file)) }.getOrNull()
    }
    val frame = remember(bitmap, square, horizontal, vertical) {
        bitmap?.let { cropMedicineBitmap(it, square, horizontal, vertical) }
    }
    Surface(modifier, color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Crop package photo", style = MaterialTheme.typography.titleMedium)
            Text("Drag the photo to center the package. Rectangle is the default.")
            frame?.let {
                Image(it.asImageBitmap(), contentDescription = "Cropped medicine photo preview",
                    modifier = Modifier.fillMaxWidth().weight(1f).pointerInput(bitmap, square) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            horizontal = (horizontal - drag.x / size.width).coerceIn(0f, 1f)
                            vertical = (vertical - drag.y / size.height).coerceIn(0f, 1f)
                        }
                    }, contentScale = ContentScale.Fit)
            } ?: Text("Could not open photo. Retake it.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { square = false }) { Text(if (!square) "✓ Rectangle" else "Rectangle") }
                TextButton(onClick = { square = true }) { Text(if (square) "✓ Square" else "Square") }
            }
            Text("Horizontal position")
            Slider(value = horizontal, onValueChange = { horizontal = it })
            Text("Vertical position")
            Slider(value = vertical, onValueChange = { vertical = it })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    try { onAccept(encodeMedicineBitmap(requireNotNull(frame))) }
                    catch (exception: Exception) { error = exception.message ?: "Could not save photo." }
                },
                enabled = frame != null && !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (saving) "Saving…" else "Save photo") }
        }
    }
}

/** Decodes and re-encodes a selected image; source URI and metadata are never retained. */
fun prepareMedicinePhoto(context: Context, uri: Uri): ByteArray =
    encodeMedicineBitmap(decodeMedicineBitmap(context, uri))

private fun decodeMedicineBitmap(context: Context, uri: Uri): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val sample = max(1, ((max(info.size.width, info.size.height).toLong() + 1199L) / 1200L).toInt())
        decoder.setTargetSampleSize(sample)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}

internal fun cropMedicineBitmap(bitmap: Bitmap, square: Boolean, horizontal: Float, vertical: Float): Bitmap {
    val ratio = if (square) 1f else if (bitmap.height > bitmap.width) 3f / 4f else 4f / 3f
    val width = minOf(bitmap.width, (bitmap.height * ratio).toInt()).coerceAtLeast(1)
    val height = minOf(bitmap.height, (bitmap.width / ratio).toInt()).coerceAtLeast(1)
    val x = ((bitmap.width - width) * horizontal.coerceIn(0f, 1f)).toInt()
    val y = ((bitmap.height - height) * vertical.coerceIn(0f, 1f)).toInt()
    return Bitmap.createBitmap(bitmap, x, y, width, height)
}

private fun encodeMedicineBitmap(bitmap: Bitmap): ByteArray {
    for (quality in listOf(82, 68, 52, 36, 24)) {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        if (output.size() <= 256_000) return output.toByteArray()
    }
    throw IllegalArgumentException("This photo cannot be reduced below 256 KB. Retake it.")
}
