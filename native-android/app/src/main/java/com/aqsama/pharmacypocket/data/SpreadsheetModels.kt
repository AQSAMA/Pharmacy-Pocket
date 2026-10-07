package com.aqsama.pharmacypocket.data

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

enum class ImportField(val label: String, val categoryLevel: Int = 0) {
    NAME("Name"), SCIENTIFIC("Scientific name"), PHARMACY_PRICE("Pharmacy price"),
    WHOLESALE_PRICE("Wholesale price"), NOTE("Notes"), DESCRIPTION("Description"),
    CATEGORY_1("Category · level 1", 1), CATEGORY_2("Category · level 2", 2),
    CATEGORY_3("Category · level 3", 3), CATEGORY_4("Category · level 4", 4),
    CUSTOM("Custom field"), IGNORE("Skip column");
    val isPrice get() = this == PHARMACY_PRICE || this == WHOLESALE_PRICE
}

enum class PriceFormat(val label: String) { DOT_DECIMAL("1,234.56"), COMMA_DECIMAL("1.234,56") }

data class ColumnMapping(val column: Int, val label: String, val field: ImportField, val onCard: Boolean = false, val priceFormat: PriceFormat = PriceFormat.DOT_DECIMAL)
data class ImportedField(val key: String, val label: String, val value: String, val field: ImportField, val onCard: Boolean, val priceFormat: PriceFormat = PriceFormat.DOT_DECIMAL, val color: String? = null, val placement: FieldPlacement = FieldPlacement.BODY)
data class SpreadsheetRow(val number: Int, val cells: List<String>)
data class SpreadsheetSheet(val name: String, val rows: List<SpreadsheetRow>)
data class SpreadsheetWorkbook(val sheets: List<SpreadsheetSheet>)
data class ImportSelection(val sheetIndex: Int = 0, val headerRow: Int = 0, val firstRow: Int = 1, val lastRow: Int = Int.MAX_VALUE)
data class ImportSource(val workbook: SpreadsheetWorkbook, val selection: ImportSelection, val idPrefix: String, val originalAvailable: Boolean = true)
data class ImportedList(val id: String, val name: String, val source: String, val mappings: List<ColumnMapping>, val imported: Boolean = true)
data class PreparedImport(val medicines: List<Medicine>, val skippedRows: Int, val errors: List<String>, val errorCount: Int)

object SpreadsheetLimits {
    const val maxBytes = 32 * 1024 * 1024
    const val maxExpandedBytes = 128 * 1024 * 1024
    const val maxRows = 100_000
    const val maxColumns = 128
    const val maxFields = maxColumns + 4
    const val maxCells = 2_000_000
    const val maxCellLength = 4096
}

fun fieldsToJson(fields: List<ImportedField>): JSONArray = JSONArray().apply {
    fields.forEach { put(JSONObject().put("key", it.key).put("label", it.label).put("value", it.value)
        .put("field", it.field.name).put("onCard", it.onCard).put("priceFormat", it.priceFormat.name).put("color", it.color).put("placement", it.placement.name)) }
}

fun fieldsFromJson(array: JSONArray?): List<ImportedField> {
    if (array == null) return emptyList()
    require(array.length() <= SpreadsheetLimits.maxFields) { "Too many custom fields." }
    return List(array.length()) { index ->
        val obj = array.getJSONObject(index)
        val value = obj.getString("value")
        val label = obj.getString("label")
        require(value.length <= SpreadsheetLimits.maxCellLength && label.length in 1..200) { "A custom field is too long." }
        ImportedField(obj.getString("key"), label, value, ImportField.valueOf(obj.getString("field")), obj.optBoolean("onCard"), PriceFormat.valueOf(obj.optString("priceFormat", PriceFormat.DOT_DECIMAL.name)),
            obj.optString("color").takeIf { it.isNotEmpty() && it != "null" }?.also { require(Regex("^#[0-9a-fA-F]{6}$").matches(it)) { "Invalid field color." } },
            FieldPlacement.valueOf(obj.optString("placement", FieldPlacement.BODY.name)))
    }.also { require(it.map { field -> field.key }.distinct().size == it.size) { "Duplicate custom field keys." } }
}

