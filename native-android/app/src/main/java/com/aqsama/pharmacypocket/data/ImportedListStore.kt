package com.aqsama.pharmacypocket.data

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    private fun open(): SQLiteDatabase {
        val file = File(context.filesDir, "SQLite/imported-lists.db")
        file.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(file, null).apply {
            execSQL("CREATE TABLE IF NOT EXISTS lists (id TEXT PRIMARY KEY, name TEXT NOT NULL, source TEXT NOT NULL, mappings TEXT NOT NULL, ready INTEGER NOT NULL DEFAULT 1, expected_rows INTEGER NOT NULL DEFAULT 0)")
            val columns = rawQuery("PRAGMA table_info(lists)", null).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            if ("ready" !in columns) execSQL("ALTER TABLE lists ADD COLUMN ready INTEGER NOT NULL DEFAULT 1")
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
                context.deleteDatabase(File(context.filesDir, "SQLite/imported-$id.db").absolutePath)
                db.delete("lists", "id = ? AND ready = 0", arrayOf(id))
            }
        }
    }

    /** Publishes only after all rows commit, with a durable record for startup recovery. */
    suspend fun create(name: String, source: String, mappings: List<ColumnMapping>, prepared: PreparedImport): ImportedList = withContext(Dispatchers.IO) {
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
                    }) != -1L)
                }
                repository.seedImportedList(prepared.medicines)
                open().use { db -> db.execSQL("UPDATE lists SET ready = 1 WHERE id = ?", arrayOf(id)) }
                item
            } catch (error: Throwable) {
                repository.close()
                context.deleteDatabase(File(context.filesDir, "SQLite/imported-$id.db").absolutePath)
                runCatching { open().use { it.delete("lists", "id = ? AND ready = 0", arrayOf(id)) } }
                throw error
            } finally { repository.close() }
        }
    }
}
