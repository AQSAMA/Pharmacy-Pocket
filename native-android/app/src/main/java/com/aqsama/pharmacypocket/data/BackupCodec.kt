package com.aqsama.pharmacypocket.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.util.Locale
import android.util.Base64
import android.graphics.BitmapFactory
import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader

object BackupCodec {
    const val schema = "pharmacy-pocket-backup"
    const val version = 3
    private const val maxSafeJsInteger = 9_007_199_254_740_991L

    fun encode(
        items: List<Medicine>,
        currency: String,
        categoryDefinitions: List<Category>,
        exportedAt: String = Instant.now().toString(),
        photos: Map<String, ByteArray> = emptyMap(),
        importedList: Boolean = false,
        photosIncluded: Boolean = true,
        categoriesIncluded: Boolean = true,
    ): String {
        val estimatedPhotoBytes = photos.values.sumOf { ((it.size.toLong() + 2L) / 3L) * 4L }
        require(estimatedPhotoBytes < PharmacyDefaults.maxBackupBytes) {
            "Photos exceed the 128 MB single-file backup limit. Remove some photos before exporting."
        }
        val root = JSONObject()
            .put("importedList", importedList)
            .put("photosIncluded", photosIncluded)
            .put("categoriesIncluded", categoriesIncluded)
            .put("schema", schema)
            .put("version", version)
            .put("exportedAt", exportedAt)
            .put("currency", currency.trim().ifEmpty { "IQD" })

        val sections = buildSections(items, categoryDefinitions)
        root.put("sections", JSONArray().apply {
            sections.forEach { section -> put(sectionToJson(section)) }
        })
        root.put("favoriteIds", JSONArray().apply {
            items.filter { it.favorite }.forEach { put(it.id) }
        })
        if (!importedList) root.put("medicines", JSONArray().apply {
            items.forEach { put(medicineToJson(it, photos)) }
        })
        root.put("categories", JSONArray().apply {
            categoryDefinitions.filter { it.id != "all" }.forEach { category ->
                put(categoryToJson(category))
            }
        })
        if (importedList) {
            // Avoid retaining a second complete object graph for large spreadsheet lists.
            val prefix = root.toString().dropLast(1) + ",\"medicines\":["
            var bytes = prefix.toByteArray(Charsets.UTF_8).size.toLong() + 2
            var characters = prefix.length.toLong() + 2
            // Size first so buffer expansion cannot briefly retain two large character arrays.
            items.forEachIndexed { index, item ->
                val encoded = medicineToJson(item, photos).toString()
                val separator = if (index == 0) 0 else 1
                bytes += encoded.toByteArray(Charsets.UTF_8).size + separator
                characters += encoded.length + separator
                require(bytes <= PharmacyDefaults.maxBackupBytes) { "The backup exceeds 128 MB." }
            }
            val output = StringBuilder(characters.toInt()).append(prefix)
            items.forEachIndexed { index, item ->
                if (index > 0) output.append(',')
                output.append(medicineToJson(item, photos).toString())
            }
            return output.append("]}").toString()
        }
        val json = root.toString(2)
        require(json.toByteArray(Charsets.UTF_8).size <= PharmacyDefaults.maxBackupBytes) {
            "The backup exceeds 128 MB. Remove some photos or shorten large notes before exporting."
        }
        return json
    }

