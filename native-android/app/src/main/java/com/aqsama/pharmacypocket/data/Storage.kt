package com.aqsama.pharmacypocket.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

private const val DATABASE_VERSION = 3

private data class ExistingRow(
    val sortOrder: Long,
    val favorite: Boolean,
    val createdAt: Long?,
    val codes: List<MedicineCode>,
)

internal class MedicineDatabase(context: Context) {
    private val dbFile = File(context.filesDir, "SQLite/pharmacy-pocket.db")
    private val lock = Any()
    @Volatile private var database: SQLiteDatabase? = null

    private fun open(): SQLiteDatabase {
        database?.takeIf { it.isOpen }?.let { return it }
        synchronized(lock) {
            database?.takeIf { it.isOpen }?.let { return it }
            dbFile.parentFile?.mkdirs()
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
            )
            runCatching { db.enableWriteAheadLogging() }
            try {
                ensureSchema(db)
            } catch (error: Throwable) {
                db.close()
                throw error
            }
            database = db
            return db
        }
    }

    private fun userVersion(db: SQLiteDatabase): Int =
        db.rawQuery("PRAGMA user_version", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun tableColumns(db: SQLiteDatabase): Set<String> = buildSet {
        db.rawQuery("PRAGMA table_info(medicines)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) add(cursor.getString(nameIndex))
        }
    }

    private fun ensureSchema(db: SQLiteDatabase) {
        val currentVersion = userVersion(db)
        require(currentVersion <= DATABASE_VERSION) {
            "This database was created by a newer Pharmacy Pocket version."
        }

        db.beginTransaction()
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS medicines (
                  id TEXT PRIMARY KEY NOT NULL,
                  category TEXT NOT NULL,
                  subcategory TEXT NOT NULL,
                  name TEXT NOT NULL,
                  note TEXT NOT NULL,
                  description TEXT NOT NULL DEFAULT '',
                  official INTEGER NOT NULL,
                  discounted INTEGER,
                  revision INTEGER NOT NULL DEFAULT 0,
                  favorite INTEGER NOT NULL DEFAULT 0,
                  sort_order INTEGER NOT NULL,
                  created_at INTEGER NOT NULL DEFAULT 0,
                  deleted_at INTEGER,
                  codes TEXT NOT NULL DEFAULT '[]'
                )
                """.trimIndent(),
            )

            var columns = tableColumns(db)
            if ("description" !in columns) {
                db.execSQL("ALTER TABLE medicines ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            }
            if ("created_at" !in columns) {
                db.execSQL("ALTER TABLE medicines ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE medicines SET created_at = (strftime('%s','now') * 1000) + sort_order WHERE created_at = 0",
                )
            }

            columns = tableColumns(db)
            if ("deleted_at" !in columns) {
                // A nullable column deliberately leaves every existing medicine active.
                db.execSQL("ALTER TABLE medicines ADD COLUMN deleted_at INTEGER")
            }
            if ("codes" !in columns) {
                db.execSQL("ALTER TABLE medicines ADD COLUMN codes TEXT NOT NULL DEFAULT '[]'")
            }
            db.execSQL("CREATE TABLE IF NOT EXISTS medicine_photos (medicine_id TEXT PRIMARY KEY NOT NULL, jpeg BLOB NOT NULL)")

            db.execSQL("PRAGMA user_version = $DATABASE_VERSION")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun medicineFromCursor(cursor: Cursor): Medicine {
        val discounted = cursor.getColumnIndexOrThrow("discounted")
        val createdAt = cursor.getColumnIndexOrThrow("created_at")
        return Medicine(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
            subcategory = cursor.getString(cursor.getColumnIndexOrThrow("subcategory")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
            description = cursor.getString(cursor.getColumnIndexOrThrow("description")) ?: "",
            official = cursor.getLong(cursor.getColumnIndexOrThrow("official")),
            discounted = if (cursor.isNull(discounted)) null else cursor.getLong(discounted),
            revision = cursor.getInt(cursor.getColumnIndexOrThrow("revision")),
            favorite = cursor.getInt(cursor.getColumnIndexOrThrow("favorite")) != 0,
            createdAt = if (cursor.isNull(createdAt)) null else cursor.getLong(createdAt),
            codes = codesFromJson(cursor.getString(cursor.getColumnIndexOrThrow("codes"))),
            hasPhoto = cursor.getInt(cursor.getColumnIndexOrThrow("has_photo")) != 0,
        )
    }

    fun loadMedicines(): List<Medicine> {
        val result = mutableListOf<Medicine>()
        open().rawQuery(
            "SELECT m.*, EXISTS(SELECT 1 FROM medicine_photos p WHERE p.medicine_id = m.id) AS has_photo FROM medicines m WHERE deleted_at IS NULL ORDER BY sort_order",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) result += medicineFromCursor(cursor)
        }
        return result
    }

    fun loadTrash(): List<TrashedMedicine> {
        val result = mutableListOf<TrashedMedicine>()
        open().rawQuery(
            "SELECT m.*, EXISTS(SELECT 1 FROM medicine_photos p WHERE p.medicine_id = m.id) AS has_photo FROM medicines m WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC, sort_order ASC",
            null,
        ).use { cursor ->
            val deletedAt = cursor.getColumnIndexOrThrow("deleted_at")
            while (cursor.moveToNext()) {
                result += TrashedMedicine(
                    medicine = medicineFromCursor(cursor),
                    deletedAt = cursor.getLong(deletedAt),
                )
            }
        }
        return result
    }

    fun trashCount(): Int =
        open().rawQuery(
            "SELECT COUNT(*) FROM medicines WHERE deleted_at IS NOT NULL",
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    internal fun close() {
        synchronized(lock) {
            database?.close()
            database = null
        }
    }

    private fun existing(db: SQLiteDatabase, id: String): ExistingRow? =
        db.rawQuery(
            "SELECT sort_order, favorite, created_at, codes FROM medicines WHERE id = ?",
            arrayOf(id),
        ).use { cursor ->
            if (!cursor.moveToFirst()) null
            else ExistingRow(
                sortOrder = cursor.getLong(0),
                favorite = cursor.getInt(1) != 0,
                createdAt = if (cursor.isNull(2)) null else cursor.getLong(2),
                codes = codesFromJson(cursor.getString(3)),
            )
        }

    private fun maxSortOrder(db: SQLiteDatabase): Long =
        db.rawQuery("SELECT COALESCE(MAX(sort_order), -1) FROM medicines", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else -1L
        }

    private fun writeMedicine(db: SQLiteDatabase, item: Medicine, newSortOrder: Long? = null): Boolean {
        val current = existing(db, item.id)
        val isNew = current == null
        val createdAt = item.createdAt?.takeIf { it >= 0 }
            ?: current?.createdAt?.takeIf { it >= 0 }
            ?: System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", item.id)
            put("category", item.category)
            put("subcategory", subcategoryLabel(item.subcategory))
            put("name", item.name)
            put("note", item.note)
            put("description", item.description)
            put("official", item.official)
            if (item.discounted == null) putNull("discounted") else put("discounted", item.discounted)
            put("revision", item.revision)
            put("favorite", if (current?.favorite ?: item.favorite) 1 else 0)
            put("sort_order", current?.sortOrder ?: newSortOrder ?: maxSortOrder(db) + 1)
            put("created_at", createdAt)
            put("codes", codesToJson(if (item.codesSpecified) item.codes else current?.codes ?: item.codes))
            putNull("deleted_at")
        }
        db.insertWithOnConflict("medicines", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return isNew
    }

    private fun writeReplacementMedicine(
        db: SQLiteDatabase,
        item: Medicine,
        sortOrder: Long,
        createdAtFallback: Long,
    ) {
        val values = ContentValues().apply {
            put("id", item.id)
            put("category", item.category)
            put("subcategory", subcategoryLabel(item.subcategory))
            put("name", item.name)
            put("note", item.note)
            put("description", item.description)
            put("official", item.official)
            if (item.discounted == null) putNull("discounted") else put("discounted", item.discounted)
            put("revision", item.revision)
            put("favorite", if (item.favorite) 1 else 0)
            put("sort_order", sortOrder)
            put("created_at", item.createdAt?.takeIf { it >= 0 } ?: createdAtFallback)
            put("codes", codesToJson(if (item.codesSpecified) item.codes else existing(db, item.id)?.codes ?: item.codes))
            putNull("deleted_at")
        }
        db.insertWithOnConflict("medicines", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun saveMedicine(item: Medicine, photo: ByteArray? = null, removePhoto: Boolean = false) {
        val db = open()
        db.beginTransaction()
        try {
            writeMedicine(db, item)
            if (removePhoto) db.delete("medicine_photos", "medicine_id = ?", arrayOf(item.id))
            if (photo != null) writePhoto(db, item.id, photo)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun toggleFavorite(id: String): Boolean? {
        val db = open()
        val current = db.rawQuery(
            "SELECT favorite FROM medicines WHERE id = ? AND deleted_at IS NULL",
            arrayOf(id),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) != 0 else return null
        }
        val next = !current
        db.execSQL(
            "UPDATE medicines SET favorite = ? WHERE id = ? AND deleted_at IS NULL",
            arrayOf<Any>(if (next) 1 else 0, id),
        )
        return next
    }

    fun moveMedicineToTrash(id: String, deletedAt: Long = System.currentTimeMillis()): Boolean {
        val db = open()
        db.beginTransaction()
        return try {
            val values = ContentValues().apply { put("deleted_at", deletedAt) }
            val changed = db.update(
                "medicines",
                values,
                "id = ? AND deleted_at IS NULL",
                arrayOf(id),
            ) == 1
            db.setTransactionSuccessful()
            changed
        } finally {
            db.endTransaction()
        }
    }

    fun restoreMedicine(id: String): Boolean {
        val db = open()
        db.beginTransaction()
        return try {
            val values = ContentValues().apply { putNull("deleted_at") }
            val changed = db.update(
                "medicines",
                values,
                "id = ? AND deleted_at IS NOT NULL",
                arrayOf(id),
            ) == 1
            db.setTransactionSuccessful()
            changed
        } finally {
            db.endTransaction()
        }
    }

    fun permanentlyDeleteMedicine(id: String): Boolean {
        val db = open()
        db.beginTransaction()
        return try {
            val changed = db.delete(
                "medicines",
                "id = ? AND deleted_at IS NOT NULL",
                arrayOf(id),
            ) == 1
            if (changed) db.delete("medicine_photos", "medicine_id = ?", arrayOf(id))
            db.setTransactionSuccessful()
            changed
        } finally {
            db.endTransaction()
        }
    }

    fun emptyTrash(): Int {
        val db = open()
        db.beginTransaction()
        return try {
            val deleted = db.delete("medicines", "deleted_at IS NOT NULL", null)
            db.execSQL("DELETE FROM medicine_photos WHERE medicine_id NOT IN (SELECT id FROM medicines)")
            db.setTransactionSuccessful()
            deleted
        } finally {
            db.endTransaction()
        }
    }

    fun restoreAll(): Int {
        val db = open()
        db.beginTransaction()
        return try {
            val values = ContentValues().apply { putNull("deleted_at") }
            val restored = db.update("medicines", values, "deleted_at IS NOT NULL", null)
            db.setTransactionSuccessful()
            restored
        } finally {
            db.endTransaction()
        }
    }

    fun mergeMedicines(items: List<Medicine>, photos: Map<String, ByteArray> = emptyMap()) {
        val db = open()
        db.beginTransaction()
        try {
            var nextSortOrder = maxSortOrder(db) + 1
            items.forEach { item ->
                if (writeMedicine(db, item, nextSortOrder)) nextSortOrder += 1
            }
            photos.forEach { (id, jpeg) -> writePhoto(db, id, jpeg) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun replaceMedicines(items: List<Medicine>, photos: Map<String, ByteArray> = emptyMap(), replacePhotos: Boolean = false) {
        val db = open()
        db.beginTransaction()
        try {
            val incomingIds = items.mapTo(HashSet<String>(items.size)) { it.id }
            val now = System.currentTimeMillis()
            val omittedActiveIds = mutableListOf<String>()
            db.rawQuery(
                "SELECT id FROM medicines WHERE deleted_at IS NULL",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    if (id !in incomingIds) omittedActiveIds += id
                }
            }

            if (omittedActiveIds.isNotEmpty()) {
                val values = ContentValues().apply { put("deleted_at", now) }
                omittedActiveIds.forEach { id ->
                    db.update(
                        "medicines",
                        values,
                        "id = ? AND deleted_at IS NULL",
                        arrayOf(id),
                    )
                }
            }

            // Keep sort_order unique across active and trashed rows. Trash retains its
            // original order so a later restore cannot tie an imported active row.
            val baseSortOrder = maxSortOrder(db) + 1
            items.forEachIndexed { index, item ->
                writeReplacementMedicine(
                    db = db,
                    item = item,
                    sortOrder = baseSortOrder + index,
                    createdAtFallback = now + index,
                )
            }
            if (replacePhotos) items.forEach { item ->
                if (item.id !in photos) db.delete("medicine_photos", "medicine_id = ?", arrayOf(item.id))
            }
            photos.forEach { (id, jpeg) -> writePhoto(db, id, jpeg) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun writePhoto(db: SQLiteDatabase, id: String, jpeg: ByteArray) {
        require(jpeg.size in 1..256_000) { "The photo must be 256 KB or less." }
        db.insertWithOnConflict("medicine_photos", null, ContentValues().apply {
            put("medicine_id", id)
            put("jpeg", jpeg)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadPhoto(id: String): ByteArray? = open().rawQuery(
        "SELECT jpeg FROM medicine_photos WHERE medicine_id = ?", arrayOf(id),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getBlob(0) else null }

    fun exportPhotos(ids: List<String>): Map<String, ByteArray> = buildMap {
        ids.forEach { id -> loadPhoto(id)?.let { put(id, it) } }
    }
}

private fun codesToJson(codes: List<MedicineCode>): String = JSONArray().apply {
    validateCodes(codes).forEach { put(JSONObject().put("kind", it.kind.name).put("value", it.value).put("label", it.label)) }
}.toString()

private fun codesFromJson(raw: String): List<MedicineCode> = buildList {
    val array = JSONArray(raw)
    for (index in 0 until array.length()) {
        val obj = array.getJSONObject(index)
        add(MedicineCode(CodeKind.valueOf(obj.getString("kind")), obj.getString("value"), obj.optString("label", "")))
    }
}

private class PreferenceStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("pharmacy-pocket-native", Context.MODE_PRIVATE)

    init {
        migrateExpoPreferences()
    }

    private fun migrateExpoPreferences() {
        if (prefs.getBoolean("expo-preferences-migrated", false)) return
        val values = mutableMapOf<String, String>()
        val legacyFile = File(context.filesDir, "SQLite/ExpoSQLiteStorage")
        if (legacyFile.exists()) {
            runCatching {
                SQLiteDatabase.openDatabase(
                    legacyFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                ).use { db ->
                    db.rawQuery(
                        "SELECT key, value FROM storage WHERE key IN (?, ?, ?)",
                        arrayOf("large-text", "currency-name", "category-definitions-v1"),
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            if (!cursor.isNull(1)) values[cursor.getString(0)] = cursor.getString(1)
                        }
                    }
                }
            }
        }
        val editor = prefs.edit()
        values["large-text"]?.let { editor.putBoolean("large-text", it == "true") }
        values["currency-name"]?.trim()?.takeIf { it.isNotEmpty() }?.let { editor.putString("currency-name", it) }
        values["category-definitions-v1"]?.let { raw ->
            runCatching { parseCategories(raw) }.getOrNull()?.let { categories ->
                editor.putString("category-definitions-v1", categoriesToJson(categories))
            }
        }
        editor.putBoolean("expo-preferences-migrated", true).apply()
    }

    fun largeText(): Boolean = prefs.getBoolean("large-text", false)
    fun setLargeText(value: Boolean) = prefs.edit().putBoolean("large-text", value).apply()

    fun themePreference(): ThemePreference = runCatching {
        ThemePreference.valueOf(prefs.getString("theme-preference", ThemePreference.SYSTEM.name) ?: ThemePreference.SYSTEM.name)
    }.getOrDefault(ThemePreference.SYSTEM)

    fun setThemePreference(value: ThemePreference) {
        prefs.edit().putString("theme-preference", value.name).apply()
    }

    fun currency(): String = prefs.getString("currency-name", null)?.trim().takeUnless { it.isNullOrEmpty() } ?: "IQD"
    fun setCurrency(value: String) {
        prefs.edit().putString("currency-name", value.trim().ifEmpty { "IQD" }.take(24)).apply()
    }

    fun categories(): List<Category> {
        val raw = prefs.getString("category-definitions-v1", null) ?: return PharmacyDefaults.categories
        return runCatching { mergeCategoryDefinitions(PharmacyDefaults.categories, parseCategories(raw)) }
            .getOrDefault(PharmacyDefaults.categories)
    }

    fun setCategories(categories: List<Category>) {
        prefs.edit().putString("category-definitions-v1", categoriesToJson(categories)).apply()
    }

    private fun parseCategories(raw: String): List<Category> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                val category = Category(
                    obj.getString("id"),
                    obj.getString("label"),
                    obj.getString("arabic"),
                    obj.getString("color"),
                )
                if (isValidCategory(category)) add(category)
            }
        }
    }

    private fun categoriesToJson(categories: List<Category>): String {
        val array = JSONArray()
        categories.forEach { category ->
            array.put(
                JSONObject()
                    .put("id", category.id)
                    .put("label", category.label)
                    .put("arabic", category.arabic)
                    .put("color", category.color.lowercase(Locale.ROOT)),
            )
        }
        return array.toString()
    }
}

class PharmacyRepository(context: Context) {
    private val database = MedicineDatabase(context.applicationContext)
    private val preferences = PreferenceStore(context.applicationContext)
    private val mutex = Mutex()

    private fun snapshotUnsafe(): AppSnapshot {
        val items = database.loadMedicines()
        val stored = preferences.categories()
        val complete = ensureCategoriesForMedicines(stored, items.map { it.category })
        if (complete != stored) preferences.setCategories(complete)
        return AppSnapshot(
            items = items,
            categories = complete,
            largeText = preferences.largeText(),
            currency = preferences.currency(),
            themePreference = preferences.themePreference(),
            trashCount = database.trashCount(),
        )
    }

    suspend fun loadSnapshot(): AppSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock { snapshotUnsafe() }
    }

    suspend fun loadTrash(): List<TrashedMedicine> = withContext(Dispatchers.IO) {
        mutex.withLock { database.loadTrash() }
    }

    suspend fun moveMedicineToTrash(id: String): AppSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(database.moveMedicineToTrash(id)) { "Medicine is no longer available to move to Trash." }
            snapshotUnsafe()
        }
    }

    suspend fun restoreMedicine(id: String): AppSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val trashed = database.loadTrash().firstOrNull { it.medicine.id == id }
                ?: throw IllegalStateException("Medicine is no longer in Trash.")
            val activeCodes = database.loadMedicines().flatMap { item -> item.codes.map { it.value } }.toSet()
            require(trashed.medicine.codes.none { it.value in activeCodes }) {
                "A code now belongs to another active medicine. Remove it from that medicine before restoring."
            }
            check(database.restoreMedicine(id)) { "Medicine is no longer in Trash." }
            snapshotUnsafe()
        }
    }

    suspend fun permanentlyDeleteMedicine(id: String): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(database.permanentlyDeleteMedicine(id)) { "Only medicines in Trash can be permanently deleted." }
            database.trashCount()
        }
    }

    suspend fun emptyTrash(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            database.emptyTrash()
            database.trashCount()
        }
    }

    suspend fun restoreAllTrash(): AppSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            database.restoreAll()
            snapshotUnsafe()
        }
    }

    suspend fun saveMedicine(item: Medicine, photo: ByteArray? = null, removePhoto: Boolean = false): AppSnapshot = withContext(Dispatchers.IO) {
        require(item.id.isNotBlank() && item.name.isNotBlank() && item.category.isNotBlank())
        require(item.official >= 0 && (item.discounted == null || item.discounted >= 0))
        mutex.withLock {
            val codes = validateCodes(item.codes)
            val conflicts = database.loadMedicines().filter { it.id != item.id }
                .flatMap { other -> other.codes.map { it.value to other.name } }.toMap()
            codes.forEach { require(it.value !in conflicts) { "Code already belongs to ${conflicts[it.value]}." } }
            database.saveMedicine(item.copy(codes = codes), photo, removePhoto)
            snapshotUnsafe()
        }
    }

    suspend fun loadPhoto(id: String): ByteArray? = withContext(Dispatchers.IO) {
        mutex.withLock { database.loadPhoto(id) }
    }

    suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val snapshot = snapshotUnsafe()
            BackupCodec.encode(snapshot.items, snapshot.currency, snapshot.categories,
                photos = database.exportPhotos(snapshot.items.filter { it.hasPhoto }.map { it.id }))
        }
    }

    suspend fun toggleFavorite(id: String): Boolean? = withContext(Dispatchers.IO) {
        mutex.withLock { database.toggleFavorite(id) }
    }

    suspend fun setLargeText(value: Boolean): AppSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            preferences.setLargeText(value)
            snapshotUnsafe()
        }
    }

    suspend fun setThemePreference(value: ThemePreference): AppSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            preferences.setThemePreference(value)
            snapshotUnsafe()
        }
    }

    suspend fun setCurrency(value: String): AppSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            preferences.setCurrency(value)
            snapshotUnsafe()
        }
    }

    suspend fun saveCategory(category: Category): AppSnapshot = withContext(Dispatchers.IO) {
        require(isValidCategory(category) && category.id != "all") { "Category details are invalid." }
        mutex.withLock {
            val current = preferences.categories()
            val exists = current.any { it.id == category.id }
            val editableCount = current.count { it.id != "all" }
            require(exists || editableCount < PharmacyDefaults.maxCategories) {
                "You can store up to ${PharmacyDefaults.maxCategories} categories."
            }
            preferences.setCategories(mergeCategoryDefinitions(current, listOf(category)))
            snapshotUnsafe()
        }
    }

    suspend fun importBackup(data: ParsedBackup, mode: ImportMode): AppSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val incomingIds = data.medicines.mapTo(HashSet()) { it.id }
            val allCodes = mutableSetOf<String>()
            val currentItems = if (mode == ImportMode.MERGE) database.loadMedicines() else emptyList()
            val currentById = currentItems.associateBy { it.id }
            currentItems.filter { it.id !in incomingIds }.forEach { item ->
                item.codes.forEach { allCodes.add(it.value) }
            }
            data.medicines.forEach { item ->
                val existingCodes = if (mode == ImportMode.MERGE && !item.codesSpecified) {
                    currentById[item.id]?.codes ?: emptyList()
                } else validateCodes(item.codes)
                existingCodes.forEach { code ->
                    require(allCodes.add(code.value)) { "The backup assigns a code to more than one medicine." }
                }
            }
            val currentCategories = preferences.categories()
            val nextCategories = resolveCategoryImport(
                currentCategories,
                data.categories,
                mode,
                data.medicines.map { it.category },
            )
            when (mode) {
                ImportMode.MERGE -> database.mergeMedicines(data.medicines, data.photos)
                ImportMode.REPLACE -> database.replaceMedicines(data.medicines, data.photos, data.sourceVersion >= 3)
            }
            if (shouldApplyImportedCurrency(mode, data.hasCurrency)) {
                preferences.setCurrency(data.currency)
            }
            preferences.setCategories(nextCategories)
            snapshotUnsafe()
        }
    }
}
