package com.aqsama.pharmacypocket.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aqsama.pharmacypocket.data.*

@Composable
internal fun SpreadsheetImportScreen(
    busy: Boolean,
    onBack: () -> Unit,
    onImport: (String, String, List<ColumnMapping>, PreparedImport, () -> Unit) -> Unit,
) {
    val model: SpreadsheetImportViewModel = viewModel()
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var link by rememberSaveable { mutableStateOf("") }
    var choosingColumn by remember { mutableStateOf<Int?>(null) }
    var choosingSheet by remember { mutableStateOf(false) }
    var choosingHeader by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.loadFile(context, uri)
    }
    fun back() { if (!busy) { model.reset(); onBack() } }
    BackHandler { back() }
    val book = state.workbook
    val prepared = state.prepared
    val ready = prepared != null && prepared.medicines.isNotEmpty() && prepared.errorCount == 0 && state.name.isNotBlank() && !state.working && !busy
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = { ScreenTopBar("Import a separate list", ::back) },
        bottomBar = {
            if (book != null) Surface(tonalElevation = 3.dp) {
                Button(onClick = { confirming = true }, enabled = ready,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    Text(if (busy) "Creating list…" else if (state.working) "Checking mapping…" else "Create list · ${prepared?.medicines?.size ?: 0} items")
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(if (book == null) "Bring your spreadsheet into your pocket" else "Map your columns", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(if (book == null) "Create an independent list with its own fields, categories, favorites and Trash." else "Choose each column’s role and label. Card fields stay compact; every included field is available in details.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (book == null) {
                item {
                    Button(onClick = { picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values", "application/csv", "text/tab-separated-values")) },
                        enabled = !state.working, modifier = Modifier.fillMaxWidth()) { Text("Choose XLSX or CSV file") }
                }
                item {
                    OutlinedTextField(link, { link = it }, label = { Text("Google Sheets link") }, modifier = Modifier.fillMaxWidth(), enabled = !state.working)
                    Text("Link import requires a sheet shared for viewing with anyone who has the link. For a private sheet, export an XLSX file. The imported copy works offline and does not sync back.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    OutlinedButton(onClick = { model.loadGoogleSheet(context, link) }, enabled = link.isNotBlank() && !state.working, modifier = Modifier.fillMaxWidth()) { Text("Load Google Sheet") }
                }
                if (state.working) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Reading spreadsheet…") }
            } else {
                val sheet = book.sheets[state.sheetIndex]
                item {
                    OutlinedTextField(state.name, model::rename, label = { Text("List name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    OutlinedButton(onClick = { choosingSheet = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Worksheet: ${sheet.name}") }
                    OutlinedButton(onClick = { choosingHeader = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Headers: row ${sheet.rows.getOrNull(state.headerRow)?.number ?: 1}") }
                    Text("${(sheet.rows.size - state.headerRow - 1).coerceAtLeast(0)} data rows · ${state.mappings.size} columns", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = model::reset, enabled = !busy) { Text("Choose another source") }
                }
                items(state.mappings, key = { it.column }) { mapping ->
                    val sample = sheet.rows.drop(state.headerRow + 1).firstOrNull { it.cells.getOrElse(mapping.column) { "" }.isNotBlank() }
                        ?.cells?.getOrElse(mapping.column) { "" }.orEmpty()
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Column ${mapping.column + 1} · ${sheet.rows[state.headerRow].cells.getOrElse(mapping.column) { "Untitled" }}", style = MaterialTheme.typography.titleSmall)
                            Text(sample.ifBlank { "No sample value" }, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = { choosingColumn = mapping.column }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(mapping.field.label) }
                            if (mapping.field.isPrice) {
                                Text("Number format", style = MaterialTheme.typography.labelMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PriceFormat.entries.forEach { format ->
                                        FilterChip(selected = mapping.priceFormat == format, enabled = !busy,
                                            onClick = { model.updateMapping(mapping.copy(priceFormat = format)) }, label = { Text(format.label) })
                                    }
                                }
                            }
                            if (mapping.field != ImportField.IGNORE) {
                                OutlinedTextField(mapping.label, { model.updateMapping(mapping.copy(label = it.take(200))) }, label = { Text("Display label") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                                if (mapping.field !in listOf(ImportField.NAME, ImportField.NOTE, ImportField.DESCRIPTION)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Text("Show on card", style = MaterialTheme.typography.bodyMedium)
                                        Switch(mapping.onCard, { model.updateMapping(mapping.copy(onCard = it)) }, enabled = !busy)
                                    }
                                }
                            }
                        }
                    }
                }
                if (prepared != null) {
                    item {
                        Text("Preview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${prepared.medicines.size} items ready · ${prepared.skippedRows} rows skipped because their Name is empty.", style = MaterialTheme.typography.bodyMedium)
                        if (prepared.errorCount > 0) Text("${prepared.errorCount} rows need attention. Fix their mapping before importing.", color = MaterialTheme.colorScheme.error)
                        prepared.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    items(prepared.medicines.take(2), key = { "preview:${it.id}" }) { item ->
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                ImportedFields(item.importedFields, compact = true)
                                Text("${item.category} › ${item.subcategory}", style = MaterialTheme.typography.bodySmall)
                                Text("${item.importedFields.size} additional fields in details", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        }
    }
    choosingColumn?.let { column ->
        val mapping = state.mappings.firstOrNull { it.column == column }
        if (mapping != null) AlertDialog(
            onDismissRequest = { choosingColumn = null }, title = { Text("Assign column ${column + 1}") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    ImportField.entries.forEach { field ->
                        TextButton(onClick = { model.updateMapping(mapping.copy(field = field)); choosingColumn = null }, modifier = Modifier.fillMaxWidth()) {
                            Text((if (mapping.field == field) "✓ " else "") + field.label)
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { choosingColumn = null }) { Text("Close") } },
        )
    }
    if (choosingSheet && book != null) AlertDialog(onDismissRequest = { choosingSheet = false }, title = { Text("Choose worksheet") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { book.sheets.forEachIndexed { index, sheet ->
            TextButton(onClick = { model.selectSheet(index); choosingSheet = false }) { Text("${sheet.name} · ${sheet.rows.size} rows") }
        } } }, confirmButton = { TextButton(onClick = { choosingSheet = false }) { Text("Close") } })
    if (choosingHeader && book != null) AlertDialog(onDismissRequest = { choosingHeader = false }, title = { Text("Choose header row") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            book.sheets[state.sheetIndex].rows.take(50).forEachIndexed { index, row ->
                TextButton(onClick = { model.selectHeader(index); choosingHeader = false }) { Text("Row ${row.number}: ${row.cells.take(3).joinToString(" · ")}", maxLines = 2) }
            }
        } }, confirmButton = { TextButton(onClick = { choosingHeader = false }) { Text("Close") } })
    if (confirming && prepared != null) AlertDialog(onDismissRequest = { if (!busy) confirming = false }, title = { Text("Create “${state.name}”?") },
        text = { Text("${prepared.medicines.size} items will be added to a new, separate list. ${prepared.skippedRows} rows with an empty Name will be skipped. Your current medicines stay in their own list.") },
        dismissButton = { TextButton(onClick = { confirming = false }, enabled = !busy) { Text("Cancel") } },
        confirmButton = { TextButton(enabled = ready, onClick = { confirming = false; onImport(state.name, state.source, state.mappings, prepared, model::reset) }) { Text("Create list") } })
}
