import { compareMedicines, normalize, type Medicine, type MedicineSort } from './medicine';

export const GENERAL_SUBCATEGORY = 'General';
export const GENERAL_SUBCATEGORY_KEY = normalize(GENERAL_SUBCATEGORY);

export function subcategoryLabel(value: string | null | undefined) {
  return value?.trim() || GENERAL_SUBCATEGORY;
}

export function subcategoryKey(value: string | null | undefined) {
  return normalize(subcategoryLabel(value));
}

export type MedicineSearchEntry = {
  item: Medicine;
  nameKey: string;
  searchText: string;
  subcategoryKey: string;
};

export type MedicineFilters = {
  category: string;
  subcategoryKey: string | null;
  favoritesOnly: boolean;
};

export type SubcategoryOption = { key: string; label: string };

export function buildMedicineSearchIndex(items: Medicine[]): MedicineSearchEntry[] {
  return items.map((item) => ({
    item,
    nameKey: normalize(item.name),
    searchText: normalize(`${item.name} ${item.note} ${item.description ?? ''} ${subcategoryLabel(item.subcategory)}`),
    subcategoryKey: subcategoryKey(item.subcategory),
  }));
}

export function listSubcategories(index: MedicineSearchEntry[], category: string): SubcategoryOption[] {
  const options = new Map<string, string>();
  for (const entry of index) {
    if (category !== 'all' && entry.item.category !== category) continue;
    if (!options.has(entry.subcategoryKey)) options.set(entry.subcategoryKey, subcategoryLabel(entry.item.subcategory));
  }
  return [...options].map(([key, label]) => ({ key, label }))
    .sort((left, right) => left.label.localeCompare(right.label, ['ar', 'en'], { sensitivity: 'base' }));
}

function matchesFilters(entry: MedicineSearchEntry, filters: MedicineFilters) {
  return (
    (filters.category === 'all' || entry.item.category === filters.category) &&
    (filters.subcategoryKey === null || entry.subcategoryKey === filters.subcategoryKey) &&
    (!filters.favoritesOnly || Boolean(entry.item.favorite))
  );
}

export function filterAndSortMedicines(index: MedicineSearchEntry[], filters: MedicineFilters, query: string, sort: MedicineSort) {
  return filterSortedMedicines(sortMedicineSearchIndex(index, sort), filters, query);
}

// Sort when the data or sort option changes, not on each category chip tap or keystroke.
export function sortMedicineSearchIndex(index: MedicineSearchEntry[], sort: MedicineSort) {
  return [...index].sort((left, right) => compareMedicines(left.item, right.item, sort));
}

export function filterSortedMedicines(sortedIndex: MedicineSearchEntry[], filters: MedicineFilters, query: string) {
  const needle = normalize(query.trim());
  const result: Medicine[] = [];
  for (const entry of sortedIndex) {
    if (matchesFilters(entry, filters) && (!needle || entry.searchText.includes(needle))) result.push(entry.item);
  }
  return result;
}

export function getMedicineSuggestions(index: MedicineSearchEntry[], filters: MedicineFilters, query: string, limit = 5) {
  const needle = normalize(query.trim());
  if (!needle || limit <= 0) return [];
  const suggestions = new Map<string, { name: string; startsWithQuery: boolean }>();
  for (const entry of index) {
    if (!matchesFilters(entry, filters) || !entry.nameKey.includes(needle) || suggestions.has(entry.nameKey)) continue;
    suggestions.set(entry.nameKey, { name: entry.item.name, startsWithQuery: entry.nameKey.startsWith(needle) });
  }
  return [...suggestions.values()]
    .sort((left, right) => Number(!left.startsWithQuery) - Number(!right.startsWithQuery) || left.name.localeCompare(right.name, ['ar', 'en'], { sensitivity: 'base' }))
    .slice(0, limit)
    .map((entry) => entry.name);
}
