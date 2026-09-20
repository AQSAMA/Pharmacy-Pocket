import 'expo-sqlite/localStorage/install';

import React, { createContext, use, useCallback, useEffect, useMemo, useState } from 'react';

import { getMedicines, initializeDatabase, mergeMedicines, saveMedicine, toggleFavorite } from './database';
import type { Medicine } from './medicine';

type Store = {
  items: Medicine[];
  ready: boolean;
  largeText: boolean;
  setLargeText(value: boolean): void;
  save(item: Medicine): Promise<void>;
  favorite(item: Medicine): Promise<void>;
  merge(items: Medicine[]): Promise<void>;
};

const MedicineContext = createContext<Store | null>(null);

export function MedicineProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<Medicine[]>([]);
  const [ready, setReady] = useState(false);
  const [largeText, setLargeTextState] = useState(false);

  const refresh = useCallback(async () => setItems(await getMedicines()), []);

  useEffect(() => {
    setLargeTextState(localStorage.getItem('large-text') === 'true');
    initializeDatabase()
      .then(refresh)
      .finally(() => setReady(true));
  }, [refresh]);

  const value = useMemo<Store>(
    () => ({
      items,
      ready,
      largeText,
      setLargeText(value) {
        setLargeTextState(value);
        localStorage.setItem('large-text', String(value));
      },
      async save(item) {
        await saveMedicine(item);
        await refresh();
      },
      async favorite(item) {
        await toggleFavorite(item.id, !item.favorite);
        await refresh();
      },
      async merge(next) {
        await mergeMedicines(next);
        await refresh();
      },
    }),
    [items, ready, largeText, refresh],
  );

  return <MedicineContext value={value}>{children}</MedicineContext>;
}

export function useMedicines() {
  const value = use(MedicineContext);
  if (!value) throw new Error('useMedicines must be used inside MedicineProvider');
  return value;
}
