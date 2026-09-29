package com.aqsama.pharmacypocket.data

import java.math.BigDecimal
import java.math.RoundingMode

/** Record-maintenance filters describe local data, never clinical suitability. */
enum class CollectionFilter(val label: String) {
    ALL("All medicines"), DISCOUNTED("With discount"), MISSING_PHOTO("Missing photo"),
    MISSING_CODES("Missing codes"), CHECK_PRICE("Check prices"),
}

fun matchesCollectionFilter(item: Medicine, filter: CollectionFilter): Boolean = when (filter) {
    CollectionFilter.ALL -> true
    CollectionFilter.DISCOUNTED -> item.discounted?.let { it < item.official } == true
    CollectionFilter.MISSING_PHOTO -> !item.hasPhoto
    CollectionFilter.MISSING_CODES -> item.codes.isEmpty()
    CollectionFilter.CHECK_PRICE -> item.discounted?.let { it > item.official } == true
}

/** Percentage is rounded only for display. Amounts retain their exact integer values. */
fun discountPercent(item: Medicine): Int? {
    val price = item.discounted ?: return null
    if (item.official <= 0 || price < 0 || price >= item.official) return null
    return BigDecimal.valueOf(item.official - price).multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(item.official), 0, RoundingMode.HALF_UP).toInt()
}

data class CollectionOverview(
    val total: Int,
    val favorites: Int,
    val withPhotos: Int,
    val withCodes: Int,
    val discounted: Int,
    val priceWarnings: Int,
    val categoryCounts: Map<String, Int>,
)

fun collectionOverview(items: List<Medicine>): CollectionOverview = CollectionOverview(
    total = items.size,
    favorites = items.count { it.favorite },
    withPhotos = items.count { it.hasPhoto },
    withCodes = items.count { it.codes.isNotEmpty() },
    discounted = items.count { matchesCollectionFilter(it, CollectionFilter.DISCOUNTED) },
    priceWarnings = items.count { matchesCollectionFilter(it, CollectionFilter.CHECK_PRICE) },
    categoryCounts = items.groupingBy { it.category }.eachCount(),
)

/** Keep comparison IDs stable and bounded; callers resolve against the current snapshot. */
fun toggleComparison(ids: List<String>, id: String): List<String> = when {
    id in ids -> ids - id
    ids.size < 3 -> ids + id
    else -> ids
}
