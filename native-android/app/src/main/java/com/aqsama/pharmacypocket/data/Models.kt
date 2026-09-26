package com.aqsama.pharmacypocket.data

import java.text.DateFormat
import java.text.Normalizer
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

data class Medicine(
    val id: String,
    val category: String,
    val subcategory: String,
    val name: String,
    val note: String,
    val description: String = "",
    val official: Long,
    val discounted: Long?,
    val revision: Int = 0,
    val favorite: Boolean = false,
    val createdAt: Long? = null,
    val codes: List<MedicineCode> = emptyList(),
    val hasPhoto: Boolean = false,
    val codesSpecified: Boolean = true,
)

enum class CodeKind { BARCODE, QR, PRICE_STICKER_QR }

data class MedicineCode(val kind: CodeKind, val value: String, val label: String = "")

fun validateCodes(codes: List<MedicineCode>): List<MedicineCode> {
    require(codes.size <= 20) { "A medicine can have up to 20 codes." }
    val cleaned = codes.map { code ->
        val value = code.value.trim()
        require(value.isNotEmpty() && value.length <= 2048 && value.none { Character.isISOControl(it) }) {
            "A scanned code is empty, too long, or contains control characters."
        }
        val label = code.label.trim()
        require(label.length <= 80 && label.none { Character.isISOControl(it) }) {
            "A company or variant label is too long or contains control characters."
        }
        MedicineCode(code.kind, value, label)
    }
    require(cleaned.map { it.value }.distinct().size == cleaned.size) { "Duplicate codes are not allowed." }
    return cleaned
}

data class Category(
    val id: String,
    val label: String,
    val arabic: String,
    val color: String,
)

