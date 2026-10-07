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
        put("official", item.official ?: JSONObject.NULL)
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

    fun parse(raw: String, listName: String = "My medications"): ParsedBackup = importList(listName) {
        try {
            // Our large-list exporter puts this marker first. Read its records incrementally.
            if (Regex("""^\s*\{\s*"importedList"\s*:\s*true\s*[,}]""").containsMatchIn(raw)) return@importList parseImported(raw)
            val root = JSONObject(raw)
            for (field in listOf("importedList", "photosIncluded", "categoriesIncluded")) if (root.has(field) && root.opt(field) !is Boolean) rejectImport(root, field, "a JSON boolean")
            val medicineArray = root.optJSONArray("medicines")
                ?: rejectImport(root, "medicines", "an array of medicine objects")
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
                    ?: throw ImportFieldError("medicines[${index}]", medicineArray.opt(index), "a medicine object")
                val item = importRecord("Medicine", index, obj) {
                    val parsed = parseMedicine(obj, favoriteIds)
                    if (!ids.add(parsed.id)) rejectImport(obj, "id", "a unique medicine ID within this list")
                    decodePhoto(obj)?.let { photos[parsed.id] = it }
                    parsed
                }
                medicines += item
            }

            val sections = if (root.has("sections")) {
                val sectionArray = root.optJSONArray("sections")
                    ?: rejectImport(root, "sections", "an array of section objects")
                buildList {
                    for (index in 0 until sectionArray.length()) {
                        val obj = sectionArray.optJSONObject(index)
                            ?: throw ImportFieldError("sections[$index]", sectionArray.opt(index), "a section object")
                        add(importRecord("Section", index, obj) { parseSection(obj) })
                    }
                }
            } else {
                buildSections(medicines, PharmacyDefaults.categories)
            }

            val categories = if (root.has("categories")) {
                val categoryArray = root.optJSONArray("categories")
                    ?: rejectImport(root, "categories", "an array of category objects")
                val parsed = mutableListOf<Category>()
                val categoryIds = mutableSetOf<String>()
                for (index in 0 until categoryArray.length()) {
                    val obj = categoryArray.optJSONObject(index)
                        ?: throw ImportFieldError("categories[$index]", categoryArray.opt(index), "a category object")
                    val category = importRecord("Category", index, obj) {
                        val category = parseCategory(obj)
                        if (category.id != "all" && !categoryIds.add(category.id)) rejectImport(obj, "id", "a unique category ID")
                        category
                    }
                    if (category.id == "all") continue
                    parsed += category
                }
                parsed
            } else {
                categoriesFromSections(sections)
            }

            val versionValue = if (root.has("version")) requiredSafeLong(root, "version") else 1L
            if (versionValue !in 1L..version.toLong()) rejectImport(root, "version", "a supported backup version from 1 to $version")
            val sourceVersion = versionValue.toInt()
            val hasCurrency = root.has("currency") && !root.isNull("currency")
            val currency = if (hasCurrency) requiredString(root, "currency", allowBlank = true).trim().ifEmpty { "IQD" } else "IQD"
            if (currency.length > 24) rejectImport(root, "currency", "a string of up to 24 characters")

            return@importList ParsedBackup(
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
            throw IllegalArgumentException("Field 'JSON': value ${error.message}; expected a well-formed Pharmacy Pocket JSON object.", error)
        } catch (error: java.io.IOException) {
            throw IllegalArgumentException("Field 'JSON': value ${error.message}; expected a well-formed Pharmacy Pocket JSON object.", error)
        } catch (error: JSONException) {
            throw IllegalArgumentException("Field 'JSON': value ${error.message}; expected a well-formed Pharmacy Pocket JSON object.", error)
        }
    }

    private fun decodePhoto(obj: JSONObject): ByteArray? {
        if (!obj.has("photoJpeg")) return null
        val encoded = requiredString(obj, "photoJpeg")
        if (encoded.length > 342_000) rejectImport(obj, "photoJpeg", "a Base64 JPEG of up to 256000 bytes")
        val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
            ?: rejectImport(obj, "photoJpeg", "a Base64 JPEG")
        if (bytes.size !in 3..256_000 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte() || bytes[2] != 0xFF.toByte()) {
            rejectImport(obj, "photoJpeg", "a Base64 JPEG of up to 256000 bytes")
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..2000 || bounds.outHeight !in 1..2000) rejectImport(obj, "photoJpeg", "a JPEG with width and height from 1 to 2000 pixels")
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
                        if (reader.peek() != JsonToken.BEGIN_OBJECT) throw ImportFieldError("medicines[${medicines.size}]", readValue(reader, 0, 128), "a medicine object")
                        val obj = readObject(reader)
                        val item = importRecord("Medicine", medicines.size, obj) {
                            val parsed = parseMedicine(obj, emptySet())
                            if (!ids.add(parsed.id)) rejectImport(obj, "id", "a unique medicine ID within this list")
                            decodePhoto(obj)?.let { photos[parsed.id] = it }
                            parsed
                        }
                        medicines += item
                    }
                    reader.endArray()
                }
                "sections" -> {
                    val parsed = mutableListOf<BackupSection>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        require(parsed.size < SpreadsheetLimits.maxRows)
                        val obj = readObject(reader)
                        parsed += importRecord("Section", parsed.size, obj) { parseSection(obj) }
                    }
                    reader.endArray(); sections = parsed
                }
                "categories" -> {
                    val parsed = mutableListOf<Category>()
                    val categoryIds = mutableSetOf<String>()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        require(parsed.size < SpreadsheetLimits.maxRows)
                        val obj = readObject(reader)
                        val category = importRecord("Category", parsed.size, obj) {
                            val category = parseCategory(obj)
                            if (category.id != "all" && !categoryIds.add(category.id)) rejectImport(obj, "id", "a unique category ID")
                            category
                        }
                        if (category.id != "all") parsed += category
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
        for (field in listOf("favorite", "imported")) if (obj.has(field) && obj.opt(field) !is Boolean) rejectImport(obj, field, "a JSON boolean")
        val id = requiredString(obj, "id")
        val category = requiredString(obj, "category")
        val subcategory = requiredString(obj, "subcategory", allowBlank = true)
        val name = requiredString(obj, "name")
        val note = requiredString(obj, "note", allowBlank = true)
        val description = if (!obj.has("description") || obj.isNull("description")) "" else requiredString(obj, "description", allowBlank = true)
        val official = if (!obj.has("official") || obj.isNull("official")) null else requiredSafeLong(obj, "official")
        val discounted = if (!obj.has("discounted") || obj.isNull("discounted")) null
        else requiredSafeLong(obj, "discounted")
        val revisionLong = requiredSafeLong(obj, "revision")
        if (revisionLong > Int.MAX_VALUE) rejectImport(obj, "revision", "an integer from 0 to ${Int.MAX_VALUE}")
        val createdAt = if (!obj.has("createdAt") || obj.isNull("createdAt")) null
        else requiredSafeLong(obj, "createdAt")
        val codes = if (obj.has("codes")) {
            val array = obj.optJSONArray("codes")
                ?: rejectImport(obj, "codes", "an array of code objects")
            if (array.length() > 20) rejectImport(obj, "codes", "at most 20 code objects")
            validateCodes(buildList {
                for (index in 0 until array.length()) {
                    val code = array.optJSONObject(index)
                        ?: throw ImportFieldError("codes[$index]", array.opt(index), "a code object")
                    fun invalid(field: String, expected: String): Nothing = throw ImportFieldError("codes[$index].$field", code.opt(field), expected)
                    val kind = (code.opt("kind") as? String)?.let { runCatching { CodeKind.valueOf(it) }.getOrNull() }
                        ?: invalid("kind", CodeKind.entries.joinToString { it.name })
                    val value = (code.opt("value") as? String)?.trim()
                        ?: invalid("value", "a nonempty string of up to 2048 characters without control characters")
                    if (value.isEmpty() || value.length > 2048 || value.any { Character.isISOControl(it) }) invalid("value", "a nonempty string of up to 2048 characters without control characters")
                    val label = if (!code.has("label")) "" else (code.opt("label") as? String)?.trim()
                        ?: invalid("label", "a string of up to 80 characters without control characters")
                    if (label.length > 80 || label.any { Character.isISOControl(it) }) invalid("label", "a string of up to 80 characters without control characters")
                    if (any { it.value == value }) invalid("value", "a code unique within this medicine")
                    add(MedicineCode(kind, value, label))
                }
            })
        } else emptyList()
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
            importedFields = parseImportedFields(obj),
        )
    }

    private fun parseSection(obj: JSONObject): BackupSection {
        val ids = obj.optJSONArray("medicineIds")
            ?: rejectImport(obj, "medicineIds", "an array of medicine ID strings")
        val medicineIds = buildList {
            for (index in 0 until ids.length()) {
                val value = ids.opt(index)
                if (value !is String) throw ImportFieldError("medicineIds[$index]", value, "a medicine ID string")
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
        if (!Regex("^#[0-9a-fA-F]{6}$").matches(category.color)) rejectImport(obj, "color", "a color in #RRGGBB format")
        return category
    }

    private fun requiredString(obj: JSONObject, key: String, allowBlank: Boolean = false): String {
        val value = obj.opt(key)
        if (value !is String || (!allowBlank && value.trim().isEmpty())) {
            rejectImport(obj, key, if (allowBlank) "a string (empty allowed)" else "a nonempty string")
        }
        return value
    }

    private fun requiredSafeLong(obj: JSONObject, key: String): Long {
        val value = obj.opt(key)
        val parsed = if (value is Number) runCatching { java.math.BigDecimal(value.toString()).longValueExact() }.getOrNull() else null
        if (parsed == null || parsed !in 0..maxSafeJsInteger) {
            rejectImport(obj, key, "a JSON integer from 0 to $maxSafeJsInteger" + if (key == "official" || key == "discounted") "; null or omission for an empty price" else "")
        }
        return parsed
    }

    private fun parseImportedFields(obj: JSONObject): List<ImportedField> {
        if (!obj.has("importedFields")) return emptyList()
        val array = obj.optJSONArray("importedFields") ?: rejectImport(obj, "importedFields", "an array of custom field objects")
        if (array.length() > SpreadsheetLimits.maxFields) rejectImport(obj, "importedFields", "at most ${SpreadsheetLimits.maxFields} custom fields")
        val keys = hashSetOf<String>()
        return List(array.length()) { index ->
            val field = array.optJSONObject(index) ?: throw ImportFieldError("importedFields[$index]", array.opt(index), "a custom field object")
            fun invalid(key: String, expected: String): Nothing = throw ImportFieldError("importedFields[$index].$key", field.opt(key), expected)
            fun string(key: String): String = field.opt(key) as? String ?: invalid(key, "a string")
            val key = string("key")
            if (!keys.add(key)) invalid("key", "a unique custom field key")
            val value = string("value")
            val label = string("label")
            if (value.length > SpreadsheetLimits.maxCellLength) invalid("value", "a string of up to ${SpreadsheetLimits.maxCellLength} characters")
            if (label.length !in 1..200) invalid("label", "a string of 1 to 200 characters")
            val type = runCatching { ImportField.valueOf(string("field")) }.getOrNull() ?: invalid("field", ImportField.entries.joinToString { it.name })
            val format = if (!field.has("priceFormat")) PriceFormat.DOT_DECIMAL else runCatching { PriceFormat.valueOf(string("priceFormat")) }.getOrNull() ?: invalid("priceFormat", PriceFormat.entries.joinToString { it.name })
            val placement = if (!field.has("placement")) FieldPlacement.BODY else runCatching { FieldPlacement.valueOf(string("placement")) }.getOrNull() ?: invalid("placement", FieldPlacement.entries.joinToString { it.name })
            val color = if (!field.has("color") || field.isNull("color")) null else string("color").takeIf { it.isNotEmpty() && it != "null" }
            if (color != null && !Regex("^#[0-9a-fA-F]{6}$").matches(color)) invalid("color", "#RRGGBB or null")
            if (field.has("onCard") && field.opt("onCard") !is Boolean) invalid("onCard", "a JSON boolean")
            if (type.isPrice && value.isNotBlank()) try { spreadsheetPrice(value, format) } catch (error: IllegalArgumentException) { invalid("value", "a price in ${format.name} format: ${error.message}") }
            ImportedField(key, label, value, type, field.optBoolean("onCard"), format, color, placement)
        }
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
