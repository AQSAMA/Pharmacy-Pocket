export type Category = { id: string; label: string; arabic: string; color: string };

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

export function categoryById(id: string) {
  return categories.find((category) => category.id === id) ?? categories[0];
}
