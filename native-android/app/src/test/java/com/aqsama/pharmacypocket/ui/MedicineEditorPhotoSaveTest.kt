package com.aqsama.pharmacypocket.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.aqsama.pharmacypocket.data.Medicine
import com.aqsama.pharmacypocket.data.PharmacyRepository
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedicineEditorPhotoSaveTest {
    private lateinit var context: Context
    private lateinit var repository: PharmacyRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val dbFile = File(context.filesDir, "SQLite/pharmacy-pocket.db")
        dbFile.delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
        repository = PharmacyRepository(context)
    }

    @Test
    fun acceptedDraftSurvivesRestorationAndReplacementCancelsRemoval() {
        val bytes = jpeg(Color.RED)
        val drafts = mutableMapOf<String, ByteArray>()

        val removal = removeEditorPhoto()
        assertTrue(removal.removeStoredPhoto)
        assertNull(removal.draftPhoto)

        val accepted = acceptEditorPhoto(bytes)
        assertFalse(accepted.removeStoredPhoto)
        updatePhotoDraft(drafts, "editor-entry", accepted.draftPhoto, busy = false)

        val restored = restorePhotoDrafts(encodePhotoDrafts(drafts))
        assertArrayEquals(bytes, restored["editor-entry"])

        val replacement = acceptEditorPhoto(jpeg(Color.BLUE))
        assertFalse(replacement.removeStoredPhoto)
        assertTrue(replacement.draftPhoto != null)
    }

    @Test
    fun busyEditorDoesNotReplaceItsCurrentAcceptedDraft() {
        val original = jpeg(Color.RED)
        val replacement = jpeg(Color.BLUE)
        val drafts = mutableMapOf("editor-entry" to original)

        updatePhotoDraft(drafts, "editor-entry", replacement, busy = true)

        assertArrayEquals(original, drafts["editor-entry"])
    }

    @Test
    fun editorSaveCarriesAcceptedJpegIntoSqliteReloadsItAndInvalidatesRendering() = runBlocking {
        val cameraBytes = jpeg(Color.RED)
        val galleryBytes = jpeg(Color.BLUE)
        val addedLaterBytes = jpeg(Color.GREEN)
        val drafts = mutableMapOf<String, ByteArray>()
        val photoVersions = mutableMapOf<String, Int>()

        val cameraChange = acceptEditorPhoto(cameraBytes)
        updatePhotoDraft(drafts, "new-editor", cameraChange.draftPhoto, busy = false)
        val restoredDrafts = restorePhotoDrafts(encodePhotoDrafts(drafts))

        val newMedicine = medicine("new")
        val afterCameraSave = saveMedicineFromEditor(
            repository = repository,
            medicine = newMedicine,
            draftPhoto = restoredDrafts["new-editor"],
            removePhoto = cameraChange.removeStoredPhoto,
        ) {
            bumpPhotoVersion(photoVersions, "new")
        }
        assertTrue(afterCameraSave.items.single { it.id == "new" }.hasPhoto)
        assertArrayEquals(cameraBytes, repository.loadPhoto("new"))
        assertEquals(1, photoVersions["new"])

        val galleryChange = acceptEditorPhoto(galleryBytes)
        updatePhotoDraft(drafts, "new-editor", galleryChange.draftPhoto, busy = false)
        val afterGalleryReplacement = saveMedicineFromEditor(
            repository = repository,
            medicine = newMedicine.copy(name = "New edited"),
            draftPhoto = drafts["new-editor"],
            removePhoto = galleryChange.removeStoredPhoto,
        ) {
            bumpPhotoVersion(photoVersions, "new")
        }
        assertTrue(afterGalleryReplacement.items.single { it.id == "new" }.hasPhoto)
        assertArrayEquals(galleryBytes, repository.loadPhoto("new"))
        assertEquals(2, photoVersions["new"])

        val existingWithoutPhoto = medicine("existing")
        saveMedicineFromEditor(repository, existingWithoutPhoto, null, false) {
            bumpPhotoVersion(photoVersions, "existing")
        }
        assertFalse(repository.loadSnapshot().items.single { it.id == "existing" }.hasPhoto)
        assertNull(photoVersions["existing"])

        val existingChange = acceptEditorPhoto(addedLaterBytes)
        updatePhotoDraft(drafts, "existing-editor", existingChange.draftPhoto, busy = false)
        val afterExistingAdd = saveMedicineFromEditor(
            repository = repository,
            medicine = existingWithoutPhoto,
            draftPhoto = drafts["existing-editor"],
            removePhoto = existingChange.removeStoredPhoto,
        ) {
            bumpPhotoVersion(photoVersions, "existing")
        }
        assertTrue(afterExistingAdd.items.single { it.id == "existing" }.hasPhoto)
        assertArrayEquals(addedLaterBytes, repository.loadPhoto("existing"))
        assertEquals(1, photoVersions["existing"])

        val quickCaptureBytes = jpeg(Color.MAGENTA)
        val quickCaptureMedicine = medicine("quick")
        val afterQuickCapture = repository.saveMedicine(quickCaptureMedicine, quickCaptureBytes)
        bumpPhotoVersion(photoVersions, "quick")
        assertTrue(afterQuickCapture.items.single { it.id == "quick" }.hasPhoto)
        assertArrayEquals(quickCaptureBytes, repository.loadPhoto("quick"))
        assertEquals(1, photoVersions["quick"])

        val reopened = repository.loadSnapshot()
        assertTrue(reopened.items.single { it.id == "new" }.hasPhoto)
        assertTrue(reopened.items.single { it.id == "existing" }.hasPhoto)
        assertTrue(reopened.items.single { it.id == "quick" }.hasPhoto)
        assertArrayEquals(galleryBytes, repository.loadPhoto("new"))
        assertArrayEquals(addedLaterBytes, repository.loadPhoto("existing"))
        assertArrayEquals(quickCaptureBytes, repository.loadPhoto("quick"))
    }

    private fun medicine(id: String) = Medicine(
        id = id,
        category = "tablets",
        subcategory = "General",
        name = "Medicine " + id,
        note = "",
        official = 1000,
        discounted = null,
    )

    private fun jpeg(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
            output.toByteArray()
        }
    }
}
