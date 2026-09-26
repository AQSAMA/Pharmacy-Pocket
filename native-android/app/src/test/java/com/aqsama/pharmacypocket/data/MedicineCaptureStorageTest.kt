package com.aqsama.pharmacypocket.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedicineCaptureStorageTest {
    private lateinit var storage: MedicineDatabase
    private lateinit var dbFile: File

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbFile = File(context.filesDir, "SQLite/pharmacy-pocket.db")
        dbFile.delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
        storage = MedicineDatabase(context)
    }

    @After fun tearDown() {
        storage.close()
        dbFile.delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
    }

    private fun item() = Medicine(
        id = "medicine-1", category = "tablets", subcategory = "General", name = "Medicine",
        note = "", official = 1000, discounted = null,
        codes = listOf(
            MedicineCode(CodeKind.BARCODE, "8901111701119", "Company A"),
            MedicineCode(CodeKind.PRICE_STICKER_QR, "sticker:opaque"),
        ),
    )

    @Test fun codesAndPhotoSurviveEditTrashRestoreAndLegacyMerge() {
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())
        storage.saveMedicine(item(), jpeg)
        assertEquals(2, storage.loadMedicines().single().codes.size)
        assertTrue(storage.loadMedicines().single().hasPhoto)
        storage.saveMedicine(item().copy(name = "Edited"))
        storage.moveMedicineToTrash("medicine-1")
        assertEquals(2, storage.loadTrash().single().medicine.codes.size)
        storage.restoreMedicine("medicine-1")
        storage.mergeMedicines(listOf(item().copy(codes = emptyList(), codesSpecified = false)))
        assertEquals(item().codes, storage.loadMedicines().single().codes)
        assertArrayEquals(jpeg, storage.loadPhoto("medicine-1"))
        storage.moveMedicineToTrash("medicine-1")
        storage.permanentlyDeleteMedicine("medicine-1")
        assertNull(storage.loadPhoto("medicine-1"))
    }

    @Test fun removingPhotoAndCodesIsExplicit() {
        storage.saveMedicine(item(), byteArrayOf(1, 2, 3))
        storage.saveMedicine(item().copy(codes = emptyList()), removePhoto = true)
        assertFalse(storage.loadMedicines().single().hasPhoto)
        assertTrue(storage.loadMedicines().single().codes.isEmpty())
    }

    @Test fun backupRoundTripIncludesCodesAndSupportsLegacyFiles() {
        val json = BackupCodec.encode(listOf(item()), "IQD", PharmacyDefaults.categories)
        assertEquals(item().codes, BackupCodec.parse(json).medicines.single().codes)
        val legacy = json.replace("\"version\": 3", "\"version\": 2")
            .replace(Regex(",?\\s*\"codes\": \\[.*?]", RegexOption.DOT_MATCHES_ALL), "")
        assertTrue(BackupCodec.parse(legacy).medicines.single().codes.isEmpty())
        assertFalse(BackupCodec.parse(legacy).medicines.single().codesSpecified)
    }
}
