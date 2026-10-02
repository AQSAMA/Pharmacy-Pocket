package com.aqsama.pharmacypocket.data

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Records pending imports before seeding their separate database. Startup publishes a
 * committed seed or removes an incomplete seed, so a process interruption cannot orphan a list.
 */
class ImportedListStore(private val context: Context) {
    // Recreated Activities share ownership with an in-flight import in this process.
    private companion object { val catalogMutex = Mutex() }
    private val mutex = catalogMutex
    private fun sourceFile(id: String) = File(context.filesDir, "imports/$id.table.gz")
    private fun open(): SQLiteDatabase {
        val file = File(context.filesDir, "SQLite/imported-lists.db")
        file.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(file, null).apply {
            execSQL("CREATE TABLE IF NOT EXISTS lists (id TEXT PRIMARY KEY, name TEXT NOT NULL, source TEXT NOT NULL, mappings TEXT NOT NULL, ready INTEGER NOT NULL DEFAULT 1, expected_rows INTEGER NOT NULL DEFAULT 0)")
            val columns = rawQuery("PRAGMA table_info(lists)", null).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            if ("ready" !in columns) execSQL("ALTER TABLE lists ADD COLUMN ready INTEGER NOT NULL DEFAULT 1")
            if ("selection" !in columns) execSQL("ALTER TABLE lists ADD COLUMN selection TEXT NOT NULL DEFAULT ''")
            if ("expected_rows" !in columns) execSQL("ALTER TABLE lists ADD COLUMN expected_rows INTEGER NOT NULL DEFAULT 0")
        }
    }

    suspend fun lists(): List<ImportedList> = withContext(Dispatchers.IO) {
        mutex.withLock {
            open().use { db ->
                reconcilePending(db)
                db.rawQuery("SELECT id, name, source, mappings FROM lists WHERE ready = 1 ORDER BY rowid", null).use { cursor ->
                    buildList { while (cursor.moveToNext()) add(ImportedList(cursor.getString(0), cursor.getString(1), cursor.getString(2), mappingsFromJson(JSONArray(cursor.getString(3))))) }
                }
            }
        }
    }

