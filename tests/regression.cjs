const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

// Compile the actual pure TS modules with the project's pinned compiler.
require.extensions['.ts'] = (module, filename) => {
  const source = fs.readFileSync(filename, 'utf8');
  module._compile(ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText, filename);
};
const { createSerialQueue } = require('../src/data/serial-queue.ts');
const { createBackup, parseBackup } = require('../src/data/backup.ts');
const { MAX_CATEGORY_DEFINITIONS, categories: defaultCategories, ensureCategoriesForMedicines, mergeCategoryDefinitions, tintCategoryColor } = require('../src/data/categories.ts');
const { compareMedicines, normalize, isMedicine, resolveCreatedAt, formatAddedDate } = require('../src/data/medicine.ts');
const { buildMedicineSearchIndex, filterAndSortMedicines, filterSortedMedicines, listSubcategories, sortMedicineSearchIndex, subcategoryKey } = require('../src/data/medicine-query.ts');
const read = (file) => fs.readFileSync(path.join(__dirname, '..', file), 'utf8');

test('added dates render saved timestamps and tolerate missing or invalid legacy dates', () => {
  const timestamp = Date.UTC(2026, 8, 21, 12);
  const formatter = new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
  assert.equal(formatAddedDate(timestamp), formatter.format(new Date(timestamp)));
  assert.equal(formatAddedDate(0), formatter.format(new Date(0)));
  for (const value of [undefined, NaN, Infinity, -1, 1.5, Number.MAX_SAFE_INTEGER]) {
    assert.equal(formatAddedDate(value), 'Unknown');
  }
});

test('writes execute serially, including rapid repeated favorite toggles', async () => {
  const enqueue = createSerialQueue();
  let favorite = false;
  let active = 0;
  let maxActive = 0;
  await Promise.all(Array.from({ length: 101 }, () => enqueue(async () => {
    maxActive = Math.max(maxActive, ++active);
    const next = !favorite;
    await new Promise(resolve => setImmediate(resolve));
    favorite = next;
    active--;
  })));
  assert.equal(maxActive, 1);
  assert.equal(favorite, true);
});

test('failed writes reject to caller but do not block later operations', async () => {
  const enqueue = createSerialQueue();
  const failure = enqueue(async () => { throw new Error('disk full'); });
  const next = enqueue(async () => 'saved');
  await assert.rejects(failure, /disk full/);
  assert.equal(await next, 'saved');
});

test('backup preserves Arabic, descriptions, favorites, and custom currency', () => {
  const item = { id: 'a', name: 'دواء', note: 'ملاحظة', description: 'Details',
    category: 'syrups', subcategory: 'General', official: 3000, discounted: null,
    revision: 0, favorite: true };
  const result = parseBackup(JSON.parse(JSON.stringify(createBackup([item], 'USD'))));
  assert.deepEqual(result.medicines, [item]);
  assert.equal(result.currency, 'USD');
});

test('category definitions keep stable ids while allowing names and colors to change', () => {
  const customized = mergeCategoryDefinitions(defaultCategories, [
    { id: 'syrups', label: 'Liquids', arabic: 'سوائل', color: '#123456' },
    { id: 'custom-inhalers', label: 'Inhalers', arabic: 'بخاخات', color: '#3f7fb5' },
  ]);
  assert.equal(customized[0].id, 'all');
  assert.equal(customized.find(item => item.id === 'syrups').label, 'Liquids');
  assert.equal(customized.find(item => item.id === 'syrups').color, '#123456');
  assert.equal(customized.at(-1).id, 'custom-inhalers');
  assert.equal(tintCategoryColor('#2f856d', 0.08), '#eef5f3');
  const legacy = ensureCategoriesForMedicines(defaultCategories, [' legacy-id ']);
  assert.equal(legacy.find(item => item.id === ' legacy-id ').label, 'legacy-id');
});

test('createBackup keeps the legacy third exportedAt argument compatible', () => {
  const item = { id: 'legacy-export', name: 'Legacy', note: '', category: 'syrups', subcategory: 'General', official: 1000, discounted: null, revision: 0 };
  const backup = createBackup([item], 'IQD', '2026-01-02T03:04:05.000Z');
  assert.equal(backup.exportedAt, '2026-01-02T03:04:05.000Z');
  assert.equal(backup.sections[0].categoryLabel, 'Syrups & sachets');
});

