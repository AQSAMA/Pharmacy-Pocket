import 'expo-sqlite/localStorage/install';

import React, { createContext, use, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, Text, View } from 'react-native';

import { getMedicines, initializeDatabase, mergeMedicines, replaceMedicines, saveMedicine, toggleFavorite } from './database';
import type { Medicine } from './medicine';
import { createSerialQueue } from './serial-queue';

type Store = {
  items: Medicine[];
  ready: boolean;
  largeText: boolean;
  currency: string;
  setLargeText(value: boolean): void;
  setCurrency(value: string): void;
  save(item: Medicine): Promise<void>;
  favorite(item: Medicine): Promise<void>;
  merge(items: Medicine[]): Promise<void>;
  replace(items: Medicine[]): Promise<void>;
};

const MedicineContext = createContext<Store | null>(null);

export function MedicineProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<Medicine[]>([]);
  const itemsRef = useRef(items);
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
  const refresh = useCallback(async () => updateItems(await getMedicines()), [updateItems]);

  const load = useCallback(async () => {
    setError(null);
    setReady(false);
    try {
      await initializeDatabase();
      await refresh();
      try {
        setLargeTextState(localStorage.getItem('large-text') === 'true');
        setCurrencyState(localStorage.getItem('currency-name')?.trim() || 'IQD');
      } catch {
        Alert.alert('Preferences unavailable', 'Using default display settings. Your medicines have been loaded.');
      }
      setReady(true);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to open local storage.');
    }
  }, [refresh]);

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
    items, ready, largeText, currency, setLargeText, setCurrency, save, favorite, merge, replace,
  }), [items, ready, largeText, currency, setLargeText, setCurrency, save, favorite, merge, replace]);

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
