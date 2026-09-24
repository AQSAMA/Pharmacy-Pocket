package com.aqsama.pharmacypocket.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqsama.pharmacypocket.data.AppSnapshot
import com.aqsama.pharmacypocket.data.Medicine
import com.aqsama.pharmacypocket.data.MedicineFilters
import com.aqsama.pharmacypocket.data.MedicineSort
import com.aqsama.pharmacypocket.data.buildSearchIndex
import com.aqsama.pharmacypocket.data.categoryById
import com.aqsama.pharmacypocket.data.filterSortedMedicines
import com.aqsama.pharmacypocket.data.listSubcategories
import com.aqsama.pharmacypocket.data.sortSearchIndex
import com.aqsama.pharmacypocket.data.subcategoryKey
import com.aqsama.pharmacypocket.data.subcategoryLabel

private data class MedicineSection(
    val key: String,
    val groupKey: String,
    val title: String,
    val category: String,
    val data: List<Medicine>,
)

private sealed interface HomeRow {
    val key: String
    data class Header(val section: MedicineSection) : HomeRow {
        override val key = "header:${section.key}"
    }
    data class Item(val item: Medicine, val first: Boolean, val last: Boolean) : HomeRow {
        override val key = "medicine:${item.id}"
    }
}

private fun buildRows(items: List<Medicine>): List<HomeRow> {
    val sections = mutableListOf<MedicineSection>()
    items.forEach { item ->
        val key = subcategoryKey(item.subcategory)
        val current = sections.lastOrNull()
        if (current != null && current.category == item.category && current.groupKey == key) {
            sections[sections.lastIndex] = current.copy(data = current.data + item)
        } else {
            sections += MedicineSection(
                key = "run:${sections.size}:${item.category}:$key",
                groupKey = key,
                title = subcategoryLabel(item.subcategory),
                category = item.category,
                data = listOf(item),
            )
        }
    }
    return buildList {
        sections.forEach { section ->
            add(HomeRow.Header(section))
            section.data.forEachIndexed { index, item ->
                add(HomeRow.Item(item, first = index == 0, last = index == section.data.lastIndex))
            }
        }
    }
}

