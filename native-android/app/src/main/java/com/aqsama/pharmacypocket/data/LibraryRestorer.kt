package com.aqsama.pharmacypocket.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Storage work outlives screen recreation; each repository is owned and closed here. */
class LibraryRestorer(context: Context) {
    private val appContext = context.applicationContext
    suspend fun restore(entries: List<ListBackup>, destinationKey: String?, mode: ImportMode) {
        require(entries.isNotEmpty()) { "Select at least one list." }
        require(destinationKey == null || entries.size == 1) { "Select one source list for a different destination." }
        val catalog = ImportedListStore(appContext)
        val lists = medicationLists(catalog.lists())
        val repositories = mutableMapOf<String?, PharmacyRepository>()
        fun repository(list: MedicationList): PharmacyRepository = repositories.getOrPut(list.id) {
            PharmacyRepository(appContext, list.id, list.imported)
        }
        fun existing(entry: ListBackup): MedicationList? =
            if (destinationKey?.startsWith("new:") == true) null
            else if (destinationKey != null) lists.firstOrNull { it.key == destinationKey }
                ?: error("The destination list is no longer available.")
            else lists.firstOrNull { it.key == entry.list.key && it.imported == entry.list.imported }
        var completed = 0
        try {
            // All predictable failures are checked before touching any destination.
            entries.forEach { entry ->
                if (destinationKey == null && entry.nameSpecified) require(entry.list.name.trim().length in 1..100) {
                    "Use a list name of 1–100 characters."
                }
                val target = existing(entry)
                if (target != null) repository(target).checkImportBackup(entry.backup, mode)
                else {
                    if (destinationKey == null && entry.list.id != null) require(lists.none { it.key == entry.list.key }) {
                        "A list with this ID has a different type. Choose a new destination list."
                    }
                    val codes = entry.backup.medicines.flatMap { validateCodes(it.codes) }.map { it.value }
                    require(codes.distinct().size == codes.size) { "A selected list assigns one code to multiple medicines." }
                    if (!entry.list.imported) resolveCategoryImport(PharmacyDefaults.categories, entry.backup.categories,
                        mode, entry.backup.medicines.map { it.category })
                }
            }
            entries.forEach { entry ->
                val matched = existing(entry)
                val target = matched ?: catalog.createRestored(
                    if (destinationKey?.startsWith("new:") == true) destinationKey.removePrefix("new:") else entry.list.name,
                    entry.list.imported, if (destinationKey == null) entry.list.id else null,
                ).let { MedicationList(it.id, it.name, it.imported) }
                repository(target).importBackup(entry.backup, mode)
                // Only separate-list restores apply saved names. Legacy files have no name,
                // and importing into a chosen destination keeps that destination's name.
                if (destinationKey == null && entry.nameSpecified && matched != null) {
                    if (target.id == null) withContext(Dispatchers.IO) {
                        check(appContext.getSharedPreferences("pharmacy-pocket-list-names", Context.MODE_PRIVATE)
                            .edit().putString(MAIN_LIST_KEY, entry.list.name.trim()).commit()) { "Could not save the main list name." }
                    } else catalog.rename(target.id!!, entry.list.name)
                }
                completed++
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw IllegalStateException("${error.message} (Completed $completed of ${entries.size} lists.)", error)
        } finally { repositories.values.forEach { it.close() } }
    }
}
