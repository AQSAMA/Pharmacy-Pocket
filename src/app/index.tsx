import { router } from 'expo-router';
import React, { useCallback, useDeferredValue, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, ScrollView, StyleSheet, Text, View, type ListRenderItemInfo } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { FloatingSearch } from '@/components/floating-search';
import { MedicineCard } from '@/components/medicine-card';
import { categories, categoryById } from '@/data/categories';
import { compareMedicines, medicineSortOptions, normalize, type Medicine, type MedicineSort } from '@/data/medicine';
import { useMedicines } from '@/data/medicine-store';

type ListRow = { key: string; kind: 'header'; section: MedicineSection } | { key: string; kind: 'medicine'; item: Medicine; first: boolean; last: boolean };

type MedicineSection = { key: string; title: string; category: string; data: Medicine[] };

export default function HomeScreen() {
  const insets = useSafeAreaInsets();
  const { items, ready, largeText, currency, setLargeText, favorite } = useMedicines();
  const [category, setCategory] = useState('all');
  const [subcategory, setSubcategory] = useState('all');
  const [sort, setSort] = useState<MedicineSort>('name-asc');
  const [query, setQuery] = useState('');
  const [favoritesOnly, setFavoritesOnly] = useState(false);
  const list = useRef<FlatList<ListRow>>(null);
  const deferredQuery = useDeferredValue(query);
  useEffect(() => { list.current?.scrollToOffset({ offset: 0, animated: false }); }, [category, subcategory, sort, deferredQuery, favoritesOnly]);

  const subcategories = useMemo(() => {
    const values = items
      .filter((item) => category === 'all' || item.category === category)
      .map((item) => item.subcategory.trim() || 'General');
    return [...new Map(values.map((value) => [normalize(value), value])).values()]
      .sort((left, right) => left.localeCompare(right, ['ar', 'en'], { sensitivity: 'base' }));
  }, [category, items]);

  useEffect(() => {
    if (subcategory !== 'all' && !subcategories.includes(subcategory)) setSubcategory('all');
  }, [subcategory, subcategories]);

  const sections = useMemo(() => {
    const needle = normalize(deferredQuery.trim());
    const groups = new Map<string, MedicineSection>();
    for (const item of items) {
      if (category !== 'all' && item.category !== category) continue;
      if (subcategory !== 'all' && item.subcategory !== subcategory) continue;
      if (favoritesOnly && !item.favorite) continue;
      if (needle && !normalize(`${item.name} ${item.note} ${item.description ?? ''} ${item.subcategory}`).includes(needle)) continue;
      const key = JSON.stringify([item.category, item.subcategory]);
      const title = item.subcategory === 'General' ? categoryById(item.category).label : item.subcategory;
      const section = groups.get(key) ?? { key, title, category: item.category, data: [] };
      section.data.push(item);
      groups.set(key, section);
    }
    return [...groups.values()].map((section) => ({ ...section, data: [...section.data].sort((left, right) => compareMedicines(left, right, sort)) }));
  }, [items, category, subcategory, sort, deferredQuery, favoritesOnly]);

  const suggestions = useMemo(() => {
    const needle = normalize(query.trim());
    if (!needle) return [];
    return items
      .filter((item) => (category === 'all' || item.category === category) && (subcategory === 'all' || item.subcategory === subcategory))
      .map((item) => ({ name: item.name, normalized: normalize(item.name) }))
      .filter((item) => item.normalized.includes(needle))
      .sort((left, right) => Number(!left.normalized.startsWith(needle)) - Number(!right.normalized.startsWith(needle)) || left.name.localeCompare(right.name, ['ar', 'en'], { sensitivity: 'base' }))
      .filter((item, index, values) => values.findIndex((candidate) => candidate.normalized === item.normalized) === index)
      .slice(0, 5)
      .map((item) => item.name);
  }, [category, items, query, subcategory]);

  const visibleCount = useMemo(() => sections.reduce((count, section) => count + section.data.length, 0), [sections]);
  const stepCategory = useCallback((direction: number) => {
    const current = categories.findIndex((item) => item.id === category);
    setCategory(categories[(current + direction + categories.length) % categories.length].id);
    setSubcategory('all');
  }, [category]);
  const selectCategory = useCallback((next: string) => {
    setCategory(next);
    setSubcategory('all');
  }, []);
  const rows = useMemo<ListRow[]>(() => sections.flatMap((section) => [
    { key: 'header:' + section.key, kind: 'header' as const, section },
    ...section.data.map((item, index) => ({ key: 'medicine:' + item.id, kind: 'medicine' as const, item, first: index === 0, last: index === section.data.length - 1 })),
  ]), [sections]);

  const renderRow = useCallback(({ item: row }: ListRenderItemInfo<ListRow>) => {
    if (row.kind === 'header') {
      const selected = categoryById(row.section.category);
      return <View style={{ paddingHorizontal: 16, paddingTop: 16, paddingBottom: 9, flexDirection: 'row', alignItems: 'center', gap: 9 }}>
        <View style={{ width: 5, height: 19, borderRadius: 4, backgroundColor: selected.color }} />
        <Text style={{ color: '#203b34', fontSize: 15, fontWeight: '800', flex: 1 }}>{row.section.title}</Text>
        <Text style={{ color: '#81928b', fontSize: 12 }}>{row.section.data.length}</Text>
        <Text style={{ color: '#81928b', fontSize: 13, writingDirection: 'rtl' }}>{selected.arabic}</Text>
      </View>;
    }
    return <View style={styles.cardContainer}><MedicineCard item={row.item} large={largeText} currency={currency} first={row.first} last={row.last} onFavorite={favorite} /></View>;
  }, [currency, favorite, largeText]);

  if (!ready) return <View style={{ flex: 1, backgroundColor: '#f4f7f6', justifyContent: 'center' }}><ActivityIndicator color="#126052" size="large" /></View>;

  return <View style={[styles.screen, { paddingTop: insets.top }]}>
    <FlatList
      ref={list}
      style={{ flex: 1 }}
      data={rows}
      keyExtractor={(item) => item.key}
      keyboardShouldPersistTaps="handled"
      keyboardDismissMode="on-drag"
      initialNumToRender={14}
      maxToRenderPerBatch={14}
      updateCellsBatchingPeriod={32}
      windowSize={21}
      removeClippedSubviews={false}
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={{ paddingBottom: 16 }}
      ListHeaderComponent={<View style={{ paddingTop: 11, paddingHorizontal: 16, gap: 11 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', paddingRight: 66 }}>
          <Pressable accessibilityLabel="Settings" onPress={() => router.push('/settings')} style={({ pressed }) => ({ width: 52, height: 52, borderRadius: 14, backgroundColor: pressed ? '#dceae3' : '#ffffff', alignItems: 'center', justifyContent: 'center' })}><Text style={{ color: '#587067', fontSize: 22 }}>•••</Text></Pressable>
        </View>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <Text style={{ color: '#71827a', fontSize: 13 }}>{visibleCount} medicines</Text>
          <View style={{ flexDirection: 'row', gap: 7 }}>
            <Pressable onPress={() => setFavoritesOnly((value) => !value)} style={{ backgroundColor: favoritesOnly ? '#dceee4' : '#ffffff', borderRadius: 10, paddingHorizontal: 11, paddingVertical: 9 }}><Text style={{ color: '#315b49', fontWeight: '600' }}>☆ Favorites</Text></Pressable>
            <Pressable onPress={() => setLargeText(!largeText)} style={{ backgroundColor: largeText ? '#dceee4' : '#ffffff', borderRadius: 10, paddingHorizontal: 11, paddingVertical: 9 }}><Text style={{ color: '#315b49', fontWeight: '600' }}>T Large</Text></Pressable>
          </View>
        </View>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 6, paddingBottom: 8 }}>
          {medicineSortOptions.map((option) => <Pressable key={option.id} accessibilityRole="button" accessibilityState={{ selected: sort === option.id }} onPress={() => setSort(option.id)} style={{ minHeight: 38, justifyContent: 'center', paddingHorizontal: 12, borderRadius: 10, backgroundColor: sort === option.id ? '#103e3b' : '#ffffff' }}><Text style={{ color: sort === option.id ? '#ffffff' : '#536c62', fontSize: 13, fontWeight: '700' }}>{option.label}</Text></Pressable>)}
        </ScrollView>
      </View>}
      renderItem={renderRow}
      ListEmptyComponent={<View style={{ alignItems: 'center', padding: 48, gap: 11 }}><Text style={{ fontSize: 30 }}>{items.length ? '⌕' : '＋'}</Text><Text selectable style={{ color: '#24443a', fontSize: 18, fontWeight: '700' }}>{items.length ? 'No medicines found' : 'Your pocket is empty'}</Text><Text selectable style={{ color: '#71827a', textAlign: 'center', lineHeight: 21 }}>{items.length ? 'Try a shorter name or another category.' : 'Import your web app JSON from Settings, or add your first medicine.'}</Text>{!items.length ? <Pressable onPress={() => router.push('/settings')} style={{ backgroundColor: '#103e3b', borderRadius: 12, paddingHorizontal: 18, minHeight: 46, justifyContent: 'center' }}><Text style={{ color: '#ffffff', fontWeight: '700' }}>Import JSON</Text></Pressable> : null}</View>}
    />
    <View style={{ paddingBottom: Math.max(insets.bottom, 9), paddingTop: 9, backgroundColor: '#ffffff', borderTopWidth: 1, borderTopColor: '#dce5e1', gap: 7 }}>
      <ScrollView horizontal style={{ flexGrow: 0, flexShrink: 0 }} showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 10, gap: 6 }}>
        {categories.map((item) => <Pressable key={item.id} onPress={() => selectCategory(item.id)} style={{ minHeight: 42, justifyContent: 'center', paddingHorizontal: 15, borderRadius: 11, backgroundColor: category === item.id ? '#103e3b' : '#eff4f1' }}><Text style={{ color: category === item.id ? '#ffffff' : '#536c62', fontSize: 16, writingDirection: 'rtl' }}>{item.arabic}</Text></Pressable>)}
      </ScrollView>
      {subcategories.length ? <ScrollView horizontal style={{ flexGrow: 0, flexShrink: 0 }} showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 10, gap: 6 }}>
        <Pressable onPress={() => setSubcategory('all')} style={{ minHeight: 38, justifyContent: 'center', paddingHorizontal: 13, borderRadius: 10, backgroundColor: subcategory === 'all' ? '#315b49' : '#f4f7f6' }}><Text style={{ color: subcategory === 'all' ? '#ffffff' : '#5d736a', fontSize: 14 }}>All subcategories</Text></Pressable>
        {subcategories.map((value) => <Pressable key={value} onPress={() => setSubcategory(value)} style={{ minHeight: 38, justifyContent: 'center', paddingHorizontal: 13, borderRadius: 10, backgroundColor: subcategory === value ? '#315b49' : '#f4f7f6' }}><Text style={{ color: subcategory === value ? '#ffffff' : '#5d736a', fontSize: 14 }}>{value}</Text></Pressable>)}
      </ScrollView> : null}
      <View style={{ flexDirection: 'row', gap: 7, paddingHorizontal: 10 }}>
        <Pressable accessibilityLabel="Previous category" onPress={() => stepCategory(-1)} style={{ width: 46, height: 44, borderRadius: 11, backgroundColor: '#eff4f1', alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#315b49', fontSize: 22 }}>‹</Text></Pressable>
        <Pressable onPress={() => router.push({ pathname: '/edit', params: { id: 'new', category: category === 'all' ? 'syrups' : category } })} style={{ flex: 1, height: 44, borderRadius: 11, backgroundColor: '#dcefe1', alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#175d3f', fontSize: 16, fontWeight: '700' }}>＋ Add medicine</Text></Pressable>
        <Pressable accessibilityLabel="Next category" onPress={() => stepCategory(1)} style={{ width: 46, height: 44, borderRadius: 11, backgroundColor: '#eff4f1', alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#315b49', fontSize: 22 }}>›</Text></Pressable>
      </View>
    </View>
    <FloatingSearch query={query} onChangeQuery={setQuery} suggestions={suggestions} top={insets.top + 8} />
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f4f7f6' },
  cardContainer: { marginHorizontal: 16 },
});
