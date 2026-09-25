package com.aqsama.pharmacypocket.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqsama.pharmacypocket.data.AppSnapshot
import com.aqsama.pharmacypocket.data.Medicine
import com.aqsama.pharmacypocket.data.buildSearchIndex
import com.aqsama.pharmacypocket.data.categoryById
import com.aqsama.pharmacypocket.data.formatAddedDate
import com.aqsama.pharmacypocket.data.formatPrice
import com.aqsama.pharmacypocket.data.listSubcategories
import com.aqsama.pharmacypocket.data.subcategoryKey
import com.aqsama.pharmacypocket.data.subcategoryLabel
import java.util.UUID

private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

@Composable
fun MedicineEditorScreen(
    snapshot: AppSnapshot,
    medicineId: String?,
    initialCategory: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onManageCategories: () -> Unit,
    onSave: (Medicine) -> Unit,
    onMoveToTrash: (Medicine) -> Unit,
) {
    val view = LocalView.current
    val existing = remember(snapshot.items, medicineId) { snapshot.items.firstOrNull { it.id == medicineId } }
    val fallbackCategory = initialCategory
        ?.takeIf { candidate -> candidate != "all" && snapshot.categories.any { it.id == candidate } }
        ?: snapshot.categories.firstOrNull { it.id != "all" }?.id
        ?: "syrups"

    var name by rememberSaveable(medicineId) { mutableStateOf(existing?.name ?: "") }
    var category by rememberSaveable(medicineId) { mutableStateOf(existing?.category ?: fallbackCategory) }
    var subcategory by rememberSaveable(medicineId) { mutableStateOf(subcategoryLabel(existing?.subcategory)) }
    var official by rememberSaveable(medicineId) { mutableStateOf(existing?.official?.toString() ?: "") }
    var discounted by rememberSaveable(medicineId) { mutableStateOf(existing?.discounted?.toString() ?: "") }
    var note by rememberSaveable(medicineId) { mutableStateOf(existing?.note ?: "") }
    var description by rememberSaveable(medicineId) { mutableStateOf(existing?.description ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var confirmTrash by remember { mutableStateOf(false) }

    val existingSubcategories = remember(snapshot.items, category) {
        listSubcategories(buildSearchIndex(snapshot.items), category)
    }

    fun submit() {
        val officialNumber = official.trim().toLongOrNull()
        val discountedNumber = discounted.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()
        val discountWasInvalid = discounted.trim().isNotEmpty() && discountedNumber == null
        if (
            name.trim().isEmpty() ||
            officialNumber == null ||
            officialNumber !in 0..MAX_SAFE_INTEGER ||
            discountWasInvalid ||
            (discountedNumber != null && discountedNumber !in 0..MAX_SAFE_INTEGER)
        ) {
            Haptics.reject(view)
            validationError = "Enter a medicine name and whole-number ${snapshot.currency} prices."
            return
        }
        Haptics.action(view)
        onSave(
            Medicine(
                id = existing?.id ?: "med-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
                name = name.trim(),
                category = category,
                subcategory = subcategoryLabel(subcategory),
                official = officialNumber,
                discounted = discountedNumber,
                note = note.trim(),
                description = description.trim(),
                revision = existing?.revision ?: 0,
                favorite = existing?.favorite ?: false,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            ),
        )
    }

    Scaffold(
        topBar = { ScreenTopBar(if (existing == null) "Add medicine" else "Edit medicine", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Field("Medicine / brand", name, { name = it })

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Category", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            TextButton(onClick = {
                                Haptics.action(view)
                                onManageCategories()
                            }) { Text("Manage categories") }
                        }
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            snapshot.categories.filter { it.id != "all" }.forEach { item ->
                                SoftChip(
                                    label = item.arabic,
                                    selected = category == item.id,
                                    accent = colorFromHex(item.color),
                                    onClick = {
                                        Haptics.selection(view)
                                        category = item.id
                                    },
                                )
                            }
                        }
                    }

                    Field("Subcategory", subcategory, { subcategory = it })
                    if (existingSubcategories.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Or select an existing subcategory", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                existingSubcategories.forEach { option ->
                                    SoftChip(
                                        label = option.label,
                                        selected = subcategoryKey(subcategory) == option.key,
                                        onClick = {
                                            Haptics.selection(view)
                                            subcategory = option.label
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        OutlinedTextField(
                            value = official,
                            onValueChange = { official = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Official price") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        OutlinedTextField(
                            value = discounted,
                            onValueChange = { discounted = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Discounted") },
                            placeholder = { Text("Optional") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }

                    Field("Supplied note", note, { note = it }, minLines = 3)
                    Field(
                        "Description",
                        description,
                        { description = it },
                        minLines = 5,
                        placeholder = "Details shown on the medicine page",
                    )

                    Text(
                        "Prices use ${snapshot.currency}. A blank discounted price means no second price was supplied.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Button(
                        enabled = !busy,
                        onClick = ::submit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp),
                    ) {
                        Text(if (busy) "Saving…" else "Save medicine", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    if (existing != null) {
                        Button(
                            enabled = !busy,
                            onClick = {
                                Haptics.action(view)
                                confirmTrash = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Text("Move to Trash", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }

    validationError?.let { error ->
        AlertDialog(
            onDismissRequest = { validationError = null },
            title = { Text("Check the details") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { validationError = null }) { Text("OK") } },
        )
    }

    if (confirmTrash && existing != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmTrash = false },
            title = { Text("Move “${existing.name}” to Trash?") },
            text = { Text("You can restore it later from Settings > Trash.") },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { confirmTrash = false },
                ) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        confirmTrash = false
                        onMoveToTrash(existing)
                    },
                ) {
                    Text(
                        "Move to Trash",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    minLines: Int = 1,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { text -> { Text(text) } },
        minLines = minLines,
        maxLines = if (minLines > 1) minLines + 3 else 1,
        singleLine = minLines == 1,
    )
}

@Composable
fun MedicineDetailScreen(
    snapshot: AppSnapshot,
    medicineId: String,
    busy: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: (Medicine) -> Unit,
    onMoveToTrash: (Medicine) -> Unit,
) {
    val view = LocalView.current
    val item = snapshot.items.firstOrNull { it.id == medicineId }
    var confirmTrash by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { ScreenTopBar("Medicine", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (item == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Medicine not found", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) { Text("Go back") }
            }
        } else {
            val category = categoryById(item.category, snapshot.categories)
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(
                        Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                        ) {
                            Column(
                                Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.08f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colorFromHex(category.color)),
                                    ) {
                                        Text(
                                            category.label,
                                            color = MaterialTheme.colorScheme.onTertiary,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        )
                                    }
                                    TextButton(onClick = {
                                        Haptics.selection(view)
                                        onToggleFavorite(item)
                                    }) {
                                        Text(
                                            if (item.favorite) "★ Favorite" else "☆ Favorite",
                                            color = if (item.favorite) Color(0xFFFFD166) else MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.82f),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }

                                Text(
                                    item.name,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    fontSize = 30.sp,
                                    lineHeight = 40.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    style = TextStyle(textDirection = TextDirection.Content),
                                )
                                if (item.note.isNotBlank()) {
                                    Text(
                                        item.note,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.78f),
                                        textAlign = TextAlign.Center,
                                    )
                                }

                                Surface(
                                    color = Color.Black.copy(alpha = 0.11f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                ) {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            "OFFICIAL PRICE · ${snapshot.currency}",
                                            modifier = Modifier.fillMaxWidth(),
                                            color = Color(0xFFA3CFB9),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center,
                                        )
                                        Text(
                                            formatPrice(item.official),
                                            modifier = Modifier.fillMaxWidth(),
                                            color = Color(0xFFB8F0CB),
                                            fontSize = 58.sp,
                                            fontWeight = FontWeight.Black,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                        )
                                    }
                                }

                                item.discounted?.let { price ->
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(
                                            if (price > item.official) "VERIFY THIS PRICE" else "IF CUSTOMER ASKS",
                                            modifier = Modifier.fillMaxWidth(),
                                            color = if (price > item.official) Color(0xFFFFB09C) else Color(0xFFDFC68C),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                        Text(
                                            "${formatPrice(price)} ${snapshot.currency}",
                                            modifier = Modifier.fillMaxWidth(),
                                            color = Color(0xFFFFE2A2),
                                            fontSize = 27.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }

                        InfoCard("Medicine info") {
                            InfoValue("CATEGORY", category.label, snapshot.largeText)
                            InfoValue("SUBCATEGORY", subcategoryLabel(item.subcategory), snapshot.largeText)
                            InfoValue("DATE ADDED", formatAddedDate(item.createdAt), snapshot.largeText)
                            Text(
                                "Older records may show the date they were imported or migrated.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }

                        if (item.description.isNotBlank()) {
                            InfoCard("Description") {
                                Text(
                                    item.description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp,
                                    lineHeight = 25.sp,
                                    textAlign = TextAlign.Start,
                                    style = TextStyle(textDirection = TextDirection.Content),
                                )
                            }
                        }

                        Button(
                            onClick = {
                                Haptics.action(view)
                                onEdit()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                        ) {
                            Text("✎ Edit medicine", fontWeight = FontWeight.ExtraBold)
                        }
                        Button(
                            enabled = !busy,
                            onClick = {
                                Haptics.action(view)
                                confirmTrash = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Text("Move to Trash", fontWeight = FontWeight.ExtraBold)
                        }
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp),
                        ) { Text("Done", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    if (confirmTrash && item != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmTrash = false },
            title = { Text("Move “${item.name}” to Trash?") },
            text = { Text("You can restore it later from Settings > Trash.") },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { confirmTrash = false },
                ) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        confirmTrash = false
                        onMoveToTrash(item)
                    },
                ) {
                    Text(
                        "Move to Trash",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            content()
        }
    }
}

@Composable
private fun InfoValue(label: String, value: String, large: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = if (large) 21.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
