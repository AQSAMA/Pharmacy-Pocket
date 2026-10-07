package com.aqsama.pharmacypocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqsama.pharmacypocket.data.*

@Composable
internal fun LibraryDrawer(lists: List<MedicationList>, selectedKey: String, enabled: Boolean,
    onSelect: (String) -> Unit, onCreate: () -> Unit, onRename: (MedicationList) -> Unit,
    onSpreadsheet: () -> Unit, onImportSettings: () -> Unit, onBackups: () -> Unit, onSettings: () -> Unit,
    importSettingsAvailable: Boolean = true) {
    var search by remember { mutableStateOf("") }
    var expandedImports by remember { mutableStateOf(true) }
    val needle = normalizeSearch(search.trim())
    ModalDrawerSheet(Modifier.widthIn(max = 360.dp)) {
        Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                        AppIcon(AppSymbol.FIELDS, modifier = Modifier.padding(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column {
                        Text("Pharmacy Pocket", style = MaterialTheme.typography.titleLarge)
                        Text("Medication library", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (lists.size > 8) item {
                OutlinedTextField(search, { search = it }, singleLine = true, placeholder = { Text("Find a list") },
                    leadingIcon = { AppIcon(AppSymbol.SEARCH) }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }
            item {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Manual lists", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = onCreate, enabled = enabled) { AppIcon(AppSymbol.ADD, "Create manual list") }
                }
            }
            item {
                NavigationDrawerItem(label = { Text("All manual lists") }, icon = { AppIcon(AppSymbol.FIELDS) },
                    selected = selectedKey == ALL_MANUAL_LISTS, onClick = { if (enabled) onSelect(ALL_MANUAL_LISTS) },
                    badge = { Text(lists.count { !it.imported }.toString()) })
            }
            items(lists.filter { !it.imported && normalizeSearch(it.name).contains(needle) }, key = { it.key }) { list ->
                DrawerListRow(list, list.key == selectedKey, enabled, onSelect, onRename)
            }
            item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
            item {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Imported lists", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { expandedImports = !expandedImports }) { AppIcon(if (expandedImports) AppSymbol.COLLAPSE else AppSymbol.EXPAND, "Toggle imported lists") }
                    IconButton(onClick = onSpreadsheet, enabled = enabled) { AppIcon(AppSymbol.ADD, "Import spreadsheet") }
                }
            }
            if (expandedImports || needle.isNotEmpty()) {
                items(lists.filter { it.imported && normalizeSearch(it.name).contains(needle) }, key = { it.key }) { list ->
                    DrawerListRow(list, list.key == selectedKey, enabled, onSelect, onRename)
                }
                if (lists.none { it.imported }) item {
                    Text("Spreadsheets appear here", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
            if (importSettingsAvailable && lists.any { it.key == selectedKey && it.imported }) item {
                NavigationDrawerItem(label = { Text("Import settings") }, icon = { AppIcon(AppSymbol.EDIT) }, selected = false,
                    onClick = { if (enabled) onImportSettings() })
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 12.dp))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NavigationDrawerItem(label = { Text("Import & export") }, icon = { AppIcon(AppSymbol.COPY) }, selected = false,
                onClick = { if (enabled) onBackups() })
            NavigationDrawerItem(label = { Text("Settings") }, icon = { AppIcon(AppSymbol.SETTINGS) }, selected = false,
                onClick = { if (enabled) onSettings() })
        }
        }
    }
}

@Composable
private fun DrawerListRow(list: MedicationList, selected: Boolean, enabled: Boolean,
    onSelect: (String) -> Unit, onRename: (MedicationList) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NavigationDrawerItem(label = { Text(list.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            icon = { AppIcon(if (list.imported) AppSymbol.CATEGORY else AppSymbol.NOTES) }, selected = selected,
            onClick = { if (enabled) onSelect(list.key) }, modifier = Modifier.weight(1f))
        IconButton(onClick = { onRename(list) }, enabled = enabled) { AppIcon(AppSymbol.MORE, "Manage ${list.name}") }
    }
}