@Composable
fun HomeScreen(
    snapshot: AppSnapshot,
    onSettings: () -> Unit,
    onAddMedicine: (String) -> Unit,
    onOpenMedicine: (String) -> Unit,
    onEditMedicine: (String) -> Unit,
    onToggleFavorite: (Medicine) -> Unit,
    onSetLargeText: (Boolean) -> Unit,
) {
    val view = LocalView.current
    val listState = rememberLazyListState()
    var category by rememberSaveable { mutableStateOf("all") }
    var selectedSubcategory by rememberSaveable { mutableStateOf<String?>(null) }
    var sortName by rememberSaveable { mutableStateOf(MedicineSort.DEFAULT.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var controlsOpen by rememberSaveable { mutableStateOf(false) }
    val sort = runCatching { MedicineSort.valueOf(sortName) }.getOrDefault(MedicineSort.DEFAULT)

    val searchIndex = remember(snapshot.items) { buildSearchIndex(snapshot.items) }
    val sortedIndex = remember(searchIndex, sort) { sortSearchIndex(searchIndex, sort) }
    val subcategories = remember(searchIndex, category) { listSubcategories(searchIndex, category) }

    LaunchedEffect(snapshot.categories, category) {
        if (category != "all" && snapshot.categories.none { it.id == category }) {
            category = "all"
            selectedSubcategory = null
        }
    }
    LaunchedEffect(subcategories, selectedSubcategory) {
        if (selectedSubcategory != null && subcategories.none { it.key == selectedSubcategory }) {
            selectedSubcategory = null
        }
    }

    val filters = remember(category, selectedSubcategory, favoritesOnly) {
        MedicineFilters(category, selectedSubcategory, favoritesOnly)
    }
    val visible = remember(sortedIndex, filters, query) {
        filterSortedMedicines(sortedIndex, filters, query)
    }
    val rows = remember(visible) { buildRows(visible) }
    val categoryCounts = remember(snapshot.items) {
        buildMap {
            put("all", snapshot.items.size)
            snapshot.items.forEach { item ->
                if (item.category != "all") put(item.category, (get(item.category) ?: 0) + 1)
            }
        }
    }
    val favoriteCount = remember(snapshot.items) { snapshot.items.count { it.favorite } }
    val activeCount = (if (favoritesOnly) 1 else 0) +
        (if (sort != MedicineSort.DEFAULT) 1 else 0) +
        (if (category != "all") 1 else 0) +
        (if (selectedSubcategory != null) 1 else 0) +
        (if (query.isNotBlank()) 1 else 0)

    val filterKey = "$category|$selectedSubcategory|${sort.name}|$query|$favoritesOnly"
    var lastFilterKey by rememberSaveable { mutableStateOf(filterKey) }
    LaunchedEffect(filterKey) {
        if (filterKey != lastFilterKey) {
            lastFilterKey = filterKey
            if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
        }
    }

    fun selectCategory(next: String) {
        Haptics.selection(view)
        category = next
        selectedSubcategory = null
    }

    fun stepCategory(direction: Int) {
        Haptics.selection(view)
        val current = snapshot.categories.indexOfFirst { it.id == category }.coerceAtLeast(0)
        val next = (current + direction + snapshot.categories.size) % snapshot.categories.size
        category = snapshot.categories[next].id
        selectedSubcategory = null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        onClick = {
                            Haptics.action(view)
                            onSettings()
                        },
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(17.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("⚙", fontSize = 21.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp),
                        placeholder = { Text("Search medicines…") },
                        leadingIcon = { Text("⌕", fontSize = 22.sp) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                TextButton(onClick = {
                                    Haptics.action(view)
                                    query = ""
                                }) { Text("×", fontSize = 22.sp) }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(17.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {}),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF7DA997),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                        ),
                    )
                }
            }
        },
        bottomBar = {
            HomeBottomBar(
                snapshot = snapshot,
                category = category,
                categoryCounts = categoryCounts,
                subcategories = subcategories.map { it.key to it.label },
                selectedSubcategory = selectedSubcategory,
                onSelectCategory = ::selectCategory,
                onSelectSubcategory = {
                    Haptics.selection(view)
                    selectedSubcategory = it
                },
                onPrevious = { stepCategory(-1) },
                onNext = { stepCategory(1) },
                onAdd = {
                    Haptics.action(view)
                    onAddMedicine(if (category == "all") snapshot.categories.getOrNull(1)?.id ?: "syrups" else category)
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item(key = "overview") {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (visible.size == snapshot.items.size) "${visible.size} medicines"
                                else "${visible.size} of ${snapshot.items.size} medicines",
                                color = Color(0xFF24443A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                            )
                            Text(
                                if (category == "all") "All categories" else categoryById(category, snapshot.categories).label,
                                color = Color(0xFF7A8A84),
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                        SoftChip(
                            label = "★ $favoriteCount",
                            selected = favoritesOnly,
                            onClick = {
                                Haptics.selection(view)
                                favoritesOnly = !favoritesOnly
                            },
                        )
                        SoftChip(
                            label = "Tune" + if (activeCount > 0) " · $activeCount" else "",
                            selected = controlsOpen,
                            onClick = {
                                Haptics.action(view)
                                controlsOpen = !controlsOpen
                            },
                        )
                    }
                    if (controlsOpen) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            SoftChip(
                                label = if (snapshot.largeText) "T Large ✓" else "T Large",
                                selected = snapshot.largeText,
                                onClick = {
                                    Haptics.selection(view)
                                    onSetLargeText(!snapshot.largeText)
                                },
                            )
                            MedicineSort.entries.forEach { option ->
                                SoftChip(
                                    label = option.label,
                                    selected = sort == option,
                                    onClick = {
                                        Haptics.selection(view)
                                        sortName = option.name
                                    },
                                )
                            }
                            if (activeCount > 0) {
                                SoftChip("Clear view") {
                                    Haptics.action(view)
                                    query = ""
                                    category = "all"
                                    selectedSubcategory = null
                                    favoritesOnly = false
                                    sortName = MedicineSort.DEFAULT.name
                                }
                            }
                        }
                    }
                }
            }

            if (rows.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(if (snapshot.items.isEmpty()) "＋" else "⌕", fontSize = 30.sp)
                        Text(
                            if (snapshot.items.isEmpty()) "Your pocket is empty" else "No medicines found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF24443A),
                        )
                        Text(
                            if (snapshot.items.isEmpty()) "Import your JSON from Settings, or add your first medicine."
                            else "Try a shorter name or another category.",
                            color = Color(0xFF71827A),
                        )
                    }
                }
            } else {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is HomeRow.Header -> SectionBreadcrumb(row.section, snapshot)
                        is HomeRow.Item -> Box(Modifier.padding(horizontal = 16.dp)) {
                            MedicineCard(
                                item = row.item,
                                category = categoryById(row.item.category, snapshot.categories),
                                large = snapshot.largeText,
                                currency = snapshot.currency,
                                first = row.first,
                                last = row.last,
                                onOpen = {
                                    Haptics.action(view)
                                    onOpenMedicine(row.item.id)
                                },
                                onEdit = {
                                    Haptics.action(view)
                                    onEditMedicine(row.item.id)
                                },
                                onFavorite = {
                                    Haptics.selection(view)
                                    onToggleFavorite(row.item)
                                },
                            )
                        }
                    }
                }
                item(key = "bottom-spacer") { Spacer(Modifier.size(16.dp)) }
            }
        }
    }
}

