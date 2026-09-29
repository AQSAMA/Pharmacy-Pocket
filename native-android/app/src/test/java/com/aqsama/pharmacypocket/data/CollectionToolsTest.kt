package com.aqsama.pharmacypocket.data

import org.junit.Assert.*
import org.junit.Test

class CollectionToolsTest {
    private fun medicine(id: String, official: Long = 1000, discounted: Long? = null) = Medicine(
        id, "tablets", "General", "Paracetamol باراسيتامول", "500 mg", "Pain relief", official, discounted,
    )

    @Test fun wordsMatchAcrossFieldsAndCodesWithoutLosingFavoriteScope() {
        val item = medicine("match").copy(favorite = true, codes = listOf(MedicineCode(CodeKind.BARCODE, "ABC123")))
        val index = buildSearchIndex(listOf(item, item.copy(id = "nonfavorite", favorite = false)))
        assertEquals(listOf("match"), filterSortedMedicines(index, MedicineFilters(favoritesOnly = true), " abc123  بَارَاسِيتَامول 500 ").map { it.id })
        assertTrue(filterSortedMedicines(index, MedicineFilters(), "Paracetamol 999").isEmpty())
    }

    @Test fun overviewCountsRecordsRatherThanInventingStockOrClinicalState() {
        val items = listOf(medicine("discount", discounted = 700).copy(hasPhoto = true, favorite = true),
            medicine("warning", discounted = 1200).copy(codes = listOf(MedicineCode(CodeKind.QR, "raw"))),
            medicine("equal", discounted = 1000), medicine("absent"))
        val result = collectionOverview(items)
        assertEquals(4, result.total)
        assertEquals(1, result.withPhotos)
        assertEquals(1, result.withCodes)
        assertEquals(1, result.discounted)
        assertEquals(1, result.priceWarnings)
        assertEquals(3, items.count { matchesCollectionFilter(it, CollectionFilter.MISSING_PHOTO) })
        assertEquals(3, items.count { matchesCollectionFilter(it, CollectionFilter.MISSING_CODES) })
    }

    @Test fun percentageHandlesZeroAndMaximumSupportedPrices() {
        assertNull(discountPercent(medicine("zero", 0, 0)))
        assertNull(discountPercent(medicine("above", 100, 120)))
        assertNull(discountPercent(medicine("same", 100, 100)))
        assertEquals(30, discountPercent(medicine("normal", 1000, 700)))
        assertEquals(100, discountPercent(medicine("free", 9_007_199_254_740_991, 0)))
        assertEquals(11, discountPercent(medicine("max", 9_007_199_254_740_991, 8_007_199_254_740_991)))
    }

    @Test fun comparisonNeverSilentlyReplacesASelectedMedicine() {
        val selected = listOf("a", "b", "c")
        assertEquals(selected, toggleComparison(selected, "d"))
        assertEquals(listOf("a", "c"), toggleComparison(selected, "b"))
        assertEquals(listOf("a", "c", "d"), toggleComparison(listOf("a", "c"), "d"))
    }
}
