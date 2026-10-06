package com.aqsama.pharmacypocket.data

enum class FieldPlacement(val label: String) { TOP("Above name"), BODY("Below price"), FOOTER("Card bottom") }

private val sourceProvenanceKeys = setOf("source-name", "source-currency", "source-price", "source-alternative-price")

/** The pharmacist's identity, placement, and prices survive an enrichment merge. */
fun mergeMedicationDetails(target: Medicine, source: Medicine, sourceCurrency: String): Medicine {
    val sourceFields = sourceDetailFields(source, sourceCurrency)
    val incomingKeys = sourceFields.mapTo(mutableSetOf()) { it.key }
    // Replace provenance as a group: an absent source discount must clear the old one.
    val fields = target.importedFields.filterNot { it.key in sourceProvenanceKeys || it.key in incomingKeys } + sourceFields.map { field ->
        val existing = target.importedFields.firstOrNull { it.key == field.key }
        field.copy(onCard = existing?.onCard ?: field.onCard, color = existing?.color ?: field.color,
            placement = existing?.placement ?: field.placement)
    }
    require(fields.size <= SpreadsheetLimits.maxFields) { "Too many fields after merging." }
    return target.copy(note = source.note, description = source.description, importedFields = fields,
        codes = validateCodes((target.codes + source.codes).distinctBy { it.value }), revision = target.revision + 1)
}

fun sourceDetailFields(item: Medicine, currency: String): List<ImportedField> {
    val provenance = listOf(
        ImportedField("source-name", "Original name", item.name, ImportField.CUSTOM, false),
        ImportedField("source-currency", "Source price currency", currency, ImportField.CUSTOM, false),
        ImportedField("source-price", "Source pharmacy price", item.official.toString(), ImportField.CUSTOM, false),
    ) + if (item.discounted != null) listOf(ImportedField("source-alternative-price", "Source alternative price", item.discounted.toString(), ImportField.CUSTOM, false)) else emptyList()
    return (provenance + item.importedFields.map { it.copy(onCard = false) }).distinctBy { it.key }
}
