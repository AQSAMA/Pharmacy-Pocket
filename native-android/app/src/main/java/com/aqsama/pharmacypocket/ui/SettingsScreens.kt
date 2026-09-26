package com.aqsama.pharmacypocket.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqsama.pharmacypocket.data.AppSnapshot
import com.aqsama.pharmacypocket.data.BackupCodec
import com.aqsama.pharmacypocket.data.Category
import com.aqsama.pharmacypocket.data.ImportMode
import com.aqsama.pharmacypocket.data.ParsedBackup
import com.aqsama.pharmacypocket.data.PharmacyDefaults
import com.aqsama.pharmacypocket.data.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

@Composable
fun SettingsScreen(
    snapshot: AppSnapshot,
    onBack: () -> Unit,
    onManageCategories: () -> Unit,
    onTrash: () -> Unit,
    onSetLargeText: (Boolean) -> Unit,
    onSetCurrency: (String) -> Unit,
    onSetTheme: (ThemePreference) -> Unit,
    onImport: (ParsedBackup, ImportMode) -> Unit,
    exportBackup: suspend () -> String,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var currencyDraft by rememberSaveable(snapshot.currency) { mutableStateOf(snapshot.currency) }
    var pendingImport by remember { mutableStateOf<ParsedBackup?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = exportBackup()
                    withContext(Dispatchers.IO) { writeText(context, uri, json) }
                    Haptics.confirm(view)
                    android.widget.Toast.makeText(context, "Backup exported", android.widget.Toast.LENGTH_SHORT).show()
                } catch (throwable: Throwable) {
                    Haptics.reject(view)
                    error = throwable.message ?: "Backup failed."
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val raw = withContext(Dispatchers.IO) { readText(context, uri) }
                    pendingImport = withContext(Dispatchers.Default) { BackupCodec.parse(raw) }
                    Haptics.confirm(view)
                } catch (throwable: Throwable) {
                    Haptics.reject(view)
                    error = throwable.message ?: "Import failed."
                }
            }
        }
    }

    Scaffold(
        topBar = { ScreenTopBar("Settings", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                snapshot.items.size.toString(),
                                color = MaterialTheme.colorScheme.onTertiary,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                "medicines stored locally",
                                color = MaterialTheme.colorScheme.onTertiary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                "Fast, offline-first, and fully exportable.",
                                color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.78f),
                                fontSize = 13.sp,
                            )
                            Surface(
                                modifier = Modifier.padding(top = 7.dp),
                                shape = RoundedCornerShape(11.dp),
                                color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.08f),
                            ) {
                                Text(
                                    "● Offline ready",
                                    color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.88f),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    SectionLabel("READING")
                    SettingsCard {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Large text", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Increase medicine names and key prices.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                            Switch(
                                checked = snapshot.largeText,
                                onCheckedChange = {
                                    Haptics.selection(view)
                                    onSetLargeText(it)
                                },
                            )
                        }
                    }

                    SectionLabel("APPEARANCE")
                    SettingsCard {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "Theme",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                "Follow Android automatically, or keep Pharmacy Pocket light or dark.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                ThemePreference.entries.forEach { option ->
                                    SoftChip(
                                        label = option.label,
                                        selected = snapshot.themePreference == option,
                                        onClick = {
                                            Haptics.selection(view)
                                            onSetTheme(option)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    SectionLabel("PRICING")
                    SettingsCard {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Currency name", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            OutlinedTextField(
                                value = currencyDraft,
                                onValueChange = { currencyDraft = it.take(24) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("IQD") },
                                singleLine = true,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Leave it blank to use IQD.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                TextButton(onClick = {
                                    Haptics.action(view)
                                    onSetCurrency(currencyDraft)
                                }) {
                                    Text("Save", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }

                    SectionLabel("CATEGORIES")
                    SettingsAction(
                        symbol = "◈",
                        title = "Manage categories",
                        description = "${(snapshot.categories.size - 1).coerceAtLeast(0)} categories · rename, add, and customize colors.",
                        onClick = {
                            Haptics.action(view)
                            onManageCategories()
                        },
                    )

                    SectionLabel("DATA")
                    SettingsCard {
                        Column {
                            SettingsActionRow(
                                symbol = "⇧",
                                title = "Export JSON",
                                description = "Create one portable backup file.",
                                onClick = {
                                    Haptics.action(view)
                                    exportLauncher.launch("pharmacy-pocket-${LocalDate.now()}.json")
                                },
                            )
                            Surface(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxWidth()) {
                                androidx.compose.foundation.layout.Spacer(Modifier.size(1.dp))
                            }
                            SettingsActionRow(
                                symbol = "⇩",
                                title = "Import JSON",
                                description = "Merge with this phone or replace the active collection safely.",
                                onClick = {
                                    Haptics.action(view)
                                    importLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                                },
                            )
                            Surface(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxWidth()) {
                                androidx.compose.foundation.layout.Spacer(Modifier.size(1.dp))
                            }
                            SettingsActionRow(
                                symbol = "♲",
                                title = "Trash",
                                description = "${snapshot.trashCount} ${if (snapshot.trashCount == 1) "medicine" else "medicines"}",
                                onClick = {
                                    Haptics.action(view)
                                    onTrash()
                                },
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("About your data", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "One JSON file contains medicines, category sections, custom category names/colors, order, favorites, descriptions, and currency. Files exported by the original web and Expo apps remain supported.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                            )
                            Text(
                                "Descriptions are reference notes and are not verified clinical guidance.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingImport?.let { data ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import Pharmacy Pocket JSON") },
            text = {
                Text(
                    "${data.medicines.size} medicines · ${data.sections.size} sections\n\n" +
                        "Merge keeps existing medicines and reactivates matching IDs from Trash. " +
                        "Replace makes the active collection match the file: active medicines missing from the file move to Trash instead of being permanently deleted, and existing unrelated Trash items are kept.",
                )
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        pendingImport = null
                        onImport(data, ImportMode.MERGE)
                    }) { Text("Merge") }
                    TextButton(onClick = {
                        pendingImport = null
                        onImport(data, ImportMode.REPLACE)
                    }) { Text("Replace", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Data operation failed") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }
}

private fun readText(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: throw IllegalStateException("Unable to read the selected file.")

private fun writeText(context: Context, uri: Uri, text: String) {
    val stream = context.contentResolver.openOutputStream(uri, "wt")
        ?: throw IllegalStateException("Unable to create the backup file.")
    stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 3.dp, top = 7.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.9.sp,
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        content = content,
    )
}

@Composable
private fun SettingsAction(
    symbol: String,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    SettingsCard {
        SettingsActionRow(symbol, title, description, onClick)
    }
}

@Composable
private fun SettingsActionRow(
    symbol: String,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(symbol, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 26.sp)
    }
}

@Composable
fun CategoryManagerScreen(
    snapshot: AppSnapshot,
    onBack: () -> Unit,
    onSaveCategory: (Category) -> Unit,
) {
    val view = LocalView.current
    var draft by remember { mutableStateOf<Category?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }
    val counts = remember(snapshot.items) {
        snapshot.items.groupingBy { it.category }.eachCount()
    }
    val editable = snapshot.categories.filter { it.id != "all" }

    fun openNew() {
        Haptics.action(view)
        val color = PharmacyDefaults.categoryColors[editable.size % PharmacyDefaults.categoryColors.size]
        draft = Category(
            id = "custom-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
            label = "",
            arabic = "",
            color = color,
        )
    }

    fun saveDraft() {
        val current = draft ?: return
        val label = current.label.trim()
        val chip = current.arabic.trim().ifEmpty { label }
        val color = current.color.trim().lowercase(Locale.ROOT)
        val hex = Regex("^#[0-9a-fA-F]{6}$")
        when {
            label.isEmpty() || label.length > 48 || chip.length > 32 || !hex.matches(color) -> {
                Haptics.reject(view)
                validationError = "Add a name, keep labels reasonably short, and use a color such as #2F856D."
                return
            }
            snapshot.categories.any {
                it.id != current.id && it.label.trim().equals(label, ignoreCase = true)
            } -> {
                Haptics.reject(view)
                validationError = "A category with that name already exists."
                return
            }
        }
        Haptics.confirm(view)
        onSaveCategory(current.copy(label = label, arabic = chip, color = color))
        draft = null
    }

    Scaffold(
        topBar = { ScreenTopBar("Categories", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(
                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Make categories yours", color = MaterialTheme.colorScheme.onTertiary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "Rename existing categories or create new ones. Colors become the card accent and a subtle tint.",
                                color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.80f),
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = ::openNew),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(46.dp),
                                shape = RoundedCornerShape(15.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text("＋", color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Add category", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Create a new medicine group with its own color.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            items(editable.size, key = { editable[it].id }) { index ->
                val category = editable[index]
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .fillMaxWidth()
                        .clickable {
                            Haptics.action(view)
                            draft = category
                        },
                    shape = RoundedCornerShape(18.dp),
                    color = tintCategoryColor(category.color, 0.085f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(width = 5.dp, height = 82.dp),
                            color = colorFromHex(category.color),
                        ) {}
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(category.label, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                                    Text(category.arabic, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
                                }
                                Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)) {
                                    Text(
                                        (counts[category.id] ?: 0).toString(),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 25.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Surface(modifier = Modifier.size(10.dp), shape = RoundedCornerShape(50), color = colorFromHex(category.color)) {}
                                Text(category.color.uppercase(Locale.ROOT), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Why there is no delete button", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Category IDs stay stable when you rename or recolor them, so medicines never lose their category. A safe delete/move flow can be added separately.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
            }
        }
    }

    draft?.let { current ->
        var localLabel by remember(current.id, current.label) { mutableStateOf(current.label) }
        var localArabic by remember(current.id, current.arabic) { mutableStateOf(current.arabic) }
        var localColor by remember(current.id, current.color) { mutableStateOf(current.color) }

        AlertDialog(
            onDismissRequest = { draft = null },
            title = {
                Text(if (snapshot.categories.any { it.id == current.id }) "Edit category" else "New category")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(17.dp),
                        color = tintCategoryColor(localColor, 0.085f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(width = 5.dp, height = 76.dp),
                                color = colorFromHex(localColor),
                            ) {}
                            Column(Modifier.padding(14.dp)) {
                                Text(localLabel.trim().ifEmpty { "Category name" }, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                                Text(localArabic.trim().ifEmpty { localLabel.trim().ifEmpty { "Short label" } }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = localLabel,
                        onValueChange = {
                            localLabel = it.take(48)
                            draft = current.copy(label = localLabel, arabic = localArabic, color = localColor)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Category name") },
                        placeholder = { Text("e.g. Inhalers") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = localArabic,
                        onValueChange = {
                            localArabic = it.take(32)
                            draft = current.copy(label = localLabel, arabic = localArabic, color = localColor)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Short / chip label") },
                        placeholder = { Text("Arabic or English") },
                        singleLine = true,
                    )
                    Text("Color", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        PharmacyDefaults.categoryColors.forEach { color ->
                            Surface(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable {
                                        Haptics.selection(view)
                                        localColor = color
                                        draft = current.copy(label = localLabel, arabic = localArabic, color = localColor)
                                    },
                                shape = RoundedCornerShape(15.dp),
                                color = if (color.equals(localColor, true)) MaterialTheme.colorScheme.surface else Color.Transparent,
                                border = if (color.equals(localColor, true)) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            ) {
                                Surface(
                                    modifier = Modifier.padding(8.dp),
                                    shape = RoundedCornerShape(11.dp),
                                    color = colorFromHex(color),
                                ) {}
                            }
                        }
                    }
                    OutlinedTextField(
                        value = localColor,
                        onValueChange = {
                            localColor = it.take(7)
                            draft = current.copy(label = localLabel, arabic = localArabic, color = localColor)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Hex color") },
                        placeholder = { Text("#2F856D") },
                        singleLine = true,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { draft = null }) { Text("Cancel") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        draft = current.copy(label = localLabel, arabic = localArabic, color = localColor)
                        saveDraft()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                ) { Text("Save category") }
            },
        )
    }

    validationError?.let { message ->
        AlertDialog(
            onDismissRequest = { validationError = null },
            title = { Text("Check the category") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { validationError = null }) { Text("OK") } },
        )
    }
}
