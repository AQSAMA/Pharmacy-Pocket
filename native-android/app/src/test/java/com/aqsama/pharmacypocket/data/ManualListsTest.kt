package com.aqsama.pharmacypocket.data

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManualListsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun medicine(id: String = "same", name: String = "Medicine", price: Long = 1500) =
        Medicine(id, "tablets", "Pain", name, "", official = price, discounted = 1200, createdAt = 42)

    @Test fun oldCatalogMigratesAsImportedWhileEmptyManualListIsDurable() = runBlocking {
        val catalog = File(context.filesDir, "SQLite/imported-lists.db").apply { parentFile?.mkdirs() }
        val id = UUID.randomUUID().toString()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(catalog, null).use { db ->
            db.execSQL("CREATE TABLE lists (id TEXT PRIMARY KEY, name TEXT NOT NULL, source TEXT NOT NULL, mappings TEXT NOT NULL)")
            db.execSQL("INSERT INTO lists VALUES (?, 'Old spreadsheet', 'source.csv', '[]')", arrayOf(id))
        }
        val store = ImportedListStore(context)
        assertTrue(store.lists().single().imported)
        val manual = store.createManual("Purchases")
        assertFalse(ImportedListStore(context).lists().single { it.id == manual.id }.imported)
        val repository = PharmacyRepository(context).forList(manual.id, imported = false)
        try {
            assertTrue(repository.loadSnapshot().items.isEmpty())
            assertTrue(repository.loadSnapshot().categories.any { it.id == "tablets" })
            repository.saveMedicine(medicine())
            assertFalse(BackupCodec.parse(repository.exportBackup()).importedList)
            store.rename(manual.id, "Stock")
            assertEquals("Stock", store.lists().single { it.id == manual.id }.name)
        } finally { repository.close() }
    }

    @Test fun aggregateSeparatesIdenticalIdsAndCurrenciesAndExcludesImportedLists() {
        val one = MedicationList(MAIN_LIST_KEY, "Mine", false)
        val two = MedicationList(UUID.randomUUID().toString(), "Stock", false)
        val iq = AppSnapshot(listOf(medicine()), PharmacyDefaults.categories, false, "IQD", ThemePreference.SYSTEM)
        val usd = iq.copy(currency = "USD", items = listOf(medicine(price = 5)))
        val aggregate = aggregateManualSnapshots(listOf(one to iq, two to usd), iq)
        assertEquals(2, aggregate.items.map { it.id }.distinct().size)
        assertEquals(2, aggregate.items.map { it.category }.distinct().size)
        assertEquals(listOf("IQD", "USD"), aggregate.items.map { it.displayCurrency })
        assertEquals("same", ListMedicineRef.decode(aggregate.items.last().id).medicineId)
        assertEquals(two.key, ListMedicineRef.decode(aggregate.items.last().id).listKey)
        assertThrows(IllegalArgumentException::class.java) { aggregateManualSnapshots(listOf(two.copy(imported = true) to usd), iq) }
    }

    @Test fun manualCopyMoveAndMergeWorkFromMainToNamedManualLists() = runBlocking {
        val main = PharmacyRepository(context)
        val other = PharmacyRepository(context, UUID.randomUUID().toString(), isImportedList = false)
        val third = PharmacyRepository(context, UUID.randomUUID().toString(), isImportedList = false)
        val cost = ImportedField("purchase-price", "Purchase price", "950.25", ImportField.WHOLESALE_PRICE, true)
        try {
            main.saveMedicine(medicine().copy(importedFields = listOf(cost), favorite = true))
            main.transferMedication("same", other, removeSource = false)
            val copy = other.loadSnapshot().items.single()
            assertEquals(1500L, copy.official)
            assertEquals(1200L, copy.discounted)
            assertEquals("Pain", copy.subcategory)
            assertEquals(listOf(cost), copy.importedFields)
            assertFalse(copy.imported)
            assertEquals(1, main.loadSnapshot().items.size)
            other.transferMedication(copy.id, third, removeSource = true)
            assertTrue(other.loadSnapshot().items.isEmpty())
            assertEquals(copy.id, other.loadTrash().single().medicine.id)
            third.saveMedicine(medicine("target", "My retained brand", 2200))
            main.transferMedication("same", third, false, mergeTargetId = "target")
            val merged = third.loadSnapshot().items.single { it.id == "target" }
            assertEquals("My retained brand", merged.name)
            assertEquals(2200L, merged.official)
            assertEquals(1200L, merged.discounted)
            assertEquals(cost, merged.importedFields.single { it.key == cost.key })
            assertThrows(IllegalArgumentException::class.java) { runBlocking { main.transferMedication("same", main, false) } }
            Unit
        } finally { main.close(); other.close(); third.close() }
    }

    @Test fun conflictingCodeDoesNotMoveEitherManualRecord() = runBlocking {
        val source = PharmacyRepository(context, UUID.randomUUID().toString(), false)
        val destination = PharmacyRepository(context, UUID.randomUUID().toString(), false)
        val code = MedicineCode(CodeKind.BARCODE, "123456")
        try {
            source.saveMedicine(medicine().copy(codes = listOf(code)))
            destination.saveMedicine(medicine("other").copy(codes = listOf(code)))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { source.transferMedication("same", destination, true) } }
            assertEquals(1, source.loadSnapshot().items.size)
            assertEquals(1, destination.loadSnapshot().items.size)
            assertTrue(source.loadTrash().isEmpty())
        } finally { source.close(); destination.close() }
    }

    @Test fun selectiveBackupAndReplacementPreserveOmittedPhotosAndCategories() = runBlocking {
        val repository = PharmacyRepository(context)
        val photo = ByteArrayOutputStream().also { out -> Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, out) }.toByteArray()
        try {
            repository.saveCategory(Category("own", "My category", "خاص", "#2f856d"))
            repository.saveMedicine(medicine().copy(category = "own"), photo)
            repository.saveMedicine(medicine("second"))
            val backup = BackupCodec.parse(repository.exportBackup(BackupSelection(setOf("same"), photos = false, categories = false)))
            assertEquals(listOf("same"), backup.medicines.map { it.id })
            assertTrue(backup.photos.isEmpty()); assertFalse(backup.photosSpecified); assertFalse(backup.categoriesSpecified)
            repository.importBackup(backup, ImportMode.REPLACE)
            assertArrayEquals(photo, repository.loadPhoto("same"))
            assertEquals("My category", repository.loadSnapshot().categories.single { it.id == "own" }.label)
            assertEquals("second", repository.loadTrash().single().medicine.id)
        } finally { repository.close() }
    }

    @Test fun libraryRoundTripKeepsListKindsAndReadsEarlierSingleListBackups() {
        val manual = MedicationList(MAIN_LIST_KEY, "My own list", false)
        val imported = MedicationList(UUID.randomUUID().toString(), "Supplier", true)
        val manualJson = BackupCodec.encode(listOf(medicine()), "IQD", PharmacyDefaults.categories)
        val importedJson = BackupCodec.encode(listOf(medicine().copy(imported = true)), "USD", PharmacyDefaults.categories, importedList = true)
        val entries = LibraryBackupCodec.parse(LibraryBackupCodec.encode(listOf(manual to manualJson, imported to importedJson)))
        assertEquals(listOf(manual, imported), entries.map { it.list })
        assertEquals(listOf("IQD", "USD"), entries.map { it.backup.currency })
        assertEquals(1, LibraryBackupCodec.parse(manualJson).size)
        assertTrue(LibraryBackupCodec.parse(importedJson).single().backup.importedList)
        assertThrows(IllegalArgumentException::class.java) { LibraryBackupCodec.encode(listOf(manual to manualJson, manual to manualJson)) }
    }
    @Test fun restoringMissingListsRetainsTheirIdAndRepeatedImportUpdatesThem() = runBlocking {
        val id = UUID.randomUUID().toString()
        val backup = BackupCodec.parse(BackupCodec.encode(listOf(medicine()), "IQD", PharmacyDefaults.categories))
        val entry = ListBackup(MedicationList(id, "Stock", false), backup)
        LibraryRestorer(context).restore(listOf(entry), null, ImportMode.MERGE)
        LibraryRestorer(context).restore(listOf(entry.copy(backup = backup.copy(medicines = listOf(medicine(name = "Updated"))))), null, ImportMode.MERGE)
        assertEquals(id, ImportedListStore(context).lists().single().id)
        val repository = PharmacyRepository(context, id, false)
        try { assertEquals("Updated", repository.loadSnapshot().items.single().name) } finally { repository.close() }
    }

    @Test fun fullImportPreflightRejectsConflictBeforeEarlierListChanges() = runBlocking {
        val main = PharmacyRepository(context)
        val manual = ImportedListStore(context).createManual("Stock")
        val stock = PharmacyRepository(context, manual.id, false)
        val code = MedicineCode(CodeKind.BARCODE, "123456")
        try {
            main.saveMedicine(medicine(name = "Original"))
            stock.saveMedicine(medicine("existing").copy(codes = listOf(code)))
            val first = ListBackup(MedicationList(MAIN_LIST_KEY, "Mine", false), BackupCodec.parse(BackupCodec.encode(listOf(medicine(name = "Changed")), "IQD", PharmacyDefaults.categories)))
            val second = ListBackup(MedicationList(manual.id, manual.name, false), BackupCodec.parse(BackupCodec.encode(listOf(medicine("conflict").copy(codes = listOf(code))), "IQD", PharmacyDefaults.categories)))
            assertThrows(IllegalStateException::class.java) { runBlocking { LibraryRestorer(context).restore(listOf(first, second), null, ImportMode.MERGE) } }
            assertEquals("Original", main.loadSnapshot().items.single().name)
            assertEquals("existing", stock.loadSnapshot().items.single().id)
        } finally { main.close(); stock.close() }
    }

    @Test fun mergeRejectsImportedDestinationWithoutChangingItsMappedPrices() = runBlocking {
        val source = PharmacyRepository(context)
        val imported = PharmacyRepository(context, UUID.randomUUID().toString())
        try {
            source.saveMedicine(medicine())
            imported.saveMedicine(medicine("target").copy(imported = true, importedFields = listOf(
                ImportedField("price", "Mapped price", "900", ImportField.PHARMACY_PRICE, true))))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { source.transferMedication("same", imported, true, mergeTargetId = "target") } }
            assertEquals("900", imported.loadSnapshot().items.single().importedFields.single().value)
            assertEquals(1, source.loadSnapshot().items.size)
        } finally { source.close(); imported.close() }
    }

    @Test fun mergingManualMedicineRetainsItsEarlierImportedProvenance() {
        val fields = listOf(
            ImportedField("source-name", "Original name", "Supplier brand", ImportField.CUSTOM, false),
            ImportedField("source-currency", "Source price currency", "USD", ImportField.CUSTOM, false),
            ImportedField("source-price", "Source pharmacy price", "3.50", ImportField.CUSTOM, false),
            ImportedField("scientific", "Scientific name", "Amoxicillin", ImportField.SCIENTIFIC, false),
        )
        val source = medicine(name = "My source name", price = 2000).copy(importedFields = fields)
        val merged = mergeMedicationDetails(medicine("target", "My target", 3000), source, "IQD")
        fields.forEach { field -> assertEquals(field, merged.importedFields.single { it.key == field.key }) }
        assertFalse(merged.importedFields.any { it.key == "source-alternative-price" })
        assertEquals("My target", merged.name)
        assertEquals(3000L, merged.official)
    }

}
