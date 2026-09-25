package com.aqsama.pharmacypocket.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqsama.pharmacypocket.data.AppSnapshot
import com.aqsama.pharmacypocket.data.TrashedMedicine
import com.aqsama.pharmacypocket.data.filterTrash
import com.aqsama.pharmacypocket.data.formatDeletedDate
import com.aqsama.pharmacypocket.data.formatPrice
import com.aqsama.pharmacypocket.data.subcategoryLabel

@Composable
fun TrashScreen(
    snapshot: AppSnapshot,
    trashItems: List<TrashedMedicine>?,
    busy: Boolean,
    onBack: () -> Unit,
    onRestore: (TrashedMedicine) -> Unit,
    onDeleteForever: (TrashedMedicine) -> Unit,
    onRestoreAll: () -> Unit,
    onEmptyTrash: () -> Unit,
) {
    val view = LocalView.current
    var query by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<TrashedMedicine?>(null) }
    var confirmRestoreAll by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }

    val filtered = remember(trashItems, query) {
        filterTrash(trashItems.orEmpty(), query)
    }

    Scaffold(
        topBar = { ScreenTopBar("Trash", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (trashItems == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Column(
                        Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "${trashItems.size} ${if (trashItems.size == 1) "medicine" else "medicines"} in Trash",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text(
                                    "Items stay here until you restore them or explicitly delete them forever.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                )
                            }
                        }

                        if (trashItems.isNotEmpty()) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Search Trash") },
                                placeholder = { Text("Name, note, description, or subcategory") },
                                singleLine = true,
                            )

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        Haptics.action(view)
                                        confirmRestoreAll = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp),
                                ) {
                                    Text("Restore All", fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        Haptics.action(view)
                                        confirmEmpty = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    ),
                                ) {
                                    Text("Empty Trash", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (trashItems.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .padding(horizontal = 18.dp, vertical = 28.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(
                                Modifier.padding(22.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("Trash is empty", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                Text(
                                    "Medicines moved to Trash will appear here and can be restored later.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else if (filtered.isEmpty()) {
                    item {
                        Text(
                            "No trashed medicines match your search.",
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(filtered, key = { it.medicine.id }) { trashed ->
                        val item = trashed.medicine
                        val categoryLabel = snapshot.categories
                            .firstOrNull { it.id == item.category }
                            ?.label
                            ?: item.category

                        Surface(
                            modifier = Modifier
                                .padding(horizontal = 18.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            ),
                        ) {
                            Column(
                                Modifier.padding(15.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    item.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = if (snapshot.largeText) 22.sp else 18.sp,
                                    lineHeight = if (snapshot.largeText) 30.sp else 25.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    style = TextStyle(textDirection = TextDirection.Content),
                                )
                                Text(
                                    "${categoryLabel} / ${subcategoryLabel(item.subcategory)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    style = TextStyle(textDirection = TextDirection.Content),
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "${formatPrice(item.official)} ${snapshot.currency}",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "Deleted ${formatDeletedDate(trashed.deletedAt)}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                    )
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Button(
                                        enabled = !busy,
                                        onClick = {
                                            Haptics.action(view)
                                            onRestore(trashed)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp),
                                    ) {
                                        Text("Restore", fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(
                                        enabled = !busy,
                                        onClick = {
                                            Haptics.action(view)
                                            pendingDelete = trashed
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp),
                                    ) {
                                        Text(
                                            "Delete forever",
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 12.dp))
                }
            }
        }
    }

    pendingDelete?.let { trashed ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingDelete = null },
            title = { Text("Permanently delete “${trashed.medicine.name}”?") },
            text = { Text("This cannot be undone.") },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { pendingDelete = null },
                ) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingDelete = null
                        onDeleteForever(trashed)
                    },
                ) {
                    Text(
                        "Delete forever",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmEmpty = false },
            title = { Text("Permanently delete all ${trashItems?.size ?: 0} medicines in Trash?") },
            text = { Text("This cannot be undone.") },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { confirmEmpty = false },
                ) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        confirmEmpty = false
                        onEmptyTrash()
                    },
                ) {
                    Text(
                        "Empty Trash",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )
    }

    if (confirmRestoreAll) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmRestoreAll = false },
            title = { Text("Restore all ${trashItems?.size ?: 0} medicines?") },
            text = { Text("All items in Trash will become active again.") },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { confirmRestoreAll = false },
                ) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        confirmRestoreAll = false
                        onRestoreAll()
                    },
                ) { Text("Restore All", fontWeight = FontWeight.Bold) }
            },
        )
    }
}
