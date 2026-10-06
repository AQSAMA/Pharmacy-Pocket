package com.aqsama.pharmacypocket.data

import org.json.JSONObject

const val MAIN_LIST_KEY = "main"
const val ALL_MANUAL_LISTS = "all-manual"

data class MedicationList(val key: String, val name: String, val imported: Boolean) {
    val id: String? get() = key.takeUnless { it == MAIN_LIST_KEY }
}

fun medicationLists(lists: List<ImportedList>, mainName: String = "My medications"): List<MedicationList> =
    listOf(MedicationList(MAIN_LIST_KEY, mainName, false)) + lists.map { MedicationList(it.id, it.name, it.imported) }

data class ListMedicineRef(val listKey: String, val medicineId: String) {
    fun encode(): String = "$listKey::$medicineId"
    companion object {
        fun decode(value: String): ListMedicineRef {
            val split = value.indexOf("::")
            require(split > 0) { "Invalid medication reference." }
            return ListMedicineRef(value.substring(0, split), value.substring(split + 2))
        }
    }
}

/** IDs are scoped to their database, including backups containing identical IDs. */
fun aggregateManualSnapshots(lists: List<Pair<MedicationList, AppSnapshot>>, appearance: AppSnapshot): AppSnapshot {
    require(lists.none { it.first.imported })
    return appearance.copy(
        items = lists.flatMap { (list, snapshot) -> snapshot.items.map { item ->
            item.copy(id = ListMedicineRef(list.key, item.id).encode(), category = "${list.key}::${item.category}",
                listLabel = list.name, displayCurrency = snapshot.currency)
        } },
        categories = listOf(PharmacyDefaults.categories.first()) + lists.flatMap { (list, snapshot) ->
            snapshot.categories.filter { it.id != "all" }.map { it.copy(id = "${list.key}::${it.id}", label = "${it.label} · ${list.name}") }
        },
        trashCount = lists.sumOf { it.second.trashCount },
    )
}

data class BackupSelection(val medicineIds: Set<String>? = null, val photos: Boolean = true, val categories: Boolean = true) {
    fun apply(items: List<Medicine>): List<Medicine> = if (medicineIds == null) items else items.filter { it.id in medicineIds }
    fun apply(backup: ParsedBackup): ParsedBackup {
        val items = apply(backup.medicines)
        val ids = items.mapTo(hashSetOf()) { it.id }
        return backup.copy(medicines = items, sections = BackupCodec.buildSections(items, backup.categories),
            photos = if (photos) backup.photos.filterKeys { it in ids } else emptyMap(),
            categories = if (categories) backup.categories else emptyList(),
            photosSpecified = photos && backup.photosSpecified, categoriesSpecified = categories && backup.categoriesSpecified)
    }
}

data class ListBackup(val list: MedicationList, val backup: ParsedBackup)

/** A library envelope nests unchanged v3 list backups; legacy single-list files still work. */
object LibraryBackupCodec {
    const val schema = "pharmacy-pocket-library"
    fun encode(entries: List<Pair<MedicationList, String>>): String {
        require(entries.isNotEmpty() && entries.size <= 256) { "Select between 1 and 256 lists." }
        require(entries.map { it.first.key }.distinct().size == entries.size) { "Duplicate list ID." }
        val prefix = JSONObject().put("schema", schema).put("version", 1).toString().dropLast(1) + ",\"lists\":["
        val encoded = entries.map { (list, json) ->
            JSONObject().put("id", list.key).put("name", list.name).put("imported", list.imported).toString().dropLast(1) + ",\"backup\":" + json + "}"
        }
        val bytes = prefix.toByteArray(Charsets.UTF_8).size.toLong() + encoded.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() } + entries.size + 2
        require(bytes <= PharmacyDefaults.maxBackupBytes) { "The backup exceeds 128 MB. Export fewer lists or omit photos." }
        return buildString(prefix.length + encoded.sumOf { it.length } + entries.size + 2) {
            append(prefix); encoded.forEachIndexed { index, json -> if (index > 0) append(','); append(json) }; append("]}")
        }
    }

    fun parse(raw: String): List<ListBackup> {
        require(raw.toByteArray(Charsets.UTF_8).size <= PharmacyDefaults.maxBackupBytes) { "The backup exceeds 128 MB." }
        // Imported-list JSON uses BackupCodec's streaming path; do not build an extra graph.
        if (!Regex("\"schema\"\\s*:\\s*\"$schema\"").containsMatchIn(raw.take(256))) {
            val backup = BackupCodec.parse(raw)
            return listOf(ListBackup(MedicationList(MAIN_LIST_KEY, if (backup.importedList) "Imported medications" else "My medications", backup.importedList), backup))
        }
        val entries = mutableListOf<ListBackup>()
        var foundSchema = ""
        var version = 0
        val keys = hashSetOf<String>()
        android.util.JsonReader(java.io.StringReader(raw)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) when (reader.nextName()) {
                "schema" -> foundSchema = reader.nextString()
                "version" -> version = reader.nextInt()
                "lists" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        require(entries.size < 256) { "Too many lists." }
                        var key = ""; var name = ""; var imported: Boolean? = null; var backup: ParsedBackup? = null
                        reader.beginObject()
                        while (reader.hasNext()) when (reader.nextName()) {
                            "id" -> key = reader.nextString()
                            "name" -> name = reader.nextString().trim()
                            "imported" -> imported = reader.nextBoolean()
                            "backup" -> backup = BackupCodec.readBackup(reader)
                            else -> reader.skipValue()
                        }
                        reader.endObject()
                        require(key == MAIN_LIST_KEY || Regex("[a-f0-9-]{36}").matches(key)) { "Invalid list ID." }
                        require(keys.add(key)) { "Duplicate list ID." }
                        require(name.length in 1..100) { "Invalid list name." }
                        val data = requireNotNull(backup) { "Missing list backup." }
                        require(imported == data.importedList) { "List type does not match its backup." }
                        entries += ListBackup(MedicationList(key, name, data.importedList), data)
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
            reader.endObject()
            require(reader.peek() == android.util.JsonToken.END_DOCUMENT) { "Unexpected content after the backup." }
        }
        require(foundSchema == schema && version == 1) { "Unsupported library backup." }
        require(entries.isNotEmpty()) { "The backup has no lists." }
        return entries
    }
}