@Composable
private fun SectionBreadcrumb(section: MedicineSection, snapshot: AppSnapshot) {
    val selected = categoryById(section.category, snapshot.categories)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BreadcrumbChip(selected.arabic, border = colorFromHex(selected.color))
            Text("/", color = Color(0xFF91A099), fontWeight = FontWeight.Bold)
            BreadcrumbChip(section.title, modifier = Modifier.widthIn(max = 190.dp))
            Text("/", color = Color(0xFF91A099), fontWeight = FontWeight.Bold)
            BreadcrumbChip(section.data.size.toString(), background = Color(0xFFEAF3EE))
        }
    }
}

@Composable
private fun BreadcrumbChip(
    text: String,
    modifier: Modifier = Modifier,
    border: Color = MaterialTheme.colorScheme.outline,
    background: Color = Color.White,
) {
    Surface(
        modifier = modifier.heightIn(min = 30.dp),
        shape = RoundedCornerShape(9.dp),
        color = background,
        border = BorderStroke(1.dp, border),
    ) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), contentAlignment = Alignment.CenterStart) {
            Text(text, color = Color(0xFF24443A), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun HomeBottomBar(
    snapshot: AppSnapshot,
    category: String,
    categoryCounts: Map<String, Int>,
    subcategories: List<Pair<String, String>>,
    selectedSubcategory: String?,
    onSelectCategory: (String) -> Unit,
    onSelectSubcategory: (String?) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAdd: () -> Unit,
) {
    Surface(
        color = Color.White,
        shadowElevation = 7.dp,
        border = BorderStroke(1.dp, Color(0xFFE0E8E4)),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                snapshot.categories.forEach { item ->
                    SoftChip(
                        label = "${item.arabic}  ${categoryCounts[item.id] ?: 0}",
                        selected = category == item.id,
                        accent = if (item.id == "all") null else colorFromHex(item.color),
                        onClick = { onSelectCategory(item.id) },
                    )
                }
            }
            if (subcategories.isNotEmpty()) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SoftChip(
                        label = "All subcategories",
                        selected = selectedSubcategory == null,
                        onClick = { onSelectSubcategory(null) },
                    )
                    subcategories.forEach { (key, label) ->
                        SoftChip(
                            label = label,
                            selected = selectedSubcategory == key,
                            onClick = { onSelectSubcategory(key) },
                        )
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                TextButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                    Text("‹", fontSize = 26.sp)
                }
                Button(
                    onClick = onAdd,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDCEFE1),
                        contentColor = Color(0xFF175D3F),
                    ),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Text("＋ Add medicine", fontWeight = FontWeight.ExtraBold)
                }
                TextButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Text("›", fontSize = 26.sp)
                }
            }
        }
    }
}
