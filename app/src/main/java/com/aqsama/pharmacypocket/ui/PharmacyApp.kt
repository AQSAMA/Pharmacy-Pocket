package com.aqsama.pharmacypocket.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.aqsama.pharmacypocket.data.AppSnapshot
import com.aqsama.pharmacypocket.data.Category
import com.aqsama.pharmacypocket.data.ImportMode
import com.aqsama.pharmacypocket.data.Medicine
import com.aqsama.pharmacypocket.data.ParsedBackup
import com.aqsama.pharmacypocket.data.PharmacyRepository
import kotlinx.coroutines.launch
import java.util.UUID

private sealed interface Destination {
    data object Home : Destination
    data object Settings : Destination
    data object Categories : Destination
    data class Editor(val medicineId: String?, val category: String?) : Destination
    data class Detail(val medicineId: String) : Destination
}

private data class NavEntry(val id: String, val destination: Destination)

@Composable
fun PharmacyApp(repository: PharmacyRepository) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val stateHolder = rememberSaveableStateHolder()
    val backStack = remember { mutableStateListOf(NavEntry("home", Destination.Home)) }
    var snapshot by remember { mutableStateOf<AppSnapshot?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun push(destination: Destination) {
        Haptics.action(view)
        backStack += NavEntry(UUID.randomUUID().toString(), destination)
    }

    fun pop() {
        if (backStack.size <= 1) return
        Haptics.action(view)
        val removed = backStack.removeAt(backStack.lastIndex)
        stateHolder.removeState(removed.id)
    }

    fun runOperation(
        successMessage: String? = null,
        onSuccess: () -> Unit = {},
        operation: suspend () -> AppSnapshot,
    ) {
        scope.launch {
            busy = true
            try {
                snapshot = operation()
                Haptics.confirm(view)
                if (successMessage != null) {
                    Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                }
                onSuccess()
            } catch (error: Throwable) {
                Haptics.reject(view)
                errorMessage = error.message ?: "The operation could not be completed."
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(repository) {
        try {
            snapshot = repository.loadSnapshot()
        } catch (error: Throwable) {
            errorMessage = error.message ?: "Unable to open local storage."
        }
    }

    BackHandler(enabled = backStack.size > 1) { pop() }

    val current = snapshot
    Box(Modifier.fillMaxSize()) {
        if (current == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            val entry = backStack.last()
            stateHolder.SaveableStateProvider(entry.id) {
                when (val destination = entry.destination) {
                    Destination.Home -> HomeScreen(
                        snapshot = current,
                        onSettings = { push(Destination.Settings) },
                        onAddMedicine = { category -> push(Destination.Editor(null, category)) },
                        onOpenMedicine = { push(Destination.Detail(it)) },
                        onEditMedicine = { push(Destination.Editor(it, null)) },
                        onToggleFavorite = { item ->
                            scope.launch {
                                try {
                                    val next = repository.toggleFavorite(item.id)
                                    if (next != null) {
                                        snapshot = snapshot?.copy(
                                            items = snapshot!!.items.map { candidate ->
                                                if (candidate.id == item.id) candidate.copy(favorite = next) else candidate
                                            },
                                        )
                                    }
                                } catch (error: Throwable) {
                                    Haptics.reject(view)
                                    errorMessage = error.message ?: "Could not update favorite."
                                }
                            }
                        },
                        onSetLargeText = { value ->
                            runOperation { repository.setLargeText(value) }
                        },
                    )

                    Destination.Settings -> SettingsScreen(
                        snapshot = current,
                        onBack = ::pop,
                        onManageCategories = { push(Destination.Categories) },
                        onSetLargeText = { value ->
                            runOperation { repository.setLargeText(value) }
                        },
                        onSetCurrency = { value ->
                            runOperation("Currency saved") { repository.setCurrency(value) }
                        },
                        onImport = { data, mode ->
                            runOperation("Import complete") { repository.importBackup(data, mode) }
                        },
                    )

                    Destination.Categories -> CategoryManagerScreen(
                        snapshot = current,
                        onBack = ::pop,
                        onSaveCategory = { category ->
                            runOperation("Category saved") { repository.saveCategory(category) }
                        },
                    )

                    is Destination.Editor -> MedicineEditorScreen(
                        snapshot = current,
                        medicineId = destination.medicineId,
                        initialCategory = destination.category,
                        busy = busy,
                        onBack = ::pop,
                        onManageCategories = { push(Destination.Categories) },
                        onSave = { medicine ->
                            runOperation(
                                successMessage = "Medicine saved",
                                onSuccess = ::pop,
                            ) { repository.saveMedicine(medicine) }
                        },
                    )

                    is Destination.Detail -> MedicineDetailScreen(
                        snapshot = current,
                        medicineId = destination.medicineId,
                        onBack = ::pop,
                        onEdit = { push(Destination.Editor(destination.medicineId, null)) },
                        onToggleFavorite = { item ->
                            scope.launch {
                                try {
                                    val next = repository.toggleFavorite(item.id)
                                    if (next != null) {
                                        snapshot = snapshot?.copy(
                                            items = snapshot!!.items.map { candidate ->
                                                if (candidate.id == item.id) candidate.copy(favorite = next) else candidate
                                            },
                                        )
                                    }
                                } catch (error: Throwable) {
                                    Haptics.reject(view)
                                    errorMessage = error.message ?: "Could not update favorite."
                                }
                            }
                        },
                    )
                }
            }
        }

        if (busy) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Pharmacy Pocket") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("OK") }
            },
        )
    }
}
