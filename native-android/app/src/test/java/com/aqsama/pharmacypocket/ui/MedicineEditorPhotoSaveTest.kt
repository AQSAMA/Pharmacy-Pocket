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
import org.junit.Assert.assertFalse
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
    fun editorSaveCarriesAcceptedJpegIntoSqliteAndReloadsIt() = runBlocking {
        val cameraBytes = jpeg(Color.RED)
        val galleryBytes = jpeg(Color.BLUE)
        val addedLaterBytes = jpeg(Color.GREEN)

        val newMedicine = medicine("new")
        val afterCameraSave = saveMedicineFromEditor(
            repository = repository,
            medicine = newMedicine,
            draftPhoto = cameraBytes,
            removePhoto = false,
        )
        assertTrue(afterCameraSave.items.single { it.id == "new" }.hasPhoto)
        assertArrayEquals(cameraBytes, repository.loadPhoto("new"))

        val afterGalleryReplacement = saveMedicineFromEditor(
            repository = repository,
            medicine = newMedicine.copy(name = "New edited"),
            draftPhoto = galleryBytes,
            removePhoto = false,
        )
        assertTrue(afterGalleryReplacement.items.single { it.id == "new" }.hasPhoto)
        assertArrayEquals(galleryBytes, repository.loadPhoto("new"))

        val existingWithoutPhoto = medicine("existing")
        saveMedicineFromEditor(repository, existingWithoutPhoto, null, false)
        assertFalse(repository.loadSnapshot().items.single { it.id == "existing" }.hasPhoto)

        val afterExistingAdd = saveMedicineFromEditor(
            repository = repository,
            medicine = existingWithoutPhoto,
            draftPhoto = addedLaterBytes,
            removePhoto = false,
        )
        assertTrue(afterExistingAdd.items.single { it.id == "existing" }.hasPhoto)
        assertArrayEquals(addedLaterBytes, repository.loadPhoto("existing"))

        val reopened = repository.loadSnapshot()
        assertTrue(reopened.items.single { it.id == "new" }.hasPhoto)
        assertTrue(reopened.items.single { it.id == "existing" }.hasPhoto)
        assertArrayEquals(galleryBytes, repository.loadPhoto("new"))
        assertArrayEquals(addedLaterBytes, repository.loadPhoto("existing"))
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
