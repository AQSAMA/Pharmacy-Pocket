package com.aqsama.pharmacypocket.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aqsama.pharmacypocket.data.AppSnapshot
import com.aqsama.pharmacypocket.data.CodeKind
import com.aqsama.pharmacypocket.data.Medicine
import com.aqsama.pharmacypocket.data.MedicineCode
import com.aqsama.pharmacypocket.data.PharmacyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** One navigation-entry owner for the photo mutation, preview, codes and save lifetime.
 * A draft is never owned by a camera composable. Only a successful commit or explicit
 * discard releases it. UI reads this object directly; there is no asynchronous path handoff.
 */
internal class MedicineMedia(
    initialCodes: List<MedicineCode> = emptyList(),
    initialPath: String? = null,
    initiallyRemoved: Boolean = false,
    initialCropSource: String? = null,
) {
    enum class Phase { READY, PREPARING, CROPPING, SAVING, CLOSED }
    var phase by mutableStateOf(if (initialCropSource == null) Phase.READY else Phase.CROPPING)
        private set
    var captureState by mutableStateOf<MediaCaptureState?>(null)
        private set
    var cropSource by mutableStateOf(initialCropSource)
        private set
    var codes by mutableStateOf(initialCodes)
        private set
    var photoPath by mutableStateOf(initialPath)
        private set
    var removePhoto by mutableStateOf(initiallyRemoved)
        private set
    var preview by mutableStateOf<ByteArray?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val processing: Boolean get() = phase == Phase.PREPARING || phase == Phase.SAVING
    val locked: Boolean get() = phase != Phase.READY
    private var generation = 0L

    fun openCamera() {
        check(!locked)
        captureState?.close()
        captureState = MediaCaptureState()
    }

    fun closeCamera() {
        captureState?.close()
        captureState = null
    }

    fun updateCodes(next: List<MedicineCode>) {
        check(!locked) { "Finish the current media operation first." }
        codes = com.aqsama.pharmacypocket.data.validateCodes(next)
    }

    suspend fun restorePreview() {
        if (preview != null) return
        val path = photoPath ?: return
        val token = generation
        val bytes = withContext(Dispatchers.IO) { readMedicinePhotoDraft(path) }
        if (generation == token && photoPath == path && phase != Phase.CLOSED) {
            preview = bytes
            if (bytes == null) error = "The selected photo is no longer available. Choose it again."
        }
    }

    suspend fun pickPhoto(context: Context, uri: android.net.Uri) {
        check(!locked)
        phase = Phase.PREPARING
        val token = ++generation
        var source: java.io.File? = null
        try {
            source = copyMedicinePhotoSource(context, uri)
            check(token == generation && phase == Phase.PREPARING)
            cropSource = source.absolutePath
            source = null
            phase = Phase.CROPPING
        } finally {
            source?.delete()
            if (token == generation && phase == Phase.PREPARING) phase = Phase.READY
        }
    }

    fun cancelCrop() {
        if (processing) return
        deleteMedicinePhotoDraft(cropSource)
        cropSource = null
        if (phase != Phase.CLOSED) phase = Phase.READY
    }

    /** Camera and Gallery both submit the final cropped JPEG here. */
    suspend fun acceptPhoto(context: Context, bytes: ByteArray) {
        check(phase == Phase.READY || phase == Phase.CROPPING) { "Finish the current media operation first." }
        phase = Phase.PREPARING
        val token = ++generation
        var pending: String? = null
        try {
            // Assign inside IO so cancellation on dispatch back cannot orphan the file.
            withContext(Dispatchers.IO) { pending = writeMedicinePhotoDraft(context, bytes) }
            currentCoroutineContext().ensureActive()
            check(token == generation && phase == Phase.PREPARING) { "This editor is no longer open." }
            val previous = photoPath
            photoPath = requireNotNull(pending)
            preview = bytes
            removePhoto = false
            error = null
            pending = null
            deleteMedicinePhotoDraft(previous)
            deleteMedicinePhotoDraft(cropSource)
            cropSource = null
        } finally {
            deleteMedicinePhotoDraft(pending)
            if (token == generation && phase != Phase.CLOSED) phase = if (cropSource == null) Phase.READY else Phase.CROPPING
        }
    }

    /**
     * Drops an unreadable replacement draft without changing the durable-photo intent.
     * This is not the same action as Remove photo: the original SQLite photo must survive.
     */
    fun discardUnavailableDraft() {
        check(!locked)
        generation++
        deleteMedicinePhotoDraft(photoPath)
        photoPath = null
        preview = null
        removePhoto = false
        error = null
    }

    fun remove() {
        check(!locked)
        generation++
        deleteMedicinePhotoDraft(photoPath)
        photoPath = null
        preview = null
        removePhoto = true
        error = null
    }

    suspend fun save(repository: PharmacyRepository, medicine: Medicine): AppSnapshot =
        save(medicine, repository::saveMedicine)

    internal suspend fun save(
        medicine: Medicine,
        commit: suspend (Medicine, ByteArray?, Boolean) -> AppSnapshot,
    ): AppSnapshot {
        check(!locked) { "Finish the current media operation first." }
        phase = Phase.SAVING
        try {
            val bytes = photoPath?.let { path ->
                withContext(Dispatchers.IO) {
                    readMedicinePhotoDraft(path)
                        ?: throw IllegalStateException("The selected photo is no longer available. Choose it again.")
                }
            }
            return commit(medicine.copy(codes = codes), bytes, removePhoto)
        } finally {
            // Keep the draft on failure, including cancellation, for a retry.
            phase = Phase.READY
        }
    }

    fun discard() {
        check(phase != Phase.SAVING) { "Cannot discard media during a medicine save." }
        generation++
        deleteMedicinePhotoDraft(cropSource)
        cropSource = null
        phase = Phase.CLOSED
        closeCamera()
        deleteMedicinePhotoDraft(photoPath)
        photoPath = null
        preview = null
    }

    fun encode(): String = JSONObject().apply {
        put("path", photoPath)
        put("crop", cropSource)
        put("removed", removePhoto)
        put("codes", JSONArray().apply {
            codes.forEach { code -> put(JSONArray(listOf(code.kind.name, code.value, code.label))) }
        })
    }.toString()

    companion object {
        fun restore(value: String): MedicineMedia {
            val obj = JSONObject(value)
            val list = obj.getJSONArray("codes")
            return MedicineMedia(
                initialCodes = (0 until list.length()).map { index ->
                    val code = list.getJSONArray(index)
                    MedicineCode(CodeKind.valueOf(code.getString(0)), code.getString(1), code.getString(2))
                },
                initialPath = obj.optString("path").takeUnless { it.isEmpty() || it == "null" },
                initiallyRemoved = obj.optBoolean("removed"),
                initialCropSource = obj.optString("crop").takeUnless { it.isEmpty() || it == "null" },
            )
        }
    }
}
