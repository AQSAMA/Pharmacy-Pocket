import 'expo-sqlite/localStorage/install';

import React, { createContext, use, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, Text, View } from 'react-native';

import {
  MAX_CATEGORY_DEFINITIONS,
  categories as defaultCategories,
  ensureCategoriesForMedicines,
  isCategory,
  mergeCategoryDefinitions,
  type Category,
} from './categories';
import { getMedicines, initializeDatabase, mergeMedicines, replaceMedicines, saveMedicine, toggleFavorite } from './database';
import type { Medicine } from './medicine';
import { createSerialQueue } from './serial-queue';

const CATEGORY_STORAGE_KEY = 'category-definitions-v1';

type Store = {
  items: Medicine[];
  categories: Category[];
  ready: boolean;
  largeText: boolean;
  currency: string;
  setLargeText(value: boolean): void;
  setCurrency(value: string): void;
  saveCategory(category: Category): void;
  importCategories(categories: Category[], mode: 'merge' | 'replace', medicines?: Medicine[]): void;
  save(item: Medicine): Promise<void>;
  favorite(item: Medicine): Promise<void>;
  merge(items: Medicine[]): Promise<void>;
  replace(items: Medicine[]): Promise<void>;
};

const MedicineContext = createContext<Store | null>(null);

function parseStoredCategories(raw: string | null) {
  if (!raw) return defaultCategories;
  const parsed = JSON.parse(raw) as unknown;
  if (!Array.isArray(parsed) || !parsed.every(isCategory)) throw new Error('Stored categories are invalid.');
  return mergeCategoryDefinitions(defaultCategories, parsed);
}

