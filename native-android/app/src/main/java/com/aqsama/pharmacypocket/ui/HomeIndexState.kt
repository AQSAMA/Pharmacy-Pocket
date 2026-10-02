package com.aqsama.pharmacypocket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqsama.pharmacypocket.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Retains the expensive normalized index when Home leaves composition for a detail page. */
internal class HomeIndexViewModel(private val worker: CoroutineDispatcher = Dispatchers.Default) : ViewModel() {
    private var items: List<Medicine>? = null
    private var job: Job? = null
    private var requestedSort: MedicineSort? = null
    private var normalized: List<MedicineSearchEntry>? = null
    private val cached = mutableMapOf<MedicineSort, List<MedicineSearchEntry>>()
    private val mutable = MutableStateFlow<List<MedicineSearchEntry>?>(null)
    val index = mutable.asStateFlow()
    fun clear() { job?.cancel(); items = null; normalized = null; requestedSort = null; cached.clear(); mutable.value = null }

    fun update(next: List<Medicine>, sort: MedicineSort) {
        if (items === next && requestedSort == sort && (job?.isActive == true || cached.containsKey(sort))) return
        requestedSort = sort
        job?.cancel()
        if (items !== next) {
            // Reuse normalized keys only for changes that cannot affect search or sorting.
            // Actual field edits wait for the fresh index; unchanged navigation stays cached.
            val latest = next.associateBy { it.id }
            val current = mutable.value
            val indexedIds = current?.mapTo(mutableSetOf()) { it.item.id }.orEmpty()
            val canReuse = latest.keys.all { it in indexedIds } && current?.all { entry ->
                latest[entry.item.id]?.let { item ->
                    entry.item.copy(favorite = item.favorite, hasPhoto = item.hasPhoto, revision = item.revision) == item
                } ?: true
            } == true
            mutable.value = if (canReuse) current?.mapNotNull { entry -> latest[entry.item.id]?.let { item ->
                if (item === entry.item) entry else entry.copy(item = item)
            } } else null
            items = next; cached.clear(); normalized = null
        }
        cached[sort]?.let { mutable.value = it; return }
        job = viewModelScope.launch {
            val base = normalized
            val result = withContext(worker) {
                val normalized = base ?: buildSearchIndex(next)
                normalized to sortSearchIndex(normalized, sort)
            }
            normalized = result.first
            cached[sort] = result.second
            mutable.value = result.second
        }
    }
}
