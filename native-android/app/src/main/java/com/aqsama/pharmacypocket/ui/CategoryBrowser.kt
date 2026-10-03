package com.aqsama.pharmacypocket.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aqsama.pharmacypocket.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryBrowser(browser: LibraryBrowser, path: List<String>, view: CategoryView, onSelect: (List<String>) -> Unit) {
    var explorer by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val children = browser.children(path)
    @Composable fun trail() {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SoftChip("All", selected = path.isEmpty()) { onSelect(emptyList()) }
            path.forEachIndexed { index, key ->
                SoftChip(browser.label(key), selected = index == path.lastIndex) { onSelect(path.take(index + 1)) }
            }
        }
    }
    @Composable fun folder(node: BrowseFolder, modifier: Modifier = Modifier) {
        OutlinedButton(onClick = { onSelect(node.path) }, modifier = modifier, shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(12.dp)) {
            Column {
                Text(node.label, style = MaterialTheme.typography.labelLarge)
                Text(if (node.count == 1) "1 medicine" else "${node.count} medicines", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    @Composable fun tree(modifier: Modifier) {
        val visible = browser.folders.filter { node -> node.path.dropLast(1).all { it in expanded || it in path } }
        LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(visible, key = { it.key }) { node ->
                Row(Modifier.fillMaxWidth().padding(start = ((node.path.size - 1).coerceAtMost(5) * 12).dp)) {
                    val hasChildren = browser.children(node.path).isNotEmpty()
                    TextButton(onClick = { expanded = if (node.key in expanded) expanded - node.key else expanded + node.key }, enabled = hasChildren,
                        modifier = Modifier.width(48.dp), contentPadding = PaddingValues(0.dp)) { Text(if (node.key in expanded || node.key in path) "−" else "+") }
                    FilterChip(selected = path == node.path, onClick = { onSelect(node.path) }, modifier = Modifier.weight(1f), label = { Text("${node.label} · ${node.count}") })
                }
            }
        }
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 3.dp, shadowElevation = 5.dp) {
        Column(Modifier.fillMaxWidth().animateContentSize().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            trail()
            when (view) {
                CategoryView.BREADCRUMBS -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    children.forEach { node -> SoftChip("${node.label} · ${node.count}") { onSelect(node.path) } }
                }
                CategoryView.FOLDERS -> LazyColumn(Modifier.heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(children.chunked(2)) { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pair.forEach { folder(it, Modifier.weight(1f)) }; if (pair.size == 1) Spacer(Modifier.weight(1f)) }
                    }
                }
                CategoryView.TREE -> tree(Modifier.heightIn(max = 180.dp))
                CategoryView.COLUMNS -> {
                    val scroll = rememberScrollState()
                    LaunchedEffect(path, scroll.maxValue) { if (path.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue) }
                    Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (depth in (path.size - 2).coerceAtLeast(0)..path.size) {
                        val prefix = path.take(depth)
                        val nodes = browser.children(prefix)
                        if (nodes.isNotEmpty()) Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            LazyColumn(Modifier.width(180.dp).heightIn(max = 180.dp).padding(6.dp)) {
                                items(nodes, key = { it.key }) { node -> FilterChip(selected = node.key in path, onClick = { onSelect(node.path) }, label = { Text("${node.label} · ${node.count}") }) }
                            }
                        }
                    }
                }
                }
                CategoryView.FLOATING -> FilledTonalButton(onClick = { explorer = true }, modifier = Modifier.fillMaxWidth()) { Text("Browse folders · ${children.size}") }
            }
        }
    }
    if (explorer) ModalBottomSheet(onDismissRequest = { explorer = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Folders", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { explorer = false }) { Text("Done") }
            }
            trail()
            tree(Modifier.fillMaxWidth().heightIn(max = 440.dp).navigationBarsPadding())
        }
    }
}
