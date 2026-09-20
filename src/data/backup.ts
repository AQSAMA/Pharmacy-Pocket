import { categoryById } from './categories';
import { isMedicine, type Medicine } from './medicine';

export const BACKUP_SCHEMA = 'pharmacy-pocket-backup';
export const BACKUP_VERSION = 2;
export const MAX_BACKUP_MEDICINES = 5000;

export type BackupSection = {
  id: string;
  category: string;
  subcategory: string;
  title: string;
  categoryLabel: string;
  categoryArabic: string;
  color: string;
  medicineIds: string[];
};

export type PharmacyPocketBackup = {
  schema: typeof BACKUP_SCHEMA;
  version: typeof BACKUP_VERSION;
  exportedAt: string;
  currency: 'IQD';
  sections: BackupSection[];
  favoriteIds: string[];
  medicines: Array<Omit<Medicine, 'favorite'>>;
};

export type ParsedBackup = {
  medicines: Medicine[];
  sections: BackupSection[];
  sourceVersion: number;
};

function sectionId(category: string, subcategory: string) {
  return `${category}::${subcategory}`;
}

export function buildSections(items: Medicine[]): BackupSection[] {
  const sections = new Map<string, BackupSection>();
  for (const item of items) {
    const id = sectionId(item.category, item.subcategory);
    const category = categoryById(item.category);
    const existing = sections.get(id);
    if (existing) {
      existing.medicineIds.push(item.id);
      continue;
    }
    sections.set(id, {
      id,
      category: item.category,
      subcategory: item.subcategory,
      title: item.subcategory === 'General' ? category.label : item.subcategory,
      categoryLabel: category.label,
      categoryArabic: category.arabic,
      color: category.color,
      medicineIds: [item.id],
    });
  }
  return [...sections.values()];
}

export function createBackup(items: Medicine[], exportedAt = new Date().toISOString()): PharmacyPocketBackup {
  return {
    schema: BACKUP_SCHEMA,
    version: BACKUP_VERSION,
    exportedAt,
    currency: 'IQD',
    sections: buildSections(items),
    favoriteIds: items.filter((item) => item.favorite).map((item) => item.id),
    medicines: items.map(({ favorite: _favorite, ...item }) => ({
      ...item,
      revision: Number.isInteger(item.revision) && item.revision >= 0 ? item.revision : 0,
    })),
  };
}

function isSection(value: unknown): value is BackupSection {
  if (!value || typeof value !== 'object') return false;
  const section = value as BackupSection;
  return (
    ['id', 'category', 'subcategory', 'title', 'categoryLabel', 'categoryArabic', 'color'].every(
      (key) => typeof section[key as keyof BackupSection] === 'string',
    ) &&
    Boolean(section.id && section.category) &&
    Array.isArray(section.medicineIds) &&
    section.medicineIds.every((id) => typeof id === 'string')
  );
}

function orderBySections(items: Medicine[], sections: BackupSection[]) {
  if (!sections.length) return items;
  const byId = new Map(items.map((item) => [item.id, item]));
  const ordered: Medicine[] = [];
  const used = new Set<string>();
  for (const section of sections) {
    for (const id of section.medicineIds) {
      const item = byId.get(id);
      if (item && !used.has(id)) {
        ordered.push(item);
        used.add(id);
      }
    }
  }
  for (const item of items) if (!used.has(item.id)) ordered.push(item);
  return ordered;
}

export function parseBackup(value: unknown): ParsedBackup {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('This is not a valid Pharmacy Pocket JSON file.');
  }
  const data = value as Record<string, unknown>;
  if (!Array.isArray(data.medicines) || data.medicines.length > MAX_BACKUP_MEDICINES) {
    throw new Error('This file does not contain a valid medicines list.');
  }
  if (!data.medicines.every(isMedicine)) {
    throw new Error('One or more medicines in this file are invalid.');
  }
  const ids = new Set<string>();
  for (const item of data.medicines) {
    if (ids.has(item.id)) throw new Error(`Duplicate medicine ID: ${item.id}`);
    ids.add(item.id);
  }
  const favoriteIds = Array.isArray(data.favoriteIds)
    ? new Set(data.favoriteIds.filter((id): id is string => typeof id === 'string'))
    : new Set<string>();
  const medicines = data.medicines.map((item) => ({
    ...item,
    revision: Number.isInteger(item.revision) && item.revision >= 0 ? item.revision : 0,
    favorite: favoriteIds.has(item.id) || item.favorite === true,
  }));
  const sections = data.sections === undefined
    ? buildSections(medicines)
    : Array.isArray(data.sections) && data.sections.every(isSection)
      ? data.sections
      : (() => { throw new Error('The sections in this file are invalid.'); })();
  const sourceVersion = Number.isInteger(data.version) ? Number(data.version) : 1;
  if (sourceVersion < 1 || sourceVersion > BACKUP_VERSION) {
    throw new Error(`Backup version ${sourceVersion} is not supported by this app.`);
  }
  return { medicines: orderBySections(medicines, sections), sections, sourceVersion };
}
