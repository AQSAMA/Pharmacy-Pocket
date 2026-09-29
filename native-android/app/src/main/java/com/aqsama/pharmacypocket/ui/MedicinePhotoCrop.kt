package com.aqsama.pharmacypocket.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MedicineCropMode {
    Rectangle,
    Square,
}

/**
 * Material 3 shell around CanHub's CropImageView.
 *
 * The library owns crop-window gestures, pinch zoom, EXIF-aware loading and rotation.
 * Pharmacy Pocket still owns the source file and performs the final bounded JPEG encoding,
 * keeping the existing MedicineMedia persistence lifecycle unchanged.
 */
@Composable
internal fun MedicinePhotoCrop(
    file: File,
    saving: Boolean,
    onAccept: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    statusMessage: String? = null,
    onCancel: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cropMode by remember(file.absolutePath) { mutableStateOf(MedicineCropMode.Rectangle) }
    var imageReady by remember(file.absolutePath) { mutableStateOf(false) }
    var cropRunning by remember(file.absolutePath) { mutableStateOf(false) }
    var error by remember(file.absolutePath) { mutableStateOf<String?>(null) }
    val busy = saving || cropRunning

    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val cropView = remember(file.absolutePath, primary) {
        CropImageView(context).apply {
            setImageCropOptions(
                CropImageOptions(
                    cropShape = CropImageView.CropShape.RECTANGLE,
                    guidelines = CropImageView.Guidelines.ON,
                    scaleType = CropImageView.ScaleType.FIT_CENTER,
                    showCropOverlay = true,
                    showProgressBar = true,
                    progressBarColor = primary,
                    autoZoomEnabled = true,
                    multiTouchEnabled = true,
                    centerMoveEnabled = true,
                    canChangeCropWindow = true,
                    maxZoom = 6,
                    initialCropWindowPaddingRatio = 0.08f,
                    fixAspectRatio = false,
                ),
            )
        }
    }

    DisposableEffect(cropView, file.absolutePath) {
        cropView.setOnSetImageUriCompleteListener(
            object : CropImageView.OnSetImageUriCompleteListener {
                override fun onSetImageUriComplete(view: CropImageView, uri: Uri, loadError: Exception?) {
                    imageReady = loadError == null
                    error = loadError?.message?.let { "Could not open photo: $it" }
                }
            },
        )
        cropView.setOnCropImageCompleteListener(
            object : CropImageView.OnCropImageCompleteListener {
                override fun onCropImageComplete(view: CropImageView, result: CropImageView.CropResult) {
                    val cropped = result.bitmap
                    if (result.error != null || cropped == null) {
                        cropRunning = false
                        error = result.error?.message ?: "Could not crop this photo."
                        return
                    }

                    scope.launch {
                        try {
                            val bytes = withContext(Dispatchers.Default) {
                                encodeMedicineBitmap(cropped)
                            }
                            error = null
                            cropRunning = false
                            onAccept(bytes)
                        } catch (failure: Exception) {
                            cropRunning = false
                            error = failure.message ?: "Could not prepare this photo."
                        }
                    }
                }
            },
        )
        cropView.setImageUriAsync(Uri.fromFile(file))

        onDispose {
            cropView.setOnSetImageUriCompleteListener(null)
            cropView.setOnCropImageCompleteListener(null)
            cropView.clearImage()
        }
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "Crop package photo",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Pinch to zoom. Drag or resize the frame around the medicine package.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                AndroidView(
                    factory = { cropView },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = cropMode == MedicineCropMode.Rectangle,
                    enabled = !busy && imageReady,
                    onClick = {
                        cropMode = MedicineCropMode.Rectangle
                        cropView.clearAspectRatio()
                    },
                    label = { Text("Rectangle") },
                )
                FilterChip(
                    selected = cropMode == MedicineCropMode.Square,
                    enabled = !busy && imageReady,
                    onClick = {
                        cropMode = MedicineCropMode.Square
                        cropView.setAspectRatio(1, 1)
                    },
                    label = { Text("Square") },
                )
                TextButton(
                    enabled = !busy && imageReady,
                    onClick = { cropView.rotateImage(-90) },
                ) { Text("↶ Rotate") }
                TextButton(
                    enabled = !busy && imageReady,
                    onClick = { cropView.rotateImage(90) },
                ) { Text("Rotate ↷") }
            }

            statusMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2,
                )
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (onCancel != null) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Cancel")
                    }
                }
                Button(
                    enabled = imageReady && !busy,
                    onClick = {
                        error = null
                        cropRunning = true
                        cropView.croppedImageAsync(
                            saveCompressFormat = Bitmap.CompressFormat.JPEG,
                            saveCompressQuality = 92,
                            reqWidth = 1600,
                            reqHeight = 1600,
                            options = CropImageView.RequestSizeOptions.RESIZE_INSIDE,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (busy) "Saving…" else "Save photo")
                }
            }
        }
    }
}
