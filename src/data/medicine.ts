export type Medicine = {
  id: string;
  category: string;
  subcategory: string;
  name: string;
  note: string;
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
    Number.isInteger(item.official) &&
    item.official >= 0 &&
    (item.discounted === null ||
      (Number.isInteger(item.discounted) && item.discounted >= 0))
  );
}

export const formatPrice = (value: number) => value.toLocaleString('en-US');
export const hasArabic = (value: string) => /[\u0600-\u06FF]/.test(value);