test('custom category names and colors survive JSON backup and legacy custom sections still import', () => {
  const custom = { id: 'custom-inhalers', label: 'Inhalers', arabic: 'بخاخات', color: '#3f7fb5' };
  const definitions = [...defaultCategories, custom];
  const item = { id: 'inh', name: 'Inhaler', note: '', category: custom.id, subcategory: 'General', official: 5000, discounted: null, revision: 0 };
  const backup = createBackup([item], 'IQD', definitions);
  assert.ok(backup.categories.some(category => category.id === custom.id && category.color === custom.color));
  assert.equal(backup.sections[0].categoryLabel, 'Inhalers');
  assert.equal(backup.sections[0].color, '#3f7fb5');

  const parsed = parseBackup(JSON.parse(JSON.stringify(backup)));
  assert.ok(parsed.categories.some(category => category.id === custom.id && category.arabic === 'بخاخات'));

  const legacy = JSON.parse(JSON.stringify(backup));
  delete legacy.categories;
  const parsedLegacy = parseBackup(legacy);
  assert.ok(parsedLegacy.categories.some(category => category.id === custom.id));
});

test('backup rejects duplicate or excessive category metadata', () => {
  const item = { id: 'category-guard', name: 'Guard', note: '', category: 'custom', subcategory: 'General', official: 1000, discounted: null, revision: 0 };
  const category = { id: 'custom', label: 'Custom', arabic: 'Custom', color: '#3f7fb5' };
  const duplicate = createBackup([item], 'IQD', [defaultCategories[0], category]);
  duplicate.categories = [category, { ...category, label: 'Duplicate' }];
  assert.throws(() => parseBackup(duplicate), /Duplicate category ID/);

  const excessive = createBackup([item]);
  excessive.categories = Array.from({ length: MAX_CATEGORY_DEFINITIONS + 1 }, (_, index) => ({
    id: `category-${index}`,
    label: `Category ${index}`,
    arabic: `Category ${index}`,
    color: '#3f7fb5',
  }));
  assert.throws(() => parseBackup(excessive), /categories in this file are invalid/);
});

test('Arabic search ignores diacritics and normalizes alef', () => {
  assert.equal(normalize('أَ'), normalize('ا'));
});

test('shared validation rejects unsafe imported prices and revisions', () => {
  const base = { id: 'safe', name: 'Medicine', category: 'syrups', subcategory: 'General', note: '', official: 3000, discounted: null, revision: 0 };
  assert.equal(isMedicine(base), true);
  for (const field of ['official', 'discounted', 'revision']) {
    const invalid = { ...base, [field]: Number.MAX_SAFE_INTEGER + 1 };
    assert.equal(isMedicine(invalid), false);
    assert.throws(() => parseBackup(createBackup([invalid])));
  }
});

test('medicine sorting supports names, dates, and prices in both directions', () => {
  const alpha = { id: 'a', name: 'Alpha', category: 'syrups', subcategory: 'General', note: '', official: 3000, discounted: null, revision: 0, createdAt: 10 };
  const beta = { ...alpha, id: 'b', name: 'Beta', official: 1000, createdAt: 20 };
  assert.deepEqual([beta, alpha].sort((a, b) => compareMedicines(a, b, 'name-asc')).map(item => item.id), ['a', 'b']);
  assert.deepEqual([alpha, beta].sort((a, b) => compareMedicines(a, b, 'name-desc')).map(item => item.id), ['b', 'a']);
  assert.deepEqual([alpha, beta].sort((a, b) => compareMedicines(a, b, 'date-desc')).map(item => item.id), ['b', 'a']);
  assert.deepEqual([beta, alpha].sort((a, b) => compareMedicines(a, b, 'date-asc')).map(item => item.id), ['a', 'b']);
  assert.deepEqual([alpha, beta].sort((a, b) => compareMedicines(a, b, 'price-asc')).map(item => item.id), ['b', 'a']);
  assert.deepEqual([beta, alpha].sort((a, b) => compareMedicines(a, b, 'price-desc')).map(item => item.id), ['a', 'b']);
});

test('subcategory keys normalize blanks, case, whitespace, and Arabic variants without reserving a label', () => {
  const base = { name: 'Medicine', category: 'syrups', note: '', official: 1000, discounted: null, revision: 0 };
  const items = [
    { ...base, id: 'blank', subcategory: '   ' },
    { ...base, id: 'general', subcategory: ' general ' },
    { ...base, id: 'all-label', subcategory: 'all' },
    { ...base, id: 'arabic-a', subcategory: ' أَقْرَاص ' },
    { ...base, id: 'arabic-b', subcategory: 'اقراص' },
  ];
  const index = buildMedicineSearchIndex(items);
  const options = listSubcategories(index, 'syrups');
  assert.equal(options.filter(option => option.key === subcategoryKey('General')).length, 1);
  assert.equal(options.filter(option => option.key === subcategoryKey('اقراص')).length, 1);
  assert.ok(options.some(option => option.label === 'all'));
  assert.deepEqual(
    filterAndSortMedicines(index, { category: 'syrups', subcategoryKey: subcategoryKey('اقراص'), favoritesOnly: false }, '', 'name-asc').map(item => item.id),
    ['arabic-a', 'arabic-b'],
  );
});

