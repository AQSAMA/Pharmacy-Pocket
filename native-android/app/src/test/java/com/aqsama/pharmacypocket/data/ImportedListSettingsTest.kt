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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportedListSettingsTest {
    private val prefix = "import-settings"
    private val sheet = SpreadsheetSheet("Prices", listOf(
        SpreadsheetRow(4, listOf("Name", "Scientific name", "Store price", "Manufacturer", "Unused")),
        SpreadsheetRow(5, listOf("Brand A", "Generic A", "1,500.25", "Maker A", "Recover this")),
        SpreadsheetRow(6, listOf("Brand B", "Generic B", "2,500.25", "Maker B", "Recover that")),
        SpreadsheetRow(7, listOf("Brand C", "Generic C", "3,500.25", "Maker C", "Third value")),
    ))
    private val mappings = suggestMappings(sheet.rows[0].cells).map { if (it.column == 4) it.copy(field = ImportField.IGNORE) else it }
    private fun source(first: Int = 5, last: Int = 7) = ImportSource(SpreadsheetWorkbook(listOf(sheet)), ImportSelection(0, 0, first, last), prefix)
    private fun prepared(first: Int = 5, last: Int = 7, mapping: List<ColumnMapping> = mappings) = prepareSpreadsheet(sheet, 0, mapping, firstRow = first, lastRow = last, idPrefix = prefix)
    private fun photo(): ByteArray = ByteArrayOutputStream().also { out ->
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { compress(Bitmap.CompressFormat.JPEG, 80, out); recycle() }
    }.toByteArray()

    @Test fun rangeUsesSpreadsheetNumbersAndKeepsStableRowIdentity() {
        val selected = prepared(6, 6)
        assertEquals(listOf("Brand B"), selected.medicines.map { it.name })
        assertEquals("$prefix-6", selected.medicines.single().id)
        assertThrows(IllegalArgumentException::class.java) { prepared(7, 5) }
    }

    @Test fun savedSourceRestoresSkippedColumnsAndSettingsWithoutReimportAndPreservesLocalEdits() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ImportedListStore(context)
        val list = store.create("Prices", "Prices.xlsx", mappings, prepared(5, 6), source(5, 6))
        val repository = PharmacyRepository(context, list.id)
        try {
            val first = repository.loadSnapshot().items.first()
            val jpeg = photo()
            val edited = first.copy(name = "Local common name", favorite = true, codes = listOf(MedicineCode(CodeKind.BARCODE, "persistent-code")),
                importedFields = first.importedFields.map { if (it.field == ImportField.PHARMACY_PRICE) it.copy(value = "1,650.75") else it })
            repository.saveMedicine(syncImportedFields(edited), jpeg)
            repository.toggleFavorite(first.id)
            val reopened = ImportedListStore(context).loadSource(list)
            assertEquals(source(5, 6), reopened)
            assertEquals("Recover this", reopened.workbook.sheets[0].rows[1].cells[4])
            val changed = mappings.map { when (it.column) {
                1 -> it.copy(label = "Actual name", onCard = false)
                3 -> it.copy(label = "Company", onCard = true)
                4 -> it.copy(field = ImportField.CUSTOM, label = "Recovered field")
                else -> it
            } }
            val updated = store.update(list, "Adjusted", changed, prepared(5, 7, changed), source())
            assertEquals(updated, store.lists().single { it.id == list.id })
            val items = repository.loadSnapshot().items
            assertEquals(3, items.size)
            val retained = items.first { it.id == first.id }
            assertEquals("Local common name", retained.name)
            assertTrue(retained.favorite)
            assertEquals(edited.codes, retained.codes)
            assertArrayEquals(jpeg, repository.loadPhoto(first.id))
            assertEquals("1,650.75", retained.importedFields.first { it.field == ImportField.PHARMACY_PRICE }.value)
            assertEquals("Recover this", retained.importedFields.first { it.label == "Recovered field" }.value)
            assertTrue(retained.importedFields.first { it.label == "Company" }.onCard)
            store.update(updated, "Adjusted", changed, prepared(5, 5, changed), source(5, 5))
            assertEquals(1, repository.loadSnapshot().items.size)
            assertEquals(2, repository.loadTrash().size)
            store.update(updated, "Adjusted", changed, prepared(5, 7, changed), source())
            assertEquals(1, repository.loadSnapshot().items.size) // Don't resurrect Trash during settings changes.
        } finally { repository.close() }
    }

    @Test fun legacyImportsRemainConfigurableUsingSavedFields() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ImportedListStore(context)
        val list = store.create("Old list", "Prices.xlsx", mappings, prepared())
        val saved = store.loadSource(list)
        assertFalse(saved.originalAvailable)
        val changed = mappings.map { if (it.column == 1) it.copy(label = "Active ingredient", onCard = false) else it }
        val selected = saved.selection
        val prepared = prepareSpreadsheet(saved.workbook.sheets[0], selected.headerRow, changed, firstRow = selected.firstRow, lastRow = selected.lastRow, idPrefix = saved.idPrefix)
        val updated = store.update(list, "Old list", changed, prepared, saved)
        assertFalse(store.loadSource(updated).originalAvailable)
        val repository = PharmacyRepository(context, list.id)
        try { assertEquals("Active ingredient", repository.loadSnapshot().items.first().importedFields.first { it.field == ImportField.SCIENTIFIC }.label) }
        finally { repository.close() }
    }

    @Test fun moveKeepsAllDetailsPhotoCodesFavoriteAndBackupWithOnlyChosenMainPrice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ImportedListStore(context)
        val list = store.create("Prices", "Prices.xlsx", mappings, prepared(), source())
        val imported = PharmacyRepository(context, list.id)
        val main = PharmacyRepository(context)
        try {
            val original = imported.loadSnapshot().items.first()
            val jpeg = photo()
            val edited = original.copy(favorite = true, note = "Personal note", description = "Complete description", codes = listOf(MedicineCode(CodeKind.BARCODE, "transfer-code")))
            imported.saveMedicine(edited, jpeg)
            imported.toggleFavorite(original.id)
            imported.moveToMain(original.id, main, "Common name", 1700, "tablets")
            val moved = main.loadSnapshot().items.single()
            assertEquals("Common name", moved.name); assertEquals(1700L, moved.official)
            assertNull(moved.discounted); assertFalse(moved.imported); assertTrue(moved.favorite)
            assertEquals("Personal note", moved.note); assertEquals("Complete description", moved.description)
            assertEquals(edited.codes, moved.codes); assertArrayEquals(jpeg, main.loadPhoto(moved.id))
            assertEquals(original.name, moved.importedFields.first { it.key == "source-name" }.value)
            assertEquals(original.importedFields.map { it.copy(onCard = false) }, moved.importedFields.filter { it.key.startsWith("column-") })
            assertFalse(moved.importedFields.any { it.onCard })
            val backup = BackupCodec.parse(main.exportBackup())
            assertFalse(backup.importedList); assertEquals(moved.importedFields, backup.medicines.single().importedFields)
            main.importBackup(backup, ImportMode.REPLACE)
            assertArrayEquals(jpeg, main.loadPhoto(moved.id))
            assertEquals(2, imported.loadSnapshot().items.size)
            assertEquals(original.id, imported.loadTrash().single().medicine.id)
            imported.permanentlyDeleteMedicine(original.id)
            store.update(list, list.name, mappings, prepared(), source())
            assertEquals(2, imported.loadSnapshot().items.size) // A settings change cannot recreate a moved row.
            assertThrows(IllegalStateException::class.java) { runBlocking { imported.moveToMain(original.id, main, "Again", 1700, "tablets") } }
            assertEquals(1, main.loadSnapshot().items.size)
        } finally { imported.close(); main.close() }
    }

    @Test fun transferCodeConflictLeavesBothListsUntouched() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val list = ImportedListStore(context).create("Prices", "Prices.xlsx", mappings, prepared())
        val imported = PharmacyRepository(context, list.id)
        val main = PharmacyRepository(context)
        try {
            val item = imported.loadSnapshot().items.first().copy(codes = listOf(MedicineCode(CodeKind.BARCODE, "conflict")))
            imported.saveMedicine(item)
            main.saveMedicine(Medicine("existing", "tablets", "General", "Existing", "", official = 100, discounted = null, codes = item.codes))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { imported.moveToMain(item.id, main, "Common", 1000, "tablets") } }
            assertEquals(3, imported.loadSnapshot().items.size); assertEquals(0, imported.loadSnapshot().trashCount)
            assertEquals(listOf("Existing"), main.loadSnapshot().items.map { it.name })
        } finally { imported.close(); main.close() }
    }
    @Test fun explicitRestoreOfMovedRowSurvivesLaterSettingsChanges() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ImportedListStore(context)
        val list = store.create("Prices", "Prices.xlsx", mappings, prepared(), source())
        val imported = PharmacyRepository(context, list.id)
        val main = PharmacyRepository(context)
        try {
            val original = imported.loadSnapshot().items.first()
            imported.moveToMain(original.id, main, "Common", 1700, "tablets")
            imported.restoreMedicine(original.id)
            store.update(list, list.name, mappings, prepared(), source())
            assertEquals(3, imported.loadSnapshot().items.size)
            assertEquals(0, imported.loadSnapshot().trashCount)
        } finally { imported.close(); main.close() }
    }

}
