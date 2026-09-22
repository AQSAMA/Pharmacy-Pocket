export type Medicine = {
  id: string;
  category: string;
  subcategory: string;
  name: string;
  note: string;
  description?: string;
  official: number;
  discounted: number | null;
  revision: number;
  favorite?: boolean;
  createdAt?: number;
};

export type MedicineSort = 'name-asc' | 'name-desc' | 'date-desc' | 'date-asc' | 'price-asc' | 'price-desc';

export const medicineSortOptions: { id: MedicineSort; label: string }[] = [
  { id: 'name-asc', label: 'A–Z' },
  { id: 'name-desc', label: 'Z–A' },
  { id: 'date-desc', label: 'Newest' },
  { id: 'date-asc', label: 'Oldest' },
  { id: 'price-asc', label: 'Price ↑' },
  { id: 'price-desc', label: 'Price ↓' },
];

export function compareMedicines(left: Medicine, right: Medicine, sort: MedicineSort) {
  if (sort === 'name-asc' || sort === 'name-desc') {
    const result = left.name.localeCompare(right.name, ['ar', 'en'], { sensitivity: 'base', numeric: true });
    return sort === 'name-asc' ? result : -result;
  }
  if (sort === 'price-asc' || sort === 'price-desc') {
    const result = left.official - right.official || left.name.localeCompare(right.name, ['ar', 'en'], { sensitivity: 'base' });
    return sort === 'price-asc' ? result : -result;
  }
  const result = (left.createdAt ?? 0) - (right.createdAt ?? 0);
  return sort === 'date-asc' ? result : -result;
}

export function resolveCreatedAt(supplied: number | undefined, existing: number | undefined, now = Date.now()) {
  if (typeof supplied === 'number' && Number.isSafeInteger(supplied) && supplied >= 0) return supplied;
  if (typeof existing === 'number' && Number.isSafeInteger(existing) && existing >= 0) return existing;
  return now;
}

export function normalize(value: string) {
  return value
    .normalize('NFKD')
    .replace(/[\u064B-\u065F\u0670\u0640]/g, '')
    .replace(/[أإآ]/g, 'ا')
    .replace(/ى/g, 'ي')
    .toLowerCase();
}

export function isMedicine(value: unknown): value is Medicine {
  if (!value || typeof value !== 'object') return false;
  const item = value as Medicine;
  return (
    ['id', 'category', 'subcategory', 'name', 'note'].every(
      (key) => typeof item[key as keyof Medicine] === 'string',
    ) &&
    Boolean(item.id && item.name.trim() && item.category) &&
    (item.description === undefined || typeof item.description === 'string') &&
    (item.createdAt === undefined || (Number.isSafeInteger(item.createdAt) && item.createdAt >= 0)) &&
    Number.isSafeInteger(item.official) &&
    item.official >= 0 &&
    Number.isSafeInteger(item.revision) &&
    item.revision >= 0 &&
    (item.discounted === null ||
      (Number.isSafeInteger(item.discounted) && item.discounted >= 0))
  );
}

const priceFormatter = new Intl.NumberFormat('en-US');
export const formatPrice = (value: number) => priceFormatter.format(value);
const addedDateFormatter = new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'short', day: 'numeric' });

export function formatAddedDate(value: number | undefined) {
  // Some legacy records have no date; malformed imported dates must not crash a card.
  if (value === undefined || !Number.isSafeInteger(value) || value < 0 || value > 8.64e15) return 'Unknown';
  return addedDateFormatter.format(new Date(value));
}

export const hasArabic = (value: string) => /[\u0600-\u06FF]/.test(value);