test('repeated legacy merges retain the first creation timestamp and explicit timestamps win', () => {
  const firstImport = resolveCreatedAt(undefined, undefined, 1000);
  const repeatedImport = resolveCreatedAt(undefined, firstImport, 2000);
  assert.equal(firstImport, 1000);
  assert.equal(repeatedImport, 1000);
  assert.equal(resolveCreatedAt(500, repeatedImport, 3000), 500);
  assert.equal(resolveCreatedAt(-1, repeatedImport, 3000), 1000);
  const database = read('src/data/database.ts');
  assert.match(database, /existing\?\.favorite \?\?/);
  assert.match(database, /resolveCreatedAt\(item\.createdAt, existing\?\.created_at\)/);
});

test('all sort modes order one global result across subcategories', () => {
  const base = { category: 'syrups', note: '', discounted: null, revision: 0 };
  const items = [
    { ...base, id: 'c', name: 'Charlie', subcategory: 'Third', official: 2000, createdAt: 20 },
    { ...base, id: 'a', name: 'Alpha', subcategory: 'First', official: 3000, createdAt: 10 },
    { ...base, id: 'b', name: 'Beta', subcategory: 'Second', official: 1000, createdAt: 30 },
  ];
  const index = buildMedicineSearchIndex(items);
  const filters = { category: 'all', subcategoryKey: null, favoritesOnly: false };
  const ids = sort => filterAndSortMedicines(index, filters, '', sort).map(item => item.id);
  assert.deepEqual(ids('name-asc'), ['a', 'b', 'c']);
  assert.deepEqual(ids('name-desc'), ['c', 'b', 'a']);
  assert.deepEqual(ids('date-asc'), ['a', 'c', 'b']);
  assert.deepEqual(ids('date-desc'), ['b', 'c', 'a']);
  assert.deepEqual(ids('price-asc'), ['b', 'c', 'a']);
  assert.deepEqual(ids('price-desc'), ['a', 'c', 'b']);
});

test('default view uses full category counts and newest-first order within each category', () => {
  const base = { name: 'Medicine', subcategory: 'General', note: '', official: 1000, discounted: null, revision: 0 };
  const items = [
    { ...base, id: 'a-old', category: 'a', createdAt: 10, favorite: true },
    { ...base, id: 'b-new', category: 'b', createdAt: 50, favorite: true },
    { ...base, id: 'a-new', category: 'a', createdAt: 30, favorite: false },
    { ...base, id: 'a-mid', category: 'a', createdAt: 20, favorite: false },
    { ...base, id: 'b-old', category: 'b', createdAt: 40, favorite: true },
    { ...base, id: 'c-only', category: 'c', createdAt: 60, favorite: true },
  ];
  const sorted = sortMedicineSearchIndex(buildMedicineSearchIndex(items), 'default');
  assert.deepEqual(sorted.map(entry => entry.item.id), ['a-new', 'a-mid', 'a-old', 'b-new', 'b-old', 'c-only']);
  assert.deepEqual(
    filterSortedMedicines(sorted, { category: 'all', subcategoryKey: null, favoritesOnly: true }, '').map(item => item.id),
    ['a-old', 'b-new', 'b-old', 'c-only'],
  );
});

test('switching subcategories preserves the selected global sort without rebuilding the index', () => {
  const items = [
    { id: 'z', name: 'Zulu', category: 'tablets', subcategory: ' Pain ', note: '', official: 3000, discounted: null, revision: 0 },
    { id: 'a', name: 'Alpha', category: 'tablets', subcategory: 'pain', note: '', official: 1000, discounted: null, revision: 0 },
    { id: 'm', name: 'Middle', category: 'tablets', subcategory: 'Other', note: '', official: 2000, discounted: null, revision: 0 },
  ];
  const index = buildMedicineSearchIndex(items);
  const sorted = sortMedicineSearchIndex(index, 'name-desc');
  const filters = { category: 'tablets', subcategoryKey: subcategoryKey('pain'), favoritesOnly: false };
  assert.deepEqual(filterSortedMedicines(sorted, filters, '').map(item => item.id), ['z', 'a']);
  assert.deepEqual(filterSortedMedicines(sorted, { ...filters, subcategoryKey: null }, '').map(item => item.id), ['z', 'm', 'a']);
  assert.deepEqual(index.map(entry => entry.item.id), ['z', 'a', 'm']);
});

