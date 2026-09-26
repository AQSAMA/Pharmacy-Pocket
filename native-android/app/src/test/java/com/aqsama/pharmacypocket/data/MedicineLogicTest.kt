package com.aqsama.pharmacypocket.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class MedicineLogicTest {
    private fun medicine(
        id: String,
        category: String,
        name: String = id,
        subcategory: String = "General",
        price: Long = 1_000,
        createdAt: Long = 0,
        favorite: Boolean = false,
    ) = Medicine(
        id = id,
        category = category,
        subcategory = subcategory,
        name = name,
        note = "",
        official = price,
        discounted = null,
        revision = 0,
        favorite = favorite,
        createdAt = createdAt,
    )

    @Test
    fun arabicSearchNormalizesAlefAndDiacritics() {
        assertEquals(normalizeSearch("أَ"), normalizeSearch("ا"))
        assertEquals(normalizeSearch("إبر"), normalizeSearch("ابر"))
    }

    @Test
    fun tagsAndChecklistAreSearchableAndTagsDeduplicate() {
        assertEquals(listOf("إبر", "Stock"), normalizeTags(" إبر, ابر, Stock, stock "))
        val item = medicine("m", "tablets").copy(
            tags = listOf("إبر"), checklist = listOf(ChecklistItem("Check shelf")),
        )
        val index = buildSearchIndex(listOf(item))
        assertEquals(listOf(item), filterSortedMedicines(index, MedicineFilters(), "ابر"))
        assertEquals(listOf(item), filterSortedMedicines(index, MedicineFilters(), "shelf"))
    }

    @Test
    fun repeatingReminderAdvancesPastCurrentTime() {
        val day = 86_400_000L
        assertEquals(3 * day, MedicineReminders.nextDue(day, ReminderRepeat.DAILY, 2 * day))
        assertEquals(null, MedicineReminders.nextDue(day, ReminderRepeat.NONE, 2 * day))
    }

    @Test
    fun legacyMergeKeepsLocalRichFieldsWhileExplicitEmptyBackupClearsThem() {
        val local = medicine("m", "tablets").copy(
            tags = listOf("Stock"), checklist = listOf(ChecklistItem("Call supplier", true)),
            reminderAt = 1_900_000_000_000L, reminderRepeat = ReminderRepeat.WEEKLY,
        )
        val encoded = BackupCodec.encode(listOf(local), "IQD", PharmacyDefaults.categories)
        val old = JSONObject(encoded).apply {
            getJSONArray("medicines").getJSONObject(0).apply {
                remove("tags"); remove("checklist"); remove("reminderAt"); remove("reminderRepeat")
            }
        }
        val parsed = BackupCodec.parse(old.toString())
        val merged = mergeImportedRichFields(parsed.medicines.single(), local, parsed.richFields[local.id])
        assertEquals(local.tags, merged.tags)
        assertEquals(local.checklist, merged.checklist)
        assertEquals(local.reminderAt, merged.reminderAt)

        val cleared = BackupCodec.parse(BackupCodec.encode(listOf(local.copy(
            tags = emptyList(), checklist = emptyList(), reminderAt = null, reminderRepeat = ReminderRepeat.NONE,
        )), "IQD", PharmacyDefaults.categories))
        val result = mergeImportedRichFields(cleared.medicines.single(), local, cleared.richFields[local.id])
        assertEquals(emptyList<String>(), result.tags)
        assertEquals(emptyList<ChecklistItem>(), result.checklist)
        assertEquals(null, result.reminderAt)
    }

    @Test
    fun blankAndEquivalentSubcategoriesShareAKey() {
        assertEquals(subcategoryKey("   "), subcategoryKey("General"))
        assertEquals(subcategoryKey(" أَقْرَاص "), subcategoryKey("اقراص"))
        assertTrue(subcategoryKey("all") != subcategoryKey(null))
    }

    @Test
    fun trashSearchUsesArabicNormalizationAcrossNameAndSubcategory() {
        val item = medicine(
            id = "arabic",
            category = "tablets",
            name = "أَقْرَاص",
            subcategory = "مُسَكِّنات",
        )
        val trash = listOf(TrashedMedicine(item, deletedAt = 1L))

        assertEquals(listOf("arabic"), filterTrash(trash, "اقراص").map { it.medicine.id })
        assertEquals(listOf("arabic"), filterTrash(trash, "مسكنات").map { it.medicine.id })
    }

    @Test
    fun defaultSortUsesFullCategoryCountsThenNewestWithinCategory() {
        val items = listOf(
            medicine("a-old", "a", createdAt = 10, favorite = true),
            medicine("b-new", "b", createdAt = 50, favorite = true),
            medicine("a-new", "a", createdAt = 30),
            medicine("a-mid", "a", createdAt = 20),
            medicine("b-old", "b", createdAt = 40, favorite = true),
            medicine("c-only", "c", createdAt = 60, favorite = true),
        )
        val sorted = sortSearchIndex(buildSearchIndex(items), MedicineSort.DEFAULT).map { it.item.id }
        assertEquals(listOf("a-new", "a-mid", "a-old", "b-new", "b-old", "c-only"), sorted)
    }

    @Test
    fun globalSortModesDoNotGetOverriddenBySubcategories() {
        val items = listOf(
            medicine("c", "syrups", "Charlie", "Third", 2_000, 20),
            medicine("a", "syrups", "Alpha", "First", 3_000, 10),
            medicine("b", "syrups", "Beta", "Second", 1_000, 30),
        )
        val index = buildSearchIndex(items)
        val filters = MedicineFilters()

        fun ids(sort: MedicineSort) =
            filterSortedMedicines(sortSearchIndex(index, sort), filters, "").map { it.id }

        assertEquals(listOf("a", "b", "c"), ids(MedicineSort.NAME_ASC))
        assertEquals(listOf("c", "b", "a"), ids(MedicineSort.NAME_DESC))
        assertEquals(listOf("a", "c", "b"), ids(MedicineSort.DATE_ASC))
        assertEquals(listOf("b", "c", "a"), ids(MedicineSort.DATE_DESC))
        assertEquals(listOf("b", "c", "a"), ids(MedicineSort.PRICE_ASC))
        assertEquals(listOf("a", "c", "b"), ids(MedicineSort.PRICE_DESC))
    }

    @Test
    fun mergeOnlyAppliesCurrencyWhenBackupExplicitlyContainsIt() {
        assertTrue(shouldApplyImportedCurrency(ImportMode.MERGE, true))
        assertTrue(!shouldApplyImportedCurrency(ImportMode.MERGE, false))
        assertTrue(shouldApplyImportedCurrency(ImportMode.REPLACE, false))
    }

    @Test
    fun categoryMergePreservesLocalStyleWhileReplaceAcceptsIncomingStyle() {
        val local = mergeCategoryDefinitions(
            PharmacyDefaults.categories,
            listOf(Category("syrups", "Local liquids", "محلي", "#123456")),
        )
        val incoming = listOf(
            Category("syrups", "Imported liquids", "مستورد", "#654321"),
            Category("custom-imported", "Imported custom", "خاص", "#3f7fb5"),
        )

        val merged = resolveCategoryImport(local, incoming, ImportMode.MERGE, listOf("custom-imported"))
        assertEquals("Local liquids", merged.first { it.id == "syrups" }.label)
        assertEquals("#123456", merged.first { it.id == "syrups" }.color)
        assertTrue(merged.any { it.id == "custom-imported" })

        val replaced = resolveCategoryImport(local, incoming, ImportMode.REPLACE, listOf("custom-imported"))
        assertEquals("Imported liquids", replaced.first { it.id == "syrups" }.label)
        assertEquals("#654321", replaced.first { it.id == "syrups" }.color)
    }
}
