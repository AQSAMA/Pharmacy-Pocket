package com.aqsama.pharmacypocket.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.aqsama.pharmacypocket.data.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LibraryFilterSheet(
    sort: MedicineSort, filter: CollectionFilter, subcategories: List<SubcategoryOption>,
    selectedSubcategory: String?, compact: Boolean, large: Boolean,
    onSort: (MedicineSort) -> Unit, onFilter: (CollectionFilter) -> Unit,
    onSubcategory: (String?) -> Unit, onCompact: (Boolean) -> Unit,
    onLargeText: (Boolean) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Filters & display", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onReset) { Text("Reset") }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                FilterGroup("Records") {
                    CollectionFilter.entries.forEach { option -> SoftChip(option.label, filter == option) { onFilter(option) } }
                }
                if (subcategories.isNotEmpty()) FilterGroup("Subcategory") {
                    SoftChip("All", selectedSubcategory == null) { onSubcategory(null) }
                    subcategories.forEach { option -> SoftChip(option.label, selectedSubcategory == option.key) { onSubcategory(option.key) } }
                }
                FilterGroup("Sort by") {
                    MedicineSort.entries.forEach { option -> SoftChip(option.label, sort == option) { onSort(option) } }
                }
                Column {
                    DisplaySwitch("Compact cards", "Show more medicines on the screen", compact, onCompact)
                    DisplaySwitch("Large text", "Larger names and prices", large, onLargeText)
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 52.dp)) { Text("Show medicines") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun DisplaySwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
internal fun CollectionOverviewScreen(
    snapshot: AppSnapshot, overview: CollectionOverview, padding: PaddingValues,
    onFilter: (CollectionFilter, String) -> Unit, onFavorites: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(overview.total.toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text("Medicines in your collection", style = MaterialTheme.typography.titleMedium)
                    Text("${overview.categoryCounts.size} ${if (overview.categoryCounts.size == 1) "category" else "categories"} · ${overview.favorites} ${if (overview.favorites == 1) "favorite" else "favorites"}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewStat(overview.favorites, "Favorites", PocketIcon.FAVORITE, Modifier.weight(1f), onFavorites)
                OverviewStat(overview.discounted, "With discount", PocketIcon.COMPARE, Modifier.weight(1f)) { onFilter(CollectionFilter.DISCOUNTED, "all") }
            }
        }
        item {
            Text("Complete your records", style = MaterialTheme.typography.titleLarge)
            Text("Photos and codes make packages easier to identify.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoverageRow("Photos", overview.withPhotos, overview.total, PocketIcon.CAMERA) { onFilter(CollectionFilter.MISSING_PHOTO, "all") }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CoverageRow("Codes", overview.withCodes, overview.total, PocketIcon.CODE) { onFilter(CollectionFilter.MISSING_CODES, "all") }
                }
            }
        }
        if (overview.priceWarnings > 0) item {
            Surface(onClick = { onFilter(CollectionFilter.CHECK_PRICE, "all") }, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${overview.priceWarnings} prices to check", style = MaterialTheme.typography.titleMedium)
                    Text("The discounted price is higher than the official price.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { Text("Categories", style = MaterialTheme.typography.titleLarge) }
        items(snapshot.categories.filter { it.id != "all" }.sortedByDescending { overview.categoryCounts[it.id] ?: 0 }, key = { it.id }) { category ->
            Surface(onClick = { onFilter(CollectionFilter.ALL, category.id) }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(color = colorFromHex(category.color), shape = RoundedCornerShape(50), modifier = Modifier.size(12.dp)) {}
                    Column(Modifier.weight(1f)) {
                        Text(category.label, style = MaterialTheme.typography.titleMedium)
                        Text(category.arabic, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Text((overview.categoryCounts[category.id] ?: 0).toString(), style = MaterialTheme.typography.titleLarge)
                    PocketIcon(PocketIcon.CHEVRON)
                }
            }
        }
    }
}

@Composable
private fun OverviewStat(count: Int, label: String, icon: PocketIcon, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PocketIcon(icon)
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CoverageRow(label: String, complete: Int, total: Int, icon: PocketIcon, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PocketIcon(icon)
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("$complete / $total", style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(progress = { if (total == 0) 0f else complete.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onClick, enabled = complete < total, modifier = Modifier.semantics { contentDescription = "View medicines missing ${label.lowercase()}" }) {
            Text(if (complete == total && total > 0) "All records complete" else "View ${total - complete} missing")
        }
    }
}

/** Each column retains full amounts; horizontal scroll makes three columns readable on phones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MedicineComparisonSheet(items: List<Medicine>, snapshot: AppSnapshot, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Compare medicines", style = MaterialTheme.typography.titleLarge)
            Text("Package details and recorded prices", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BoxWithConstraints(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                val columnWidth = maxOf(144.dp, (maxWidth - 8.dp) / 2) * LocalDensity.current.fontScale.coerceAtLeast(1f)
                Column(Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                    ComparisonRow(items, columnWidth) { item ->
                        Text(item.name, style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content))
                    }
                    ComparisonRow(items, columnWidth) { item -> ComparisonValue("Official · ${snapshot.currency}", formatPrice(item.official)) }
                    ComparisonRow(items, columnWidth) { item -> ComparisonValue("Discounted · ${snapshot.currency}", item.discounted?.let(::formatPrice) ?: "Not recorded") }
                    ComparisonRow(items, columnWidth) { item -> ComparisonValue("Difference · ${snapshot.currency}", item.discounted?.let { formatPrice(item.official - it) } ?: "—") }
                    ComparisonRow(items, columnWidth) { item -> ComparisonValue("Discount", if (matchesCollectionFilter(item, CollectionFilter.CHECK_PRICE)) "Check price" else discountPercent(item)?.let { "$it%" } ?: "—") }
                    ComparisonRow(items, columnWidth) { item -> ComparisonValue("Category", categoryById(item.category, snapshot.categories).label) }
                    ComparisonRow(items, columnWidth) { item -> ComparisonValue("Subcategory", subcategoryLabel(item.subcategory)) }
                    ComparisonRow(items, columnWidth) { item -> ComparisonValue("Package", "${item.codes.size} codes · ${if (item.hasPhoto) "Photo saved" else "No photo"}") }
                    ComparisonRow(items, columnWidth) { item ->
                        OutlinedButton(onClick = { onOpen(item.id) }, modifier = Modifier.fillMaxWidth()) { Text("Open medicine") }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) { Text("Done") }
        }
    }
}

/** A shared row measures its tallest cell so different languages/font scales stay aligned. */
@Composable
private fun ComparisonRow(items: List<Medicine>, columnWidth: Dp, content: @Composable (Medicine) -> Unit) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(columnWidth).fillMaxHeight()) {
                SelectionContainer { Box(Modifier.padding(12.dp)) { content(item) } }
            }
        }
    }
}

@Composable
private fun ComparisonValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content))
    }
}
