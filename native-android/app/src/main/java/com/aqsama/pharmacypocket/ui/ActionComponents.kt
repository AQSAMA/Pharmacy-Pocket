package com.aqsama.pharmacypocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aqsama.pharmacypocket.R

internal enum class AppSymbol(val resource: Int) {
    ADD(R.drawable.ic_add), BACK(R.drawable.ic_back), CAMERA(R.drawable.ic_camera),
    CHECK(R.drawable.ic_check), CLOSE(R.drawable.ic_close), COPY(R.drawable.ic_copy),
    DRAG(R.drawable.ic_drag), EDIT(R.drawable.ic_edit), EYE(R.drawable.ic_eye),
    EYE_OFF(R.drawable.ic_eye_off), MENU(R.drawable.ic_menu), MERGE(R.drawable.ic_merge),
    MORE(R.drawable.ic_more), MOVE(R.drawable.ic_move), NEXT(R.drawable.ic_next),
    SEARCH(R.drawable.ic_search), SETTINGS(R.drawable.ic_settings), STAR(R.drawable.ic_star),
    TRASH(R.drawable.ic_trash), FIELDS(R.drawable.ic_fields), CATEGORY(R.drawable.ic_category),
    NOTES(R.drawable.ic_notes), EXPAND(R.drawable.ic_expand), COLLAPSE(R.drawable.ic_collapse),
}

@Composable
internal fun AppIcon(symbol: AppSymbol, description: String? = null, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(painterResource(symbol.resource), description, modifier.size(24.dp), tint)
}

/** A familiar settings row: one target, one label, and a visible current value. */
@Composable
internal fun ActionRow(symbol: AppSymbol, title: String, value: String? = null, enabled: Boolean = true,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(symbol, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            AppIcon(AppSymbol.NEXT, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
