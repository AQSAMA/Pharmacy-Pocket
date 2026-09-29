package com.aqsama.pharmacypocket.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aqsama.pharmacypocket.data.MedicineCode
import java.io.File

/** Exclusive camera modes: a crop and an active shutter cannot coexist. */
internal sealed interface MediaCapturePhase {
    data object Scanning : MediaCapturePhase
    data class Capturing(val file: File) : MediaCapturePhase
    data class Cropping(val file: File) : MediaCapturePhase
    data class ReviewingCode(val code: MedicineCode) : MediaCapturePhase
    data class SavingPhoto(val file: File) : MediaCapturePhase
    data class SavingCode(val code: MedicineCode) : MediaCapturePhase
    data object Closed : MediaCapturePhase
}

internal class MediaCaptureState {
    var phase by mutableStateOf<MediaCapturePhase>(MediaCapturePhase.Scanning)
        private set
    val cropFile: File? get() = when (val value = phase) {
        is MediaCapturePhase.Cropping -> value.file
        is MediaCapturePhase.SavingPhoto -> value.file
        else -> null
    }
    val captureFile: File? get() = (phase as? MediaCapturePhase.Capturing)?.file
    val code: MedicineCode? get() = (phase as? MediaCapturePhase.ReviewingCode)?.code
    val scanning: Boolean get() = phase == MediaCapturePhase.Scanning
    val saving: Boolean get() = phase is MediaCapturePhase.SavingCode || phase is MediaCapturePhase.SavingPhoto
    val capturing: Boolean get() = phase is MediaCapturePhase.Capturing

    fun capture(file: File): Boolean {
        if (!scanning) return false
        phase = MediaCapturePhase.Capturing(file)
        return true
    }
    fun captured(file: File) {
        if (captureFile == file) phase = MediaCapturePhase.Cropping(file) else file.delete()
    }
    fun captureFailed(file: File) {
        file.delete()
        if (captureFile == file) phase = MediaCapturePhase.Scanning
    }
    fun detect(code: MedicineCode): Boolean {
        if (!scanning) return false
        phase = MediaCapturePhase.ReviewingCode(code)
        return true
    }
    fun savePhoto(): Boolean {
        val value = phase as? MediaCapturePhase.Cropping ?: return false
        phase = MediaCapturePhase.SavingPhoto(value.file)
        return true
    }
    fun photoSaved(file: File, success: Boolean) {
        val value = phase as? MediaCapturePhase.SavingPhoto ?: return
        if (value.file != file) return
        if (success) { value.file.delete(); phase = MediaCapturePhase.Scanning }
        else phase = MediaCapturePhase.Cropping(value.file)
    }
    fun saveCode(): Boolean {
        val value = phase as? MediaCapturePhase.ReviewingCode ?: return false
        phase = MediaCapturePhase.SavingCode(value.code)
        return true
    }
    fun codeSaved(code: MedicineCode) {
        if ((phase as? MediaCapturePhase.SavingCode)?.code == code) phase = MediaCapturePhase.Scanning
    }
    fun retake() {
        if (saving || capturing) return
        cropFile?.delete()
        if (phase != MediaCapturePhase.Closed) phase = MediaCapturePhase.Scanning
    }
    fun close() {
        cropFile?.delete()
        captureFile?.delete()
        phase = MediaCapturePhase.Closed
    }
}