    /** Atomically committed seeds are recoverable; partial seeds are safely discarded. */
    private fun reconcilePending(db: SQLiteDatabase) {
        val pending = db.rawQuery("SELECT id, expected_rows FROM lists WHERE ready = 0", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getInt(1)) }
        }
        pending.forEach { (id, expected) ->
            require(Regex("[a-f0-9-]{36}").matches(id)) { "Invalid imported-list ID." }
            val database = MedicineDatabase(context, id)
            val complete = try { expected > 0 && database.activeCount() == expected } finally { database.close() }
            if (complete) {
                db.execSQL("UPDATE lists SET ready = 1 WHERE id = ?", arrayOf(id))
            } else {
                sourceFile(id).delete()
                File(sourceFile(id).parentFile, "${sourceFile(id).name}.pending").delete()
                context.deleteDatabase(File(context.filesDir, "SQLite/imported-$id.db").absolutePath)
                db.delete("lists", "id = ? AND ready = 0", arrayOf(id))
            }
        }
    }

    /** Publishes only after all rows commit, with a durable record for startup recovery. */
    suspend fun create(name: String, source: String, mappings: List<ColumnMapping>, prepared: PreparedImport, sourceData: ImportSource? = null): ImportedList = withContext(Dispatchers.IO) {
        require(name.trim().length in 1..100) { "Give this list a name of up to 100 characters." }
        validateMappings(mappings)
        require(prepared.errorCount == 0 && prepared.medicines.size in 1..SpreadsheetLimits.maxRows) { "Resolve import errors before creating the list." }
        require(prepared.medicines.map { it.id }.distinct().size == prepared.medicines.size) { "The spreadsheet contains duplicate record IDs." }
        mutex.withLock {
            val id = UUID.randomUUID().toString()
            val item = ImportedList(id, name.trim(), source.take(300), mappings)
            val repository = PharmacyRepository(context, id)
            try {
                open().use { db ->
                    check(db.insertOrThrow("lists", null, ContentValues().apply {
                        put("id", id); put("name", item.name); put("source", item.source); put("mappings", mappingsToJson(mappings).toString())
                        put("ready", 0); put("expected_rows", prepared.medicines.size)
                        put("selection", sourceData?.let(::selectionJson) ?: "")
                    }) != -1L)
                }
                sourceData?.let { ImportSourceArchive.write(sourceFile(id), it.workbook) }
                repository.seedImportedList(prepared.medicines)
                open().use { db -> db.execSQL("UPDATE lists SET ready = 1 WHERE id = ?", arrayOf(id)) }
                item
            } catch (error: Throwable) {
                repository.close()
                sourceFile(id).delete()
                File(sourceFile(id).parentFile, "${sourceFile(id).name}.pending").delete()
                context.deleteDatabase(File(context.filesDir, "SQLite/imported-$id.db").absolutePath)
                runCatching { open().use { it.delete("lists", "id = ? AND ready = 0", arrayOf(id)) } }
                throw error
            } finally { repository.close() }
        }
    }
    private fun selectionJson(source: ImportSource): String = JSONObject().apply {
        put("sheet", source.selection.sheetIndex); put("header", source.selection.headerRow)
        put("first", source.selection.firstRow); put("last", source.selection.lastRow); put("prefix", source.idPrefix); put("originalAvailable", source.originalAvailable)
    }.toString()

    suspend fun loadSource(list: ImportedList): ImportSource = withContext(Dispatchers.IO) {
        mutex.withLock {
            val config = open().use { db -> db.rawQuery("SELECT selection FROM lists WHERE id = ?", arrayOf(list.id)).use { c ->
                require(c.moveToFirst()); c.getString(0)
            } }
            if (sourceFile(list.id).exists() && config.isNotEmpty()) {
                val obj = JSONObject(config)
                ImportSource(ImportSourceArchive.read(sourceFile(list.id)), ImportSelection(obj.getInt("sheet"), obj.getInt("header"), obj.getInt("first"), obj.getInt("last")), obj.getString("prefix"), obj.optBoolean("originalAvailable", true))
            } else {
                // Upgrade older imports using every field already stored on their records.
                val repo = PharmacyRepository(context, list.id)
                val records = try { repo.loadSnapshot().items + repo.loadTrash().map { it.medicine } } finally { repo.close() }
                val sourceRows = records.filter { it.id.startsWith("import-") && it.id.substringAfterLast('-').toIntOrNull() != null }
                require(sourceRows.isNotEmpty()) { "This list has no spreadsheet rows to configure." }
                val prefix = sourceRows.first().id.substringBeforeLast('-')
                val width = (list.mappings.maxOfOrNull { it.column } ?: 0) + 1
                val rows = sourceRows.filter { it.id.substringBeforeLast('-') == prefix }.map { item ->
                    SpreadsheetRow(item.id.substringAfterLast('-').toInt(), List(width) { column ->
                        val mapping = list.mappings.firstOrNull { it.column == column }
                        when (mapping?.field) {
                            ImportField.NAME -> item.name; ImportField.NOTE -> item.note; ImportField.DESCRIPTION -> item.description
                            else -> item.importedFields.firstOrNull { it.key == "column-$column" }?.value.orEmpty()
                        }
                    })
                }.sortedBy { it.number }
                val headers = List(width) { column -> list.mappings.firstOrNull { it.column == column }?.label ?: "Column ${column + 1}" }
                ImportSource(SpreadsheetWorkbook(listOf(SpreadsheetSheet("Saved fields", listOf(SpreadsheetRow(0, headers)) + rows))),
                    ImportSelection(firstRow = rows.first().number, lastRow = rows.last().number), prefix, originalAvailable = false)
            }
        }
    }

    suspend fun update(list: ImportedList, name: String, mappings: List<ColumnMapping>, prepared: PreparedImport, source: ImportSource): ImportedList = withContext(Dispatchers.IO) {
        require(name.trim().length in 1..100)
        validateMappings(mappings)
        require(prepared.errorCount == 0 && prepared.medicines.isNotEmpty()) { "Resolve the mapping errors first." }
        mutex.withLock {
            val repo = PharmacyRepository(context, list.id)
            try {
                if (!sourceFile(list.id).exists()) ImportSourceArchive.write(sourceFile(list.id), source.workbook)
                repo.reconfigureImport(prepared.medicines, list.mappings, mappings, source.idPrefix,
                    File(context.filesDir, "SQLite/imported-lists.db"), name.trim(), selectionJson(source))
            } finally { repo.close() }
            list.copy(name = name.trim(), mappings = mappings)
        }
    }

}
