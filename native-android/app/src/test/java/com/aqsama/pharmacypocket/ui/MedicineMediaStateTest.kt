package com.aqsama.pharmacypocket.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.aqsama.pharmacypocket.data.CodeKind
import com.aqsama.pharmacypocket.data.Medicine
import com.aqsama.pharmacypocket.data.MedicineCode
import com.aqsama.pharmacypocket.data.PharmacyRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedicineMediaStateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun jpeg() = encodeMedicineBitmap(Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888))

    @Test fun shutterAndScannerAreExclusiveWithCropAndSave() {
        val state = MediaCaptureState()
        val file = File(context.cacheDir, "capture-state.jpg").apply { writeBytes(jpeg()) }
        assertTrue(state.capture(file))
        assertFalse(state.capture(File(context.cacheDir, "second.jpg")))
        state.captured(file)
        assertFalse(state.scanning)
        assertFalse(state.detect(MedicineCode(CodeKind.QR, "raw sticker payload")))
        assertFalse(state.capture(File(context.cacheDir, "second.jpg")))
        assertTrue(state.savePhoto())
        state.retake()
        assertTrue(state.saving)
        assertTrue(file.exists())
        state.photoSaved(file, false)
        assertEquals(file, state.cropFile)
        assertTrue(state.savePhoto())
        state.photoSaved(file, true)
        assertFalse(file.exists())
        assertTrue(state.scanning)
    }

    @Test fun staleCameraResultCannotReopenClosedSession() {
        val state = MediaCaptureState()
        val file = File(context.cacheDir, "late-capture.jpg")
        state.capture(file)
        state.close()
        file.writeBytes(jpeg())
        state.captured(file)
        assertEquals(MediaCapturePhase.Closed, state.phase)
        assertFalse(file.exists())
    }

    @Test fun codeWriteLocksNavigationAndCaptureUntilAcknowledged() {
        val state = MediaCaptureState()
        val code = MedicineCode(CodeKind.QR, "raw data, not a price")
        assertTrue(state.detect(code))
        assertTrue(state.saveCode())
        assertFalse(state.capture(File(context.cacheDir, "during-code.jpg")))
        assertFalse(state.detect(code))
        assertTrue(state.saving)
        state.codeSaved(code)
        assertTrue(state.scanning)
    }

    @Test fun acceptedJpegIsPreviewAndRestoredDraftAndSurvivesFailedSave() = runBlocking {
        val bytes = jpeg()
        val code = MedicineCode(CodeKind.QR, "raw sticker data")
        val media = MedicineMedia(listOf(code))
        media.acceptPhoto(context, bytes)
        assertArrayEquals(bytes, media.preview)
        val restored = MedicineMedia.restore(media.encode())
        restored.restorePreview()
        assertArrayEquals(bytes, restored.preview)
        assertEquals(listOf(code), restored.codes)
        val path = requireNotNull(restored.photoPath)
        try {
            restored.save(PharmacyRepository(context), Medicine(
                id = "invalid", category = "tablets", subcategory = "General", name = "", note = "",
                official = 0, discounted = null,
            ))
            fail("Invalid medicine must fail")
        } catch (_: IllegalArgumentException) { }
        assertTrue(File(path).exists())
        assertArrayEquals(bytes, restored.preview)
        assertFalse(restored.locked)
        restored.remove()
        assertFalse(File(path).exists())
        assertNull(restored.preview)
        assertTrue(MedicineMedia.restore(restored.encode()).removePhoto)
    }

    @Test fun savingPinsDraftAndRejectsDiscardReplacementAndCodeEdits() = runBlocking {
        val media = MedicineMedia()
        val bytes = jpeg()
        media.acceptPhoto(context, bytes)
        val path = requireNotNull(media.photoPath)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val job = launch {
            media.save(Medicine(
                id = "pin", category = "tablets", subcategory = "General", name = "Pinned",
                note = "", official = 100, discounted = null,
            )) { _, photo, _ ->
                assertArrayEquals(bytes, photo)
                entered.complete(Unit)
                release.await()
                error("Injected write failure")
            }
        }
        entered.await()
        assertTrue(media.locked)
        try { media.discard(); fail("Must not delete during save") } catch (_: IllegalStateException) { }
        try { media.updateCodes(emptyList()); fail("Must not mutate during save") } catch (_: IllegalStateException) { }
        try { media.acceptPhoto(context, jpeg()); fail("Must not replace during save") } catch (_: IllegalStateException) { }
        assertTrue(File(path).exists())
        job.cancelAndJoin()
        assertFalse(media.locked)
        assertTrue(File(path).exists())
        assertArrayEquals(bytes, media.preview)
        media.discard()
        assertFalse(File(path).exists())
    }

    @Test fun missingDraftStopsMedicineSaveRatherThanDroppingPhoto() = runBlocking {
        val media = MedicineMedia()
        media.acceptPhoto(context, jpeg())
        File(requireNotNull(media.photoPath)).delete()
        try {
            media.save(PharmacyRepository(context), Medicine(
                id = "missing-photo", category = "tablets", subcategory = "General", name = "Missing photo",
                note = "", official = 100, discounted = null,
            ))
            fail("Missing draft must fail")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("no longer available"))
        }
        assertFalse(media.locked)
    }
}
