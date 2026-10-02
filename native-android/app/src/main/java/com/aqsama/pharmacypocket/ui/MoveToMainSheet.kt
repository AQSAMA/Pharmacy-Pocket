package com.aqsama.pharmacypocket.ui

import androidx.compose.foundation.layout.*
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
internal fun MoveToMainSheet(item: Medicine, categories: List<Category>, currency: String, busy: Boolean, onDismiss: () -> Unit, onMove: (String, Long, String) -> Unit) {
    var name by rememberSaveable(item.id) { mutableStateOf(item.name) }
    var price by rememberSaveable(item.id) { mutableStateOf(item.official.toString()) }
    var category by rememberSaveable(item.id) { mutableStateOf(categories.firstOrNull { it.id != "all" }?.id ?: "tablets") }
    val value = price.toLongOrNull()
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Move to My medications", style = MaterialTheme.typography.titleLarge)
            Text("Keep a simple card for daily use. The original name, scientific name, source prices and other fields remain in details.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(name, { name = it.take(SpreadsheetLimits.maxCellLength) }, label = { Text("Common name on your card") }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            Text("Original name: ${item.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(price, { price = it }, label = { Text("Your pharmacy price ($currency)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), enabled = !busy,
                isError = value == null || value !in 0..9_007_199_254_740_991L)
            Text("Category in My medications", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.filter { it.id != "all" }.forEach { option ->
                    FilterChip(selected = category == option.id, onClick = { category = option.id }, enabled = !busy, label = { Text(option.label) })
                }
            }
            Text("Photos, package codes, favorites and notes move with it. The original row leaves this list and stays in its Trash.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onMove(name.trim(), requireNotNull(value), category) }, enabled = !busy && name.isNotBlank() && value != null && value in 0..9_007_199_254_740_991L,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()) { Text(if (busy) "Moving…" else "Move medication") }
        }
    }
}