fun mappingsToJson(mappings: List<ColumnMapping>): JSONArray = JSONArray().apply {
    mappings.forEach { put(JSONObject().put("column", it.column).put("label", it.label)
        .put("field", it.field.name).put("onCard", it.onCard).put("priceFormat", it.priceFormat.name)) }
}
fun mappingsFromJson(array: JSONArray): List<ColumnMapping> = List(array.length()) { index ->
    val obj = array.getJSONObject(index)
    ColumnMapping(obj.getInt("column"), obj.getString("label"), ImportField.valueOf(obj.getString("field")), obj.optBoolean("onCard"), PriceFormat.valueOf(obj.optString("priceFormat", PriceFormat.DOT_DECIMAL.name)))
}

fun suggestMappings(headers: List<String>): List<ColumnMapping> {
    val used = mutableSetOf<ImportField>()
    return headers.mapIndexed { index, header ->
        val key = normalizeSearch(header).trim().replace(Regex("[_-]+"), " ")
        val suggested = when {
            key in listOf("name", "trading name", "trade name", "brand", "brand name", "medicine", "اسم", "الاسم التجاري", "اسم الدواء") -> ImportField.NAME
            key.contains("scientific") || key.contains("generic") || key.contains("العلمي") -> ImportField.SCIENTIFIC
            key in listOf("pharmacy price", "store price", "retail price", "official", "سعر الصيدلية", "سعر البيع") -> ImportField.PHARMACY_PRICE
            key in listOf("wholesale price", "distributor price", "cost price", "سعر الجملة", "سعر الشراء") -> ImportField.WHOLESALE_PRICE
            key in listOf("category", "التصنيف", "الفئة") -> ImportField.CATEGORY_1
            key in listOf("subcategory", "sub category", "التصنيف الفرعي") -> ImportField.CATEGORY_2
            key in listOf("note", "notes", "ملاحظات") -> ImportField.NOTE
            key in listOf("description", "الوصف") -> ImportField.DESCRIPTION
            else -> ImportField.CUSTOM
        }
        val field = if (suggested == ImportField.CUSTOM || used.add(suggested)) suggested else ImportField.CUSTOM
        ColumnMapping(index, header.ifBlank { "Column ${index + 1}" }.take(200), field,
            field in listOf(ImportField.SCIENTIFIC, ImportField.PHARMACY_PRICE, ImportField.WHOLESALE_PRICE))
    }
}

fun validateMappings(mappings: List<ColumnMapping>) {
    require(mappings.isNotEmpty() && mappings.size <= SpreadsheetLimits.maxColumns)
    require(mappings.map { it.column }.distinct().size == mappings.size && mappings.all { it.column in 0 until SpreadsheetLimits.maxColumns })
    require(mappings.count { it.field == ImportField.NAME } == 1) { "Choose exactly one Name column." }
    val assigned = mappings.filter { it.field !in listOf(ImportField.CUSTOM, ImportField.IGNORE) }
    require(assigned.map { it.field }.distinct().size == assigned.size) { "Each specific field can be assigned only once." }
    require(mappings.filter { it.field != ImportField.IGNORE }.all { it.label.trim().length in 1..200 }) { "Give each included column a label." }
    val levels = assigned.map { it.field.categoryLevel }.filter { it > 0 }.sorted()
    require(levels == (1..levels.size).toList()) { "Assign category levels in order, starting at level 1." }
    require(mappings.count { it.onCard && it.field !in listOf(ImportField.NAME, ImportField.IGNORE, ImportField.NOTE, ImportField.DESCRIPTION) } <= 6) {
        "Show up to six extra fields on cards; all included fields remain in details."
    }
}

