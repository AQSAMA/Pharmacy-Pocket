package com.aqsama.pharmacypocket.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aqsama.pharmacypocket.data.*

private enum class LibraryTab(val label: String, val icon: PocketIcon) {
    MEDICINES("Medicines", PocketIcon.LIBRARY), FAVORITES("Favorites", PocketIcon.FAVORITE),
    OVERVIEW("Overview", PocketIcon.OVERVIEW),
}

/** The library owns view state; medicine/media persistence stays at the existing app owner. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    snapshot: AppSnapshot,
    onSettings: () -> Unit,
    onAddMedicine: (String, String?) -> Unit,
    onOpenMedicine: (String) -> Unit,
    onEditMedicine: (String) -> Unit,
    onToggleFavorite: (Medicine) -> Unit,
    onSetLargeText: (Boolean) -> Unit,
    onQuickCapture: (String) -> Unit,
    loadPhoto: suspend (String) -> ByteArray?,
    photoVersions: Map<String, Int>,
) {
    val view = LocalView.current
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    var tabName by rememberSaveable { mutableStateOf(LibraryTab.MEDICINES.name) }
    val tab = LibraryTab.valueOf(tabName)
    var category by rememberSaveable { mutableStateOf("all") }
    var subcategory by rememberSaveable { mutableStateOf<String?>(null) }
    var sortName by rememberSaveable { mutableStateOf(MedicineSort.DEFAULT.name) }
    var filterName by rememberSaveable { mutableStateOf(CollectionFilter.ALL.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var compact by rememberSaveable { mutableStateOf(false) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var comparisonMode by rememberSaveable { mutableStateOf(false) }
    var comparisonIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var comparisonOpen by rememberSaveable { mutableStateOf(false) }
    val sort = MedicineSort.valueOf(sortName)
    val collectionFilter = CollectionFilter.valueOf(filterName)
    val overview = remember(snapshot.items) { collectionOverview(snapshot.items) }
    val searchIndex = remember(snapshot.items) { buildSearchIndex(snapshot.items) }
    val sortedIndex = remember(searchIndex, sort) { sortSearchIndex(searchIndex, sort) }
    val subcategories = remember(searchIndex, category) { listSubcategories(searchIndex, category) }
    val categories = remember(snapshot.categories, overview) {
        snapshot.categories.sortedByDescending { if (it.id == "all") Int.MAX_VALUE else overview.categoryCounts[it.id] ?: 0 }
    }
    val filters = MedicineFilters(category, subcategory, tab == LibraryTab.FAVORITES)
    val visible = remember(sortedIndex, filters, query, collectionFilter) {
        filterSortedMedicines(sortedIndex, filters, query).filter { matchesCollectionFilter(it, collectionFilter) }
    }
    val comparison = remember(comparisonIds, snapshot.items) {
        comparisonIds.mapNotNull { id -> snapshot.items.firstOrNull { it.id == id } }
    }
    val filterCount = (if (category != "all") 1 else 0) + (if (subcategory != null) 1 else 0) +
        (if (collectionFilter != CollectionFilter.ALL) 1 else 0) + (if (sort != MedicineSort.DEFAULT) 1 else 0)
    val addCategory = category.takeUnless { it == "all" }
        ?: snapshot.categories.firstOrNull { it.id != "all" }?.id ?: "syrups"

    fun clearFilters() {
        category = "all"
        subcategory = null
        filterName = CollectionFilter.ALL.name
        sortName = MedicineSort.DEFAULT.name
        query = ""
    }
    fun selectCategory(id: String) {
        Haptics.selection(view)
        category = id
        subcategory = null
    }
    fun selectForComparison(id: String) {
        comparisonMode = true
        comparisonIds = ArrayList(toggleComparison(comparisonIds, id))
        Haptics.selection(view)
    }
    fun browseFilter(filter: CollectionFilter, categoryId: String = "all") {
        clearFilters()
        tabName = LibraryTab.MEDICINES.name
        category = categoryId
        filterName = filter.name
    }
    LaunchedEffect(snapshot.categories, category) {
        if (snapshot.categories.none { it.id == category }) { category = "all"; subcategory = null }
    }
    LaunchedEffect(subcategories, subcategory) {
        if (subcategory != null && subcategories.none { it.key == subcategory }) subcategory = null
    }
    LaunchedEffect(snapshot.items) {
        comparisonIds = ArrayList(comparisonIds.filter { id -> snapshot.items.any { it.id == id } })
        if (comparisonIds.size < 2) comparisonOpen = false
    }
    LaunchedEffect(tabName, category, subcategory, query, filterName, sortName) { listState.scrollToItem(0) }
    BackHandler(comparisonMode && !comparisonOpen && !filtersOpen) {
        comparisonMode = false; comparisonIds = arrayListOf()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pharmacy Pocket", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tab.label, style = MaterialTheme.typography.headlineLarge)
                    }
                    if (tab != LibraryTab.OVERVIEW) PocketIconButton(PocketIcon.COMPARE, "Compare medicines") {
                        comparisonMode = !comparisonMode
                        if (!comparisonMode) comparisonIds = arrayListOf()
                    }
                    PocketIconButton(PocketIcon.SETTINGS, "Settings", onSettings)
                }
                if (tab != LibraryTab.OVERVIEW) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            modifier = Modifier.weight(1f), singleLine = true,
                            placeholder = { Text("Name, note or code") },
                            leadingIcon = { PocketIcon(PocketIcon.SEARCH) },
                            trailingIcon = { if (query.isNotEmpty()) PocketIconButton(PocketIcon.CLOSE, "Clear search") { query = "" } },
                            shape = RoundedCornerShape(28.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                        FilledTonalIconButton(onClick = { focus.clearFocus(); filtersOpen = true }, modifier = Modifier.size(52.dp)) {
                            BadgedBox(badge = { if (filterCount > 0) Badge { Text(filterCount.toString()) } }) {
                                PocketIcon(PocketIcon.FILTER, "Filters and display")
                            }
                        }
                    }
                    Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        categories.forEach { item ->
                            SoftChip(
                                "${if (item.id == "all") "All" else item.arabic}  ${if (item.id == "all") overview.total else overview.categoryCounts[item.id] ?: 0}",
                                selected = category == item.id,
                                accent = if (item.id == "all") null else colorFromHex(item.color),
                            ) { selectCategory(item.id) }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (comparisonMode) Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${comparisonIds.size}/3 selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        Button(onClick = { comparisonOpen = true }, enabled = comparison.size >= 2) { Text("Compare") }
                        PocketIconButton(PocketIcon.CLOSE, "Cancel comparison") { comparisonMode = false; comparisonIds = arrayListOf() }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 2.dp) {
                        Row(Modifier.padding(4.dp)) {
                            LibraryTab.entries.forEach { destination ->
                                val selected = tab == destination
                                val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, label = "dock")
                                Surface(onClick = { focus.clearFocus(); tabName = destination.name }, modifier = Modifier.weight(1f).semantics { this.selected = selected; role = Role.Tab }, shape = RoundedCornerShape(28.dp), color = color) {
                                    Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        PocketIcon(destination.icon, destination.label)
                                        // Icons keep the dock usable at narrow widths; selected label is also in the header.
                                    }
                                }
                            }
                        }
                    }
                    FilledTonalIconButton(onClick = { onAddMedicine(addCategory, "photo") }, modifier = Modifier.size(52.dp)) {
                        PocketIcon(PocketIcon.CAMERA, "Scan code or photograph new medicine")
                    }
                    FloatingActionButton(onClick = { onAddMedicine(addCategory, null) }, shape = RoundedCornerShape(24.dp)) {
                        PocketIcon(PocketIcon.ADD, "Add medicine")
                    }
                }
            }
        },
    ) { padding ->
        if (tab == LibraryTab.OVERVIEW) {
            CollectionOverviewScreen(snapshot, overview, padding, onFilter = ::browseFilter, onFavorites = {
                clearFilters(); tabName = LibraryTab.FAVORITES.name
            })
        } else {
            LazyColumn(
                state = listState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "results") {
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (visible.size == 1) "1 medicine" else "${visible.size} medicines", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            if (filterCount > 0 || query.isNotBlank()) TextButton(onClick = ::clearFilters) { Text("Reset") }
                        }
                        if (comparisonMode) Text("Tap medicines to select up to three. Compare package details and prices.", style = MaterialTheme.typography.bodySmall)
                        if (subcategory != null || collectionFilter != CollectionFilter.ALL) {
                            Text(listOfNotNull(subcategories.firstOrNull { it.key == subcategory }?.label,
                                collectionFilter.label.takeUnless { collectionFilter == CollectionFilter.ALL }).joinToString(" · "),
                                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (visible.isEmpty()) item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        PocketIcon(if (snapshot.items.isEmpty()) PocketIcon.LIBRARY else PocketIcon.SEARCH, modifier = Modifier.size(48.dp))
                        Text(if (snapshot.items.isEmpty()) "Add your first medicine" else if (tab == LibraryTab.FAVORITES && overview.favorites == 0) "No favorites yet" else "No matching medicines", style = MaterialTheme.typography.titleLarge)
                        Text(if (snapshot.items.isEmpty()) "Add a medicine or import your existing JSON backup in Settings."
                            else if (tab == LibraryTab.FAVORITES && overview.favorites == 0) "Tap the heart on a medicine to keep it here."
                            else "Change the search or reset the filters.", style = MaterialTheme.typography.bodyMedium)
                        if (snapshot.items.isEmpty()) Button(onClick = { onAddMedicine(addCategory, null) }) { Text("Add medicine") }
                        else if (filterCount > 0 || query.isNotBlank()) OutlinedButton(onClick = ::clearFilters) { Text("Reset filters") }
                    }
                }
                items(visible, key = { it.id }, contentType = { "medicine" }) { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!compact) Text("${categoryById(item.category, snapshot.categories).arabic} / ${subcategoryLabel(item.subcategory)}",
                            modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MedicineCard(
                            item, categoryById(item.category, snapshot.categories), snapshot.largeText, snapshot.currency,
                            first = true, last = true,
                            onOpen = { if (comparisonMode) selectForComparison(item.id) else onOpenMedicine(item.id) },
                            onEdit = { onEditMedicine(item.id) }, onFavorite = { onToggleFavorite(item) },
                            onCamera = { onQuickCapture(item.id) }, loadPhoto = loadPhoto,
                            photoVersion = photoVersions[item.id] ?: 0, onCompare = { selectForComparison(item.id) },
                            comparisonSelected = item.id in comparisonIds, compact = compact,
                        )
                    }
                }
            }
        }
    }
    if (filtersOpen) LibraryFilterSheet(
        sort, collectionFilter, subcategories, subcategory, compact, snapshot.largeText,
        onSort = { sortName = it.name }, onFilter = { filterName = it.name },
        onSubcategory = { subcategory = it }, onCompact = { compact = it },
        onLargeText = onSetLargeText, onReset = ::clearFilters, onDismiss = { filtersOpen = false },
    )
    if (comparisonOpen && comparison.size >= 2) MedicineComparisonSheet(
        comparison, snapshot, onDismiss = { comparisonOpen = false }, onOpen = { id -> comparisonOpen = false; onOpenMedicine(id) },
    )
}
