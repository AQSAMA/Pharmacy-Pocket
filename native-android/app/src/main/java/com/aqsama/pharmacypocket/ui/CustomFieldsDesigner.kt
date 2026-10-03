package com.aqsama.pharmacypocket.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.zIndex
import com.aqsama.pharmacypocket.data.*
import kotlinx.coroutines.delay
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomFieldsDesigner(fields: List<ImportedField>, previewName: String = "Medication", previewPrice: String? = null, onChange: (List<ImportedField>) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ImportedField?>(null) }
    val view = LocalView.current
    val edgePixels = with(LocalDensity.current) { 48.dp.toPx() }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Custom fields & card layout · ${fields.size}")
    }
    if (open) {
        val state = rememberLazyListState()
        val bounds = remember { mutableStateMapOf<String, Rect>() }
        var listBounds by remember { mutableStateOf(Rect.Zero) }
        var dragKey by remember { mutableStateOf<String?>(null) }
        var origin by remember { mutableStateOf(Offset.Zero) }
        var pointer by remember { mutableStateOf(Offset.Zero) }
        var grabOffset by remember { mutableStateOf(Offset.Zero) }
        val latestFields by rememberUpdatedState(fields)
        val latestChange by rememberUpdatedState(onChange)
        val dropKey = bounds.entries.lastOrNull { pointer in it.value }?.key
        fun drop() {
            val key = dragKey ?: return
            val field = latestFields.firstOrNull { it.key == key } ?: run { dragKey = null; return }
            val target = bounds.entries.lastOrNull { pointer in it.value }?.key
            if (target != null && target != key) {
                val next = latestFields.filterNot { it.key == key }.toMutableList()
                val zone = FieldPlacement.entries.firstOrNull { target == "zone:${it.name}" }
                val targetField = next.firstOrNull { it.key == target }
                val placement = zone ?: targetField?.placement ?: field.placement
                val index = targetField?.let {
                    next.indexOf(it) + if (pointer.y > (bounds[target]?.center?.y ?: Float.MAX_VALUE)) 1 else 0
                } ?: (next.indexOfLast { it.placement == placement } + 1)
                next.add(index.coerceIn(0, next.size), field.copy(placement = placement, onCard = true))
                latestChange(next)
                Haptics.selection(view)
            }
            dragKey = null
        }
        LaunchedEffect(dragKey) {
            while (dragKey != null) {
                val edge = edgePixels
                val amount = when {
                    pointer.y < listBounds.top + edge -> -18f
                    pointer.y > listBounds.bottom - edge -> 18f
                    else -> 0f
                }
                if (amount != 0f) state.scrollBy(amount)
                delay(16)
            }
        }
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Card fields", style = MaterialTheme.typography.titleLarge)
                Text("Hold a field and drag it into a position. Tap Edit for its value and color.", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        editing = ImportedField("custom-${UUID.randomUUID()}", "New field", "", ImportField.CUSTOM, true)
                    }, enabled = fields.size < SpreadsheetLimits.maxFields) { Text("+ Add field") }
                    TextButton(onClick = { open = false }) { Text("Done") }
                }
                Box(Modifier.fillMaxWidth().onGloballyPositioned { listBounds = it.boundsInRoot() }) {
                LazyColumn(state = state, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downPosition = listBounds.topLeft + down.position
                        val row = bounds.entries.firstOrNull { !it.key.startsWith("zone:") && downPosition in it.value }
                        val longPress = if (row != null) awaitLongPressOrCancellation(down.id) else null
                        if (longPress != null && row != null) {
                            pointer = listBounds.topLeft + longPress.position
                            origin = downPosition; grabOffset = downPosition - row.value.topLeft
                            dragKey = row.key; Haptics.selection(view)
                            try {
                                while (true) {
                                    // Consume before LazyColumn and the sheet can start their own scroll drag.
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == longPress.id } ?: break
                                    pointer = listBounds.topLeft + change.position
                                    change.consume()
                                    if (!change.pressed) { drop(); break }
                                }
                            } finally { dragKey = null }
                        }
                    }
                }, contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "preview") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { preview = !preview }) { Text(if (preview) "Hide card preview" else "Preview your card") }
                            if (preview) Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ImportedFields(fields.filter { it.placement == FieldPlacement.TOP }, compact = true)
                                    Text(previewName.ifBlank { "Medication" }, style = MaterialTheme.typography.titleLarge)
                                    previewPrice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium) }
                                    ImportedFields(fields.filter { it.placement == FieldPlacement.BODY }, compact = true)
                                    ImportedFields(fields.filter { it.placement == FieldPlacement.FOOTER }, compact = true)
                                }
                            }
                        }
                    }
                    FieldPlacement.entries.forEach { placement ->
                        item(key = "zone:${placement.name}") {
                            DisposableEffect(placement) { onDispose { bounds.remove("zone:${placement.name}") } }
                            Surface(Modifier.fillMaxWidth().testTag("card-zone:${placement.name}").onGloballyPositioned { bounds["zone:${placement.name}"] = it.boundsInRoot() }, shape = MaterialTheme.shapes.medium,
                                color = if (dragKey != null && dropKey == "zone:${placement.name}") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text(placement.label, Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        items(fields.filter { it.placement == placement }, key = { it.key }) { field ->
                            DisposableEffect(field.key) { onDispose { bounds.remove(field.key) } }
                            val dragging = dragKey == field.key
                            val lift by animateFloatAsState(if (dragging) 1.025f else 1f)
                            Surface(
                                modifier = Modifier.fillMaxWidth().testTag("card-field:${field.key}").animateItem().zIndex(if (dragging) 2f else 0f)
                                    .onGloballyPositioned { bounds[field.key] = it.boundsInRoot() }
                                    .graphicsLayer { alpha = if (dragging) 0.35f else 1f; scaleX = lift; scaleY = lift },
                                shape = MaterialTheme.shapes.medium,
                                color = if (dropKey == field.key && dragKey != null && !dragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                shadowElevation = if (dragging) 10.dp else 0.dp,
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("≡", Modifier.padding(end = 12.dp), style = MaterialTheme.typography.titleLarge)
                                    Column(Modifier.weight(1f)) {
                                        Text(field.label, color = field.color?.let(::colorFromHex) ?: MaterialTheme.colorScheme.onSurface)
                                        Text(field.value.ifBlank { "Empty" }, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                                        Row {
                                            TextButton(onClick = {
                                                val next = fields.toMutableList(); val i = next.indexOfFirst { it.key == field.key }
                                                if (i > 0) { next.removeAt(i); next.add(i - 1, field.copy(placement = next[i - 1].placement)); onChange(next) }
                                            }, enabled = fields.indexOf(field) > 0) { Text("↑") }
                                            TextButton(onClick = {
                                                val next = fields.toMutableList(); val i = next.indexOfFirst { it.key == field.key }
                                                if (i < next.lastIndex) { val place = next[i + 1].placement; next.removeAt(i); next.add(i + 1, field.copy(placement = place)); onChange(next) }
                                            }, enabled = fields.indexOf(field) < fields.lastIndex) { Text("↓") }
                                        }
                                    }
                                    Switch(field.onCard, { visible -> onChange(fields.map { if (it.key == field.key) it.copy(onCard = visible) else it }) }, modifier = Modifier.semantics { contentDescription = "Show ${field.label} on card" })
                                    TextButton(onClick = { editing = field }) { Text("Edit") }
                                }
                            }
                        }
                    }
                }
                fields.firstOrNull { it.key == dragKey }?.let { field ->
                    Surface(Modifier.fillMaxWidth().offset { IntOffset((pointer.x - origin.x).roundToInt(), (pointer.y - listBounds.top - grabOffset.y).roundToInt()) }.zIndex(3f),
                        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 12.dp) {
                        Column(Modifier.padding(16.dp)) {
                            Text(field.label, style = MaterialTheme.typography.titleMedium)
                            Text(field.value.ifBlank { "Empty" }, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                }
            }
        }
    }
    editing?.let { initial ->
        var label by remember(initial.key) { mutableStateOf(initial.label) }
        var value by remember(initial.key) { mutableStateOf(initial.value) }
        var color by remember(initial.key) { mutableStateOf(initial.color.orEmpty()) }
        var placement by remember(initial.key) { mutableStateOf(initial.placement) }
        val validColor = color.isBlank() || Regex("^#[0-9a-fA-F]{6}$").matches(color)
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Edit field") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it.take(200) }, label = { Text("Label") })
                OutlinedTextField(value, { value = it.take(SpreadsheetLimits.maxCellLength) }, label = { Text("Value") })
                OutlinedTextField(color, { color = it.take(7) }, label = { Text("Color · #RRGGBB or empty for automatic") }, isError = !validColor)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PharmacyDefaults.categoryColors.forEach { hex ->
                        FilterChip(selected = color == hex, onClick = { color = hex }, label = { Text("●", color = colorFromHex(hex)) })
                    }
                }
                FieldPlacement.entries.forEach { option ->
                    FilterChip(selected = placement == option, onClick = { placement = option }, label = { Text(option.label) })
                }
                if (initial.key.startsWith("custom-")) TextButton(onClick = { onChange(fields.filterNot { it.key == initial.key }); editing = null }) { Text("Delete field") }
            }
        }, confirmButton = {
            TextButton(enabled = label.isNotBlank() && validColor, onClick = {
                val field = initial.copy(label = label.trim(), value = value, color = color.takeIf { it.isNotBlank() }, placement = placement)
                onChange(if (fields.any { it.key == field.key }) fields.map { if (it.key == field.key) field else it } else fields + field)
                editing = null
            }) { Text("Apply") }
        }, dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } })
    }
}
