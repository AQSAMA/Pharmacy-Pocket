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
};

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
export const hasArabic = (value: string) => /[\u0600-\u06FF]/.test(value);
