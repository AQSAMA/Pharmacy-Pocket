package com.aqsama.pharmacypocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aqsama.pharmacypocket.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveToMainSheet(item: Medicine, main: AppSnapshot, lists: List<ImportedList>, busy: Boolean, onDismiss: () -> Unit,
    onTransfer: (String?, Boolean, String, Long, String, String?) -> Unit) {
    var name by rememberSaveable(item.id) { mutableStateOf(item.name) }
    var price by rememberSaveable(item.id) { mutableStateOf(item.official.toString()) }
    var category by rememberSaveable(item.id) { mutableStateOf(main.categories.firstOrNull { it.id != "all" }?.id ?: "tablets") }
    var destination by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    var action by rememberSaveable(item.id) { mutableStateOf("Copy") }
    var targetId by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    var search by rememberSaveable(item.id) { mutableStateOf("") }
    var removeAfterMerge by rememberSaveable(item.id) { mutableStateOf(false) }
    val merging = action == "Merge"
    val target = main.items.firstOrNull { it.id == targetId }
    val value = price.toLongOrNull()
    val valid = if (merging) target != null else destination != null || (name.isNotBlank() && value != null && value in 0..9_007_199_254_740_991L)
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Use imported medication", style = MaterialTheme.typography.titleLarge)
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Copy", "Move", "Merge").forEach { mode -> FilterChip(selected = action == mode, onClick = { action = mode; if (mode == "Merge") destination = null }, enabled = !busy, label = { Text(mode) }) }
            }
            if (!merging) {
                Text("Destination", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = destination == null, onClick = { destination = null }, enabled = !busy, label = { Text("My medications") })
                    lists.forEach { list -> FilterChip(selected = destination == list.id, onClick = { destination = list.id }, enabled = !busy, label = { Text(list.name) }) }
                }
                if (destination == null) {
                    OutlinedTextField(name, { name = it.take(SpreadsheetLimits.maxCellLength) }, label = { Text("Common name on your card") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    OutlinedTextField(price, { price = it }, label = { Text("Your pharmacy price (${main.currency})") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), enabled = !busy,
                        isError = value == null || value !in 0..9_007_199_254_740_991L)
                    Text("Category in My medications", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        main.categories.filter { it.id != "all" }.forEach { option ->
                            FilterChip(selected = category == option.id, onClick = { category = option.id }, enabled = !busy, label = { Text(categoryAncestors(option.id, main.categories).joinToString(" › ") { it.label }) })
                        }
                    }
                }
                Text(if (action == "Move") "The original row moves to the source list’s Trash. Photos, codes and details travel with it." else "The original stays in this list. The new medication keeps its photo, codes and details.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Choose a medication in My medications", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(search, { search = it }, label = { Text("Search your common name") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                val results = remember(search, main.items) { val needle = normalizeSearch(search.trim()); main.items.filter { normalizeSearch(it.name).contains(needle) } }
                if (results.isEmpty()) Text("No medications match.")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                    items(results, key = { it.id }) { medicine ->
                        FilterChip(selected = targetId == medicine.id, onClick = { targetId = medicine.id }, enabled = !busy, label = { Text("${medicine.name} · ${formatPrice(medicine.official)} ${main.currency}") })
                    }
                }
                target?.let { medicine ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Keep: ${medicine.name}", style = MaterialTheme.typography.titleMedium)
                            Text("Your price: ${formatPrice(medicine.official)} ${main.currency}")
                            medicine.discounted?.let { Text("Discounted price: ${formatPrice(it)} ${main.currency}") }
                            Text("Bring in: original name, scientific name, source prices, notes, description, fields, photo and package codes.")
                            Text("Your category, favorite and date added stay as you set them.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Move source to Trash after merge", Modifier.weight(1f))
                    Switch(checked = removeAfterMerge, onCheckedChange = { removeAfterMerge = it }, enabled = !busy)
                }
            }
            Button(onClick = { onTransfer(destination, action == "Move" || merging && removeAfterMerge, name.trim(), value ?: item.official, category, if (merging) targetId else null) }, enabled = !busy && valid,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()) { Text(if (busy) "Saving…" else "$action medication") }
        }
    }
}
