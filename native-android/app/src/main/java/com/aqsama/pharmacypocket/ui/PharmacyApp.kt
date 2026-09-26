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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.aqsama.pharmacypocket.data.AppSnapshot
import com.aqsama.pharmacypocket.data.Category
import com.aqsama.pharmacypocket.data.ImportMode
import com.aqsama.pharmacypocket.data.Medicine
import com.aqsama.pharmacypocket.data.MedicineCode
import com.aqsama.pharmacypocket.data.validateCodes
import com.aqsama.pharmacypocket.data.ParsedBackup
import com.aqsama.pharmacypocket.data.PharmacyRepository
import com.aqsama.pharmacypocket.data.ThemePreference
import com.aqsama.pharmacypocket.data.TrashedMedicine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.io.File
import org.json.JSONObject

private sealed interface Destination {
    data object Home : Destination
    data object Settings : Destination
    data object Categories : Destination
    data object Trash : Destination
    data class Editor(val medicineId: String?, val category: String?, val initialCapture: String? = null) : Destination
    data class Detail(val medicineId: String) : Destination
}

private data class NavEntry(val id: String, val destination: Destination)

private val navSaver = listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<NavEntry>, String>(
    save = { entries -> entries.map { entry ->
        JSONObject().put("id", entry.id).apply {
            when (val destination = entry.destination) {
                Destination.Home -> put("screen", "home")
                Destination.Settings -> put("screen", "settings")
                Destination.Categories -> put("screen", "categories")
                Destination.Trash -> put("screen", "trash")
                is Destination.Editor -> {
                    put("screen", "editor")
                    put("medicineId", destination.medicineId)
                    put("category", destination.category)
                    put("capture", destination.initialCapture)
                }
                is Destination.Detail -> { put("screen", "detail"); put("medicineId", destination.medicineId) }
            }
        }.toString()
    } },
    restore = { encoded ->
        val entries = encoded.mapNotNull { raw ->
            runCatching {
                val obj = JSONObject(raw)
                val destination = when (obj.getString("screen")) {
                    "home" -> Destination.Home
                    "settings" -> Destination.Settings
                    "categories" -> Destination.Categories
                    "trash" -> Destination.Trash
                    "editor" -> Destination.Editor(obj.optString("medicineId").takeUnless { it.isEmpty() || it == "null" },
                        obj.optString("category").takeUnless { it.isEmpty() || it == "null" },
                        obj.optString("capture").takeUnless { it.isEmpty() || it == "null" })
                    "detail" -> Destination.Detail(obj.getString("medicineId"))
                    else -> throw IllegalArgumentException("Unknown screen")
                }
                NavEntry(obj.getString("id"), destination)
            }.getOrNull()
        }
        androidx.compose.runtime.mutableStateListOf<NavEntry>().apply {
            addAll(if (entries.firstOrNull()?.destination == Destination.Home) entries else listOf(NavEntry("home", Destination.Home)))
        }
    },
)

private val photoDraftSaver = listSaver<androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>, String>(
    save = { drafts -> drafts.entries.flatMap { listOf(it.key, it.value) } },
    restore = { parts -> androidx.compose.runtime.mutableStateMapOf<String, String>().apply {
        parts.chunked(2).forEach { if (it.size == 2) put(it[0], it[1]) }
    } },
)

@Composable
fun PharmacyApp(repository: PharmacyRepository) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val stateHolder = rememberSaveableStateHolder()
    val backStack = rememberSaveable(saver = navSaver) { mutableStateListOf(NavEntry("home", Destination.Home)) }
    val photoDrafts = rememberSaveable(saver = photoDraftSaver) { mutableStateMapOf<String, String>() }

    fun discardDraft(entryId: String) {
        photoDrafts.remove(entryId)?.let { File(it).delete() }
    }
    var snapshot by remember { mutableStateOf<AppSnapshot?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableStateOf(0) }
    var trashItems by remember { mutableStateOf<List<TrashedMedicine>?>(null) }
    var quickCaptureId by remember { mutableStateOf<String?>(null) }
    val photoVersions = remember { mutableStateMapOf<String, Int>() }
    val cameraSaveMutex = remember { Mutex() }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        val retainedPaths = photoDrafts.values.toSet()
        withContext(Dispatchers.IO) {
            val staleBefore = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            File(context.noBackupFilesDir, "medicine_drafts").listFiles()?.forEach { file ->
                if (file.isFile && file.absolutePath !in retainedPaths && file.lastModified() < staleBefore) {
                    file.delete()
                }
            }
        }
    }

    fun push(destination: Destination) {
        Haptics.action(view)
        backStack += NavEntry(UUID.randomUUID().toString(), destination)
    }

    fun pop() {
        if (backStack.size <= 1) return
        Haptics.action(view)
        val removed = backStack.removeAt(backStack.lastIndex)
        discardDraft(removed.id)
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
            .forEach { stateHolder.removeState(it.id); discardDraft(it.id) }
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
                        onQuickCapture = { quickCaptureId = it },
                        loadPhoto = repository::loadPhoto,
                        photoVersions = photoVersions,
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
                        photoPath = photoDrafts[entry.id],
                        onPhotoPath = { path ->
                            if (busy) {
                                path?.let { File(it).delete() }
                            } else {
                                photoDrafts.remove(entry.id)?.takeIf { it != path }?.let { File(it).delete() }
                                if (path != null) photoDrafts[entry.id] = path
                            }
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
            quickCaptureId?.let { id ->
                val medicine = current.items.firstOrNull { it.id == id }
                if (medicine != null) MedicineCameraScreen(
                    title = medicine.name,
                    existingCodes = medicine.codes.mapTo(mutableSetOf()) { it.value },
                    onCode = { code, acknowledge ->
                        scope.launch {
                            try { cameraSaveMutex.withLock {
                                val latest = snapshot ?: throw IllegalStateException("Medicine unavailable")
                                val item = latest.items.firstOrNull { it.id == id }
                                    ?: throw IllegalStateException("Medicine unavailable")
                                val validated = validateCodes(listOf(code)).single()
                                val owner = latest.items.firstOrNull { candidate ->
                                    candidate.codes.any { it.value == validated.value }
                                }
                                when {
                                    owner?.id == id -> acknowledge("Code already added")
                                    owner != null -> acknowledge("Code belongs to ${owner.name}")
                                    item.codes.size >= 20 -> acknowledge("A medicine can have up to 20 codes")
                                    else -> {
                                        snapshot = repository.saveMedicine(item.copy(codes = item.codes + validated))
                                        acknowledge("Saved ${if (validated.kind == com.aqsama.pharmacypocket.data.CodeKind.BARCODE) "barcode" else "QR"}")
                                    }
                                }
                            } } catch (error: Exception) {
                                acknowledge(error.message ?: "Could not save code")
                            }
                        }
                    },
                    onPhoto = { bytes, acknowledge ->
                        scope.launch {
                            try { cameraSaveMutex.withLock {
                                val item = snapshot?.items?.firstOrNull { it.id == id }
                                    ?: throw IllegalStateException("Medicine unavailable")
                                snapshot = repository.saveMedicine(item, bytes)
                                photoVersions[id] = (photoVersions[id] ?: 0) + 1
                                acknowledge("Saved photo")
                            } } catch (error: Exception) {
                                acknowledge(error.message ?: "Could not save photo")
                            }
                        }
                    },
                    onDismiss = { quickCaptureId = null },
                )
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
