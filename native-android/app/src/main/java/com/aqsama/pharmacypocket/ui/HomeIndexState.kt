package com.aqsama.pharmacypocket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqsama.pharmacypocket.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Retains the expensive normalized index when Home leaves composition for a detail page. */
internal class HomeIndexViewModel : ViewModel() {
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
            // Publish changed favorites/details and remove retired rows immediately while
            // search normalization and sorting refresh in the background.
            val latest = next.associateBy { it.id }
            mutable.value = mutable.value?.mapNotNull { entry -> latest[entry.item.id]?.let { item ->
                if (item === entry.item) entry else entry.copy(item = item)
            } }
            items = next; cached.clear(); normalized = null
        }
        cached[sort]?.let { mutable.value = it; return }
        job = viewModelScope.launch {
            val base = normalized
            val result = withContext(Dispatchers.Default) {
                val normalized = base ?: buildSearchIndex(next)
                normalized to sortSearchIndex(normalized, sort)
            }
            normalized = result.first
            cached[sort] = result.second
            mutable.value = result.second
        }
    }
}
