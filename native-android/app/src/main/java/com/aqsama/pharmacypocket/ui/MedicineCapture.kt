package com.aqsama.pharmacypocket.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
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
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.ensureActive

internal data class MediaSaveResult(val success: Boolean, val message: String)

/** Keeps one expressive camera open for code verification and package photography. */
@Composable
internal fun MedicineCameraScreen(
    title: String,
    existingCodes: Set<String>,
    onCode: (MedicineCode, (MediaSaveResult) -> Unit) -> Unit,
    onPhoto: (ByteArray, (MediaSaveResult) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onOpenMedicine: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var allowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(Unit) { if (!allowed) request.launch(Manifest.permission.CAMERA) }

    var scannerAvailable by remember { mutableStateOf(true) }
    fun idleCameraMessage(): String =
        if (scannerAvailable) "Scan a code or take a package photo"
        else "Code scanner unavailable. Photo capture still works."

    var message by remember { mutableStateOf("Scan a code or take a package photo") }
    val captureState = remember { MediaCaptureState() }
    val capturedFile = captureState.cropFile
    val capturing = captureState.capturing
    val saving = captureState.saving
    val pendingCode = captureState.code
    val disposed = remember { AtomicBoolean(false) }
    var lastCode by remember { mutableStateOf<MedicineCode?>(null) }
    var lastCodeState by remember { mutableStateOf<String?>(null) }
    val ignoredCodes = remember { mutableStateListOf<String>() }
    val seen = remember { mutableSetOf<String>() }
    val currentCodes by rememberUpdatedState(existingCodes)
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnPhoto by rememberUpdatedState(onPhoto)
    val handling = remember { AtomicBoolean(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    fun receive(code: MedicineCode) {
        if (
            capturedFile != null ||
            capturing ||
            saving ||
            pendingCode != null ||
            code.value in ignoredCodes ||
            !handling.compareAndSet(false, true)
        ) return

        lastCode = code
        if (code.value in currentCodes || code.value in seen) {
            lastCodeState = "Already saved"
            message = "This code is already attached"
            if (code.value !in ignoredCodes) ignoredCodes.add(code.value)
            Haptics.selection(view)
            handling.set(false)
            return
        }

        captureState.detect(code)
        lastCodeState = "Detected"
        message = "Check the detected value before saving"
        Haptics.selection(view)
    }

    fun takePhoto() {
        val capture = imageCapture ?: return
        if (capturedFile != null || saving || capturing || pendingCode != null || handling.get() || disposed.get()) return
        val file = File(context.cacheDir, "medicine_capture/${UUID.randomUUID()}.jpg")
        file.parentFile?.mkdirs()
        if (!captureState.capture(file)) return
        message = "Taking photo…"
        try {
            capture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        if (disposed.get()) {
                            file.delete()
                        } else {
                            captureState.captured(file)
                            message = "Frame the package, then save"
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        captureState.captureFailed(file)
                        if (!disposed.get()) {
                            message = "Could not take photo. Try again."
                            Haptics.reject(view)
                        }
                    }
                },
            )
        } catch (error: Exception) {
            captureState.captureFailed(file)
            message = "Could not open shutter. Try again."
            Haptics.reject(view)
        }
    }

    Dialog(
        onDismissRequest = { if (!saving && !capturing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                CameraHeader(
                    title = title,
                    savedCodeCount = existingCodes.size,
                    photoPreviewOpen = capturedFile != null,
                    enabled = !saving && !capturing,
                    onClose = {
                        if (capturedFile != null) {
                            captureState.retake()
                            message = idleCameraMessage()
                        } else {
                            onDismiss()
                        }
                    },
                )

                if (!allowed) {
                    Column(
                        Modifier.weight(1f).padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Camera access is needed to scan and photograph a package.")
                        Button(
                            onClick = { request.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.padding(top = 12.dp),
                        ) { Text("Allow camera") }
                    }
                } else {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .clip(RoundedCornerShape(28.dp)),
                    ) {
                        if (capturedFile == null) LiveMedicineCamera(
                            enabled = capturedFile == null && pendingCode == null && !capturing && !saving,
                            onCaptureReady = { imageCapture = it },
                            onDetected = ::receive,
                            onScannerAvailabilityChanged = { available ->
                                scannerAvailable = available
                                if (!available) message = idleCameraMessage()
                            },
                            onError = { message = it },
                        )

                        capturedFile?.let { file ->
                            MedicinePhotoCrop(
                                file = file,
                                saving = saving,
                                statusMessage = message,
                                onAccept = { bytes ->
                                    if (!captureState.savePhoto()) return@MedicinePhotoCrop
                                    try { currentOnPhoto(bytes) { result ->
                                        if (disposed.get()) return@currentOnPhoto
                                        message = result.message
                                        val success = result.success
                                        if (success) {
                                            Haptics.confirm(view)

                                        } else {
                                            Haptics.reject(view)
                                        }
                                        captureState.photoSaved(file, success)
                                    } } catch (error: Exception) {
                                        captureState.photoSaved(file, false)
                                        message = error.message ?: "Could not save photo"
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        if (capturedFile == null) {
                            if (scannerAvailable) {
                                Box(
                                    Modifier
                                        .align(Alignment.Center)
                                        .fillMaxWidth(0.78f)
                                        .aspectRatio(1.22f)
                                        .border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                            RoundedCornerShape(24.dp),
                                        ),
                                )
                            } else {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 14.dp, start = 14.dp, end = 14.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
                                ) {
                                    Text(
                                        "Code scanning unavailable · Photo capture still works",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            lastCode?.let { code ->
                                CodePreviewPill(
                                    code = code,
                                    state = lastCodeState,
                                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp, start = 14.dp, end = 14.dp),
                                )
                            }

                            pendingCode?.let { detected ->
                                DetectedCodeCard(
                                    code = detected,
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                                    onSkip = {
                                        if (detected.value !in ignoredCodes) ignoredCodes.add(detected.value)
                                        captureState.retake()
                                        lastCodeState = "Skipped"
                                        message = "Skipped · keep scanning or take a photo"
                                        handling.set(false)
                                    },
                                    onSave = { kind, label ->
                                        val accepted = detected.copy(kind = kind, label = label)
                                        if (!captureState.saveCode()) return@DetectedCodeCard
                                        lastCode = accepted
                                        lastCodeState = "Saving…"
                                        try {
                                            currentOnCode(accepted) { result ->
                                                if (disposed.get()) return@currentOnCode
                                                captureState.codeSaved(detected)
                                                val success = result.success
                                                if (success) {
                                                    seen.add(detected.value)
                                                    if (detected.value !in ignoredCodes) ignoredCodes.add(detected.value)
                                                    lastCodeState = "Saved"
                                                    Haptics.confirm(view)
                                                } else {
                                                    lastCodeState = "Not saved"
                                                    Haptics.reject(view)
                                                }
                                                message = result.message
                                                handling.set(false)
                                            }
                                        } catch (error: Exception) {
                                            captureState.codeSaved(detected)
                                            lastCodeState = "Not saved"
                                            message = error.message ?: "Could not save code"
                                            handling.set(false)
                                            Haptics.reject(view)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    if (capturedFile == null) CameraControlDeck(
                        message = message,
                        canCapture = capturedFile == null && imageCapture != null && !saving && !capturing && pendingCode == null && !handling.get(),
                        busy = saving || capturing,
                        showRescan = ignoredCodes.isNotEmpty() && capturedFile == null,
                        onCapture = ::takePhoto,
                        onDone = onDismiss,
                        onOpenMedicine = onOpenMedicine,
                        onRescan = {
                            ignoredCodes.clear()
                            message = "Scanning codes again"
                            lastCodeState = null
                        },
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            captureState.close()
        }
    }
}

@Composable
private fun CameraHeader(
    title: String,
    savedCodeCount: Int,
    photoPreviewOpen: Boolean,
    enabled: Boolean,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(
            onClick = onClose,
            enabled = enabled,
            modifier = Modifier.widthIn(min = 74.dp),
        ) { Text(if (photoPreviewOpen) "Retake" else "Close") }

        Text(
            title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                "$savedCodeCount codes",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CodePreviewPill(
    code: MedicineCode,
    state: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
        shadowElevation = 4.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        codeKindLabel(code.kind),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                state?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            RawCodeReadout(code.value)
        }
    }
}

@Composable
private fun RawCodeReadout(
    value: String,
    expandedByDefault: Boolean = false,
    scrollExpanded: Boolean = true,
) {
    var expanded by remember(value) { mutableStateOf(expandedByDefault) }
    val canExpand = value.length > 96
    val scrollState = rememberScrollState()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        ) {
            Text(
                value,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (expanded && scrollExpanded) {
                            Modifier.heightIn(max = 150.dp).verticalScroll(scrollState)
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        if (canExpand) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Text(if (expanded) "Collapse raw value" else "Show full raw value", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DetectedCodeCard(
    code: MedicineCode,
    modifier: Modifier = Modifier,
    onSkip: () -> Unit,
    onSave: (CodeKind, String) -> Unit,
) {
    var label by remember(code.value) { mutableStateOf("") }
    var kind by remember(code.value) { mutableStateOf(code.kind) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Detected ${codeKindLabel(code.kind)}", fontWeight = FontWeight.ExtraBold)
                    RawCodeReadout(
                        value = code.value,
                        expandedByDefault = true,
                        scrollExpanded = false,
                    )
                }
            }

            if (code.kind == CodeKind.QR) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { kind = CodeKind.QR }) {
                        Text(if (kind == CodeKind.QR) "✓ QR" else "QR")
                    }
                    TextButton(onClick = { kind = CodeKind.PRICE_STICKER_QR }) {
                        Text(if (kind == CodeKind.PRICE_STICKER_QR) "✓ Sticker QR" else "Sticker QR")
                    }
                }
            }

            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Company / variant (optional)") },
                singleLine = true,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSkip) { Text("Skip") }
                Button(onClick = { onSave(kind, label) }) { Text("Save code") }
            }
        }
    }
}

@Composable
private fun CameraControlDeck(
    message: String,
    canCapture: Boolean,
    busy: Boolean,
    showRescan: Boolean,
    onCapture: () -> Unit,
    onDone: () -> Unit,
    onOpenMedicine: (() -> Unit)?,
    onRescan: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 5.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                message,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (showRescan) {
                TextButton(onClick = onRescan, enabled = !busy) {
                    Text("Scan the same code again")
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = onDone,
                    enabled = !busy,
                    modifier = Modifier.widthIn(min = 88.dp),
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onCapture,
                    enabled = canCapture,
                    modifier = Modifier.size(76.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Text("●", fontSize = 28.sp)
                }

                if (onOpenMedicine != null) {
                    TextButton(
                        onClick = onOpenMedicine,
                        enabled = !busy,
                        modifier = Modifier.widthIn(min = 88.dp),
                    ) {
                        Text("Medicine info", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(Modifier.width(88.dp))
                }
            }
        }
    }
}

private fun codeKindLabel(kind: CodeKind): String = when (kind) {
    CodeKind.PRICE_STICKER_QR -> "Sticker QR"
    CodeKind.QR -> "QR"
    CodeKind.BARCODE -> "Barcode"
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun LiveMedicineCamera(
    enabled: Boolean,
    onCaptureReady: (ImageCapture?) -> Unit,
    onDetected: (MedicineCode) -> Unit,
    onScannerAvailabilityChanged: (Boolean) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentDetected by rememberUpdatedState(onDetected)
    val currentScannerAvailabilityChanged by rememberUpdatedState(onScannerAvailabilityChanged)
    val currentError by rememberUpdatedState(onError)
    val currentReady by rememberUpdatedState(onCaptureReady)

    DisposableEffect(lifecycle, previewView) {
        val future = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        val scannerResult = runCatching {
            BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build(),
            )
        }
        val scanner = scannerResult.getOrNull()
        currentScannerAvailabilityChanged(scanner != null)
        val main = ContextCompat.getMainExecutor(context)
        var useCases: List<androidx.camera.core.UseCase> = emptyList()
        var disposed = false

        future.addListener({
            if (!disposed) {
                try {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val photo = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                    val cameraUseCases = mutableListOf<androidx.camera.core.UseCase>(preview, photo)
                    scanner?.let { activeScanner ->
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { frame ->
                            val media = frame.image
                            if (media == null || !currentEnabled) {
                                frame.close()
                            } else {
                                activeScanner.process(InputImage.fromMediaImage(media, frame.imageInfo.rotationDegrees))
                                    .addOnSuccessListener(main) { results ->
                                        if (!disposed && currentEnabled) {
                                            results.firstOrNull { !it.rawValue.isNullOrEmpty() }?.let { barcode ->
                                                currentDetected(
                                                    MedicineCode(
                                                        if (barcode.format == Barcode.FORMAT_QR_CODE) CodeKind.QR else CodeKind.BARCODE,
                                                        barcode.rawValue.orEmpty(),
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                    .addOnCompleteListener(main) { frame.close() }
                            }
                        }
                        cameraUseCases += analysis
                    }
                    useCases = cameraUseCases
                    provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, *useCases.toTypedArray())
                    currentReady(photo)
                } catch (error: Exception) {
                    currentError("Camera unavailable: ${error.message ?: "try again"}")
                }
            }
        }, main)

        onDispose {
            disposed = true
            currentReady(null)
            if (future.isDone) runCatching { future.get().unbind(*useCases.toTypedArray()) }
            executor.shutdown()
            scanner?.close()
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
internal fun MedicinePhotoCrop(
    file: File,
    saving: Boolean,
    onAccept: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    statusMessage: String? = null,
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
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Crop package photo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Drag to center the package. Rectangle is the default.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            frame?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = "Cropped medicine photo preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(bitmap, square, saving) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                if (saving) return@detectDragGestures
                                horizontal = (horizontal - drag.x / size.width).coerceIn(0f, 1f)
                                vertical = (vertical - drag.y / size.height).coerceIn(0f, 1f)
                            }
                        },
                    contentScale = ContentScale.Fit,
                )
            } ?: Text("Could not open photo. Retake it.")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !saving, onClick = { square = false }) { Text(if (!square) "✓ Rectangle" else "Rectangle") }
                TextButton(enabled = !saving, onClick = { square = true }) { Text(if (square) "✓ Square" else "Square") }
            }

            Text("Horizontal position", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(enabled = !saving, value = horizontal, onValueChange = { horizontal = it })
            Text("Vertical position", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(enabled = !saving, value = vertical, onValueChange = { vertical = it })
            statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    try {
                        onAccept(encodeMedicineBitmap(requireNotNull(frame)))
                    } catch (exception: Exception) {
                        error = exception.message ?: "Could not save photo."
                    }
                },
                enabled = frame != null && !saving,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp),
            ) {
                Text(if (saving) "Saving…" else "Save photo", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Decodes and re-encodes a selected image; source URI and metadata are never retained. */
fun prepareMedicinePhoto(context: Context, uri: Uri): ByteArray =
    encodeMedicineBitmap(decodeMedicineBitmap(context, uri))

internal fun decodeMedicineBitmap(context: Context, uri: Uri): Bitmap =
    runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > 1200) {
                val scale = 1200f / longest.toFloat()
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
    }.getOrElse {
        decodeMedicineBitmapLegacy(context, uri)
    }

private fun decodeMedicineBitmapLegacy(context: Context, uri: Uri): Bitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    } ?: throw IllegalArgumentException("Could not open the selected photo.")
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Could not decode the selected photo." }

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > 1200) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(
            stream,
            null,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    } ?: throw IllegalArgumentException("Could not decode the selected photo.")

    val orientation = runCatching {
        resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    return applyExifOrientation(decoded, orientation)
}

private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { transformed ->
        if (transformed !== bitmap) bitmap.recycle()
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

internal fun encodeMedicineBitmap(bitmap: Bitmap): ByteArray {
    var candidate = bitmap
    var ownsCandidate = false
    try {
        repeat(8) {
            for (quality in listOf(82, 68, 52, 36, 24)) {
                val bytes = ByteArrayOutputStream().use { output ->
                    if (!candidate.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                        byteArrayOf()
                    } else {
                        output.toByteArray()
                    }
                }
                if (bytes.isNotEmpty() && bytes.size <= MAX_MEDICINE_PHOTO_BYTES) return bytes
            }

            if (max(candidate.width, candidate.height) <= 320) return@repeat
            val next = Bitmap.createScaledBitmap(
                candidate,
                (candidate.width * 0.78f).toInt().coerceAtLeast(1),
                (candidate.height * 0.78f).toInt().coerceAtLeast(1),
                true,
            )
            if (ownsCandidate && candidate !== bitmap && !candidate.isRecycled) candidate.recycle()
            candidate = next
            ownsCandidate = candidate !== bitmap
        }
    } finally {
        if (ownsCandidate && candidate !== bitmap && !candidate.isRecycled) candidate.recycle()
    }
    throw IllegalArgumentException("This photo could not be prepared for local storage.")
}

/** Copies a temporary picker grant into an owned crop input; never retains an external URI. */
internal suspend fun copyMedicinePhotoSource(context: Context, uri: Uri): File {
    var file: File? = null
    try {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val bytes = prepareMedicinePhoto(context, uri)
            file = File(writeMedicinePhotoDraft(context, bytes))
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        return requireNotNull(file)
    } catch (error: Throwable) {
        file?.delete()
        throw error
    }
}
