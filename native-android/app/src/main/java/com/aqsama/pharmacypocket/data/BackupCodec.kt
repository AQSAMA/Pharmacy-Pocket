package com.aqsama.pharmacypocket.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.util.Locale
import android.util.Base64
import android.graphics.BitmapFactory

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
    ): String {
        val root = JSONObject()
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
        root.put("medicines", JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("category", item.category)
                    put("subcategory", item.subcategory)
                    put("name", item.name)
                    put("note", item.note)
                    put("description", item.description)
                    put("official", item.official)
                    put("discounted", item.discounted ?: JSONObject.NULL)
                    put("revision", item.revision.coerceAtLeast(0))
                    item.createdAt?.let { put("createdAt", it) }
                    put("codes", JSONArray().apply {
                        item.codes.forEach { code ->
                            put(JSONObject().put("kind", code.kind.name).put("value", code.value))
                        }
                    })
                    photos[item.id]?.let { jpeg -> put("photoJpeg", Base64.encodeToString(jpeg, Base64.NO_WRAP)) }
                })
            }
        })
        root.put("categories", JSONArray().apply {
            categoryDefinitions.filter { it.id != "all" }.forEach { category ->
                put(categoryToJson(category))
            }
        })
        return root.toString(2)
    }

    fun parse(raw: String): ParsedBackup {
        try {
            val root = JSONObject(raw)
            val medicineArray = root.optJSONArray("medicines")
                ?: throw IllegalArgumentException("This file does not contain a valid medicines list.")
            require(medicineArray.length() <= PharmacyDefaults.maxBackupMedicines) {
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
                if (obj.has("photoJpeg")) {
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
                    photos[item.id] = bytes
                }
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
            )
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: JSONException) {
            throw IllegalArgumentException("This is not a valid Pharmacy Pocket JSON file.", error)
        }
    }

    fun buildSections(items: List<Medicine>, definitions: List<Category>): List<BackupSection> {
        val sections = linkedMapOf<String, BackupSection>()
        items.forEach { item ->
            val subcategory = subcategoryLabel(item.subcategory)
            val id = "${item.category}::$subcategory"
            val existing = sections[id]
            if (existing != null) {
                sections[id] = existing.copy(medicineIds = existing.medicineIds + item.id)
            } else {
                val category = categoryById(item.category, definitions)
                sections[id] = BackupSection(
                    id = id,
                    category = item.category,
                    subcategory = subcategory,
                    title = if (subcategory == PharmacyDefaults.generalSubcategory) category.label else subcategory,
                    categoryLabel = category.label,
                    categoryArabic = category.arabic,
                    color = category.color,
                    medicineIds = listOf(item.id),
                )
            }
        }
        return sections.values.toList()
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
                    add(MedicineCode(kind, requiredString(code, "value")))
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