test('navigation and scroll regression guards', () => {
  const home = read('src/app/index.tsx');
  assert.match(home, /<FlatList/);
  assert.doesNotMatch(home, /GestureDetector|PanResponder|stickySectionHeadersEnabled/);
  assert.match(home, /removeClippedSubviews=\{false\}/);
  assert.match(home, /paddingTop: insets.top/);
  assert.match(home, /useState<MedicineSort>\('default'\)/);
  assert.match(home, /Tune/);
  assert.match(home, /categoryCounts/);
  assert.match(home, /categoryById\(row\.item\.category, categories\)/);
  assert.match(home, /item\.category === 'all'/);
  assert.match(home, /favoriteCount/);
  assert.match(home, /Show favorites only, \$\{favoriteCount\} favorites/);
  assert.match(home, /clearViewFilters/);
  assert.match(home, /quickFavorite: \{ minWidth: 54, height: 48/);
  assert.match(home, /!categories\.some\(\(item\) => item\.id === category\)/);
  assert.match(home, /setCategory\('all'\)/);
  assert.match(home, /searchDock/);
  assert.match(home, /breadcrumbRow/);
  assert.match(home, /<View accessible accessibilityLabel=\{`\$\{row\.section\.data\.length\} medicines`\}/);
  assert.match(home, /direction: 'ltr'/);
  assert.doesNotMatch(home, /getMedicineSuggestions/);
  assert.doesNotMatch(read('src/data/medicine-query.ts'), /getMedicineSuggestions|nameKey/);
  assert.doesNotMatch(home, /width: 5, height: 19/);
  assert.match(home, /accessibilityState=\{\{ checked: favoritesOnly \}\}/);
  assert.match(home, /accessibilityState=\{\{ checked: largeText \}\}/);
  assert.doesNotMatch(home, /position: 'absolute'/);
  const search = read('src/components/floating-search.tsx');
  assert.doesNotMatch(search, /Animated|position: 'absolute'/);
  assert.doesNotMatch(search, /useState|expanded/);
  assert.doesNotMatch(search, /suggestions/);
  assert.match(search, /Boolean\(query\.trim\(\)\)/);
  assert.match(search, /minHeight: 50/);
  assert.equal((search.match(/width: 48/g) || []).length >= 2, true);
  assert.match(home, /quickFavorite: \{ minWidth: 54, height: 48/);
  assert.match(search, /Search medicines/);
  const layout = read('src/app/_layout.tsx');
  assert.doesNotMatch(layout, /formSheet|sheetAllowedDetents/);
  assert.match(layout, /presentation: 'card', animation: 'none'/);
  const categoryManager = read('src/app/categories.tsx');
  assert.match(categoryManager, /Add category/);
  assert.match(categoryManager, /Save category/);
  assert.match(categoryManager, /CATEGORY_COLORS/);
  assert.match(categoryManager, /tintCategoryColor/);
  const settings = read('src/app/settings.tsx');
  assert.match(settings, /Manage categories/);
  assert.match(settings, /accessibilityLabel=\{label\}/);
  assert.match(read('src/app/edit.tsx'), /minHeight: 48, justifyContent: 'center'/);
  assert.match(read('src/app/medicine\/\[id\]\.tsx'), /favoriteButton: \{ minHeight: 48/);
  const card = read('src/components/medicine-card.tsx');
  assert.match(card, /onFavorite\(item\)\.catch/);
  assert.match(card, /width: 48, height: 48/);
  assert.match(card, /tintCategoryColor\(category\.color/);
  assert.doesNotMatch(card, /categoryById/);
  const haptics = read('src/components/haptics.ts');
  assert.match(haptics, /performAndroidHapticsAsync/);
  assert.match(haptics, /AndroidHaptics\.Segment_Frequent_Tick/);
  assert.match(haptics, /AndroidHaptics\.Confirm/);
  assert.doesNotMatch(haptics, /Vibration/);
  assert.match(read('package.json'), /"expo-haptics": "~55\.0\.18"/);
  assert.match(read('src/data/medicine-store.tsx'), /category-definitions-v1/);
  assert.match(read('src/data/medicine-store.tsx'), /valid\.filter\(\(item\) => !current\.some/);
  assert.match(read('src/data/medicine-store.tsx'), /MAX_CATEGORY_DEFINITIONS/);
  assert.match(read('src/data/backup.ts'), /categories\?: Category\[\]/);
});
