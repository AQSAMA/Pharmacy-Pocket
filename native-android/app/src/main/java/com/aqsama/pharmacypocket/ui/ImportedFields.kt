package com.aqsama.pharmacypocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqsama.pharmacypocket.data.ImportedField

/** Adaptive fields share the medicine card's typography and wrap at narrow widths. */
@Composable
internal fun ImportedFields(fields: List<ImportedField>, compact: Boolean, large: Boolean = false) {
    val visible = fields.filter { it.value.isNotBlank() && (!compact || it.onCard) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val twoColumns = availableWidth >= 320.dp && !large
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            visible.forEach { field ->
                val wide = !twoColumns || field.value.length > 55
                Column(Modifier.width(if (wide) availableWidth else (availableWidth - 12.dp) / 2), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(field.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(field.value, style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (field.field.isPrice) FontWeight.Bold else FontWeight.Normal,
                        color = if (field.field.isPrice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = if (compact && !field.field.isPrice) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
