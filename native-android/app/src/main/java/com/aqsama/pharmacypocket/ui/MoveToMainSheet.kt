package com.aqsama.pharmacypocket.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqsama.pharmacypocket.data.*

private enum class TransferMode(val label: String, val icon: AppSymbol) {
    COPY("Copy", AppSymbol.COPY), MOVE("Move", AppSymbol.MOVE), MERGE("Merge", AppSymbol.MERGE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveToMainSheet(item: Medicine, main: AppSnapshot, lists: List<ImportedList>, busy: Boolean, onDismiss: () -> Unit,
    onTransfer: (String?, Boolean, String, Long, String, String?) -> Unit) {
    var name by rememberSaveable(item.id) { mutableStateOf(item.name) }
    var price by rememberSaveable(item.id) { mutableStateOf(item.official.toString()) }
    var category by rememberSaveable(item.id) { mutableStateOf(main.categories.firstOrNull { it.id != "all" }?.id ?: "tablets") }
    var destination by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    var mode by rememberSaveable(item.id) { mutableStateOf(TransferMode.COPY) }
    var targetId by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    var search by rememberSaveable(item.id) { mutableStateOf("") }
    var picker by rememberSaveable(item.id) { mutableStateOf("") }
    var removeAfterMerge by rememberSaveable(item.id) { mutableStateOf(false) }
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    val latestBusy by rememberUpdatedState(busy)
    val merging = mode == TransferMode.MERGE
    val target = main.items.firstOrNull { it.id == targetId }
    val value = price.trim().toLongOrNull()
    val valid = if (merging) target != null else destination != null || (name.isNotBlank() && value != null && value in 0..9_007_199_254_740_991L)
    val destinationLabel = lists.firstOrNull { it.id == destination }?.name ?: "My medications"
    val destinationExists = destination == null || lists.any { it.id == destination }
    fun pick(screen: String) { picker = screen; search = ""; Haptics.action(view) }
    fun closePicker() { picker = ""; keyboard?.hide() }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !latestBusy })) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding().testTag("transfer-sheet")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = !busy, onClick = { if (picker.isEmpty()) onDismiss() else closePicker() }) {
                    AppIcon(if (picker.isEmpty()) AppSymbol.CLOSE else AppSymbol.BACK, if (picker.isEmpty()) "Close transfer" else "Back to transfer")
                }
                Column(Modifier.weight(1f)) {
                    Text(when (picker) { "lists" -> "Choose list"; "medicines" -> "Choose medication"; "categories" -> "Category"; else -> "Transfer" }, style = MaterialTheme.typography.titleLarge)
                    Text(item.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Crossfade(targetState = picker, modifier = Modifier.weight(1f), label = "transfer picker") { screen ->
                when (screen) {
                    "lists", "medicines", "categories" -> {
                        Column(Modifier.fillMaxSize()) {
                            OutlinedTextField(search, { search = it }, label = { Text("Search") }, singleLine = true, enabled = !busy,
                                leadingIcon = { AppIcon(AppSymbol.SEARCH) }, trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { AppIcon(AppSymbol.CLOSE, "Clear search") } },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
                            val needle = normalizeSearch(search.trim())
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                when (screen) {
                                    "lists" -> {
                                        if (normalizeSearch("My medications").contains(needle)) item {
                                            PickerRow("My medications", selected = destination == null, enabled = !busy) { destination = null; closePicker() }
                                        }
                                        items(lists.filter { normalizeSearch(it.name).contains(needle) }, key = { it.id }) { list ->
                                            PickerRow(list.name, selected = destination == list.id, enabled = !busy) { destination = list.id; closePicker() }
                                        }
                                    }
                                    "medicines" -> {
                                        val results = main.items.filter { normalizeSearch(it.name).contains(needle) }
                                        if (results.isEmpty()) item { Text(if (main.items.isEmpty()) "No medications yet" else "No matches", Modifier.padding(16.dp)) }
                                        items(results, key = { it.id }) { medicine ->
                                            PickerRow(medicine.name, "${formatPrice(medicine.official)} ${main.currency}", selected = targetId == medicine.id, enabled = !busy, modifier = Modifier.testTag("picker-medication:${medicine.id}")) { targetId = medicine.id; closePicker() }
                                        }
                                    }
                                    else -> items(main.categories.filter { it.id != "all" && normalizeSearch("${it.label} ${it.arabic}").contains(needle) }, key = { it.id }) { option ->
                                        PickerRow(option.label, option.arabic.takeIf { it != option.label }, selected = category == option.id, enabled = !busy) { category = option.id; closePicker() }
                                    }
                                }
                            }
                        }
                    }
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                TransferMode.entries.forEachIndexed { index, option ->
                                    SegmentedButton(selected = mode == option, onClick = {
                                        mode = option; if (option == TransferMode.MERGE) destination = null; Haptics.selection(view)
                                    }, enabled = !busy, shape = SegmentedButtonDefaults.itemShape(index, TransferMode.entries.size)) { Text(option.label) }
                                }
                            }
                        }
                        if (merging) {
                            item { ActionRow(AppSymbol.MERGE, "Merge into", target?.name ?: "Choose medication", !busy, Modifier.testTag("transfer-target")) { pick("medicines") } }
                            target?.let { medicine ->
                                item {
                                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                AppIcon(AppSymbol.CHECK); Text("Name & prices kept", style = MaterialTheme.typography.labelLarge)
                                            }
                                            Text(medicine.name, style = MaterialTheme.typography.titleMedium)
                                            Text("${formatPrice(medicine.official)} ${main.currency}", style = MaterialTheme.typography.titleMedium)
                                            medicine.discounted?.let { Text("${formatPrice(it)} ${main.currency} · discounted", style = MaterialTheme.typography.bodyMedium) }
                                            HorizontalDivider()
                                            Text("Other details updated", style = MaterialTheme.typography.labelLarge)
                                            item.importedFields.firstOrNull { it.field == ImportField.SCIENTIFIC }?.value?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                        }
                                    }
                                }
                            }
                            item {
                                Row(Modifier.fillMaxWidth().toggleable(removeAfterMerge, enabled = !busy, role = Role.Switch, onValueChange = { removeAfterMerge = it }).padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AppIcon(AppSymbol.TRASH); Text("Move source to Trash", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = removeAfterMerge, onCheckedChange = null, enabled = !busy)
                                }
                            }
                        } else {
                            item { ActionRow(AppSymbol.MOVE, "To", destinationLabel, !busy, Modifier.testTag("transfer-destination")) { pick("lists") } }
                            if (destination == null) {
                                item { OutlinedTextField(name, { name = it.take(SpreadsheetLimits.maxCellLength) }, label = { Text("Common name") }, modifier = Modifier.fillMaxWidth(), enabled = !busy, maxLines = 3) }
                                item { OutlinedTextField(price, { price = it }, label = { Text("Your price") }, suffix = { Text(main.currency) }, singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                                    modifier = Modifier.fillMaxWidth(), enabled = !busy, isError = value == null || value !in 0..9_007_199_254_740_991L) }
                                item { ActionRow(AppSymbol.CATEGORY, "Category", main.categories.firstOrNull { it.id == category }?.label, !busy) { pick("categories") } }
                            }
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppIcon(if (mode == TransferMode.MOVE) AppSymbol.TRASH else AppSymbol.COPY, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (mode == TransferMode.MOVE) "Original moves to Trash" else "Original stays in this list", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (picker.isEmpty()) {
                HorizontalDivider()
                Button(onClick = {
                    keyboard?.hide()
                    onTransfer(destination, mode == TransferMode.MOVE || merging && removeAfterMerge, name.trim(), value ?: item.official, category, if (merging) targetId else null)
                }, enabled = !busy && valid && destinationExists, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 54.dp).testTag("transfer-confirm")) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else AppIcon(mode.icon)
                    Spacer(Modifier.width(8.dp)); Text(if (busy) "Saving…" else mode.label)
                }
            } else Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun PickerRow(title: String, subtitle: String? = null, selected: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (selected) AppIcon(AppSymbol.CHECK)
        }
    }
}
