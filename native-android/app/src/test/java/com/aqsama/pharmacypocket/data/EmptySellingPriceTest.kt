package com.aqsama.pharmacypocket.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmptySellingPriceTest {
    private fun item(price: Long? = null) = Medicine("empty", "tablets", "General", "Test medicine", "", official = price, discounted = null,
        importedFields = listOf(ImportedField("purchase", "Purchase price", "1700", ImportField.WHOLESALE_PRICE, true)))
    private fun json(price: Long? = null) = BackupCodec.encode(listOf(item(price)), "IQD", PharmacyDefaults.categories)

    @Test fun nullZeroAndLegacyPricesRoundTripWithoutChangingPurchasePrices() {
        for (price in listOf(null, 0L, 1250L)) {
            val raw = json(price)
            val field = JSONObject(raw).getJSONArray("medicines").getJSONObject(0)
            assertTrue(field.has("official"))
            assertEquals(price == null, field.isNull("official"))
            for (version in 1..3) {
                val parsed = BackupCodec.parse(JSONObject(raw).put("version", version).toString()).medicines.single()
                assertEquals(price, parsed.official)
                assertEquals(item().importedFields, parsed.importedFields)
            }
            val envelope = LibraryBackupCodec.encode(listOf(MedicationList("main", "My stock", false) to raw))
            assertEquals(price, LibraryBackupCodec.parse(envelope).single().backup.medicines.single().official)
        }
        val omitted = JSONObject(json())
        omitted.getJSONArray("medicines").getJSONObject(0).remove("official")
        assertNull(BackupCodec.parse(omitted.toString()).medicines.single().official)
    }

    @Test fun diagnosticsContainListMedicineFieldValueAndExpectedFormatRegardlessOfPropertyOrder() {
        for (bad in listOf<Any>("1250", -1, 1.25, true, 9_007_199_254_740_992L)) {
            val root = JSONObject(json())
            root.getJSONArray("medicines").getJSONObject(0).put("official", bad)
            // Name intentionally follows the backup in the streaming envelope.
            val entry = JSONObject().put("backup", root).put("name", "Ward A").put("id", "main").put("imported", false)
            val raw = JSONObject().put("schema", LibraryBackupCodec.schema).put("version", 1).put("lists", JSONArray().put(entry)).toString()
            val failure = assertThrows(IllegalArgumentException::class.java) { LibraryBackupCodec.parse(raw) }
            val message = failure.message!!
            for (part in listOf("Ward A", "Medicine #1", "Test medicine", "official", importValue(bad), "expected", "integer")) assertTrue(message, message.contains(part))
        }
    }

    @Test fun streamingMetadataUsesTheSameStrictTypesAndDiagnosticsAsSingleListBackups() {
        val badFields = listOf(
            "version" to "3", "version" to 1.5, "version" to 4,
            "currency" to 1250, "currency" to "X".repeat(25),
            "photosIncluded" to "true", "categoriesIncluded" to JSONObject.NULL,
            "importedList" to "false",
        )
        for ((field, bad) in badFields) {
            val root = JSONObject(json()).put(field, bad)
            val single = assertThrows(IllegalArgumentException::class.java) { BackupCodec.parse(root.toString()) }
            val envelope = LibraryBackupCodec.encode(listOf(MedicationList("main", "Ward A", false) to root.toString()))
            val library = assertThrows(IllegalArgumentException::class.java) { LibraryBackupCodec.parse(envelope) }
            for (failure in listOf(single, library)) {
                val message = failure.message!!
                for (part in listOf(field, importValue(bad), "expected")) assertTrue(message, message.contains(part))
            }
            assertTrue(library.message!!, library.message!!.contains("Ward A"))
            // Force the compact imported-list streaming route too.
            if (field != "importedList") {
                val compact = "{\"importedList\":true," + root.toString().removePrefix("{")
                val failure = assertThrows(IllegalArgumentException::class.java) { BackupCodec.parse(compact) }
                assertTrue(failure.message!!, failure.message!!.contains("Field '$field'"))
            }
        }
    }

    @Test fun nestedCodeAndCustomFieldErrorsHaveExactPaths() {
        val root = JSONObject(json())
        val medicine = root.getJSONArray("medicines").getJSONObject(0)
        medicine.put("codes", JSONArray().put(JSONObject().put("kind", "TYPO").put("value", "abc")))
        val codeFailure = assertThrows(IllegalArgumentException::class.java) { BackupCodec.parse(root.toString()) }
        assertTrue(codeFailure.message!!, codeFailure.message!!.contains("codes[0].kind"))
        medicine.remove("codes")
        medicine.getJSONArray("importedFields").getJSONObject(0).put("color", "red")
        val fieldFailure = assertThrows(IllegalArgumentException::class.java) { BackupCodec.parse(root.toString()) }
        assertTrue(fieldFailure.message!!, fieldFailure.message!!.contains("importedFields[0].color"))
    }

    @Test fun storageEditTrashRestoreAndReopenPreserveNullAndPurchasePrice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString()
        val storage = MedicineDatabase(context, id)
        try {
            storage.saveMedicine(item())
            assertNull(storage.loadMedicines().single().official)
            storage.saveMedicine(item(0))
            assertEquals(0L, storage.loadMedicines().single().official)
            storage.saveMedicine(item())
            storage.moveMedicineToTrash("empty")
            assertNull(storage.loadTrash().single().medicine.official)
            storage.restoreMedicine("empty")
            storage.close()
            assertNull(storage.loadMedicines().single().official)
            assertEquals("1700", storage.loadMedicines().single().importedFields.single().value)
        } finally { storage.close(); storage.dbFile.delete() }
    }

    @Test fun v4MigrationPreservesRowsPhotosAndTrashAndAllowsNull() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = MedicineDatabase(context, UUID.randomUUID().toString())
        storage.dbFile.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(storage.dbFile, null).use { old ->
            old.execSQL("CREATE TABLE medicines (id TEXT PRIMARY KEY NOT NULL,category TEXT NOT NULL,subcategory TEXT NOT NULL,name TEXT NOT NULL,note TEXT NOT NULL,official INTEGER NOT NULL,discounted INTEGER,revision INTEGER NOT NULL DEFAULT 0,favorite INTEGER NOT NULL DEFAULT 0,sort_order INTEGER NOT NULL,deleted_at INTEGER)")
            old.execSQL("INSERT INTO medicines VALUES ('empty','tablets','General','Old medicine','',1250,1000,7,1,8,NULL)")
            old.execSQL("INSERT INTO medicines VALUES ('trash','tablets','General','Trashed','',0,NULL,0,0,9,123)")
            old.execSQL("CREATE TABLE medicine_photos (medicine_id TEXT PRIMARY KEY NOT NULL,jpeg BLOB NOT NULL)")
            old.execSQL("INSERT INTO medicine_photos VALUES ('empty',X'010203')")
            old.execSQL("PRAGMA user_version=4")
        }
        try {
            val old = storage.loadMedicines().single()
            assertEquals(1250L, old.official); assertEquals(1000L, old.discounted)
            assertEquals(7, old.revision); assertTrue(old.favorite)
            assertEquals(0L, storage.loadTrash().single().medicine.official)
            assertArrayEquals(byteArrayOf(1,2,3), storage.loadPhoto(old.id))
            storage.saveMedicine(old.copy(official = null))
            storage.close()
            assertNull(storage.loadMedicines().single().official)
            assertArrayEquals(byteArrayOf(1,2,3), storage.loadPhoto(old.id))
        } finally { storage.close(); storage.dbFile.delete() }
    }

    @Test fun repositoryBackupReplaceMergeAndTransfersPreserveEmptySellingPrices() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val firstId = UUID.randomUUID().toString()
        val secondId = UUID.randomUUID().toString()
        val first = PharmacyRepository(context, firstId, false)
        val second = PharmacyRepository(context, secondId, false)
        try {
            first.saveMedicine(item(1250))
            first.importBackup(BackupCodec.parse(json()), ImportMode.MERGE)
            assertNull(first.loadSnapshot().items.single().official)
            second.importBackup(BackupCodec.parse(first.exportBackup()), ImportMode.REPLACE)
            assertNull(second.loadSnapshot().items.single().official)
            assertEquals(item().importedFields, second.loadSnapshot().items.single().importedFields)
            second.importBackup(BackupCodec.parse(json(0)), ImportMode.REPLACE)
            assertEquals(0L, second.loadSnapshot().items.single().official)
            first.setSellingPrice("empty", 1250)
            assertEquals("1700", first.loadSnapshot().items.single().importedFields.single().value)
            second.importBackup(BackupCodec.parse(BackupCodec.encode(emptyList(), "IQD", PharmacyDefaults.categories)), ImportMode.REPLACE)
            first.transferMedication("empty", second, false, price = null, priceSpecified = true)
            assertNull(second.loadSnapshot().items.single().official)
        } finally {
            first.close(); second.close()
            File(context.filesDir, "SQLite/imported-$firstId.db").delete()
            File(context.filesDir, "SQLite/imported-$secondId.db").delete()
        }
    }

    @Test fun emptyPricesSortAfterKnownPricesInBothDirections() {
        val empty = item()
        assertTrue(compareMedicines(empty, item(0), MedicineSort.PRICE_ASC) > 0)
        assertTrue(compareMedicines(empty, item(0), MedicineSort.PRICE_DESC) > 0)
        assertEquals("Add price", formatPrice(null)); assertEquals("0", formatPrice(0))
    }
}
