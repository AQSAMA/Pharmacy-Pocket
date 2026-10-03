package com.aqsama.pharmacypocket.data

import android.content.Context
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

    @Test fun fieldsAndParentFoldersSurviveDatabaseTrashAndBackup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PharmacyRepository(context)
        try {
            repo.saveCategory(Category("child", "Pain", "ألم", "#2f856d", "tablets"))
            repo.saveCategory(Category("leaf", "Adult", "بالغ", "#596aab", "child"))
            val fields = listOf(
                ImportedField("custom-dose", "جرعة", "Twice daily", ImportField.CUSTOM, true, color = "#2f856d", placement = FieldPlacement.TOP),
                ImportedField("custom-company", "Company", "Maker", ImportField.CUSTOM, false, placement = FieldPlacement.FOOTER),
            )
            repo.saveMedicine(medicine().copy(category = "leaf", importedFields = fields))
            repo.setCategoryView(CategoryView.TREE)
            repo.moveMedicineToTrash("m")
            assertEquals(fields, repo.loadTrash().single().medicine.importedFields)
            repo.restoreMedicine("m")
            val backup = BackupCodec.parse(repo.exportBackup())
            assertEquals(fields, backup.medicines.single().importedFields)
            assertEquals("child", backup.categories.first { it.id == "leaf" }.parentId)
            repo.importBackup(backup, ImportMode.REPLACE)
            val reloaded = repo.loadSnapshot()
            assertEquals(listOf("tablets", "child", "leaf"), categoryAncestors("leaf", reloaded.categories).map { it.id })
            assertEquals(CategoryView.TREE, reloaded.categoryView)
        } finally { repo.close() }
    }

    @Test fun cyclesAndMissingParentsAreRejectedBeforeSaving() = runBlocking {
        val repo = PharmacyRepository(ApplicationProvider.getApplicationContext())
        try {
            repo.saveCategory(Category("child", "Child", "Child", "#2f856d", "tablets"))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.saveCategory(Category("tablets", "Tablets", "Tablets", "#596aab", "child")) } }
            assertThrows(IllegalStateException::class.java) { runBlocking { repo.saveCategory(Category("orphan", "Orphan", "Orphan", "#596aab", "missing")) } }
            assertNull(repo.loadSnapshot().categories.first { it.id == "tablets" }.parentId)
        } finally { repo.close() }
    }

    @Test fun parentBrowsingIncludesDescendantsAndKeepsRepeatedSubfolderNamesSeparate() {
        val cats = listOf(Category("all", "All", "All", "#2f856d"), Category("tablets", "Tablets", "حبوب", "#2f856d"),
            Category("child", "Pain", "ألم", "#596aab", "tablets"), Category("other", "Other", "Other", "#596aab"))
        val browser = buildLibraryBrowser(snapshot(listOf(medicine().copy(category = "child", subcategory = "Adult"), medicine("other").copy(category = "other", subcategory = "Adult")), cats))
        assertEquals(1, browser.folders.first { it.categoryId == "tablets" }.count)
        assertEquals(2, browser.folders.count { it.label == "Adult" })
        assertNotEquals(browser.medicinePaths["m"]!!.last(), browser.medicinePaths["other"]!!.last())
        assertEquals(listOf("category:tablets", "category:child"), browser.medicinePaths["m"]!!.take(2))
        assertEquals("Pain", browser.children(listOf("category:tablets")).single().label)
    }

    @Test fun copyMoveAndMergePreservePricesIdentityAndSourceUntilRequested() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = PharmacyRepository(context, UUID.randomUUID().toString())
        val other = PharmacyRepository(context, UUID.randomUUID().toString())
        val main = PharmacyRepository(context)
        try {
            val fields = listOf(ImportedField("column-1", "Scientific", "Amoxicillin", ImportField.SCIENTIFIC, true))
            val original = medicine("source", "Imported brand").copy(imported = true, note = "Imported note", importedFields = fields,
                codes = listOf(MedicineCode(CodeKind.BARCODE, "123456789")))
            source.saveMedicine(original)
            source.transferMedication(original.id, other, false)
            assertEquals(1, source.loadSnapshot().items.size)
            assertEquals(fields, other.loadSnapshot().items.single().importedFields)
            val own = medicine("own", "My common name").copy(favorite = true, importedFields = listOf(ImportedField("custom-note", "Shelf", "A4", ImportField.CUSTOM, true)))
            main.saveMedicine(own)
            source.transferMedication(original.id, main, false, mergeTargetId = own.id)
            val merged = main.loadSnapshot().items.single()
            assertEquals(own.name, merged.name); assertEquals(own.official, merged.official); assertEquals(own.discounted, merged.discounted)
            assertEquals(own.category, merged.category); assertEquals(own.createdAt, merged.createdAt); assertTrue(merged.favorite)
            assertEquals("Imported note", merged.note)
            assertEquals("Amoxicillin", merged.importedFields.first { it.field == ImportField.SCIENTIFIC }.value)
            assertEquals("A4", merged.importedFields.first { it.key == "custom-note" }.value)
            assertEquals(1, source.loadSnapshot().items.size)
            // Merging again updates the same record and never duplicates codes or fields.
            source.transferMedication(original.id, main, true, mergeTargetId = own.id)
            assertEquals(1, main.loadSnapshot().items.size)
            assertEquals(1, main.loadSnapshot().items.single().codes.size)
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
