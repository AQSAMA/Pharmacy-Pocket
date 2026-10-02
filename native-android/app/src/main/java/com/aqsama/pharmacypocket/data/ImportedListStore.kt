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

/** A separate catalog: publishing a list happens only after all rows have been committed. */
class ImportedListStore(private val context: Context) {
    private val mutex = Mutex()
    private fun open(): SQLiteDatabase {
        val file = File(context.filesDir, "SQLite/imported-lists.db")
        file.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(file, null).apply {
            execSQL("CREATE TABLE IF NOT EXISTS lists (id TEXT PRIMARY KEY, name TEXT NOT NULL, source TEXT NOT NULL, mappings TEXT NOT NULL)")
        }
    }

    suspend fun lists(): List<ImportedList> = withContext(Dispatchers.IO) {
        mutex.withLock {
            open().use { db ->
                db.rawQuery("SELECT id, name, source, mappings FROM lists ORDER BY rowid", null).use { cursor ->
                    buildList { while (cursor.moveToNext()) add(ImportedList(cursor.getString(0), cursor.getString(1), cursor.getString(2), mappingsFromJson(JSONArray(cursor.getString(3))))) }
                }
            }
        }
    }

    suspend fun create(name: String, source: String, mappings: List<ColumnMapping>, prepared: PreparedImport): ImportedList = withContext(Dispatchers.IO) {
        require(name.trim().length in 1..100) { "Give this list a name of up to 100 characters." }
        validateMappings(mappings)
        require(prepared.errorCount == 0 && prepared.medicines.size in 1..SpreadsheetLimits.maxRows) { "Resolve import errors before creating the list." }
        mutex.withLock {
            val id = UUID.randomUUID().toString()
            val item = ImportedList(id, name.trim(), source.take(300), mappings)
            val repository = PharmacyRepository(context, id)
            try {
                repository.seedImportedList(prepared.medicines)
                open().use { db ->
                    check(db.insertOrThrow("lists", null, ContentValues().apply {
                        put("id", id); put("name", item.name); put("source", item.source); put("mappings", mappingsToJson(mappings).toString())
                    }) != -1L)
                }
                item
            } catch (error: Throwable) {
                repository.close()
                context.deleteDatabase(File(context.filesDir, "SQLite/imported-$id.db").absolutePath)
                throw error
            } finally { repository.close() }
        }
    }
}
