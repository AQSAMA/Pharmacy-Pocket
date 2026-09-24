export type Category = { id: string; label: string; arabic: string; color: string };

export const CATEGORY_COLORS = [
  '#2f856d',
  '#596aab',
  '#9672ab',
  '#bc798b',
  '#c79749',
  '#4e9cab',
  '#72a03b',
  '#bc8959',
  '#758790',
  '#b85d5d',
  '#3f7fb5',
  '#8a6d3b',
] as const;

export const categories: Category[] = [
  { id: 'all', label: 'All', arabic: 'الكل', color: '#126052' },
  { id: 'syrups', label: 'Syrups & sachets', arabic: 'شراب', color: '#2f856d' },
  { id: 'tablets', label: 'Tablets & strips', arabic: 'حبوب', color: '#596aab' },
  { id: 'boxes', label: 'Boxes', arabic: 'علب', color: '#9672ab' },
  { id: 'ampoules', label: 'Ampoules', arabic: 'أمبولات', color: '#bc798b' },
  { id: 'vials', label: 'Vials', arabic: 'فيالات', color: '#c79749' },
  { id: 'drops', label: 'Drops', arabic: 'قطرات', color: '#4e9cab' },
  { id: 'effervescent', label: 'Effervescent', arabic: 'فوار', color: '#72a03b' },
  { id: 'topicals', label: 'Creams & oils', arabic: 'موضعي', color: '#bc8959' },
  { id: 'supplies', label: 'Supplies', arabic: 'مستلزمات', color: '#758790' },
];

const HEX_COLOR = /^#[0-9a-f]{6}$/i;

export function isCategory(value: unknown): value is Category {
  if (!value || typeof value !== 'object') return false;
  const item = value as Category;
  return (
    typeof item.id === 'string' &&
    Boolean(item.id.trim()) &&
    typeof item.label === 'string' &&
    Boolean(item.label.trim()) &&
    typeof item.arabic === 'string' &&
    Boolean(item.arabic.trim()) &&
    typeof item.color === 'string' &&
    HEX_COLOR.test(item.color)
  );
}

export function categoryById(id: string, source: readonly Category[] = categories) {
  return source.find((category) => category.id === id) ?? categories.find((category) => category.id === id) ?? categories[0];
}

export function mergeCategoryDefinitions(base: readonly Category[], incoming: readonly Category[]) {
  const byId = new Map<string, Category>();
  for (const item of base) byId.set(item.id, { ...item });
  for (const item of incoming) {
    if (!isCategory(item) || item.id === 'all') continue;
    byId.set(item.id, {
      id: item.id,
      label: item.label.trim(),
      arabic: item.arabic.trim(),
      color: item.color.toLowerCase(),
    });
  }

  const ordered: Category[] = [];
  const all = base.find((item) => item.id === 'all') ?? categories[0];
  ordered.push({ ...all });

  const seen = new Set(['all']);
  for (const item of base) {
    if (item.id === 'all' || seen.has(item.id)) continue;
    ordered.push(byId.get(item.id) ?? { ...item });
    seen.add(item.id);
  }
  for (const item of incoming) {
    if (item.id === 'all' || seen.has(item.id) || !isCategory(item)) continue;
    ordered.push(byId.get(item.id) ?? { ...item });
    seen.add(item.id);
  }
  return ordered;
}

export function ensureCategoriesForMedicines(source: readonly Category[], categoryIds: readonly string[]) {
  const next = [...source];
  const ids = new Set(next.map((item) => item.id));
  for (const rawId of categoryIds) {
    const label = rawId.trim();
    if (!label || rawId === 'all' || ids.has(rawId)) continue;
    next.push({ id: rawId, label, arabic: label, color: '#758790' });
    ids.add(rawId);
  }
  return next;
}

export function tintCategoryColor(color: string, strength = 0.08) {
  if (!HEX_COLOR.test(color)) return '#f7faf8';
  const amount = Math.max(0, Math.min(1, strength));
  const rgb = [1, 3, 5].map((start) => Number.parseInt(color.slice(start, start + 2), 16));
  const mixed = rgb.map((channel) => Math.round(255 - (255 - channel) * amount));
  return `#${mixed.map((channel) => channel.toString(16).padStart(2, '0')).join('')}`;
}
