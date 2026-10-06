package com.aqsama.pharmacypocket.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aqsama.pharmacypocket.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

internal class ListBackupModel : ViewModel() {
    var initialized = false
    var tab by mutableStateOf(0)
    var selectedKeys by mutableStateOf<Set<String>>(emptySet())
    var medicineIds by mutableStateOf<Map<String, Set<String>>>(emptyMap())
    var photos by mutableStateOf(true)
    var categories by mutableStateOf(true)
    var entries by mutableStateOf<List<ListBackup>>(emptyList())
    var importKeys by mutableStateOf<Set<String>>(emptySet())
    var importIds by mutableStateOf<Map<String, Set<String>>>(emptyMap())
    var destination by mutableStateOf<String?>(null)
    var mode by mutableStateOf(ImportMode.MERGE)
    var pendingExport by mutableStateOf<String?>(null)
    var exportPickerLaunched = false
    var working by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var importVersion by mutableIntStateOf(0)
    fun releaseContents() { entries = emptyList(); pendingExport = null; medicineIds = emptyMap(); importIds = emptyMap() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListBackupScreen(lists: List<MedicationList>, initialKey: String, onBack: () -> Unit,
    loadSnapshot: suspend (String) -> AppSnapshot,
    exportList: suspend (String, BackupSelection) -> String,
    onImport: suspend (List<ListBackup>, String?, ImportMode) -> Unit,
    modelKey: String = "list-backups", onDataChanged: suspend () -> Unit = {}) {
    val model: ListBackupModel = viewModel(key = modelKey)
    if (!model.initialized) { model.selectedKeys = setOf(initialKey); model.initialized = true }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = model.viewModelScope
    var working by model::working
    var error by model::error
    var message by model::message
    LaunchedEffect(model.importVersion) {
        if (model.importVersion > 0) try { onDataChanged() }
        catch (error: Exception) { if (error is CancellationException) throw error; model.error = error.message }
    }
    DisposableEffect(model) {
        onDispose {
            var host: android.content.Context? = context
            while (host is android.content.ContextWrapper && host !is android.app.Activity) host = host.baseContext
            if ((host as? android.app.Activity)?.isChangingConfigurations != true && !model.working) model.releaseContents()
        }
    }
    var picker by remember { mutableStateOf<Pair<MedicationList, List<Medicine>>?>(null) }
    var choosingDestination by remember { mutableStateOf(false) }
    var namingNewList by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var confirmImport by remember { mutableStateOf(false) }
    val importSelection = model.entries.filter { it.list.key in model.importKeys }.map { entry ->
        entry.copy(backup = BackupSelection(model.importIds[entry.list.key], model.photos, model.categories).apply(entry.backup))
    }
    fun fail(throwable: Exception) {
        if (throwable is CancellationException) throw throwable
        error = throwable.message ?: "Data operation failed."
    }
    BackHandler(enabled = working) { }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = model.pendingExport
        model.pendingExport = null; model.exportPickerLaunched = false
        if (uri != null && json != null) scope.launch {
            working = true
            try { withContext(Dispatchers.IO) { writeText(context, uri, json) }; message = "Backup saved" }
            catch (e: Exception) { fail(e) }
            finally { working = false }
        }
    }
    LaunchedEffect(model.pendingExport) {
        if (model.pendingExport != null && !model.exportPickerLaunched) {
            model.exportPickerLaunched = true
            try { saveFile.launch("pharmacy-pocket-${LocalDate.now()}.json") }
            catch (error: Exception) { model.exportPickerLaunched = false; model.pendingExport = null; fail(error) }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            working = true
            try {
                val entries = withContext(Dispatchers.IO) { LibraryBackupCodec.parse(readText(context, uri)) }
                model.entries = entries; model.importKeys = entries.mapTo(hashSetOf()) { it.list.key }
                model.importIds = emptyMap(); model.destination = null; model.mode = ImportMode.MERGE
            } catch (e: Exception) { fail(e) }
            finally { working = false }
        }
    }
    Scaffold(topBar = { ScreenTopBar("Import & export", { if (!working) onBack() }) }, bottomBar = {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(enabled = !working && if (model.tab == 0) model.selectedKeys.isNotEmpty() else importSelection.isNotEmpty() &&
                    importSelection.all { it.backup.medicines.isNotEmpty() || model.importIds[it.list.key] == null },
                    onClick = {
                        if (model.tab == 1) confirmImport = true
                        else scope.launch {
                            working = true
                            try {
                                val selected = lists.filter { it.key in model.selectedKeys }
                                val selection = BackupSelection(photos = model.photos, categories = model.categories)
                                val output = selected.map { list -> list to exportList(list.key, selection.copy(medicineIds = model.medicineIds[list.key])) }
                                model.pendingExport = withContext(Dispatchers.Default) { LibraryBackupCodec.encode(output) }
                            } catch (e: Exception) { fail(e) }
                            finally { working = false }
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    AppIcon(if (model.tab == 0) AppSymbol.COPY else AppSymbol.MERGE)
                    Spacer(Modifier.width(8.dp)); Text(if (model.tab == 0) "Choose save location" else "Review import")
                }
            }
        }
    }) { insets ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
            top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Export", "Import").forEachIndexed { index, label ->
                        SegmentedButton(selected = model.tab == index, onClick = { model.tab = index }, enabled = !working,
                            shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
                    }
                }
            }
            if (model.tab == 0) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Choose lists", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(enabled = !working, onClick = { model.selectedKeys = lists.mapTo(hashSetOf()) { it.key }; model.medicineIds = emptyMap(); model.photos = true; model.categories = true }) { Text("Full library") }
                    }
                }
                items(lists, key = { it.key }) { list ->
                    BackupListRow(list, list.key in model.selectedKeys, model.medicineIds[list.key]?.size, !working,
                        onToggle = { model.selectedKeys = model.selectedKeys.toggle(list.key) }, onChoose = {
                            scope.launch {
                                working = true
                                try { picker = list to loadSnapshot(list.key).items }
                                catch (e: Exception) { fail(e) }
                                finally { working = false }
                            }
                        })
                }
            } else {
                item { ActionRow(AppSymbol.FIELDS, "Open JSON backup", if (model.entries.isEmpty()) "Single list or full library" else "${model.entries.size} lists in file", !working) {
                    openFile.launch(arrayOf("application/json", "text/json", "text/plain", "application/octet-stream"))
                } }
                if (model.entries.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Choose contents", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton(enabled = !working, onClick = { model.importKeys = model.entries.mapTo(hashSetOf()) { it.list.key }; model.importIds = emptyMap(); model.destination = null; model.photos = true; model.categories = true }) { Text("Everything") }
                        }
                    }
                    items(model.entries, key = { it.list.key }) { entry ->
                        BackupListRow(entry.list, entry.list.key in model.importKeys, model.importIds[entry.list.key]?.size ?: entry.backup.medicines.size,
                            !working, onToggle = { model.importKeys = model.importKeys.toggle(entry.list.key); model.destination = null },
                            onChoose = { picker = entry.list to entry.backup.medicines })
                    }
                    item { ActionRow(AppSymbol.MOVE, "Destination", model.destination?.let { key -> if (key.startsWith("new:")) "New: ${key.removePrefix("new:")}" else lists.firstOrNull { it.key == key }?.name } ?: "Keep lists separate", !working) { choosingDestination = true } }
                    item {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ImportMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(selected = model.mode == mode, onClick = { model.mode = mode }, enabled = !working,
                                    shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(if (mode == ImportMode.MERGE) "Merge" else "Replace") }
                            }
                        }
                    }
                    item { Text(if (model.mode == ImportMode.MERGE) "Matching IDs update. Other medicines stay. Matching Trash entries return to the list."
                        else "Unselected active medicines in each destination move to Trash. Existing Trash stays.", style = MaterialTheme.typography.bodySmall,
                        color = if (model.mode == ImportMode.REPLACE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            item { HorizontalDivider() }
            item { BackupToggle("Photos", model.photos, !working) { model.photos = it } }
            item { BackupToggle("Category definitions", model.categories, !working) { model.categories = it } }
            item { Text("Active medicines, prices, custom fields, codes and favorites are included. Trash and original spreadsheet workbooks are kept on this device.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            message?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.primary) } }
        }
    }
    picker?.let { (list, medicines) ->
        val ids = if (model.tab == 0) model.medicineIds[list.key] else model.importIds[list.key]
        MedicineSelectionSheet(list.name, medicines, ids, onDismiss = { picker = null }, onApply = { selected ->
            if (model.tab == 0) { model.medicineIds = model.medicineIds.withSelection(list.key, selected); model.selectedKeys = model.selectedKeys + list.key }
            else { model.importIds = model.importIds.withSelection(list.key, selected); model.importKeys = model.importKeys + list.key }
            picker = null
        })
    }
    if (choosingDestination) ModalBottomSheet(onDismissRequest = { choosingDestination = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Import destination", style = MaterialTheme.typography.titleLarge) }
            item { ActionRow(AppSymbol.FIELDS, "Keep lists separate", "Restore to matching lists; create missing lists") { model.destination = null; choosingDestination = false } }
            if (importSelection.size == 1) item {
                ActionRow(AppSymbol.ADD, "New list", if (importSelection.single().list.imported) "Imported list" else "Manual list") {
                    newListName = importSelection.single().list.name; choosingDestination = false; namingNewList = true
                }
            }
            if (importSelection.size == 1) items(lists.filter { it.imported == importSelection.single().list.imported }, key = { it.key }) { list ->
                ActionRow(AppSymbol.NOTES, list.name) { model.destination = list.key; choosingDestination = false }
            } else item { Text("Select one source list to choose a different destination.", style = MaterialTheme.typography.bodyMedium) }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
    if (namingNewList) AlertDialog(onDismissRequest = { namingNewList = false }, title = { Text("New destination list") },
        text = { OutlinedTextField(newListName, { newListName = it.take(100) }, label = { Text("List name") }, singleLine = true) },
        dismissButton = { TextButton(onClick = { namingNewList = false }) { Text("Cancel") } },
        confirmButton = { TextButton(enabled = newListName.isNotBlank(), onClick = { model.destination = "new:${newListName.trim()}"; namingNewList = false }) { Text("Use list") } })
    if (confirmImport) AlertDialog(onDismissRequest = { confirmImport = false }, title = { Text("${if (model.mode == ImportMode.MERGE) "Merge" else "Replace"} selected contents?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${importSelection.sumOf { it.backup.medicines.size }} medicines · ${importSelection.size} lists")
            Text("To: ${model.destination?.let { key -> if (key.startsWith("new:")) "New: ${key.removePrefix("new:")}" else lists.first { it.key == key }.name } ?: "matching or new separate lists"}")
            if (model.mode == ImportMode.REPLACE) Text("Active medicines absent from this selection will move to Trash.", color = MaterialTheme.colorScheme.error)
        } }, dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = {
            confirmImport = false
            val selected = importSelection; val destination = model.destination; val mode = model.mode
            scope.launch {
                working = true
                try { onImport(selected, destination, mode); model.entries = emptyList(); model.importVersion++; message = "Import complete" }
                catch (e: Exception) { fail(e) }
                finally { working = false }
            }
        }) { Text("Import") } })
    error?.let { text -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Import & export") }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}

private fun Set<String>.toggle(key: String): Set<String> = if (key in this) this - key else this + key
private fun Map<String, Set<String>>.withSelection(key: String, ids: Set<String>?): Map<String, Set<String>> =
    if (ids == null) this - key else this + (key to ids)

@Composable
private fun BackupToggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun BackupListRow(list: MedicationList, checked: Boolean, count: Int?, enabled: Boolean, onToggle: () -> Unit, onChoose: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked, null, enabled = enabled)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(list.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${if (list.imported) "Imported" else "Manual"} · ${count?.let { "$it selected" } ?: "All medicines"}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onChoose, enabled = enabled) { AppIcon(AppSymbol.FIELDS, "Choose medicines in ${list.name}") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicineSelectionSheet(name: String, items: List<Medicine>, initial: Set<String>?, onDismiss: () -> Unit, onApply: (Set<String>?) -> Unit) {
    var selection by remember(items) { mutableStateOf(initial ?: items.mapTo(hashSetOf()) { it.id }) }
    var query by remember { mutableStateOf("") }
    val results = remember(items, query) { val needle = normalizeSearch(query.trim()); items.filter { normalizeSearch("${it.name} ${it.subcategory}").contains(needle) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding()) {
            Text(name, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search medications") }, leadingIcon = { AppIcon(AppSymbol.SEARCH) },
                modifier = Modifier.fillMaxWidth().padding(16.dp))
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${selection.size} / ${items.size}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { selection = selection + results.map { it.id } }) { Text(if (query.isBlank()) "All" else "All matches") }
                TextButton(onClick = { selection = selection - results.map { it.id }.toSet() }) { Text("Clear") }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(results, key = { it.id }) { item ->
                    Row(Modifier.fillMaxWidth().toggleable(item.id in selection, role = Role.Checkbox, onValueChange = { selection = selection.toggle(item.id) }).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.id in selection, onCheckedChange = null)
                        Text(item.name, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Button(onClick = { onApply(selection.takeUnless { it.size == items.size }) }, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) { Text("Use selection") }
        }
    }
}
