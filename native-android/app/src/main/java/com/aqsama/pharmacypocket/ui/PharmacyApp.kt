package com.aqsama.pharmacypocket.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import com.aqsama.pharmacypocket.data.ImportedList
import com.aqsama.pharmacypocket.data.ImportedListStore
import com.aqsama.pharmacypocket.data.ImportedField
import com.aqsama.pharmacypocket.data.ImportField
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import org.json.JSONObject

private sealed interface Destination {
    data object Home : Destination
    data object Settings : Destination
    data object Categories : Destination
    data object Trash : Destination
    data object SpreadsheetImport : Destination
    data object SpreadsheetSettings : Destination
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
                Destination.SpreadsheetSettings -> put("screen", "spreadsheetSettings")
                Destination.SpreadsheetImport -> put("screen", "spreadsheetImport")
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
                    "spreadsheetSettings" -> Destination.SpreadsheetSettings
                    "spreadsheetImport" -> Destination.SpreadsheetImport
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

internal fun bumpPhotoVersion(versions: MutableMap<String, Int>, medicineId: String) {
    versions[medicineId] = (versions[medicineId] ?: 0) + 1
}

private val mediaSaver = listSaver<androidx.compose.runtime.snapshots.SnapshotStateMap<String, MedicineMedia>, String>(
    save = { entries -> entries.flatMap { listOf(it.key, it.value.encode()) } },
    restore = { parts -> mutableStateMapOf<String, MedicineMedia>().apply {
        parts.chunked(2).forEach { put(it[0], MedicineMedia.restore(it[1])) }
    } },
)

@Composable
fun PharmacyApp(baseRepository: PharmacyRepository) {
    val context = LocalContext.current
    var selectedListId by rememberSaveable { mutableStateOf<String?>(null) }
    val repositories = remember { mutableMapOf<String?, PharmacyRepository>(null to baseRepository) }
    val repository = remember(selectedListId) { repositories.getOrPut(selectedListId) { baseRepository.forList(selectedListId) } }
    val homeIndexModel: HomeIndexViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "home-index")
    val listStore = remember { ImportedListStore(context.applicationContext) }
    var importedLists by remember { mutableStateOf<List<ImportedList>>(emptyList()) }
    val selectedList = importedLists.firstOrNull { it.id == selectedListId }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val stateHolder = rememberSaveableStateHolder()
    val backStack = rememberSaveable(saver = navSaver) { mutableStateListOf(NavEntry("home", Destination.Home)) }
    val mediaDrafts = rememberSaveable(saver = mediaSaver) { mutableStateMapOf<String, MedicineMedia>() }

    fun discardDraft(entryId: String) {
        mediaDrafts.remove(entryId)?.discard()
    }

    LaunchedEffect(Unit) {
        val retainedPaths = mediaDrafts.values.flatMap { listOfNotNull(it.photoPath, it.cropSource) }.toSet()
        withContext(Dispatchers.IO) {
            cleanupMedicinePhotoDrafts(context, retainedPaths)
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            val scoped = repositories.values.filter { it !== baseRepository }
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { scoped.forEach { it.closeWhenIdle() } }
        }
    }
    var snapshot by remember { mutableStateOf<AppSnapshot?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableStateOf(0) }
    var trashItems by remember { mutableStateOf<List<TrashedMedicine>?>(null) }
    var movingMedicine by remember { mutableStateOf<Medicine?>(null) }
    var mainCurrency by remember { mutableStateOf("IQD") }
    var mainCategories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var quickCaptureId by remember { mutableStateOf<String?>(null) }
    val photoVersions = remember { mutableStateMapOf<String, Int>() }
    val cameraSaveMutex = remember { Mutex() }
    val snackbarHostState = remember { SnackbarHostState() }

    fun push(destination: Destination) {
        Haptics.action(view)
        backStack += NavEntry(UUID.randomUUID().toString(), destination)
    }

