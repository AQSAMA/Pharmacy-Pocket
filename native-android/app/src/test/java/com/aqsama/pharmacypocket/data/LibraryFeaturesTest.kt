package com.aqsama.pharmacypocket.data

import android.content.Context
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryFeaturesTest {
    private fun medicine(id: String = "m", name: String = "Mine") = Medicine(id, "tablets", "General", name, "", official = 1500, discounted = 1200, createdAt = 42)
    private fun snapshot(items: List<Medicine>, categories: List<Category>) = AppSnapshot(items, categories, false, "IQD", ThemePreference.SYSTEM)

    @Test fun repeatedMergesReplaceSourceProvenanceAndClearAbsentDiscount() {
        val personal = ImportedField("source-shelf", "Shelf", "A4", ImportField.CUSTOM, true,
            color = "#2f856d", placement = FieldPlacement.FOOTER)
        val own = medicine().copy(favorite = true, importedFields = listOf(personal))
        val first = mergeMedicationDetails(own, medicine("first", "First source"), "IQD")
        assertEquals("1200", first.importedFields.single { it.key == "source-alternative-price" }.value)
        val styled = first.copy(importedFields = first.importedFields.map {
            if (it.key == "source-price") it.copy(onCard = true, color = "#596aab", placement = FieldPlacement.TOP) else it
        })
        val secondSource = medicine("second", "Second source").copy(official = 2500, discounted = null)
        val second = mergeMedicationDetails(styled, secondSource, "USD")
        assertFalse(second.importedFields.any { it.key == "source-alternative-price" })
        assertEquals("Second source", second.importedFields.single { it.key == "source-name" }.value)
        assertEquals("USD", second.importedFields.single { it.key == "source-currency" }.value)
        val price = second.importedFields.single { it.key == "source-price" }
        assertEquals("2500", price.value)
        assertTrue(price.onCard)
        assertEquals("#596aab", price.color)
        assertEquals(FieldPlacement.TOP, price.placement)
        assertEquals(personal, second.importedFields.single { it.key == personal.key })
        assertEquals(own.copy(importedFields = second.importedFields, revision = 2), second)

        val third = mergeMedicationDetails(second, secondSource.copy(discounted = 2000), "USD")
        assertEquals("2000", third.importedFields.single { it.key == "source-alternative-price" }.value)
        assertEquals(third.importedFields.size, third.importedFields.map { it.key }.distinct().size)
    }

    @Test fun fieldsAndFlatCategoriesSurviveDatabaseTrashAndBackup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PharmacyRepository(context)
        try {
            repo.saveCategory(Category("leaf", "Adult", "بالغ", "#596aab"))
            val fields = listOf(
                ImportedField("custom-dose", "جرعة", "Twice daily", ImportField.CUSTOM, true, color = "#2f856d", placement = FieldPlacement.TOP),
                ImportedField("custom-company", "Company", "Maker", ImportField.CUSTOM, false, placement = FieldPlacement.FOOTER),
            )
            repo.saveMedicine(medicine().copy(category = "leaf", importedFields = fields))
            repo.moveMedicineToTrash("m")
            assertEquals(fields, repo.loadTrash().single().medicine.importedFields)
            repo.restoreMedicine("m")
            val backup = BackupCodec.parse(repo.exportBackup())
            assertEquals(fields, backup.medicines.single().importedFields)
            repo.importBackup(backup, ImportMode.REPLACE)
            val reloaded = repo.loadSnapshot()
            assertEquals("leaf", reloaded.items.single().category)
            assertTrue(reloaded.categories.any { it.id == "leaf" })
        } finally { repo.close() }
    }

    @Test fun earlierNestedTrialBackupsKeepCategoriesAndMedicineAssignments() = runBlocking {
        val categories = listOf(Category("tablets", "Tablets", "حبوب", "#2f856d"), Category("leaf", "Pain", "ألم", "#596aab"))
        val raw = org.json.JSONObject(BackupCodec.encode(listOf(medicine().copy(category = "leaf")), "IQD", categories))
        raw.getJSONArray("categories").getJSONObject(1).put("parentId", "tablets")
        val backup = BackupCodec.parse(raw.toString())
        val repo = PharmacyRepository(ApplicationProvider.getApplicationContext())
        try {
            repo.importBackup(backup, ImportMode.REPLACE)
            assertEquals("leaf", repo.loadSnapshot().items.single().category)
            assertTrue(repo.loadSnapshot().categories.any { it.id == "leaf" })
            val exported = org.json.JSONObject(repo.exportBackup()).getJSONArray("categories")
            for (i in 0 until exported.length()) assertFalse(exported.getJSONObject(i).has("parentId"))
        } finally { repo.close() }
    }

    @Test fun copyMoveAndMergePreservePricesIdentityAndSourceUntilRequested() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = PharmacyRepository(context, UUID.randomUUID().toString())
        val other = PharmacyRepository(context, UUID.randomUUID().toString())
        val main = PharmacyRepository(context)
        try {
            val fields = listOf(ImportedField("column-1", "Scientific", "Amoxicillin", ImportField.SCIENTIFIC, true))
            source.saveCategory(Category("leaf", "Leaf", "Leaf", "#596aab"))
            source.saveCategory(Category("unrelated", "Unrelated", "Unrelated", "#596aab"))
            val original = medicine("source", "Imported brand").copy(category = "leaf", imported = true, note = "Imported note", importedFields = fields,
                codes = listOf(MedicineCode(CodeKind.BARCODE, "123456789")))
            val jpeg = ByteArrayOutputStream().also { output ->
                Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { compress(Bitmap.CompressFormat.JPEG, 80, output); recycle() }
            }.toByteArray()
            source.saveMedicine(original, jpeg)
            source.transferMedication(original.id, other, false)
            assertEquals(1, source.loadSnapshot().items.size)
            assertEquals(fields, other.loadSnapshot().items.single().importedFields)
            assertEquals("Leaf", other.loadSnapshot().categories.first { it.id == "leaf" }.label)
            assertFalse(other.loadSnapshot().categories.any { it.id == "unrelated" })
            assertArrayEquals(jpeg, other.loadPhoto(other.loadSnapshot().items.single().id))
            val own = medicine("own", "My common name").copy(favorite = true, importedFields = listOf(ImportedField("custom-note", "Shelf", "A4", ImportField.CUSTOM, true)))
            main.saveMedicine(own)
            source.transferMedication(original.id, main, false, mergeTargetId = own.id)
            val merged = main.loadSnapshot().items.single()
            assertEquals(own.name, merged.name); assertEquals(own.official, merged.official); assertEquals(own.discounted, merged.discounted)
            assertEquals(own.category, merged.category); assertEquals(own.createdAt, merged.createdAt); assertTrue(merged.favorite)
            assertEquals("Imported note", merged.note)
            assertArrayEquals(jpeg, main.loadPhoto(own.id))
            assertEquals("Amoxicillin", merged.importedFields.first { it.field == ImportField.SCIENTIFIC }.value)
            assertEquals("A4", merged.importedFields.first { it.key == "custom-note" }.value)
            assertEquals(1, source.loadSnapshot().items.size)
            // Merging again updates the same record and never duplicates codes or fields.
            source.saveMedicine(original.copy(discounted = null))
            source.transferMedication(original.id, main, true, mergeTargetId = own.id)
            assertEquals(1, main.loadSnapshot().items.size)
            assertEquals(1, main.loadSnapshot().items.single().codes.size)
            assertFalse(main.loadSnapshot().items.single().importedFields.any { it.key == "source-alternative-price" })
            assertEquals(own.discounted, main.loadSnapshot().items.single().discounted)
            assertTrue(source.loadSnapshot().items.isEmpty())
            assertEquals(original.id, source.loadTrash().single().medicine.id)
        } finally { source.close(); other.close(); main.close() }
    }

    @Test fun conflictDoesNotChangeSourceOrMergeTarget() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = PharmacyRepository(context, UUID.randomUUID().toString())
        val main = PharmacyRepository(context)
        try {
            val code = MedicineCode(CodeKind.BARCODE, "taken")
            source.saveMedicine(medicine("source").copy(imported = true, codes = listOf(code)))
            main.saveMedicine(medicine("target"))
            main.saveMedicine(medicine("owner").copy(codes = listOf(code)))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { source.transferMedication("source", main, true, mergeTargetId = "target") } }
            assertEquals(1, source.loadSnapshot().items.size); assertEquals(0, source.loadSnapshot().trashCount)
            assertTrue(main.loadSnapshot().items.first { it.id == "target" }.importedFields.isEmpty())
        } finally { source.close(); main.close() }
    }
}
