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
const { normalize } = require('../src/data/medicine.ts');
const read = (file) => fs.readFileSync(path.join(__dirname, '..', file), 'utf8');

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

test('Arabic search ignores diacritics and normalizes alef', () => {
  assert.equal(normalize('أَ'), normalize('ا'));
});

test('navigation and scroll regression guards', () => {
  const home = read('src/app/index.tsx');
  assert.match(home, /<FlatList/);
  assert.doesNotMatch(home, /GestureDetector|PanResponder|stickySectionHeadersEnabled/);
  assert.match(home, /removeClippedSubviews=\{false\}/);
  assert.match(home, /paddingTop: insets.top/);
  assert.doesNotMatch(home, /position: 'absolute'/);
  const layout = read('src/app/_layout.tsx');
  assert.doesNotMatch(layout, /formSheet|sheetAllowedDetents/);
  assert.match(layout, /presentation: 'card', animation: 'none'/);
  assert.match(read('src/components/medicine-card.tsx'), /onFavorite\(item\)\.catch/);
});
