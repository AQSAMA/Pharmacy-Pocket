package com.aqsama.pharmacypocket.ui

import com.aqsama.pharmacypocket.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeIndexStateTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun main() { Dispatchers.setMain(dispatcher) }
    @After fun reset() { Dispatchers.resetMain() }
    private suspend fun ready(model: HomeIndexViewModel) = withTimeout(30_000) {
        while (model.index.value == null) { dispatcher.scheduler.advanceUntilIdle(); delay(10) }
        model.index.value!!
    }
    @Test fun returningFromDetailKeepsThePreparedLargeListAndCachedSort() = runBlocking {
        val items = List(21464) { i -> Medicine("row-$i", "tablets", "General", "Brand ${21464-i}", "", official = i.toLong(), discounted = null,
            imported = true, importedFields = listOf(ImportedField("ingredient", "Scientific name", "Generic compound $i", ImportField.SCIENTIFIC, true))) }
        val model = HomeIndexViewModel()
        model.update(items, MedicineSort.DEFAULT)
        val prepared = ready(model)
        assertEquals(21464, prepared.size)
        // Home leaves composition for Details and re-enters with the same retained snapshot.
        model.update(items, MedicineSort.DEFAULT)
        assertSame(prepared, model.index.value)
        model.update(items, MedicineSort.NAME_DESC)
        withTimeout(30_000) { while (model.index.value === prepared) { dispatcher.scheduler.advanceUntilIdle(); delay(10) } }
        model.update(items, MedicineSort.DEFAULT)
        assertSame(prepared, model.index.value)
        assertEquals("Brand 1", model.index.value!!.first().item.name)
    }
    @Test fun retiredRowsAndFavoriteChangesPublishBeforeBackgroundReindexing() = runBlocking {
        val first = Medicine("first", "tablets", "General", "First", "", official = 100, discounted = null)
        val second = first.copy(id = "second", name = "Second")
        val model = HomeIndexViewModel()
        model.update(listOf(first, second), MedicineSort.DEFAULT)
        ready(model)
        model.update(listOf(second.copy(favorite = true)), MedicineSort.DEFAULT)
        assertEquals(listOf("second"), model.index.value!!.map { it.item.id })
        assertTrue(model.index.value!!.single().item.favorite)
        model.clear()
    }

    @Test fun changedNamesFieldsAndPricesNeverUseOldSearchKeysOrSortOrder() {
        val worker = StandardTestDispatcher(dispatcher.scheduler)
        val model = HomeIndexViewModel(worker)
        val first = Medicine("first", "tablets", "General", "Old brand", "", official = 100, discounted = null, imported = true,
            importedFields = listOf(ImportedField("price", "Pharmacy price", "100", ImportField.PHARMACY_PRICE, true),
                ImportedField("science", "Scientific name", "Old ingredient", ImportField.SCIENTIFIC, true)))
        val second = first.copy(id = "second", name = "Other", importedFields = first.importedFields.map { if (it.field.isPrice) it.copy(value = "200") else it })
        model.update(listOf(first, second), MedicineSort.PRICE_ASC)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("first", "second"), model.index.value!!.map { it.item.id })
        val edited = first.copy(name = "New brand", importedFields = first.importedFields.map {
            it.copy(value = if (it.field.isPrice) "900" else "New ingredient")
        })
        model.update(listOf(edited, second), MedicineSort.PRICE_ASC)
        assertNull(model.index.value)
        dispatcher.scheduler.advanceUntilIdle()
        val fresh = model.index.value!!
        assertEquals(listOf("second", "first"), fresh.map { it.item.id })
        assertTrue(filterSortedMedicines(fresh, MedicineFilters(), "Old brand").isEmpty())
        assertEquals(listOf("first"), filterSortedMedicines(fresh, MedicineFilters(), "New ingredient").map { it.id })
        model.clear()
    }

}