    fun pop() {
        if (backStack.size <= 1 || mediaDrafts[backStack.last().id]?.locked == true) return
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
                        val restored = repository.restoreMedicine(item.id)
                        if (selectedListId == repository.listId) snapshot = restored
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

    LaunchedEffect(Unit) {
        try { importedLists = listStore.lists() } catch (error: Exception) { errorMessage = error.message }
    }

    fun selectList(id: String?) {
        if (busy) return
        backStack.forEach { stateHolder.removeState(it.id); discardDraft(it.id) }
        backStack.clear()
        backStack += NavEntry("home-${id ?: "manual"}", Destination.Home)
        quickCaptureId = null
        movingMedicine = null
        trashItems = null
        snapshot = null
        homeIndexModel.clear()
        selectedListId = id
        loadAttempt++
        scope.launch { snackbarHostState.currentSnackbarData?.dismiss(); drawerState.close() }
    }

    LaunchedEffect(repository, loadAttempt) {
        try {
            snapshot = repository.loadSnapshot()
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            errorMessage = error.message ?: "Unable to open local storage."
        }
    }

    BackHandler(enabled = backStack.size > 1) { if (!busy) pop() }

    val current = snapshot
    PharmacyPocketTheme(current?.themePreference ?: ThemePreference.SYSTEM) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = backStack.last().destination == Destination.Home && !busy,
            drawerContent = {
                ModalDrawerSheet {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pharmacy Pocket", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                        Text("Your lists", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                        NavigationDrawerItem(label = { Text("My medications") }, selected = selectedListId == null,
                            onClick = { selectList(null) })
                        importedLists.forEach { list ->
                            NavigationDrawerItem(label = { Text(list.name) }, selected = list.id == selectedListId,
                                onClick = { selectList(list.id) })
                        }
                        NavigationDrawerItem(label = { Text("＋ Import a spreadsheet") }, selected = false, onClick = {
                            if (!busy) { scope.launch { drawerState.close() }; push(Destination.SpreadsheetImport) }
                        })
                        if (selectedList != null) NavigationDrawerItem(label = { Text("Import settings · ${selectedList.name}") }, selected = false, onClick = {
                            if (!busy) { scope.launch { drawerState.close() }; push(Destination.SpreadsheetSettings) }
                        })
                        NavigationDrawerItem(label = { Text("Settings · ${selectedList?.name ?: "My medications"}") }, selected = false, onClick = {
                            if (!busy) { scope.launch { drawerState.close() }; push(Destination.Settings) }
                        })
                    }
                }
            },
        ) {
        Box(Modifier.fillMaxSize()) {
        if (current == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            val entry = backStack.last()
            stateHolder.SaveableStateProvider(entry.id) {
                when (val destination = entry.destination) {
                    Destination.Home -> HomeScreen(
                        snapshot = current,
                        indexModel = homeIndexModel,
                        onLists = { scope.launch { drawerState.open() } },
                        listName = selectedList?.name,
                        imported = selectedListId != null,
                        onSettings = { push(Destination.Settings) },
                        onAddMedicine = { category, initialCapture -> push(Destination.Editor(null, category, initialCapture)) },
                        onOpenMedicine = { push(Destination.Detail(it)) },
                        onEditMedicine = { push(Destination.Editor(it, null)) },
                        onToggleFavorite = { item ->
                            scope.launch {
                                try {
                                    val next = repository.toggleFavorite(item.id)
                                    if (next != null && selectedListId == repository.listId) {
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

                    Destination.SpreadsheetImport, Destination.SpreadsheetSettings -> SpreadsheetImportScreen(
                        busy = busy,
                        onBack = ::pop,
                        modelKey = "import-${entry.id}",
                        editList = if (destination == Destination.SpreadsheetSettings) selectedList else null,
                        loadSource = selectedList?.let { list -> { listStore.loadSource(list) } },
                        onImport = { name, source, mappings, prepared, sourceData, onCreated ->
                            if (!busy) {
                                busy = true
                                scope.launch {
                                    try {
                                        if (destination == Destination.SpreadsheetSettings) {
                                            val list = listStore.update(requireNotNull(selectedList), name, mappings, prepared, sourceData)
                                            importedLists = importedLists.map { if (it.id == list.id) list else it }
                                            snapshot = repository.loadSnapshot()
                                            onCreated(); pop()
                                        } else {
                                            val list = listStore.create(name, source, mappings, prepared, sourceData)
                                            importedLists = importedLists + list
                                            onCreated()
                                            busy = false
                                            selectList(list.id)
                                        }
                                    } catch (error: Exception) {
                                        errorMessage = error.message ?: "Could not create the imported list."
                                    } finally { busy = false }
                                }
                            }
                        },
                    )

                    Destination.Settings -> SettingsScreen(
                        snapshot = current,
                        onBack = { if (!busy) pop() },
                        onManageCategories = { if (!busy) push(Destination.Categories) },
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
                        listName = selectedList?.name,
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

                    is Destination.Editor -> {
                        val media = mediaDrafts.getOrPut(entry.id) {
                            MedicineMedia(current.items.firstOrNull { it.id == destination.medicineId }?.codes.orEmpty())
                        }
                        MedicineEditorScreen(
                            snapshot = current,
                            medicineId = destination.medicineId,
                            initialCategory = destination.category,
                            initialCapture = destination.initialCapture,
                            busy = busy,
                            onBack = { if (!busy) pop() },
                            onManageCategories = { if (!busy && !media.locked) push(Destination.Categories) },
                            loadPhoto = repository::loadPhoto,
                            importedTemplate = selectedList?.mappings?.filter { it.field !in listOf(ImportField.NAME, ImportField.NOTE, ImportField.DESCRIPTION, ImportField.IGNORE) }?.map {
                                ImportedField("column-${it.column}", it.label, if (it.field.categoryLevel == 1) destination.category.orEmpty() else "", it.field, it.onCard, it.priceFormat)
                            },
                            media = media,
                            onSave = { medicine ->
                                runOperation(
                                    successMessage = "Medicine saved",
                                    onSuccess = ::pop,
                                ) {
                                    media.save(repository, medicine).also { bumpPhotoVersion(photoVersions, medicine.id) }
                                }
                            },
                            onMoveToTrash = ::moveToTrash,
                        )
                    }

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
                                    if (next != null && selectedListId == repository.listId) {
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
                        photoVersion = photoVersions[destination.medicineId] ?: 0,
                        onMoveToMain = if (selectedListId != null) { item ->
                            scope.launch {
                                try { val main = baseRepository.loadSnapshot(); mainCategories = main.categories; mainCurrency = main.currency; movingMedicine = item }
                                catch (error: Exception) { errorMessage = error.message ?: "Could not open My medications." }
                            }
                        } else null,
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
                                    owner?.id == id -> acknowledge(MediaSaveResult(true, "Code already added"))
                                    owner != null -> acknowledge(MediaSaveResult(false, "Code belongs to ${owner.name}"))
                                    item.codes.size >= 20 -> acknowledge(MediaSaveResult(false, "A medicine can have up to 20 codes"))
                                    else -> {
                                        snapshot = repository.saveMedicine(item.copy(codes = item.codes + validated))
                                        acknowledge(MediaSaveResult(true, "Saved ${if (validated.kind == com.aqsama.pharmacypocket.data.CodeKind.BARCODE) "barcode" else "QR"}"))
                                    }
                                }
                            } } catch (error: Exception) {
                                acknowledge(MediaSaveResult(false, error.message ?: "Could not save code"))
                            }
                        }
                    },
                    onPhoto = { bytes, acknowledge ->
                        scope.launch {
                            try { cameraSaveMutex.withLock {
                                val item = snapshot?.items?.firstOrNull { it.id == id }
                                    ?: throw IllegalStateException("Medicine unavailable")
                                snapshot = repository.saveMedicine(item, bytes)
                                bumpPhotoVersion(photoVersions, id)
                                acknowledge(MediaSaveResult(true, "Saved photo"))
                            } } catch (error: Exception) {
                                acknowledge(MediaSaveResult(false, error.message ?: "Could not save photo"))
                            }
                        }
                    },
                    onDismiss = { quickCaptureId = null },
                    onOpenMedicine = {
                        quickCaptureId = null
                        push(Destination.Detail(id))
                    },
                )
            }
        }

        movingMedicine?.let { item ->
            MoveToMainSheet(item, mainCategories, mainCurrency, busy, onDismiss = { movingMedicine = null }, onMove = { name, price, category ->
                runOperation(successMessage = "Moved to My medications", onSuccess = {
                    movingMedicine = null
                    removeMedicineDestinations(item.id)
                }) { repository.moveToMain(item.id, baseRepository, name, price, category) }
            })
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
