package com.aqsama.pharmacypocket.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
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
        File(context.noBackupFilesDir, "medicine_drafts").deleteRecursively()
        repository = PharmacyRepository(context)
    }

    @Test
    fun diskBackedDraftStoresTheExactAcceptedJpeg() {
        val bytes = jpeg(Color.RED)

        val path = writeMedicinePhotoDraft(context, bytes)

        assertArrayEquals(bytes, readMedicinePhotoDraft(path))
        deleteMedicinePhotoDraft(path)
        assertNull(readMedicinePhotoDraft(path))
    }

    @Test
    fun draftPathSurvivesRestorationAndReplacementUsesOneSourceOfTruth() {
        val firstBytes = jpeg(Color.RED)
        val replacementBytes = jpeg(Color.BLUE)
        val firstPath = writeMedicinePhotoDraft(context, firstBytes)
        val replacementPath = writeMedicinePhotoDraft(context, replacementBytes)
        val drafts = mutableMapOf("editor-entry" to firstPath)

        val restored = restoreMedicinePhotoDraftPaths(encodeMedicinePhotoDraftPaths(drafts)).toMutableMap()
        assertEquals(firstPath, restored["editor-entry"])
        assertArrayEquals(firstBytes, readMedicinePhotoDraft(restored["editor-entry"]))

        updateMedicinePhotoDraftPath(restored, "editor-entry", replacementPath, busy = false)
        assertNull(readMedicinePhotoDraft(firstPath))
        assertEquals(replacementPath, restored["editor-entry"])
        assertArrayEquals(replacementBytes, readMedicinePhotoDraft(restored["editor-entry"]))

        val rejectedPath = writeMedicinePhotoDraft(context, jpeg(Color.GREEN))
        updateMedicinePhotoDraftPath(restored, "editor-entry", rejectedPath, busy = true)
        assertNull(readMedicinePhotoDraft(rejectedPath))
        assertEquals(replacementPath, restored["editor-entry"])

        updateMedicinePhotoDraftPath(restored, "editor-entry", null, busy = false)
        assertFalse(restored.containsKey("editor-entry"))
        assertNull(readMedicinePhotoDraft(replacementPath))
    }

    @Test
    fun galleryPickerReturnFlowsThroughEditorPreviewRestorationAndSave() = runBlocking {
        val sourceFile = File(context.cacheDir, "gallery-picker-source.jpg")
        val source = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.CYAN)
        }
        sourceFile.outputStream().use { output ->
            assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 92, output))
        }

        val drafts = mutableMapOf<String, String>()
        val entryId = "picker-editor"
        try {
            val accepted = acceptEditorPickedPhoto(
                context = context,
                uri = Uri.fromFile(sourceFile),
                isBusy = { false },
                onDraftPhotoPathChange = { path ->
                    updateMedicinePhotoDraftPath(drafts, entryId, path, busy = false)
                },
            )

            assertTrue(accepted)
            val draftPath = requireNotNull(drafts[entryId])
            val previewBytes = requireNotNull(readMedicinePhotoDraft(draftPath))
            val preview = requireNotNull(BitmapFactory.decodeByteArray(previewBytes, 0, previewBytes.size))
            assertTrue(preview.width <= 1200)
            assertTrue(preview.height <= 1200)
            assertEquals(preview.width, preview.height * 2)

            val restoredDrafts = restoreMedicinePhotoDraftPaths(
                encodeMedicinePhotoDraftPaths(drafts),
            )
            val restoredPath = requireNotNull(restoredDrafts[entryId])
            val restoredPreviewBytes = requireNotNull(readMedicinePhotoDraft(restoredPath))
            val restoredPreview = requireNotNull(
                BitmapFactory.decodeByteArray(restoredPreviewBytes, 0, restoredPreviewBytes.size),
            )
            assertEquals(preview.width, restoredPreview.width)
            assertEquals(preview.height, restoredPreview.height)

            val medicine = medicine("picker")
            val saved = saveMedicineFromEditor(
                repository = repository,
                medicine = medicine,
                draftPhotoPath = restoredPath,
                removePhoto = false,
            )

            assertTrue(saved.items.single { it.id == "picker" }.hasPhoto)
            val persisted = requireNotNull(repository.loadPhoto("picker"))
            val persistedPreview = requireNotNull(
                BitmapFactory.decodeByteArray(persisted, 0, persisted.size),
            )
            assertEquals(restoredPreview.width, persistedPreview.width)
            assertEquals(restoredPreview.height, persistedPreview.height)
        } finally {
            sourceFile.delete()
            drafts.values.forEach(::deleteMedicinePhotoDraft)
        }
    }

    @Test
    fun editorSaveRejectsADraftThatDisappearedInsteadOfSilentlySavingWithoutPhoto() = runBlocking {
        val path = writeMedicinePhotoDraft(context, jpeg(Color.RED))
        deleteMedicinePhotoDraft(path)

        var failed = false
        try {
            saveMedicineFromEditor(
                repository = repository,
                medicine = medicine("missing"),
                draftPhotoPath = path,
                removePhoto = false,
            )
        } catch (error: IllegalStateException) {
            failed = true
            assertTrue(error.message.orEmpty().contains("no longer available"))
        }

        assertTrue(failed)
        assertTrue(repository.loadSnapshot().items.none { it.id == "missing" })
    }

    @Test
    fun editorAndQuickCapturePersistPhotosAndReloadThem() = runBlocking {
        val cameraBytes = jpeg(Color.RED)
        val galleryBytes = jpeg(Color.BLUE)
        val addedLaterBytes = jpeg(Color.GREEN)
        val photoVersions = mutableMapOf<String, Int>()

        val cameraPath = writeMedicinePhotoDraft(context, cameraBytes)
        val newMedicine = medicine("new")
        val afterCameraSave = saveMedicineFromEditor(
            repository = repository,
            medicine = newMedicine,
            draftPhotoPath = cameraPath,
            removePhoto = false,
        ) {
            bumpPhotoVersion(photoVersions, "new")
        }
        assertTrue(afterCameraSave.items.single { it.id == "new" }.hasPhoto)
        assertArrayEquals(cameraBytes, repository.loadPhoto("new"))
        assertEquals(1, photoVersions["new"])

        val galleryPath = writeMedicinePhotoDraft(context, galleryBytes)
        val afterGalleryReplacement = saveMedicineFromEditor(
            repository = repository,
            medicine = newMedicine.copy(name = "New edited"),
            draftPhotoPath = galleryPath,
            removePhoto = false,
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

        val existingPath = writeMedicinePhotoDraft(context, addedLaterBytes)
        val afterExistingAdd = saveMedicineFromEditor(
            repository = repository,
            medicine = existingWithoutPhoto,
            draftPhotoPath = existingPath,
            removePhoto = false,
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
