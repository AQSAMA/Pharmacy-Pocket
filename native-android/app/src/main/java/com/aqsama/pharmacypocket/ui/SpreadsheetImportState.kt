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

    fun selectSheet(index: Int) {
        val sheet = mutable.value.workbook?.sheets?.getOrNull(index) ?: return
        val header = sheet.rows.take(20).indices.maxByOrNull { row ->
            sheet.rows[row].cells.count { cell -> cell.isNotBlank() && cell.toDoubleOrNull() == null } - row
        } ?: 0
        mutable.update { it.copy(sheetIndex = index, headerRow = header) }
        selectHeader(header)
    }

    fun selectHeader(index: Int) {
        val current = mutable.value
        val sheet = current.workbook?.sheets?.getOrNull(current.sheetIndex) ?: return
        val header = sheet.rows.getOrNull(index) ?: return
        val width = sheet.rows.maxOfOrNull { it.cells.size } ?: 0
        val labels = List(width) { header.cells.getOrElse(it) { "Column ${it + 1}" } }
        mutable.update { it.copy(headerRow = index, mappings = suggestMappings(labels), error = null) }
        prepare()
    }

    fun rename(value: String) { mutable.update { it.copy(name = value.take(100)) } }
    fun updateMapping(mapping: ColumnMapping) {
        mutable.update { it.copy(mappings = it.mappings.map { old -> if (old.column == mapping.column) mapping else old }) }
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
                    prepareSpreadsheet(current.workbook!!.sheets[current.sheetIndex], current.headerRow, current.mappings, coroutine::ensureActive)
                }
                mutable.update { it.copy(prepared = prepared, working = false) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(error = error.message ?: "Check the column mapping.", working = false) }
            }
        }
    }

    fun reset() { loading?.cancel(); preparation?.cancel(); mutable.value = SpreadsheetImportState() }
}
