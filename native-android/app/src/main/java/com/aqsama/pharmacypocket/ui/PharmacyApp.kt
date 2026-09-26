package com.aqsama.pharmacypocket.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.aqsama.pharmacypocket.data.ThemePreference
import com.aqsama.pharmacypocket.data.TrashedMedicine
import kotlinx.coroutines.launch
import java.util.UUID

private sealed interface Destination {
    data object Home : Destination
    data object Settings : Destination
    data object Categories : Destination
    data object Trash : Destination
    data class Editor(val medicineId: String?, val category: String?, val initialCapture: String? = null) : Destination
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
    val photoDrafts = remember { mutableStateMapOf<String, ByteArray>() }
    var snapshot by remember { mutableStateOf<AppSnapshot?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableStateOf(0) }
    var trashItems by remember { mutableStateOf<List<TrashedMedicine>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun push(destination: Destination) {
        Haptics.action(view)
        backStack += NavEntry(UUID.randomUUID().toString(), destination)
    }

    fun pop() {
        if (backStack.size <= 1) return
        Haptics.action(view)
        val removed = backStack.removeAt(backStack.lastIndex)
        photoDrafts.remove(removed.id)
        stateHolder.removeState(removed.id)
    }

    fun removeMedicineDestinations(medicineId: String) {
        val retained = backStack.filterNot { entry ->
            when (val destination = entry.destination) {
                is Destination.Detail -> destination.medicineId == medicineId
                is Destination.Editor -> destination.medicineId == medicineId
                else -> false
            }
        }
        backStack
            .filterNot { it in retained }
            .forEach { stateHolder.removeState(it.id); photoDrafts.remove(it.id) }
        backStack.clear()
        if (retained.isEmpty()) {
            backStack += NavEntry("home", Destination.Home)
        } else {
            backStack.addAll(retained)
        }
    }

    fun moveToTrash(item: Medicine) {
        if (busy) return
        busy = true
        scope.launch {
            var moved = false
            try {
                snapshot = repository.moveMedicineToTrash(item.id)
                removeMedicineDestinations(item.id)
                Haptics.confirm(view)
                moved = true
            } catch (error: Throwable) {
                Haptics.reject(view)
                errorMessage = error.message ?: "Could not move the medicine to Trash."
            } finally {
                busy = false
            }

            if (moved) {
                val result = snackbarHostState.showSnackbar(
                    message = "Moved to Trash",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    try {
                        snapshot = repository.restoreMedicine(item.id)
                        Haptics.confirm(view)
                    } catch (error: Throwable) {
                        Haptics.reject(view)
                        errorMessage = error.message ?: "Undo failed."
                    }
                }
            }
        }
    }

    fun runTrashOnlyOperation(
        onSuccess: () -> Unit = {},
        operation: suspend () -> Int,
    ) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val nextTrashCount = operation()
                snapshot = snapshot?.copy(trashCount = nextTrashCount)
                Haptics.confirm(view)
                onSuccess()
            } catch (error: Throwable) {
                Haptics.reject(view)
                errorMessage = error.message ?: "The Trash operation could not be completed."
            } finally {
                busy = false
            }
        }
    }

    fun runOperation(
        successMessage: String? = null,
        onSuccess: () -> Unit = {},
        operation: suspend () -> AppSnapshot,
    ) {
        if (busy) return
        busy = true
        scope.launch {
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

    LaunchedEffect(repository, loadAttempt) {
        try {
            snapshot = repository.loadSnapshot()
        } catch (error: Throwable) {
            errorMessage = error.message ?: "Unable to open local storage."
        }
    }

    BackHandler(enabled = backStack.size > 1) { pop() }

    val current = snapshot
    PharmacyPocketTheme(current?.themePreference ?: ThemePreference.SYSTEM) {
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
                        onAddMedicine = { category, initialCapture -> push(Destination.Editor(null, category, initialCapture)) },
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
                        onTrash = {
                            trashItems = null
                            push(Destination.Trash)
                        },
                        onSetLargeText = { value ->
                            runOperation { repository.setLargeText(value) }
                        },
                        onSetCurrency = { value ->
                            runOperation("Currency saved") { repository.setCurrency(value) }
                        },
                        onSetTheme = { value ->
                            runOperation { repository.setThemePreference(value) }
                        },
                        onImport = { data, mode ->
                            runOperation("Import complete") { repository.importBackup(data, mode) }
                        },
                        exportBackup = repository::exportBackup,
                    )

                    Destination.Categories -> CategoryManagerScreen(
                        snapshot = current,
                        onBack = ::pop,
                        onSaveCategory = { category ->
                            runOperation("Category saved") { repository.saveCategory(category) }
                        },
                    )

                    Destination.Trash -> {
                        LaunchedEffect(entry.id, current.trashCount) {
                            try {
                                trashItems = repository.loadTrash()
                            } catch (error: Throwable) {
                                trashItems = emptyList()
                                Haptics.reject(view)
                                errorMessage = error.message ?: "Could not load Trash."
                            }
                        }
                        TrashScreen(
                            snapshot = current,
                            trashItems = trashItems,
                            busy = busy,
                            onBack = ::pop,
                            onRestore = { trashed ->
                                runOperation { repository.restoreMedicine(trashed.medicine.id) }
                            },
                            onDeleteForever = { trashed ->
                                runTrashOnlyOperation(
                                    onSuccess = {
                                        trashItems = trashItems?.filterNot {
                                            it.medicine.id == trashed.medicine.id
                                        }
                                    },
                                ) { repository.permanentlyDeleteMedicine(trashed.medicine.id) }
                            },
                            onRestoreAll = {
                                runOperation { repository.restoreAllTrash() }
                            },
                            onEmptyTrash = {
                                runTrashOnlyOperation(
                                    onSuccess = { trashItems = emptyList() },
                                ) { repository.emptyTrash() }
                            },
                        )
                    }

                    is Destination.Editor -> MedicineEditorScreen(
                        snapshot = current,
                        medicineId = destination.medicineId,
                        initialCategory = destination.category,
                        initialCapture = destination.initialCapture,
                        busy = busy,
                        onBack = ::pop,
                        onManageCategories = { push(Destination.Categories) },
                        loadPhoto = repository::loadPhoto,
                        photoBytes = photoDrafts[entry.id],
                        onPhotoBytes = { bytes ->
                            if (bytes == null) photoDrafts.remove(entry.id) else photoDrafts[entry.id] = bytes
                        },
                        onSave = { medicine, photo, removePhoto ->
                            runOperation(
                                successMessage = "Medicine saved",
                                onSuccess = ::pop,
                            ) { repository.saveMedicine(medicine, photo, removePhoto) }
                        },
                        onMoveToTrash = ::moveToTrash,
                    )

                    is Destination.Detail -> MedicineDetailScreen(
                        snapshot = current,
                        medicineId = destination.medicineId,
                        busy = busy,
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
                        onMoveToTrash = ::moveToTrash,
                        loadPhoto = repository::loadPhoto,
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    errorMessage?.let { message ->
        val initialLoadFailed = snapshot == null
        AlertDialog(
            onDismissRequest = {
                if (!initialLoadFailed) errorMessage = null
            },
            title = { Text("Pharmacy Pocket") },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        errorMessage = null
                        if (initialLoadFailed) loadAttempt += 1
                    },
                ) {
                    Text(if (initialLoadFailed) "Retry" else "OK")
                }
            },
        )
    }
    }
}