enum class ThemePreference(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

data class AppSnapshot(
    val items: List<Medicine>,
    val categories: List<Category>,
    val largeText: Boolean,
    val currency: String,
    val themePreference: ThemePreference,
    val trashCount: Int = 0,
)

data class TrashedMedicine(
    val medicine: Medicine,
    val deletedAt: Long,
)

enum class MedicineSort(val label: String) {
    DEFAULT("Default"),
    NAME_ASC("A–Z"),
    NAME_DESC("Z–A"),
    DATE_DESC("Newest"),
    DATE_ASC("Oldest"),
    PRICE_ASC("Price ↑"),
    PRICE_DESC("Price ↓"),
}

enum class ImportMode { MERGE, REPLACE }

fun shouldApplyImportedCurrency(mode: ImportMode, hasCurrency: Boolean): Boolean =
    mode == ImportMode.REPLACE || hasCurrency

data class BackupSection(
    val id: String,
    val category: String,
    val subcategory: String,
    val title: String,
    val categoryLabel: String,
    val categoryArabic: String,
    val color: String,
    val medicineIds: List<String>,
)

data class ParsedBackup(
    val medicines: List<Medicine>,
    val sections: List<BackupSection>,
    val categories: List<Category>,
    val currency: String,
    val hasCurrency: Boolean,
    val sourceVersion: Int,
    val photos: Map<String, ByteArray> = emptyMap(),
)

object PharmacyDefaults {
    const val maxCategories = 256
    const val maxBackupMedicines = 5_000
    const val maxBackupBytes = 128 * 1024 * 1024
    const val generalSubcategory = "General"
    val categoryColors = listOf(
        "#2f856d", "#596aab", "#9672ab", "#bc798b", "#c79749", "#4e9cab",
        "#72a03b", "#bc8959", "#758790", "#b85d5d", "#3f7fb5", "#8a6d3b",
    )
    val categories = listOf(
        Category("all", "All", "الكل", "#126052"),
        Category("syrups", "Syrups & sachets", "شراب", "#2f856d"),
        Category("tablets", "Tablets & strips", "حبوب", "#596aab"),
        Category("boxes", "Boxes", "علب", "#9672ab"),
        Category("ampoules", "Ampoules", "أمبولات", "#bc798b"),
        Category("vials", "Vials", "فيالات", "#c79749"),
        Category("drops", "Drops", "قطرات", "#4e9cab"),
        Category("effervescent", "Effervescent", "فوار", "#72a03b"),
        Category("topicals", "Creams & oils", "موضعي", "#bc8959"),
        Category("supplies", "Supplies", "مستلزمات", "#758790"),
    )
}

private val arabicMarks = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
private val alefVariants = Regex("[أإآ]")
private val hexColor = Regex("^#[0-9a-fA-F]{6}$")

fun normalizeSearch(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(arabicMarks, "")
        .replace(alefVariants, "ا")
        .replace('ى', 'ي')
        .lowercase(Locale.ROOT)

fun subcategoryLabel(value: String?): String = value?.trim().takeUnless { it.isNullOrBlank() }
    ?: PharmacyDefaults.generalSubcategory

fun subcategoryKey(value: String?): String = normalizeSearch(subcategoryLabel(value))

fun isValidCategory(category: Category): Boolean =
    category.id.trim().isNotEmpty() &&
        category.label.trim().isNotEmpty() &&
        category.arabic.trim().isNotEmpty() &&
        hexColor.matches(category.color)

fun categoryById(id: String, categories: List<Category>): Category =
    categories.firstOrNull { it.id == id }
        ?: PharmacyDefaults.categories.firstOrNull { it.id == id }
        ?: PharmacyDefaults.categories.first()

fun mergeCategoryDefinitions(base: List<Category>, incoming: List<Category>): List<Category> {
    val byId = linkedMapOf<String, Category>()
    base.forEach { byId[it.id] = it }
    incoming.filter(::isValidCategory).filter { it.id != "all" }.forEach {
        byId[it.id] = it.copy(
            label = it.label.trim(),
            arabic = it.arabic.trim(),
            color = it.color.lowercase(Locale.ROOT),
        )
    }

    val all = base.firstOrNull { it.id == "all" } ?: PharmacyDefaults.categories.first()
    val ordered = mutableListOf(all)
    val seen = mutableSetOf("all")
    base.forEach { item ->
        if (item.id != "all" && seen.add(item.id)) ordered += byId[item.id] ?: item
    }
    incoming.forEach { item ->
        if (item.id != "all" && isValidCategory(item) && seen.add(item.id)) {
            ordered += byId[item.id] ?: item
        }
    }
    return ordered
}

fun ensureCategoriesForMedicines(source: List<Category>, categoryIds: Collection<String>): List<Category> {
    val next = source.toMutableList()
    val ids = next.mapTo(mutableSetOf()) { it.id }
    categoryIds.forEach { rawId ->
        val label = rawId.trim()
        if (label.isNotEmpty() && rawId != "all" && ids.add(rawId)) {
            next += Category(rawId, label, label, "#758790")
        }
    }
    return next
}

fun resolveCategoryImport(
    current: List<Category>,
    incoming: List<Category>,
    mode: ImportMode,
    medicineCategoryIds: Collection<String>,
): List<Category> {
    val valid = incoming.filter(::isValidCategory).filter { it.id != "all" }
    val definitions = when (mode) {
        ImportMode.REPLACE -> mergeCategoryDefinitions(PharmacyDefaults.categories, valid)
        ImportMode.MERGE -> mergeCategoryDefinitions(
            current,
            valid.filter { candidate -> current.none { it.id == candidate.id } },
        )
    }
    val complete = ensureCategoriesForMedicines(definitions, medicineCategoryIds)
    val count = complete.count { it.id != "all" }
    require(count <= PharmacyDefaults.maxCategories) {
        "Pharmacy Pocket supports up to ${PharmacyDefaults.maxCategories} categories."
    }
    return complete
}

data class MedicineSearchEntry(
    val item: Medicine,
    val searchText: String,
    val subcategoryKey: String,
)

data class MedicineFilters(
    val category: String = "all",
    val subcategoryKey: String? = null,
    val favoritesOnly: Boolean = false,
)

data class SubcategoryOption(val key: String, val label: String)

fun buildSearchIndex(items: List<Medicine>): List<MedicineSearchEntry> = items.map { item ->
    MedicineSearchEntry(
        item,
        normalizeSearch("${item.name} ${item.note} ${item.description} ${subcategoryLabel(item.subcategory)} ${item.codes.joinToString(" ") { it.value }}"),
        subcategoryKey(item.subcategory),
    )
}

fun listSubcategories(index: List<MedicineSearchEntry>, category: String): List<SubcategoryOption> {
    val options = linkedMapOf<String, String>()
    index.forEach { entry ->
        if (category == "all" || entry.item.category == category) {
            options.putIfAbsent(entry.subcategoryKey, subcategoryLabel(entry.item.subcategory))
        }
    }
    return options.map { SubcategoryOption(it.key, it.value) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}

private fun compareNames(left: String, right: String): Int =
    normalizeSearch(left).compareTo(normalizeSearch(right))

fun compareMedicines(left: Medicine, right: Medicine, sort: MedicineSort): Int = when (sort) {
    MedicineSort.NAME_ASC -> compareNames(left.name, right.name)
    MedicineSort.NAME_DESC -> -compareNames(left.name, right.name)
    MedicineSort.PRICE_ASC -> {
        val price = left.official.compareTo(right.official)
        if (price != 0) price else compareNames(left.name, right.name)
    }
    MedicineSort.PRICE_DESC -> {
        val price = right.official.compareTo(left.official)
        if (price != 0) price else -compareNames(left.name, right.name)
    }
    MedicineSort.DATE_ASC -> {
        val date = (left.createdAt ?: 0L).compareTo(right.createdAt ?: 0L)
        if (date != 0) date else compareNames(left.name, right.name)
    }
    MedicineSort.DATE_DESC, MedicineSort.DEFAULT -> {
        val date = (right.createdAt ?: 0L).compareTo(left.createdAt ?: 0L)
        if (date != 0) date else compareNames(left.name, right.name)
    }
}

fun sortSearchIndex(index: List<MedicineSearchEntry>, sort: MedicineSort): List<MedicineSearchEntry> {
    if (sort != MedicineSort.DEFAULT) {
        return index.sortedWith { left, right -> compareMedicines(left.item, right.item, sort) }
    }
    val counts = mutableMapOf<String, Int>()
    val order = mutableMapOf<String, Int>()
    index.forEach { entry ->
        counts[entry.item.category] = (counts[entry.item.category] ?: 0) + 1
        order.putIfAbsent(entry.item.category, order.size)
    }
    return index.sortedWith { left, right ->
        if (left.item.category != right.item.category) {
            val countDiff = (counts[right.item.category] ?: 0) - (counts[left.item.category] ?: 0)
            if (countDiff != 0) countDiff
            else (order[left.item.category] ?: 0) - (order[right.item.category] ?: 0)
        } else {
            compareMedicines(left.item, right.item, MedicineSort.DATE_DESC)
        }
    }
}

fun filterSortedMedicines(
    sortedIndex: List<MedicineSearchEntry>,
    filters: MedicineFilters,
    query: String,
): List<Medicine> {
    val needle = normalizeSearch(query.trim())
    return sortedIndex.asSequence()
        .filter { entry ->
            (filters.category == "all" || entry.item.category == filters.category) &&
                (filters.subcategoryKey == null || entry.subcategoryKey == filters.subcategoryKey) &&
                (!filters.favoritesOnly || entry.item.favorite) &&
                (needle.isEmpty() || entry.searchText.contains(needle))
        }
        .map { it.item }
        .toList()
}

fun formatPrice(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

fun formatAddedDate(value: Long?): String {
    if (value == null || value < 0 || value > 8_640_000_000_000_000L) return "Unknown"
    return runCatching { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(value)) }.getOrDefault("Unknown")
}

fun formatDeletedDate(value: Long): String {
    if (value < 0 || value > 8_640_000_000_000_000L) return "Unknown"
    return runCatching {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(value))
    }.getOrDefault("Unknown")
}

fun filterTrash(items: List<TrashedMedicine>, query: String): List<TrashedMedicine> {
    val needle = normalizeSearch(query.trim())
    if (needle.isEmpty()) return items
    return items.filter { trashed ->
        val item = trashed.medicine
        normalizeSearch(
            "${item.name} ${item.note} ${item.description} ${subcategoryLabel(item.subcategory)}",
        ).contains(needle)
    }
}

fun hasArabic(value: String): Boolean = value.any { it.code in 0x0600..0x06FF }
