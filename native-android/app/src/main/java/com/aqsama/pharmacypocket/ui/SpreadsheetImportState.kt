package com.aqsama.pharmacypocket.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqsama.pharmacypocket.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal data class SpreadsheetImportState(
    val workbook: SpreadsheetWorkbook? = null,
    val sheetIndex: Int = 0,
    val headerRow: Int = 0,
    val firstRow: Int = 1,
    val lastRow: Int = Int.MAX_VALUE,
    val idPrefix: String = "import-${java.util.UUID.randomUUID()}",
    val originalAvailable: Boolean = true,
    val mappings: List<ColumnMapping> = emptyList(),
    val name: String = "",
    val source: String = "",
    val prepared: PreparedImport? = null,
    val working: Boolean = false,
    val error: String? = null,
)

internal class SpreadsheetImportViewModel : ViewModel() {
    private val mutable = MutableStateFlow(SpreadsheetImportState())
    val state = mutable.asStateFlow()
    private var preparation: Job? = null
    private var loading: Job? = null
    private var editingListId: String? = null
    private var customMappings = false

    fun loadFile(context: Context, uri: Uri) = load(context) { file ->
        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Spreadsheet"
        context.contentResolver.openInputStream(uri)?.use { SpreadsheetReader.copyBounded(it, file) }
            ?: error("Could not open this file.")
        Triple(SpreadsheetReader.read(file), name.substringBeforeLast('.'), name)
    }

    fun loadGoogleSheet(context: Context, link: String) = load(context) { file ->
        Triple(SpreadsheetReader.readGoogleSheet(link, file), "Google Sheets list", link.trim())
    }

    private fun load(context: Context, read: (File) -> Triple<SpreadsheetWorkbook, String, String>) {
        if (mutable.value.working) return
        preparation?.cancel()
        mutable.update { it.copy(working = true, error = null, prepared = null) }
        loading = viewModelScope.launch {
            try {
                val (book, name, source) = withContext(Dispatchers.IO) {
                    val file = File.createTempFile("spreadsheet-", ".tmp", context.cacheDir)
                    try { read(file) } finally { file.delete() }
                }
                require(book.sheets.any { it.rows.size > 1 }) { "The workbook needs a header and at least one data row." }
                mutable.value = SpreadsheetImportState(workbook = book, name = name.take(100), source = source)
                selectSheet(book.sheets.indexOfFirst { it.rows.size > 1 })
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(working = false, error = error.message ?: "Could not read the spreadsheet.") }
            }
        }
    }

    fun edit(list: ImportedList, load: suspend () -> ImportSource) {
        if (editingListId == list.id && (mutable.value.workbook != null || loading?.isActive == true)) return
        editingListId = list.id
        customMappings = true
        preparation?.cancel(); loading?.cancel()
        mutable.value = SpreadsheetImportState(working = true)
        loading = viewModelScope.launch {
            try {
                val saved = load()
                mutable.value = SpreadsheetImportState(workbook = saved.workbook, name = list.name, source = list.source,
                    sheetIndex = saved.selection.sheetIndex, headerRow = saved.selection.headerRow,
                    firstRow = saved.selection.firstRow, lastRow = saved.selection.lastRow, mappings = list.mappings,
                    idPrefix = saved.idPrefix, originalAvailable = saved.originalAvailable)
                prepare()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(working = false, error = error.message) }
            }
        }
    }

    fun sourceData(): ImportSource {
        val current = mutable.value
        return ImportSource(requireNotNull(current.workbook), ImportSelection(current.sheetIndex, current.headerRow, current.firstRow, current.lastRow), current.idPrefix, current.originalAvailable)
    }

    fun selectSheet(index: Int) {
        val sheet = mutable.value.workbook?.sheets?.getOrNull(index) ?: return
        val header = sheet.rows.take(50).indices.maxByOrNull { row ->
            val cells = sheet.rows[row].cells
            suggestMappings(cells).count { it.field !in listOf(ImportField.CUSTOM, ImportField.IGNORE) } * 20 +
                cells.count { it.isNotBlank() && it.toDoubleOrNull() == null } * 2 - cells.count { it.toDoubleOrNull() != null } * 4 - row
        } ?: 0
        customMappings = false
        mutable.update { it.copy(sheetIndex = index, mappings = emptyList(), firstRow = 1, lastRow = Int.MAX_VALUE, headerRow = header, idPrefix = if (index == it.sheetIndex) it.idPrefix else "import-${java.util.UUID.randomUUID()}") }
        selectHeader(header)
    }

    fun selectRange(first: Int, last: Int) {
        mutable.update { it.copy(firstRow = first, lastRow = last) }; prepare()
    }

    fun selectHeader(index: Int) {
        val current = mutable.value
        val sheet = current.workbook?.sheets?.getOrNull(current.sheetIndex) ?: return
        val header = sheet.rows.getOrNull(index) ?: return
        val width = sheet.rows.maxOfOrNull { it.cells.size } ?: 0
        val labels = List(width) { header.cells.getOrElse(it) { "Column ${it + 1}" } }
        val suggestions = suggestMappings(labels)
        val mapped = if (customMappings) suggestions.map { suggested ->
            val old = current.mappings.firstOrNull { it.column == suggested.column }
            val oldHeader = sheet.rows.getOrNull(current.headerRow)?.cells?.getOrElse(suggested.column) { "Column ${suggested.column + 1}" }
            old?.copy(label = if (old.label == oldHeader) suggested.label else old.label) ?: suggested
        } else suggestions
        val first = maxOf(current.firstRow, (header.number + 1).coerceAtLeast(1))
        val last = current.lastRow.takeIf { it != Int.MAX_VALUE && it >= first } ?: sheet.rows.last().number
        mutable.update { it.copy(headerRow = index, firstRow = first, lastRow = last, mappings = mapped, error = null) }
        prepare()
    }

    fun rename(value: String) { mutable.update { it.copy(name = value.take(100)) } }
    fun useSuggestedMappings() {
        customMappings = false
        selectHeader(mutable.value.headerRow)
    }
    fun updateMapping(mapping: ColumnMapping) {
        customMappings = true
        mutable.update { it.copy(mappings = it.mappings.map { old ->
            when {
                old.column == mapping.column -> mapping.copy(onCard = mapping.onCard && mapping.field !in listOf(ImportField.NAME, ImportField.IGNORE, ImportField.NOTE, ImportField.DESCRIPTION))
                mapping.field !in listOf(ImportField.CUSTOM, ImportField.IGNORE) && old.field == mapping.field -> old.copy(field = ImportField.CUSTOM)
                else -> old
            }
        }) }
        prepare()
    }

    private fun prepare() {
        preparation?.cancel()
        val current = mutable.value
        mutable.update { it.copy(prepared = null, working = true, error = null) }
        preparation = viewModelScope.launch {
            delay(200)
            try {
                val prepared = withContext(Dispatchers.Default) {
                    val coroutine = currentCoroutineContext()
                    prepareSpreadsheet(current.workbook!!.sheets[current.sheetIndex], current.headerRow, current.mappings, coroutine::ensureActive, current.firstRow, current.lastRow, current.idPrefix)
                }
                mutable.update { it.copy(prepared = prepared, working = false) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(error = error.message ?: "Check the column mapping.", working = false) }
            }
        }
    }

    fun reset() { editingListId = null; customMappings = false; loading?.cancel(); preparation?.cancel(); mutable.value = SpreadsheetImportState() }
}
