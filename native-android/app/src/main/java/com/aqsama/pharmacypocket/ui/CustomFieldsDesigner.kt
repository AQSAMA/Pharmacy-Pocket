package com.aqsama.pharmacypocket.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aqsama.pharmacypocket.data.*
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomFieldsDesigner(fields: List<ImportedField>, previewName: String = "Medication", previewPrice: String? = null, onChange: (List<ImportedField>) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var preview by rememberSaveable { mutableStateOf(true) }
    var editing by remember { mutableStateOf<ImportedField?>(null) }
    val view = LocalView.current
    val edgePixels = with(LocalDensity.current) { 48.dp.toPx() }
    ActionRow(AppSymbol.FIELDS, "Card fields", "${fields.count { it.onCard }} shown · ${fields.size} total") { open = true; Haptics.action(view) }
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
        fun shift(key: String, direction: Int): Boolean {
            val ordered = FieldPlacement.entries.flatMap { place -> latestFields.filter { it.placement == place } }.toMutableList()
            val index = ordered.indexOfFirst { it.key == key }
            val target = index + direction
            if (index < 0 || target !in ordered.indices) return false
            val placement = ordered[target].placement
            val field = ordered.removeAt(index).copy(placement = placement)
            ordered.add(target, field)
            latestChange(ordered)
            Haptics.selection(view)
            return true
        }
        LaunchedEffect(dragKey) {
            while (dragKey != null) {
                val amount = when {
                    pointer.y < listBounds.top + edgePixels -> -18f
                    pointer.y > listBounds.bottom - edgePixels -> 18f
                    else -> 0f
                }
                if (amount != 0f) state.scrollBy(amount)
                delay(16)
            }
        }
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Card fields", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { open = false }) { Text("Done") }
                }
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(AppSymbol.DRAG, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Hold to arrange", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).onGloballyPositioned { listBounds = it.boundsInRoot() }) {
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("card-fields-list").pointerInput(Unit) {
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
                                        // Own the drag before either scrolling parent can claim it.
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == longPress.id } ?: break
                                        pointer = listBounds.topLeft + change.position
                                        change.consume()
                                        if (!change.pressed) { drop(); break }
                                    }
                                } finally { dragKey = null }
                            }
                        }
                    }, contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item(key = "preview") {
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column {
                                    Row(Modifier.fillMaxWidth().clickable { preview = !preview }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("Preview", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                        AppIcon(if (preview) AppSymbol.COLLAPSE else AppSymbol.EXPAND, "Toggle card preview")
                                    }
                                    AnimatedVisibility(preview) {
                                        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            ImportedFields(fields.filter { it.placement == FieldPlacement.TOP }, compact = true)
                                            Text(previewName.ifBlank { "Medication" }, style = MaterialTheme.typography.titleMedium)
                                            previewPrice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall) }
                                            ImportedFields(fields.filter { it.placement == FieldPlacement.BODY }, compact = true)
                                            ImportedFields(fields.filter { it.placement == FieldPlacement.FOOTER }, compact = true)
                                        }
                                    }
                                }
                            }
                        }
                        FieldPlacement.entries.forEach { placement ->
                            item(key = "zone:${placement.name}") {
                                DisposableEffect(placement) { onDispose { bounds.remove("zone:${placement.name}") } }
                                val active = dragKey != null && dropKey == "zone:${placement.name}"
                                Surface(Modifier.fillMaxWidth().testTag("card-zone:${placement.name}").onGloballyPositioned { bounds["zone:${placement.name}"] = it.boundsInRoot() },
                                    shape = MaterialTheme.shapes.medium, color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AppIcon(AppSymbol.FIELDS, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(placement.label, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                            items(fields.filter { it.placement == placement }, key = { it.key }) { field ->
                                DisposableEffect(field.key) { onDispose { bounds.remove(field.key) } }
                                var menu by remember { mutableStateOf(false) }
                                val dragging = dragKey == field.key
                                val lift by animateFloatAsState(if (dragging) 1.025f else 1f)
                                Surface(modifier = Modifier.fillMaxWidth().testTag("card-field:${field.key}").animateItem().zIndex(if (dragging) 2f else 0f)
                                    .onGloballyPositioned { bounds[field.key] = it.boundsInRoot() }
                                    .graphicsLayer { alpha = if (dragging) 0.35f else 1f; scaleX = lift; scaleY = lift }
                                    .semantics { customActions = listOf(CustomAccessibilityAction("Move up") { shift(field.key, -1) }, CustomAccessibilityAction("Move down") { shift(field.key, 1) }) },
                                    shape = MaterialTheme.shapes.large,
                                    color = if (dropKey == field.key && dragKey != null && !dragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                    shadowElevation = if (dragging) 10.dp else 0.dp) {
                                    Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AppIcon(AppSymbol.DRAG, "Drag ${field.label}", Modifier.padding(8.dp))
                                        Column(Modifier.weight(1f).clickable { editing = field }.padding(vertical = 6.dp)) {
                                            Text(field.label, style = MaterialTheme.typography.titleSmall, color = field.color?.let(::colorFromHex) ?: MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(field.value.ifBlank { "Add value" }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconToggleButton(checked = field.onCard, onCheckedChange = { visible ->
                                            onChange(fields.map { if (it.key == field.key) it.copy(onCard = visible) else it }); Haptics.selection(view)
                                        }) { AppIcon(if (field.onCard) AppSymbol.EYE else AppSymbol.EYE_OFF, "Show ${field.label} on card", tint = if (field.onCard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                                        Box {
                                            IconButton(onClick = { menu = true }) { AppIcon(AppSymbol.MORE, "Options for ${field.label}") }
                                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                                DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { AppIcon(AppSymbol.EDIT) }, onClick = { menu = false; editing = field })
                                                FieldPlacement.entries.forEach { option ->
                                                    DropdownMenuItem(text = { Text(option.label) }, trailingIcon = { if (field.placement == option) AppIcon(AppSymbol.CHECK) }, onClick = {
                                                        onChange(fields.map { if (it.key == field.key) it.copy(placement = option) else it }); menu = false
                                                    })
                                                }
                                                DropdownMenuItem(text = { Text("Move up") }, onClick = { shift(field.key, -1); menu = false })
                                                DropdownMenuItem(text = { Text("Move down") }, onClick = { shift(field.key, 1); menu = false })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    fields.firstOrNull { it.key == dragKey }?.let { field ->
                        Surface(Modifier.fillMaxWidth().absoluteOffset { IntOffset((pointer.x - origin.x).roundToInt(), (pointer.y - listBounds.top - grabOffset.y).roundToInt()) }.zIndex(3f),
                            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 12.dp) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AppIcon(AppSymbol.DRAG)
                                Column { Text(field.label, style = MaterialTheme.typography.titleSmall); Text(field.value, maxLines = 2, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
                FilledTonalButton(onClick = { editing = ImportedField("custom-${UUID.randomUUID()}", "", "", ImportField.CUSTOM, true) }, enabled = fields.size < SpreadsheetLimits.maxFields,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 48.dp)) {
                    AppIcon(AppSymbol.ADD); Spacer(Modifier.width(8.dp)); Text("Add field")
                }
            }
        }
    }
    editing?.let { initial ->
        var label by remember(initial.key) { mutableStateOf(initial.label) }
        var value by remember(initial.key) { mutableStateOf(initial.value) }
        var color by remember(initial.key) { mutableStateOf(initial.color.orEmpty()) }
        var placement by remember(initial.key) { mutableStateOf(initial.placement) }
        var customColor by remember(initial.key) { mutableStateOf(false) }
        val validColor = color.isBlank() || Regex("^#[0-9a-fA-F]{6}$").matches(color)
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(if (fields.any { it.key == initial.key }) "Edit field" else "Add field") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(label, { label = it.take(200) }, label = { Text("Label") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value, { value = it.take(SpreadsheetLimits.maxCellLength) }, label = { Text("Value") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                Text("Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("") + PharmacyDefaults.categoryColors).forEach { hex ->
                        val selected = color.equals(hex, true)
                        Surface(onClick = { color = hex; Haptics.selection(view) }, modifier = Modifier.size(44.dp).semantics { contentDescription = if (hex.isEmpty()) "Automatic color" else "Color $hex" },
                            shape = androidx.compose.foundation.shape.CircleShape, color = if (hex.isEmpty()) MaterialTheme.colorScheme.surfaceContainerHigh else colorFromHex(hex),
                            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null) {
                            Box(contentAlignment = Alignment.Center) {
                                if (selected) AppIcon(AppSymbol.CHECK, tint = if (hex.isEmpty()) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.White)
                            }
                        }
                    }
                }
                TextButton(onClick = { customColor = !customColor }) { Text("Custom color"); AppIcon(if (customColor) AppSymbol.COLLAPSE else AppSymbol.EXPAND) }
                if (customColor) OutlinedTextField(color, { color = it.take(7) }, label = { Text("Hex color") }, placeholder = { Text("#2f856d") }, isError = !validColor,
                    modifier = Modifier.fillMaxWidth(), singleLine = true, supportingText = if (!validColor) ({ Text("Use # and six hex digits") }) else null)
                Text("Position", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldPlacement.entries.forEach { option ->
                        FilterChip(selected = placement == option, onClick = { placement = option }, label = { Text(option.label) })
                    }
                }
                if (initial.key.startsWith("custom-") && fields.any { it.key == initial.key }) TextButton(onClick = { onChange(fields.filterNot { it.key == initial.key }); editing = null }) {
                    AppIcon(AppSymbol.TRASH, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp)); Text("Delete field", color = MaterialTheme.colorScheme.error)
                }
            }
        }, confirmButton = {
            TextButton(enabled = label.isNotBlank() && validColor, onClick = {
                val field = initial.copy(label = label.trim(), value = value, color = color.takeIf { it.isNotBlank() }, placement = placement)
                onChange(if (fields.any { it.key == field.key }) fields.map { if (it.key == field.key) field else it } else fields + field)
                editing = null; Haptics.confirm(view)
            }) { Text("Apply") }
        }, dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } })
    }
}
