import * as SQLite from 'expo-sqlite';

import { resolveCreatedAt, type Medicine } from './medicine';

type MedicineRow = Omit<Medicine, 'favorite' | 'createdAt'> & { favorite: number; created_at: number };

let databasePromise: ReturnType<typeof SQLite.openDatabaseAsync> | undefined;

function database() {
  databasePromise ??= SQLite.openDatabaseAsync('pharmacy-pocket.db').catch((error) => {
    databasePromise = undefined;
    throw error;
  });
  return databasePromise;
}

export async function initializeDatabase() {
  const db = await database();
  await db.execAsync(`
    PRAGMA journal_mode = WAL;
    CREATE TABLE IF NOT EXISTS medicines (
      id TEXT PRIMARY KEY NOT NULL,
      category TEXT NOT NULL,
      subcategory TEXT NOT NULL,
      name TEXT NOT NULL,
      note TEXT NOT NULL,
      description TEXT NOT NULL DEFAULT '',
      official INTEGER NOT NULL,
      discounted INTEGER,
      revision INTEGER NOT NULL DEFAULT 0,
      favorite INTEGER NOT NULL DEFAULT 0,
      sort_order INTEGER NOT NULL,
      created_at INTEGER NOT NULL DEFAULT 0
    );
  `);
  const columns = await db.getAllAsync<{ name: string }>('PRAGMA table_info(medicines)');
  if (!columns.some((column) => column.name === 'description')) {
    await db.execAsync("ALTER TABLE medicines ADD COLUMN description TEXT NOT NULL DEFAULT ''");
  }
  if (!columns.some((column) => column.name === 'created_at')) {
    await db.execAsync('ALTER TABLE medicines ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0');
  }
  await db.runAsync("UPDATE medicines SET created_at = CAST(strftime('%s', 'now') AS INTEGER) * 1000 + sort_order WHERE created_at = 0");
}

export async function getMedicines(): Promise<Medicine[]> {
  const db = await database();
  const rows = await db.getAllAsync<MedicineRow>('SELECT * FROM medicines ORDER BY sort_order');
  return rows.map(({ created_at, ...row }) => ({ ...row, createdAt: created_at, favorite: Boolean(row.favorite) }));
}

export async function saveMedicine(item: Medicine) {
  const db = await database();
  const existing = await db.getFirstAsync<{ sort_order: number; favorite: number; created_at: number }>(
    'SELECT sort_order, favorite, created_at FROM medicines WHERE id = ?',
    item.id,
  );
  const last = await db.getFirstAsync<{ value: number }>('SELECT COALESCE(MAX(sort_order), -1) AS value FROM medicines');
  await db.runAsync(
    `INSERT OR REPLACE INTO medicines
     (id, category, subcategory, name, note, description, official, discounted, revision, favorite, sort_order, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    item.id,
    item.category,
    item.subcategory,
    item.name,
    item.note,
    item.description ?? '',
    item.official,
    item.discounted,
    item.revision,
    existing?.favorite ?? Number(item.favorite ?? false),
    existing?.sort_order ?? (last?.value ?? -1) + 1,
    resolveCreatedAt(item.createdAt, existing?.created_at),
  );
}

export async function toggleFavorite(id: string, favorite: boolean) {
  const db = await database();
  await db.runAsync('UPDATE medicines SET favorite = ? WHERE id = ?', Number(favorite), id);
}

export async function mergeMedicines(items: Medicine[]) {
  const db = await database();
  await db.withTransactionAsync(async () => {
    for (const item of items) await saveMedicine(item);
  });
}

export async function replaceMedicines(items: Medicine[]) {
  const db = await database();
  await db.withTransactionAsync(async () => {
    await db.runAsync('DELETE FROM medicines');
    for (const [index, item] of items.entries()) {
      await db.runAsync(
        `INSERT INTO medicines
         (id, category, subcategory, name, note, description, official, discounted, revision, favorite, sort_order, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        item.id,
        item.category,
        item.subcategory,
        item.name,
        item.note,
        item.description ?? '',
        item.official,
        item.discounted,
        item.revision,
        Number(item.favorite ?? false),
        index,
        item.createdAt ?? Date.now() + index,
      );
    }
  });
}
