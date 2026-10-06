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
import com.aqsama.pharmacypocket.data.*
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedTextField
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
    data object Backups : Destination
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
                Destination.Backups -> put("screen", "backups")
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
                    "backups" -> Destination.Backups
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
    val homeIndexModel: HomeIndexViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "home-index")
    val listStore = remember { ImportedListStore(context.applicationContext) }
    var importedLists by remember { mutableStateOf<List<ImportedList>>(emptyList()) }
    var catalogLoaded by remember { mutableStateOf(false) }
    val namingPrefs = remember { context.getSharedPreferences("pharmacy-pocket-list-names", android.content.Context.MODE_PRIVATE) }
    var mainName by remember { mutableStateOf(namingPrefs.getString("main", "My medications") ?: "My medications") }
    val allLists = medicationLists(importedLists, mainName)
    val allManual = selectedListId == ALL_MANUAL_LISTS
    fun repositoryFor(id: String?): PharmacyRepository = repositories.getOrPut(id) {
        baseRepository.forList(id, importedLists.firstOrNull { it.id == id }?.imported ?: (id != null))
    }
    val repository = remember(selectedListId, catalogLoaded) {
        if (allManual || !catalogLoaded) baseRepository else repositoryFor(selectedListId)
    }
    val selectedList = importedLists.firstOrNull { it.id == selectedListId }
    var namingList by remember { mutableStateOf<MedicationList?>(null) }
    var creatingList by remember { mutableStateOf(false) }
    var listNameDraft by remember { mutableStateOf("") }
    var addToList by remember { mutableStateOf<Pair<String?, String?>?>(null) }
    var settingsListPicker by remember { mutableStateOf(false) }
    suspend fun selectedSnapshot(): AppSnapshot {
        if (!allManual) return repository.loadSnapshot()
        val manualSnapshots = medicationLists(importedLists, mainName).filter { !it.imported }.map { it to repositoryFor(it.id).loadSnapshot() }
        return aggregateManualSnapshots(manualSnapshots, baseRepository.loadSnapshot())
    }
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
    var mainSnapshot by remember { mutableStateOf<AppSnapshot?>(null) }
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
                val result = operation()
                snapshot = if (allManual) selectedSnapshot() else result
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
        try { importedLists = listStore.lists(); catalogLoaded = true } catch (error: Exception) { errorMessage = error.message }
    }

    fun selectList(id: String?, destination: Destination = Destination.Home) {
        if (busy) return
        backStack.forEach { stateHolder.removeState(it.id); discardDraft(it.id) }
        backStack.clear()
        backStack += NavEntry("home-${id ?: "manual"}", Destination.Home)
        if (destination != Destination.Home) backStack += NavEntry(UUID.randomUUID().toString(), destination)
        quickCaptureId = null
        movingMedicine = null
        trashItems = null
        snapshot = null
        homeIndexModel.clear()
        selectedListId = id
        loadAttempt++
        scope.launch { snackbarHostState.currentSnackbarData?.dismiss(); drawerState.close() }
    }

    LaunchedEffect(repository, loadAttempt, catalogLoaded, importedLists, mainName) {
        if (!catalogLoaded) return@LaunchedEffect
        if (selectedListId != null && !allManual && allLists.none { it.id == selectedListId }) { selectList(null); return@LaunchedEffect }
        try {
            snapshot = selectedSnapshot()
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
                LibraryDrawer(allLists, selectedListId ?: MAIN_LIST_KEY, !busy,
                    onSelect = { selectList(it.takeUnless { key -> key == MAIN_LIST_KEY }) },
                    onCreate = { creatingList = true; listNameDraft = "" },
                    onRename = { namingList = it; listNameDraft = it.name },
                    onSpreadsheet = { scope.launch { drawerState.close() }; push(Destination.SpreadsheetImport) },
                    onImportSettings = { scope.launch { drawerState.close() }; push(Destination.SpreadsheetSettings) },
                    onBackups = { scope.launch { drawerState.close() }; push(Destination.Backups) },
                    onSettings = { scope.launch { drawerState.close() }; if (allManual) settingsListPicker = true else push(Destination.Settings) },
                    importSettingsAvailable = selectedList?.mappings?.isNotEmpty() == true)

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
                        listName = if (allManual) "All manual lists" else selectedList?.name ?: mainName,
                        imported = repository.isImportedList,
                        onSettings = { if (allManual) settingsListPicker = true else push(Destination.Settings) },
                        onAddMedicine = { category, initialCapture ->
                            if (allManual) addToList = category to initialCapture else push(Destination.Editor(null, category, initialCapture))
                        },
                        onOpenMedicine = { id ->
                            if (allManual) { val ref = ListMedicineRef.decode(id); selectList(ref.listKey.takeUnless { it == MAIN_LIST_KEY }, Destination.Detail(ref.medicineId)) }
                            else push(Destination.Detail(id))
                        },
                        onEditMedicine = { id ->
                            if (allManual) { val ref = ListMedicineRef.decode(id); selectList(ref.listKey.takeUnless { it == MAIN_LIST_KEY }, Destination.Editor(ref.medicineId, null)) }
                            else push(Destination.Editor(id, null))
                        },
                        onToggleFavorite = { item ->
                            scope.launch {
                                try {
                                    if (allManual) {
                                        val ref = ListMedicineRef.decode(item.id)
                                        repositoryFor(ref.listKey.takeUnless { it == MAIN_LIST_KEY }).toggleFavorite(ref.medicineId)
                                        if (selectedListId == ALL_MANUAL_LISTS) snapshot = selectedSnapshot()
                                    } else {
                                        val next = repository.toggleFavorite(item.id)
                                        if (next != null && selectedListId == repository.listId) snapshot = snapshot?.copy(
                                            items = snapshot!!.items.map { if (it.id == item.id) it.copy(favorite = next) else it })
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
                        onQuickCapture = { id ->
                            if (allManual) {
                                val ref = ListMedicineRef.decode(id)
                                selectList(ref.listKey.takeUnless { it == MAIN_LIST_KEY })
                                quickCaptureId = ref.medicineId
                            } else quickCaptureId = id
                        },
                        loadPhoto = { id ->
                            if (allManual) { val ref = ListMedicineRef.decode(id); repositoryFor(ref.listKey.takeUnless { it == MAIN_LIST_KEY }).loadPhoto(ref.medicineId) }
                            else repository.loadPhoto(id)
                        },
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

                    Destination.Backups -> ListBackupScreen(
                        modelKey = "backup-${entry.id}", lists = allLists, initialKey = selectedListId?.takeUnless { it == ALL_MANUAL_LISTS } ?: MAIN_LIST_KEY,
                        onBack = ::pop, loadSnapshot = { key -> repositoryFor(key.takeUnless { it == MAIN_LIST_KEY }).loadSnapshot() },
                        exportList = { key, selection -> repositoryFor(key.takeUnless { it == MAIN_LIST_KEY }).exportBackup(selection) },
                        onImport = { entries, destinationKey, mode ->
                            LibraryRestorer(context.applicationContext).restore(entries, destinationKey, mode)
                        },
                        onDataChanged = {
                            importedLists = listStore.lists()
                            mainName = namingPrefs.getString("main", "My medications") ?: "My medications"
                            snapshot = selectedSnapshot()
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
                        exportBackup = { repository.exportBackup() },
                        onBackups = { push(Destination.Backups) },
                        listName = selectedList?.name ?: mainName,
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
                            importedTemplate = selectedList?.takeIf { it.imported }?.mappings?.filter { it.field !in listOf(ImportField.NAME, ImportField.NOTE, ImportField.DESCRIPTION, ImportField.IGNORE) }?.map {
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
                        onMoveToMain = { item ->
                            scope.launch {
                                try { val main = baseRepository.loadSnapshot(); mainSnapshot = main; movingMedicine = item }
                                catch (error: Exception) { errorMessage = error.message ?: "Could not open My medications." }
                            }
                        },
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
            mainSnapshot?.let { main ->
                MoveToMainSheet(item, main, importedLists.filter { it.id != selectedListId }, busy, onDismiss = { movingMedicine = null },
                    onTransfer = { destinationId, removeSource, name, price, category, mergeId ->
                        val destinationRepository = repositoryFor(destinationId)
                        runOperation(successMessage = if (mergeId != null) "Medication merged" else if (removeSource) "Medication moved" else "Medication copied", onSuccess = {
                            movingMedicine = null
                            if (removeSource) removeMedicineDestinations(item.id)
                            if (mergeId != null) bumpPhotoVersion(photoVersions, mergeId)
                        }) { repository.transferMedication(item.id, destinationRepository, removeSource, name, price, category, mergeId) }
                    }, mainAvailable = selectedListId != null, mainName = mainName,
                    loadDestination = { repositoryFor(it).loadSnapshot() })
            }
        }

        if (creatingList || namingList != null) {
            AlertDialog(onDismissRequest = { if (!busy) { creatingList = false; namingList = null } },
                title = { Text(if (creatingList) "New manual list" else "Rename list") },
                text = { OutlinedTextField(listNameDraft, { listNameDraft = it.take(100) }, label = { Text("List name") }, singleLine = true) },
                dismissButton = { TextButton(enabled = !busy, onClick = { creatingList = false; namingList = null }) { Text("Cancel") } },
                confirmButton = { TextButton(enabled = !busy && listNameDraft.isNotBlank(), onClick = {
                    busy = true
                    scope.launch {
                        try {
                            if (creatingList) {
                                val list = listStore.createManual(listNameDraft)
                                importedLists = listStore.lists(); creatingList = false; busy = false; selectList(list.id)
                            } else {
                                val list = requireNotNull(namingList)
                                if (list.id == null) { mainName = listNameDraft.trim(); namingPrefs.edit().putString("main", mainName).apply() }
                                else { listStore.rename(requireNotNull(list.id), listNameDraft); importedLists = listStore.lists() }
                                namingList = null
                            }
                        } catch (error: Exception) { errorMessage = error.message }
                        finally { busy = false }
                    }
                }) { Text(if (creatingList) "Create" else "Save") } })
        }
        if (addToList != null || settingsListPicker) {
            AlertDialog(onDismissRequest = { addToList = null; settingsListPicker = false },
                title = { Text(if (settingsListPicker) "List settings" else "Add medication to") },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                    allLists.filter { settingsListPicker || !it.imported }.forEach { list ->
                        ActionRow(AppSymbol.NOTES, list.name) {
                            val request = addToList
                            val destination = if (settingsListPicker) Destination.Settings else Destination.Editor(null, null, request?.second)
                            addToList = null; settingsListPicker = false; selectList(list.id, destination)
                        }
                    }
                } }, confirmButton = { TextButton(onClick = { addToList = null; settingsListPicker = false }) { Text("Cancel") } })
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