fun spreadsheetPrice(value: String, format: PriceFormat = PriceFormat.DOT_DECIMAL): BigDecimal? {
    if (value.isBlank()) return null
    val translated = buildString {
        value.trim().forEach { char -> append(when (char) {
            in '٠'..'٩' -> '0' + (char - '٠')
            in '۰'..'۹' -> '0' + (char - '۰')
            '٫' -> '.'
            else -> char
        }) }
    }.replace("٬", "").replace(" ", "").replace("\u00a0", "")
    val normalized = if (format == PriceFormat.DOT_DECIMAL) {
        if (translated.contains(',')) require(Regex("""^[+]?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?(?:[eE][+-]?\d+)?$""").matches(translated)) { "Use the selected price format: 1,234.56" }
        translated.replace(",", "")
    } else {
        if (translated.contains('.')) require(Regex("""^[+]?(?:\d{1,3}(?:\.\d{3})+|\d+)(?:,\d+)?(?:[eE][+-]?\d+)?$""").matches(translated)) { "Use the selected price format: 1.234,56" }
        translated.replace(".", "").replace(',', '.')
    }
    val price = normalized.toBigDecimalOrNull() ?: throw IllegalArgumentException("Price must be a number")
    require(price >= BigDecimal.ZERO && price <= BigDecimal("9007199254740991")) { "Price is outside the supported range" }
    return price
}

fun syncImportedFields(item: Medicine): Medicine {
    if (!item.imported) return item
    fun price(field: ImportField): Long? = item.importedFields.firstOrNull { it.field == field }
        ?.let { spreadsheetPrice(it.value, it.priceFormat) }?.setScale(0, RoundingMode.HALF_UP)?.longValueExact()
    val categories = item.importedFields.filter { it.field.categoryLevel > 0 }.sortedBy { it.field.categoryLevel }
    return item.copy(
        category = categories.firstOrNull()?.value?.trim()?.ifBlank { "Uncategorized" } ?: "Uncategorized",
        subcategory = categories.drop(1).map { it.value.trim().ifBlank { "Uncategorized" } }.joinToString(" › ").ifBlank { "General" },
        official = price(ImportField.PHARMACY_PRICE),
        discounted = price(ImportField.WHOLESALE_PRICE),
    )
}

fun prepareSpreadsheet(sheet: SpreadsheetSheet, headerRow: Int, mappings: List<ColumnMapping>, checkCancelled: () -> Unit = {}, firstRow: Int = 1, lastRow: Int = Int.MAX_VALUE, idPrefix: String? = null): PreparedImport {
    validateMappings(mappings)
    require(headerRow in 0 until sheet.rows.size) { "Select a valid header row." }
    val medicines = mutableListOf<Medicine>()
    val errors = mutableListOf<String>()
    var errorCount = 0
    var skipped = 0
    val now = System.currentTimeMillis()
    require(firstRow <= lastRow && firstRow >= 1) { "Choose a valid data-row range." }
    val prefix = idPrefix ?: "import-${UUID.randomUUID()}"
    for (row in sheet.rows.drop(headerRow + 1)) {
        if (row.number !in firstRow..lastRow) continue
        checkCancelled()
        fun value(field: ImportField) = mappings.firstOrNull { it.field == field }?.let { row.cells.getOrElse(it.column) { "" }.trim() } ?: ""
        if (value(ImportField.NAME).isBlank()) { skipped++; continue }
        val fields = mappings.filter { it.field !in listOf(ImportField.NAME, ImportField.NOTE, ImportField.DESCRIPTION, ImportField.IGNORE) }.map {
            ImportedField("column-${it.column}", it.label.trim(), row.cells.getOrElse(it.column) { "" }.trim(), it.field, it.onCard, it.priceFormat)
        }
        try {
            medicines += syncImportedFields(Medicine(
                id = "$prefix-${row.number}", category = "Uncategorized", subcategory = "General",
                name = value(ImportField.NAME), note = value(ImportField.NOTE), description = value(ImportField.DESCRIPTION),
                official = 0, discounted = null, createdAt = now, imported = true, importedFields = fields,
            ))
        } catch (error: IllegalArgumentException) {
            errorCount++
            if (errors.size < 5) errors += "Row ${row.number}: ${error.message}. Check the price mapping or use Custom field."
        }
    }
    return PreparedImport(medicines, skipped, errors, errorCount)
}