export function MedicineProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<Medicine[]>([]);
  const itemsRef = useRef(items);
  const [categoryItems, setCategoryItems] = useState<Category[]>(defaultCategories);
  const categoryItemsRef = useRef(categoryItems);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [largeText, setLargeTextState] = useState(false);
  const [currency, setCurrencyState] = useState('IQD');
  // Serialize writes AND their refreshes so imports cannot race favorites or edits.
  const queue = useRef(createSerialQueue()).current;

  const updateItems = useCallback((next: Medicine[]) => {
    itemsRef.current = next;
    setItems(next);
  }, []);

  const updateCategories = useCallback((next: Category[], persist = true) => {
    if (persist) localStorage.setItem(CATEGORY_STORAGE_KEY, JSON.stringify(next));
    categoryItemsRef.current = next;
    setCategoryItems(next);
  }, []);

  const refresh = useCallback(async () => updateItems(await getMedicines()), [updateItems]);

  const load = useCallback(async () => {
    setError(null);
    setReady(false);
    try {
      await initializeDatabase();
      const loadedItems = await getMedicines();
      updateItems(loadedItems);
      try {
        setLargeTextState(localStorage.getItem('large-text') === 'true');
        setCurrencyState(localStorage.getItem('currency-name')?.trim() || 'IQD');
        const storedCategories = parseStoredCategories(localStorage.getItem(CATEGORY_STORAGE_KEY));
        const completeCategories = ensureCategoriesForMedicines(storedCategories, loadedItems.map((item) => item.category));
        updateCategories(completeCategories, completeCategories.length !== storedCategories.length);
      } catch {
        const fallbackCategories = ensureCategoriesForMedicines(defaultCategories, loadedItems.map((item) => item.category));
        categoryItemsRef.current = fallbackCategories;
        setCategoryItems(fallbackCategories);
        Alert.alert('Preferences unavailable', 'Using default display settings. Your medicines have been loaded.');
      }
      setReady(true);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to open local storage.');
    }
  }, [updateCategories, updateItems]);

  useEffect(() => { void queue(load); }, [load, queue]);

  const setLargeText = useCallback((next: boolean) => {
    try {
      localStorage.setItem('large-text', String(next));
      setLargeTextState(next);
    } catch { Alert.alert('Could not save preference', 'Please try again.'); }
  }, []);

  const setCurrency = useCallback((value: string) => {
    const next = value.trim() || 'IQD';
    try {
      localStorage.setItem('currency-name', next);
      setCurrencyState(next);
    } catch { Alert.alert('Could not save currency', 'Please try again.'); }
  }, []);

  const saveCategory = useCallback((category: Category) => {
    if (!isCategory(category) || category.id === 'all') throw new Error('Category details are invalid.');
    const current = categoryItemsRef.current;
    const exists = current.some((item) => item.id === category.id);
    if (!exists && current.length - 1 >= MAX_CATEGORY_DEFINITIONS) {
      throw new Error(`You can store up to ${MAX_CATEGORY_DEFINITIONS} categories.`);
    }
    const next = mergeCategoryDefinitions(current, [category]);
    updateCategories(next);
  }, [updateCategories]);

  const importCategories = useCallback((incoming: Category[], mode: 'merge' | 'replace', medicines: Medicine[] = []) => {
    const valid = incoming.filter((item) => isCategory(item) && item.id !== 'all');
    const current = categoryItemsRef.current;
    const definitions = mode === 'replace'
      ? mergeCategoryDefinitions(defaultCategories, valid)
      : mergeCategoryDefinitions(
          current,
          valid.filter((item) => !current.some((existing) => existing.id === item.id)),
        );
    const complete = ensureCategoriesForMedicines(definitions, medicines.map((item) => item.category));
    updateCategories(complete);
  }, [updateCategories]);

  const save = useCallback((item: Medicine) => queue(async () => {
    await saveMedicine(item);
    await refresh();
  }), [queue, refresh]);

  const favorite = useCallback((item: Medicine) => queue(async () => {
    const current = itemsRef.current.find((candidate) => candidate.id === item.id);
    if (!current) return;
    const next = !current.favorite;
    // Persist first; failed writes do not leave the UI claiming data was saved.
    await toggleFavorite(item.id, next);
    updateItems(itemsRef.current.map((candidate) => candidate.id === item.id ? { ...candidate, favorite: next } : candidate));
  }), [queue, updateItems]);

  const merge = useCallback((next: Medicine[]) => queue(async () => {
    await mergeMedicines(next);
    await refresh();
  }), [queue, refresh]);

  const replace = useCallback((next: Medicine[]) => queue(async () => {
    await replaceMedicines(next);
    await refresh();
  }), [queue, refresh]);

  const value = useMemo<Store>(() => ({
    items,
    categories: categoryItems,
    ready,
    largeText,
    currency,
    setLargeText,
    setCurrency,
    saveCategory,
    importCategories,
    save,
    favorite,
    merge,
    replace,
  }), [
    items,
    categoryItems,
    ready,
    largeText,
    currency,
    setLargeText,
    setCurrency,
    saveCategory,
    importCategories,
    save,
    favorite,
    merge,
    replace,
  ]);

  if (error) return <View style={{ flex: 1, justifyContent: 'center', padding: 24, gap: 16, backgroundColor: '#f4f7f6' }}>
    <Text style={{ fontSize: 22, fontWeight: '700' }}>Could not open your medicines</Text>
    <Text selectable>{error}</Text>
    <Text>Your data has not been cleared. Please retry; do not uninstall the app.</Text>
    <Pressable accessibilityRole="button" onPress={() => void queue(load)} style={{ padding: 18, backgroundColor: '#dcefe1', borderRadius: 12 }}><Text>Retry</Text></Pressable>
  </View>;

  if (!ready) return <View style={{ flex: 1, justifyContent: 'center', backgroundColor: '#f4f7f6' }}><ActivityIndicator size="large" color="#126052" /></View>;

  return <MedicineContext value={value}>{children}</MedicineContext>;
}

export function useMedicines() {
  const value = use(MedicineContext);
  if (!value) throw new Error('useMedicines must be used inside MedicineProvider');
  return value;
}
