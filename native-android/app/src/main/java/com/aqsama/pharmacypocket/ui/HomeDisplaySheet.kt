package com.aqsama.pharmacypocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aqsama.pharmacypocket.data.MedicineSort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeDisplaySheet(sort: MedicineSort, large: Boolean, canReset: Boolean, onSort: (MedicineSort) -> Unit,
    onLargeText: (Boolean) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sort & display", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            MedicineSort.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(sort == option, role = Role.RadioButton, onClick = { onSort(option) }).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(option.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    RadioButton(selected = sort == option, onClick = null)
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Large text", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(large, onLargeText)
            }
            TextButton(onClick = onReset, enabled = canReset, modifier = Modifier.fillMaxWidth()) { Text("Reset filters") }
        }
    }
}
