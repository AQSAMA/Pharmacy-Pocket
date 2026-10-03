package com.aqsama.pharmacypocket.data

enum class FieldPlacement(val label: String) { TOP("Above name"), BODY("Below price"), FOOTER("Card bottom") }
enum class CategoryView(val label: String, val description: String) {
    BREADCRUMBS("Breadcrumbs", "A compact path with one level at a time"),
    FOLDERS("Folders", "Folder tiles with medicine counts"),
    TREE("Tree", "Expand branches and see their relationships"),
    COLUMNS("Columns", "Browse adjacent levels like a file manager"),
    FLOATING("Floating explorer", "A small path bar with a roomy explorer sheet"),
}

fun categoryAncestors(id: String, categories: List<Category>): List<Category> {
    val byId = categories.associateBy { it.id }
    val seen = mutableSetOf<String>()
    val path = mutableListOf<Category>()
    var next: String? = id
    while (next != null && next != "all" && seen.add(next)) {
        val item = byId[next] ?: break
        path += item
        next = item.parentId
    }
    return path.asReversed()
}

fun validateCategoryTree(categories: List<Category>) {
    val byId = categories.associateBy { it.id }
    categories.filter { it.id != "all" }.forEach { item ->
        val seen = mutableSetOf(item.id)
        var parent = item.parentId
        while (parent != null) {
            require(parent != "all" && seen.add(parent)) { "A category cannot contain itself or one of its parents." }
            val node = byId[parent] ?: error("The parent category is missing.")
            parent = node.parentId
        }
    }
}

data class BrowseFolder(val key: String, val label: String, val path: List<String>, val count: Int, val categoryId: String?)
data class LibraryBrowser(val folders: List<BrowseFolder>, val medicinePaths: Map<String, List<String>>) {
    fun children(path: List<String>) = folders.filter { it.path.size == path.size + 1 && it.path.take(path.size) == path }
    fun label(key: String) = folders.firstOrNull { it.key == key }?.label ?: key
}

/** Stable, ancestry-qualified keys keep identically named subfolders separate. */
fun buildLibraryBrowser(snapshot: AppSnapshot): LibraryBrowser {
    val folders = linkedMapOf<String, BrowseFolder>()
    fun add(path: List<String>, label: String, categoryId: String?) {
        folders.putIfAbsent(path.last(), BrowseFolder(path.last(), label, path, 0, categoryId))
    }
    snapshot.categories.filter { it.id != "all" }.forEach { category ->
        val path = categoryAncestors(category.id, snapshot.categories).map { "category:${it.id}" }
        add(path, category.label, category.id)
    }
    val paths = snapshot.items.associate { item ->
        val path = categoryAncestors(item.category, snapshot.categories).map { "category:${it.id}" }.toMutableList()
        val deeper = if (item.imported) item.importedFields.filter { it.field.categoryLevel > 1 }.sortedBy { it.field.categoryLevel }.map { it.value.trim().ifBlank { "Uncategorized" } }
            else listOf(subcategoryLabel(item.subcategory)).filter { it != PharmacyDefaults.generalSubcategory }
        deeper.forEach { label ->
            val key = path.lastOrNull().orEmpty() + "/" + normalizeSearch(label).let { "${it.length}:$it" }
            path += key
            add(path.toList(), label, null)
        }
        item.id to path.toList()
    }
    val counts = mutableMapOf<String, Int>()
    paths.values.forEach { path -> path.forEach { key -> counts[key] = (counts[key] ?: 0) + 1 } }
    val byParent = folders.values.groupBy { it.path.dropLast(1).lastOrNull() }
    val ordered = mutableListOf<BrowseFolder>()
    fun visit(parent: String?) {
        byParent[parent].orEmpty().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }).forEach { folder ->
            ordered += folder.copy(count = counts[folder.key] ?: 0)
            visit(folder.key)
        }
    }
    visit(null)
    return LibraryBrowser(ordered, paths)
}

/** The pharmacist's identity, placement, and prices survive an enrichment merge. */
fun mergeMedicationDetails(target: Medicine, source: Medicine, sourceCurrency: String): Medicine {
    val sourceFields = sourceDetailFields(source, sourceCurrency)
    val incomingKeys = sourceFields.mapTo(mutableSetOf()) { it.key }
    val fields = target.importedFields.filterNot { it.key in incomingKeys } + sourceFields.map { field ->
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
