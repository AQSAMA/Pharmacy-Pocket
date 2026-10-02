package com.aqsama.pharmacypocket.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpreadsheetImportScreen(
    busy: Boolean,
    onBack: () -> Unit,
    onImport: (String, String, List<ColumnMapping>, PreparedImport, ImportSource, () -> Unit) -> Unit,
    editList: ImportedList? = null,
    loadSource: (suspend () -> ImportSource)? = null,
    modelKey: String = "spreadsheet-import",
) {
    val model: SpreadsheetImportViewModel = viewModel(key = modelKey)
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(editList?.id) { if (editList != null && loadSource != null) model.edit(editList, loadSource) }
    val context = LocalContext.current
    var link by rememberSaveable { mutableStateOf("") }
    var choosingColumn by remember { mutableStateOf<Int?>(null) }
    var choosingSheet by remember { mutableStateOf(false) }
    var choosingHeader by remember { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf("Rows") }
    var expandedColumn by rememberSaveable { mutableStateOf<Int?>(null) }
    var roleQuery by rememberSaveable { mutableStateOf("") }
    var headerInput by remember { mutableStateOf("") }
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
        topBar = { ScreenTopBar(if (editList == null) "Import a separate list" else "Import settings", ::back) },
        bottomBar = {
            if (book != null) Surface(tonalElevation = 3.dp) {
                Button(onClick = { confirming = true }, enabled = ready,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    Text(if (busy) "Creating list…" else if (state.working) "Checking mapping…" else "${if (editList == null) "Create list" else "Save settings"} · ${prepared?.medicines?.size ?: 0} rows")
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                if (book == null) {
                    Text("Bring your spreadsheet into your pocket", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Create an independent list with its own fields, categories, favorites and Trash.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Text("Select rows, map columns, then preview your cards.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (book == null && editList == null) {
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

            } else if (book != null) {
                val sheet = book.sheets[state.sheetIndex]
                item {
                    OutlinedTextField(state.name, model::rename, label = { Text("List name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    if (!state.originalAvailable) Text("This older import uses its saved fields. Previously skipped columns are unavailable; all stored details and edits remain editable.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Rows", "Columns", "Preview").forEach { label ->
                            FilterChip(selected = section == label, onClick = { section = label }, label = { Text(label) })
                        }
                    }
                    Text("${prepared?.medicines?.size ?: 0} rows ready · ${state.mappings.count { it.field != ImportField.IGNORE }} included columns", style = MaterialTheme.typography.labelLarge)
                    if (section == "Columns") TextButton(onClick = model::useSuggestedMappings, enabled = !busy) { Text("Use suggested mappings") }
                    if (section == "Rows") {
                        OutlinedButton(onClick = { choosingSheet = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Worksheet: ${sheet.name}") }
                        OutlinedButton(onClick = { headerInput = sheet.rows[state.headerRow].number.toString(); choosingHeader = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Header row: ${sheet.rows[state.headerRow].number}") }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(state.firstRow.takeIf { it > 0 }?.toString().orEmpty(), { model.selectRange(it.toIntOrNull() ?: 0, state.lastRow) }, label = { Text("First data row") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.weight(1f), enabled = !busy)
                            OutlinedTextField(state.lastRow.takeIf { it > 0 && it != Int.MAX_VALUE }?.toString().orEmpty(), { model.selectRange(state.firstRow, it.toIntOrNull() ?: 0) }, label = { Text("Last data row") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.weight(1f), enabled = !busy)
                        }
                        Text("Row numbers match your spreadsheet. Rows before the header and outside this range are excluded.", style = MaterialTheme.typography.bodySmall)
                        if (editList == null) TextButton(onClick = model::reset, enabled = !busy) { Text("Choose another source") }
                    }
                }
                if (section == "Rows") {
                    item { Text("Row preview", style = MaterialTheme.typography.titleMedium) }
                    items(sheet.rows.filter { it.number in state.firstRow..state.lastRow }.take(4), key = { "row:${it.number}" }) { row ->
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("Row ${row.number}", style = MaterialTheme.typography.labelMedium)
                                Text(row.cells.filter { it.isNotBlank() }.take(3).joinToString(" · "), maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (section == "Columns") {
                    items(state.mappings, key = { it.column }) { mapping ->
                        val sample = sheet.rows.asSequence().drop(state.headerRow + 1).firstOrNull { it.cells.getOrElse(mapping.column) { "" }.isNotBlank() }
                            ?.cells?.getOrElse(mapping.column) { "" }.orEmpty()
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${spreadsheetColumnLabel(mapping.column)} · ${sheet.rows[state.headerRow].cells.getOrElse(mapping.column) { "Untitled" }}", style = MaterialTheme.typography.titleSmall)
                                Text(sample.ifBlank { "No sample value" }, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = { choosingColumn = mapping.column }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(mapping.field.label) }
                                TextButton(onClick = { expandedColumn = if (expandedColumn == mapping.column) null else mapping.column }, enabled = !busy) { Text(if (expandedColumn == mapping.column) "Fewer options" else "Label & card options") }
                                if (expandedColumn == mapping.column && mapping.field.isPrice) {
                                    Text("Number format", style = MaterialTheme.typography.labelMedium)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        PriceFormat.entries.forEach { format ->
                                            FilterChip(selected = mapping.priceFormat == format, enabled = !busy,
                                                onClick = { model.updateMapping(mapping.copy(priceFormat = format)) }, label = { Text(format.label) })
                                        }
                                    }
                                }
                                if (expandedColumn == mapping.column && mapping.field != ImportField.IGNORE) {
                                    OutlinedTextField(mapping.label, { model.updateMapping(mapping.copy(label = it.take(200))) }, label = { Text("Display label") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                                    if (mapping.field !in listOf(ImportField.NAME, ImportField.NOTE, ImportField.DESCRIPTION)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Text("Show on card", style = MaterialTheme.typography.bodyMedium)
                                            Switch(mapping.onCard, { model.updateMapping(mapping.copy(onCard = it)) }, enabled = !busy && (mapping.onCard || state.mappings.count { it.onCard } < 6))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (prepared != null && section == "Preview") {
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
            if (state.working) item { LinearProgressIndicator(Modifier.fillMaxWidth()); if (book == null) Text("Reading spreadsheet…") }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        }
    }
    choosingColumn?.let { column ->
        val mapping = state.mappings.firstOrNull { it.column == column }
        if (mapping != null) ModalBottomSheet(onDismissRequest = { choosingColumn = null; roleQuery = "" }) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(horizontal = 20.dp).imePadding()) {
                Text("${spreadsheetColumnLabel(column)} · ${mapping.label}", style = MaterialTheme.typography.titleLarge)
                Text("Assign a role. A specific role moves here if another column already uses it.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(roleQuery, { roleQuery = it }, label = { Text("Find a role") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(ImportField.entries.filter { it.label.contains(roleQuery, ignoreCase = true) }, key = { it.name }) { field ->
                        ListItem(headlineContent = { Text(field.label) }, leadingContent = { RadioButton(selected = mapping.field == field, onClick = null) },
                            modifier = Modifier.fillMaxWidth().clickable { model.updateMapping(mapping.copy(field = field)); choosingColumn = null; roleQuery = "" })
                    }
                }
                TextButton(onClick = { choosingColumn = null; roleQuery = "" }, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) { Text("Done") }
            }
        }
    }

    if (choosingSheet && book != null) AlertDialog(onDismissRequest = { choosingSheet = false }, title = { Text("Choose worksheet") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { book.sheets.forEachIndexed { index, sheet ->
            TextButton(onClick = { model.selectSheet(index); choosingSheet = false }) { Text("${sheet.name} · ${sheet.rows.size} rows") }
        } } }, confirmButton = { TextButton(onClick = { choosingSheet = false }) { Text("Close") } })
    if (choosingHeader && book != null) AlertDialog(onDismissRequest = { choosingHeader = false }, title = { Text("Choose header row") },
        text = { Column {
            OutlinedTextField(headerInput, { headerInput = it }, label = { Text("Spreadsheet row number") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            val row = book.sheets[state.sheetIndex].rows.firstOrNull { it.number == headerInput.toIntOrNull() }
            Text(row?.cells?.filter { it.isNotBlank() }?.take(6)?.joinToString(" · ") ?: "Choose a row containing your column headings.", modifier = Modifier.padding(top = 12.dp))
        } }, confirmButton = { TextButton(enabled = book.sheets[state.sheetIndex].rows.any { it.number == headerInput.toIntOrNull() } && !busy, onClick = {
            model.selectHeader(book.sheets[state.sheetIndex].rows.indexOfFirst { it.number == headerInput.toIntOrNull() }); choosingHeader = false
        }) { Text("Use this row") } }, dismissButton = { TextButton(onClick = { choosingHeader = false }) { Text("Cancel") } })
    if (confirming && prepared != null) AlertDialog(onDismissRequest = { if (!busy) confirming = false }, title = { Text("${if (editList == null) "Create" else "Update"} “${state.name}”?") },
        text = { Text(if (editList == null) "${prepared.medicines.size} items will be added to a separate list. ${prepared.skippedRows} rows with an empty Name are skipped." else "Save these row and column settings. Existing edits, favorites, photos and codes stay with their rows. Excluded active rows move to Trash; rows already in Trash stay there. My medications remains separate.") },
        dismissButton = { TextButton(onClick = { confirming = false }, enabled = !busy) { Text("Cancel") } },
        confirmButton = { TextButton(enabled = ready, onClick = { confirming = false; onImport(state.name, state.source, state.mappings, prepared, model.sourceData(), model::reset) }) { Text(if (editList == null) "Create list" else "Save settings") } })
}

internal fun spreadsheetColumnLabel(index: Int): String {
    var number = index + 1
    var label = ""
    while (number > 0) { number--; label = ('A' + number % 26) + label; number /= 26 }
    return "Column $label"
}