    private fun medicineToJson(item: Medicine, photos: Map<String, ByteArray>): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("category", item.category)
        put("subcategory", item.subcategory)
        put("name", item.name)
        put("note", item.note)
        put("description", item.description)
        if (item.imported || item.importedFields.isNotEmpty()) { put("imported", item.imported); put("importedFields", fieldsToJson(item.importedFields)) }
        put("official", item.official)
        put("discounted", item.discounted ?: JSONObject.NULL)
        put("revision", item.revision.coerceAtLeast(0))
        item.createdAt?.let { put("createdAt", it) }
        put("codes", JSONArray().apply {
            item.codes.forEach { code ->
                put(JSONObject().put("kind", code.kind.name).put("value", code.value).put("label", code.label))
            }
        })
        photos[item.id]?.let { jpeg -> put("photoJpeg", Base64.encodeToString(jpeg, Base64.NO_WRAP)) }
    }

    fun parse(raw: String): ParsedBackup {
        try {
            // Our large-list exporter puts this marker first. Read its records incrementally.
            if (Regex("""^\s*\{\s*"importedList"\s*:\s*true\s*[,}]""").containsMatchIn(raw)) return parseImported(raw)
            val root = JSONObject(raw)
            val medicineArray = root.optJSONArray("medicines")
                ?: throw IllegalArgumentException("This file does not contain a valid medicines list.")
            require(medicineArray.length() <= if (root.optBoolean("importedList", false)) SpreadsheetLimits.maxRows else PharmacyDefaults.maxBackupMedicines) {
                "This file contains too many medicines."
            }

            val favoriteIds = mutableSetOf<String>()
            root.optJSONArray("favoriteIds")?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index, "").takeIf { it.isNotEmpty() }?.let(favoriteIds::add)
                }
            }

            val medicines = mutableListOf<Medicine>()
            val photos = mutableMapOf<String, ByteArray>()
            val ids = mutableSetOf<String>()
            for (index in 0 until medicineArray.length()) {
                val obj = medicineArray.optJSONObject(index)
                    ?: throw IllegalArgumentException("One or more medicines in this file are invalid.")
                val item = parseMedicine(obj, favoriteIds)
                require(ids.add(item.id)) { "Duplicate medicine ID: ${item.id}" }
                medicines += item
                decodePhoto(obj)?.let { photos[item.id] = it }
            }

            val sections = if (root.has("sections")) {
                val sectionArray = root.optJSONArray("sections")
                    ?: throw IllegalArgumentException("The sections in this file are invalid.")
                buildList {
                    for (index in 0 until sectionArray.length()) {
                        add(parseSection(sectionArray.optJSONObject(index)
                            ?: throw IllegalArgumentException("The sections in this file are invalid.")))
                    }
                }
            } else {
                buildSections(medicines, PharmacyDefaults.categories)
            }

            val categories = if (root.has("categories")) {
                val categoryArray = root.optJSONArray("categories")
                    ?: throw IllegalArgumentException("The categories in this file are invalid.")
                val parsed = mutableListOf<Category>()
                val categoryIds = mutableSetOf<String>()
                for (index in 0 until categoryArray.length()) {
                    val category = parseCategory(categoryArray.optJSONObject(index)
                        ?: throw IllegalArgumentException("The categories in this file are invalid."))
                    if (category.id == "all") continue
                    require(categoryIds.add(category.id)) { "Duplicate category ID: ${category.id}" }
                    parsed += category
                }
                parsed
            } else {
                categoriesFromSections(sections)
            }

            val sourceVersion = if (root.has("version")) root.optInt("version", 1) else 1
            require(sourceVersion in 1..version) {
                "Backup version $sourceVersion is not supported by this app."
            }
            val hasCurrency = root.has("currency") && !root.isNull("currency")
            val currency = root.optString("currency", "IQD").trim().ifEmpty { "IQD" }
            require(currency.length <= 24) { "The currency name in this file is too long." }

            return ParsedBackup(
                medicines = orderBySections(medicines, sections),
                sections = sections,
                categories = categories,
                currency = currency,
                hasCurrency = hasCurrency,
                sourceVersion = sourceVersion,
                photos = photos,
                importedList = root.optBoolean("importedList", false),
                photosSpecified = root.optBoolean("photosIncluded", true),
                categoriesSpecified = root.optBoolean("categoriesIncluded", true),
            )
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: IllegalStateException) {
            throw IllegalArgumentException("This is not a valid Pharmacy Pocket JSON file.", error)
        } catch (error: java.io.IOException) {
            throw IllegalArgumentException("This is not a valid Pharmacy Pocket JSON file.", error)
        } catch (error: JSONException) {
            throw IllegalArgumentException("This is not a valid Pharmacy Pocket JSON file.", error)
        }
    }

    private fun decodePhoto(obj: JSONObject): ByteArray? {
        if (!obj.has("photoJpeg")) return null
        val encoded = requiredString(obj, "photoJpeg")
        require(encoded.length <= 342_000) { "A photo in this backup is too large." }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        require(bytes.size in 1..256_000 && bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            "A photo in this backup is invalid."
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..2000 && bounds.outHeight in 1..2000) {
            "A photo in this backup has invalid dimensions."
        }
        return bytes
    }

    /** Restores our compact imported-list backups without retaining a full JSON object graph. */
    private fun parseImported(raw: String): ParsedBackup {
        JsonReader(StringReader(raw)).use { reader ->
            val backup = readBackup(reader)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected content after the backup." }
            return backup
        }
    }

    /** Stream a list nested in a library envelope without building a duplicate JSON graph. */
    internal fun readBackup(reader: JsonReader): ParsedBackup {
        val medicines = mutableListOf<Medicine>()
        val photos = mutableMapOf<String, ByteArray>()
        val favorites = mutableSetOf<String>()
        val ids = mutableSetOf<String>()
        var sections: List<BackupSection>? = null
        var categories: List<Category>? = null
        var currency = "IQD"
        var hasCurrency = false
        var sourceVersion = 1
        var photosIncluded = true
        var categoriesIncluded = true
        var hasMedicines = false
        var importedList = false
        run {
            reader.beginObject()
            while (reader.hasNext()) when (reader.nextName()) {
                "importedList" -> importedList = reader.nextBoolean()
                "photosIncluded" -> photosIncluded = reader.nextBoolean()
                "categoriesIncluded" -> categoriesIncluded = reader.nextBoolean()
                "version" -> sourceVersion = reader.nextInt()
                "currency" -> {
                    if (reader.peek() == JsonToken.NULL) reader.nextNull() else {
                        currency = reader.nextString().trim().ifBlank { "IQD" }; hasCurrency = true
                        require(currency.length <= 24) { "The currency name in this file is too long." }
                    }
                }
                "favoriteIds" -> {
                    reader.beginArray()
                    while (reader.hasNext()) { require(favorites.size <= SpreadsheetLimits.maxRows); favorites += reader.nextString() }
                    reader.endArray()
                }
                "medicines" -> {
                    hasMedicines = true
                    reader.beginArray()
                    while (reader.hasNext()) {
                        require(medicines.size < SpreadsheetLimits.maxRows) { "This file contains too many medicines." }
                        val obj = readObject(reader)
                        val item = parseMedicine(obj, emptySet())
                        require(ids.add(item.id)) { "Duplicate medicine ID: ${item.id}" }
                        medicines += item
                        decodePhoto(obj)?.let { photos[item.id] = it }
                    }
                    reader.endArray()
                }
                "sections" -> {
                    val parsed = mutableListOf<BackupSection>()
                    reader.beginArray()
                    while (reader.hasNext()) { require(parsed.size < SpreadsheetLimits.maxRows); parsed += parseSection(readObject(reader)) }
                    reader.endArray(); sections = parsed
                }
                "categories" -> {
                    val parsed = mutableListOf<Category>()
                    val categoryIds = mutableSetOf<String>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        require(parsed.size < SpreadsheetLimits.maxRows)
                        val category = parseCategory(readObject(reader))
                        if (category.id != "all") {
                            require(categoryIds.add(category.id)) { "Duplicate category ID: ${category.id}" }
                            parsed += category
                        }
                    }
                    reader.endArray(); categories = parsed
                }
                else -> reader.skipValue()
            }
            reader.endObject()
        }
        require(medicines.size <= if (importedList) SpreadsheetLimits.maxRows else PharmacyDefaults.maxBackupMedicines) { "This file contains too many medicines." }
        require(hasMedicines) { "This file does not contain a valid medicines list." }
        require(sourceVersion in 1..version) { "Backup version $sourceVersion is not supported by this app." }
        val completeSections = sections ?: buildSections(medicines, PharmacyDefaults.categories)
        val completeCategories = categories ?: categoriesFromSections(completeSections)
        val favorited = medicines.map { if (it.id in favorites) it.copy(favorite = true) else it }
        return ParsedBackup(orderBySections(favorited, completeSections), completeSections, completeCategories,
            currency, hasCurrency, sourceVersion, photos, importedList = importedList, photosSpecified = photosIncluded, categoriesSpecified = categoriesIncluded)
    }

    private fun readObject(reader: JsonReader, depth: Int = 0): JSONObject {
        require(depth < 16) { "The backup contains excessive nesting." }
        val obj = JSONObject()
        reader.beginObject()
        while (reader.hasNext()) {
            require(obj.length() < 256) { "The backup contains too many object fields." }
            val name = reader.nextName()
            val arrayLimit = when (name) { "importedFields" -> SpreadsheetLimits.maxFields; "codes" -> 20; "medicineIds" -> SpreadsheetLimits.maxRows; else -> 128 }
            obj.put(name, readValue(reader, depth + 1, arrayLimit))
        }
        reader.endObject()
        return obj
    }

    private fun readValue(reader: JsonReader, depth: Int, arrayLimit: Int): Any {
        require(depth < 16) { "The backup contains excessive nesting." }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> readObject(reader, depth)
            JsonToken.BEGIN_ARRAY -> JSONArray().apply {
                reader.beginArray()
                while (reader.hasNext()) { require(length() < arrayLimit) { "An array in the backup is too large." }; put(readValue(reader, depth + 1, 128)) }
                reader.endArray()
            }
            JsonToken.STRING -> reader.nextString().also { require(it.length <= 342_000) { "A value in the backup is too large." } }
            JsonToken.NUMBER -> reader.nextString().toBigDecimal()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> { reader.nextNull(); JSONObject.NULL }
            else -> throw IllegalArgumentException("Invalid backup value.")
        }
    }

    fun buildSections(items: List<Medicine>, definitions: List<Category>): List<BackupSection> {
        val groups = linkedMapOf<Pair<String, String>, MutableList<String>>()
        items.forEach { item -> groups.getOrPut(item.category to subcategoryLabel(item.subcategory)) { mutableListOf() }.add(item.id) }
        return groups.map { (key, ids) ->
            val (categoryId, subcategory) = key
            val category = categoryById(categoryId, definitions)
            BackupSection("$categoryId::$subcategory", categoryId, subcategory,
                if (subcategory == PharmacyDefaults.generalSubcategory) category.label else subcategory,
                category.label, category.arabic, category.color, ids)
        }
    }

    private fun parseMedicine(obj: JSONObject, favoriteIds: Set<String>): Medicine {
        val id = requiredString(obj, "id")
        val category = requiredString(obj, "category")
        val subcategory = requiredString(obj, "subcategory", allowBlank = true)
        val name = requiredString(obj, "name")
        val note = requiredString(obj, "note", allowBlank = true)
        val description = if (obj.has("description")) obj.optString("description", "") else ""
        val official = requiredSafeLong(obj, "official")
        val discounted = if (!obj.has("discounted") || obj.isNull("discounted")) null
        else requiredSafeLong(obj, "discounted")
        val revisionLong = requiredSafeLong(obj, "revision")
        require(revisionLong <= Int.MAX_VALUE) { "One or more medicines in this file are invalid." }
        val createdAt = if (!obj.has("createdAt") || obj.isNull("createdAt")) null
        else requiredSafeLong(obj, "createdAt")
        val codes = if (obj.has("codes")) {
            val array = obj.optJSONArray("codes")
                ?: throw IllegalArgumentException("The codes in this file are invalid.")
            require(array.length() <= 20) { "A medicine has too many codes." }
            validateCodes(buildList {
                for (index in 0 until array.length()) {
                    val code = array.optJSONObject(index)
                        ?: throw IllegalArgumentException("The codes in this file are invalid.")
                    val kind = runCatching { CodeKind.valueOf(requiredString(code, "kind")) }
                        .getOrElse { throw IllegalArgumentException("The code type in this file is invalid.") }
                    add(MedicineCode(kind, requiredString(code, "value"), code.optString("label", "")))
                }
            })
        } else emptyList()
        require(official >= 0 && (discounted == null || discounted >= 0) && revisionLong >= 0) {
            "One or more medicines in this file are invalid."
        }
        return Medicine(
            id = id,
            category = category,
            subcategory = subcategory,
            name = name,
            note = note,
            description = description,
            official = official,
            discounted = discounted,
            revision = revisionLong.toInt(),
            favorite = favoriteIds.contains(id) || obj.optBoolean("favorite", false),
            createdAt = createdAt,
            codes = codes,
            codesSpecified = obj.has("codes"),
            imported = obj.optBoolean("imported", false),
            importedFields = fieldsFromJson(obj.optJSONArray("importedFields")),
        )
    }

    private fun parseSection(obj: JSONObject): BackupSection {
        val ids = obj.optJSONArray("medicineIds")
            ?: throw IllegalArgumentException("The sections in this file are invalid.")
        val medicineIds = buildList {
            for (index in 0 until ids.length()) {
                val value = ids.opt(index)
                if (value !is String) throw IllegalArgumentException("The sections in this file are invalid.")
                add(value)
            }
        }
        return BackupSection(
            id = requiredString(obj, "id"),
            category = requiredString(obj, "category"),
            subcategory = requiredString(obj, "subcategory", allowBlank = true),
            title = requiredString(obj, "title", allowBlank = true),
            categoryLabel = requiredString(obj, "categoryLabel", allowBlank = true),
            categoryArabic = requiredString(obj, "categoryArabic", allowBlank = true),
            color = requiredString(obj, "color", allowBlank = true),
            medicineIds = medicineIds,
        )
    }

    private fun parseCategory(obj: JSONObject): Category {
        val category = Category(
            id = requiredString(obj, "id"),
            label = requiredString(obj, "label"),
            arabic = requiredString(obj, "arabic"),
            color = requiredString(obj, "color").lowercase(Locale.ROOT),
        )
        require(isValidCategory(category)) { "The categories in this file are invalid." }
        return category
    }

    private fun requiredString(obj: JSONObject, key: String, allowBlank: Boolean = false): String {
        val value = obj.opt(key)
        if (value !is String || (!allowBlank && value.trim().isEmpty())) {
            throw IllegalArgumentException("One or more medicines in this file are invalid.")
        }
        return value
    }

    private fun requiredSafeLong(obj: JSONObject, key: String): Long {
        val value = obj.opt(key)
        val number = value as? Number
            ?: throw IllegalArgumentException("One or more medicines in this file are invalid.")
        val asDouble = number.toDouble()
        val asLong = number.toLong()
        if (!asDouble.isFinite() || asDouble != asLong.toDouble() || asLong < 0 || asLong > maxSafeJsInteger) {
            throw IllegalArgumentException("One or more medicines in this file are invalid.")
        }
        return asLong
    }

    private fun sectionToJson(section: BackupSection) = JSONObject()
        .put("id", section.id)
        .put("category", section.category)
        .put("subcategory", section.subcategory)
        .put("title", section.title)
        .put("categoryLabel", section.categoryLabel)
        .put("categoryArabic", section.categoryArabic)
        .put("color", section.color)
        .put("medicineIds", JSONArray(section.medicineIds))

    private fun categoryToJson(category: Category) = JSONObject()
        .put("id", category.id)
        .put("label", category.label)
        .put("arabic", category.arabic)
        .put("color", category.color.lowercase(Locale.ROOT))

    private fun categoriesFromSections(sections: List<BackupSection>): List<Category> {
        val builtInIds = PharmacyDefaults.categories.mapTo(mutableSetOf()) { it.id }
        val found = linkedMapOf<String, Category>()
        sections.forEach { section ->
            if (section.category !in builtInIds && section.category != "all" && section.category !in found) {
                val candidate = Category(
                    id = section.category,
                    label = section.categoryLabel.ifBlank { section.category },
                    arabic = section.categoryArabic.ifBlank { section.categoryLabel.ifBlank { section.category } },
                    color = section.color.takeIf { Regex("^#[0-9a-fA-F]{6}$").matches(it) } ?: "#758790",
                )
                if (isValidCategory(candidate)) found[candidate.id] = candidate
            }
        }
        return found.values.toList()
    }

    private fun orderBySections(items: List<Medicine>, sections: List<BackupSection>): List<Medicine> {
        if (sections.isEmpty()) return items
        val byId = items.associateBy { it.id }
        val ordered = mutableListOf<Medicine>()
        val used = mutableSetOf<String>()
        sections.forEach { section ->
            section.medicineIds.forEach { id ->
                val item = byId[id]
                if (item != null && used.add(id)) ordered += item
            }
        }
        items.forEach { if (used.add(it.id)) ordered += it }
        return ordered
    }
}
