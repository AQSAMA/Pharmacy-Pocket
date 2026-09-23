import { router } from 'expo-router';
import React, { useCallback, useDeferredValue, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, ScrollView, StyleSheet, Text, View, type ListRenderItemInfo } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { FloatingSearch } from '@/components/floating-search';
import { MedicineCard } from '@/components/medicine-card';
import { categories, categoryById } from '@/data/categories';
import { medicineSortOptions, type Medicine, type MedicineSort } from '@/data/medicine';
import { buildMedicineSearchIndex, filterSortedMedicines, listSubcategories, sortMedicineSearchIndex, subcategoryKey, subcategoryLabel, type MedicineFilters } from '@/data/medicine-query';
import { useMedicines } from '@/data/medicine-store';

type ListRow = { key: string; kind: 'header'; section: MedicineSection } | { key: string; kind: 'medicine'; item: Medicine; first: boolean; last: boolean };

type MedicineSection = { key: string; groupKey: string; title: string; category: string; data: Medicine[] };

export default function HomeScreen() {
  const insets = useSafeAreaInsets();
  const { items, ready, largeText, currency, setLargeText, favorite } = useMedicines();
  const [category, setCategory] = useState('all');
  const [selectedSubcategoryKey, setSelectedSubcategoryKey] = useState<string | null>(null);
  const [sort, setSort] = useState<MedicineSort>('default');
  const [query, setQuery] = useState('');
  const [favoritesOnly, setFavoritesOnly] = useState(false);
  const [controlsOpen, setControlsOpen] = useState(false);
  const list = useRef<FlatList<ListRow>>(null);
  const deferredQuery = useDeferredValue(query);
  const searchIndex = useMemo(() => buildMedicineSearchIndex(items), [items]);
  const sortedIndex = useMemo(() => sortMedicineSearchIndex(searchIndex, sort), [searchIndex, sort]);
  useEffect(() => { list.current?.scrollToOffset({ offset: 0, animated: false }); }, [category, selectedSubcategoryKey, sort, deferredQuery, favoritesOnly]);

  const subcategories = useMemo(() => {
    return listSubcategories(searchIndex, category);
  }, [category, searchIndex]);

  useEffect(() => {
    if (selectedSubcategoryKey !== null && !subcategories.some((option) => option.key === selectedSubcategoryKey)) setSelectedSubcategoryKey(null);
  }, [selectedSubcategoryKey, subcategories]);

  const filters = useMemo<MedicineFilters>(() => ({ category, subcategoryKey: selectedSubcategoryKey, favoritesOnly }), [category, favoritesOnly, selectedSubcategoryKey]);
  const visibleMedicines = useMemo(() => filterSortedMedicines(sortedIndex, filters, deferredQuery), [deferredQuery, filters, sortedIndex]);

  const sections = useMemo(() => {
    const runs: MedicineSection[] = [];
    for (const item of visibleMedicines) {
      const key = subcategoryKey(item.subcategory);
      const current = runs[runs.length - 1];
      if (current?.category === item.category && current.groupKey === key) {
        current.data.push(item);
        continue;
      }
      runs.push({
        key: `run:${runs.length}:${item.category}:${key}`,
        groupKey: key,
        title: subcategoryLabel(item.subcategory),
        category: item.category,
        data: [item],
      });
    }
    return runs;
  }, [visibleMedicines]);

  const visibleCount = visibleMedicines.length;
  const activeControlCount = Number(favoritesOnly) + Number(sort !== 'default');
  const stepCategory = useCallback((direction: number) => {
    const current = categories.findIndex((item) => item.id === category);
    setCategory(categories[(current + direction + categories.length) % categories.length].id);
    setSelectedSubcategoryKey(null);
  }, [category]);
  const selectCategory = useCallback((next: string) => {
    setCategory(next);
    setSelectedSubcategoryKey(null);
  }, []);
  const rows = useMemo<ListRow[]>(() => sections.flatMap((section) => [
    { key: 'header:' + section.key, kind: 'header' as const, section },
    ...section.data.map((item, index) => ({ key: 'medicine:' + item.id, kind: 'medicine' as const, item, first: index === 0, last: index === section.data.length - 1 })),
  ]), [sections]);

  const renderRow = useCallback(({ item: row }: ListRenderItemInfo<ListRow>) => {
    if (row.kind === 'header') {
      const selected = categoryById(row.section.category);
      return <View style={styles.sectionHeader}>
        <View style={styles.breadcrumbRow}>
          <View style={[styles.breadcrumbChip, { borderColor: selected.color }]}>
            <Text numberOfLines={1} style={[styles.breadcrumbText, styles.categoryBreadcrumbText]}>{selected.arabic}</Text>
          </View>
          <Text accessibilityElementsHidden importantForAccessibility="no" style={styles.breadcrumbSeparator}>/</Text>
          <View style={[styles.breadcrumbChip, styles.subcategoryChip]}>
            <Text numberOfLines={1} ellipsizeMode="tail" style={styles.breadcrumbText}>{row.section.title}</Text>
          </View>
          <Text accessibilityElementsHidden importantForAccessibility="no" style={styles.breadcrumbSeparator}>/</Text>
          <View accessibilityLabel={`${row.section.data.length} medicines`} style={[styles.breadcrumbChip, styles.countChip]}>
            <Text style={styles.countText}>{row.section.data.length}</Text>
          </View>
        </View>
      </View>;
    }
    return <View style={styles.cardContainer}><MedicineCard item={row.item} large={largeText} currency={currency} first={row.first} last={row.last} onFavorite={favorite} /></View>;
  }, [currency, favorite, largeText]);

  if (!ready) return <View style={{ flex: 1, backgroundColor: '#f4f7f6', justifyContent: 'center' }}><ActivityIndicator color="#126052" size="large" /></View>;

  return <View style={[styles.screen, { paddingTop: insets.top }]}>
    <View style={styles.searchDock}>
      <View style={styles.searchToolbar}>
        <Pressable accessibilityLabel="Settings" onPress={() => router.push('/settings')} style={({ pressed }) => [styles.settingsButton, { backgroundColor: pressed ? '#dceae3' : '#ffffff' }]}><Text style={{ color: '#587067', fontSize: 22 }}>•••</Text></Pressable>
        <FloatingSearch query={query} onChangeQuery={setQuery} />
      </View>
    </View>
    <FlatList
      ref={list}
      style={{ flex: 1 }}
      data={rows}
      keyExtractor={(item) => item.key}
      keyboardShouldPersistTaps="handled"
      keyboardDismissMode="on-drag"
      initialNumToRender={10}
      maxToRenderPerBatch={10}
      updateCellsBatchingPeriod={24}
      windowSize={15}
      removeClippedSubviews={false}
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={{ paddingBottom: 16 }}
      ListHeaderComponent={<View style={styles.listHeader}>
        <View style={{ minHeight: 40, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
          <Text style={{ color: '#71827a', fontSize: 13, fontVariant: ['tabular-nums'] }}>{visibleCount} medicines</Text>
          <Pressable accessibilityRole="button" accessibilityState={{ expanded: controlsOpen }} onPress={() => setControlsOpen((value) => !value)} style={({ pressed }) => ({ minHeight: 38, justifyContent: 'center', paddingHorizontal: 13, borderRadius: 11, backgroundColor: controlsOpen ? '#103e3b' : pressed ? '#e5eee9' : '#ffffff' })}><Text style={{ color: controlsOpen ? '#ffffff' : '#315b49', fontSize: 13, fontWeight: '700' }}>{'Filters & sort' + (activeControlCount ? ' · ' + activeControlCount : '')}</Text></Pressable>
        </View>
        {controlsOpen ? <ScrollView horizontal showsHorizontalScrollIndicator={false} keyboardShouldPersistTaps="handled" contentContainerStyle={{ gap: 6, paddingBottom: 2 }}>
          <Pressable accessibilityRole="togglebutton" accessibilityState={{ checked: favoritesOnly }} onPress={() => setFavoritesOnly((value) => !value)} style={{ minHeight: 38, justifyContent: 'center', paddingHorizontal: 12, borderRadius: 10, backgroundColor: favoritesOnly ? '#dceee4' : '#ffffff' }}><Text style={{ color: '#315b49', fontSize: 13, fontWeight: '700' }}>☆ Favorites</Text></Pressable>
          <Pressable accessibilityRole="togglebutton" accessibilityState={{ checked: largeText }} onPress={() => setLargeText(!largeText)} style={{ minHeight: 38, justifyContent: 'center', paddingHorizontal: 12, borderRadius: 10, backgroundColor: largeText ? '#dceee4' : '#ffffff' }}><Text style={{ color: '#315b49', fontSize: 13, fontWeight: '700' }}>T Large</Text></Pressable>
          {medicineSortOptions.map((option) => <Pressable key={option.id} accessibilityRole="button" accessibilityState={{ selected: sort === option.id }} onPress={() => setSort(option.id)} style={{ minHeight: 38, justifyContent: 'center', paddingHorizontal: 12, borderRadius: 10, backgroundColor: sort === option.id ? '#103e3b' : '#ffffff' }}><Text style={{ color: sort === option.id ? '#ffffff' : '#536c62', fontSize: 13, fontWeight: '700' }}>{option.label}</Text></Pressable>)}
          {activeControlCount ? <Pressable accessibilityRole="button" onPress={() => { setFavoritesOnly(false); setSort('default'); }} style={{ minHeight: 38, justifyContent: 'center', paddingHorizontal: 12, borderRadius: 10, backgroundColor: '#f0e9e3' }}><Text style={{ color: '#795f4c', fontSize: 13, fontWeight: '700' }}>Reset</Text></Pressable> : null}
        </ScrollView> : null}
      </View>}
      renderItem={renderRow}
      ListEmptyComponent={<View style={{ alignItems: 'center', padding: 48, gap: 11 }}><Text style={{ fontSize: 30 }}>{items.length ? '⌕' : '＋'}</Text><Text selectable style={{ color: '#24443a', fontSize: 18, fontWeight: '700' }}>{items.length ? 'No medicines found' : 'Your pocket is empty'}</Text><Text selectable style={{ color: '#71827a', textAlign: 'center', lineHeight: 21 }}>{items.length ? 'Try a shorter name or another category.' : 'Import your web app JSON from Settings, or add your first medicine.'}</Text>{!items.length ? <Pressable onPress={() => router.push('/settings')} style={{ backgroundColor: '#103e3b', borderRadius: 12, paddingHorizontal: 18, minHeight: 46, justifyContent: 'center' }}><Text style={{ color: '#ffffff', fontWeight: '700' }}>Import JSON</Text></Pressable> : null}</View>}
    />
    <View style={{ paddingBottom: Math.max(insets.bottom, 9), paddingTop: 9, backgroundColor: '#ffffff', borderTopWidth: 1, borderTopColor: '#dce5e1', gap: 7 }}>
      <ScrollView horizontal style={{ flexGrow: 0, flexShrink: 0 }} showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 10, gap: 6 }}>
        {categories.map((item) => <Pressable key={item.id} onPress={() => selectCategory(item.id)} style={{ minHeight: 42, justifyContent: 'center', paddingHorizontal: 15, borderRadius: 11, backgroundColor: category === item.id ? '#103e3b' : '#eff4f1' }}><Text style={{ color: category === item.id ? '#ffffff' : '#536c62', fontSize: 16, writingDirection: 'rtl' }}>{item.arabic}</Text></Pressable>)}
      </ScrollView>
      {subcategories.length ? <ScrollView horizontal style={{ flexGrow: 0, flexShrink: 0 }} showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 10, gap: 6 }}>
        <Pressable onPress={() => setSelectedSubcategoryKey(null)} style={{ minHeight: 38, justifyContent: 'center', paddingHorizontal: 13, borderRadius: 10, backgroundColor: selectedSubcategoryKey === null ? '#315b49' : '#f4f7f6' }}><Text style={{ color: selectedSubcategoryKey === null ? '#ffffff' : '#5d736a', fontSize: 14 }}>All subcategories</Text></Pressable>
        {subcategories.map((option) => <Pressable key={option.key} onPress={() => setSelectedSubcategoryKey(option.key)} style={{ minHeight: 38, justifyContent: 'center', paddingHorizontal: 13, borderRadius: 10, backgroundColor: selectedSubcategoryKey === option.key ? '#315b49' : '#f4f7f6' }}><Text style={{ color: selectedSubcategoryKey === option.key ? '#ffffff' : '#5d736a', fontSize: 14 }}>{option.label}</Text></Pressable>)}
      </ScrollView> : null}
      <View style={{ flexDirection: 'row', gap: 7, paddingHorizontal: 10 }}>
        <Pressable accessibilityLabel="Previous category" onPress={() => stepCategory(-1)} style={{ width: 46, height: 44, borderRadius: 11, backgroundColor: '#eff4f1', alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#315b49', fontSize: 22 }}>‹</Text></Pressable>
        <Pressable onPress={() => router.push({ pathname: '/edit', params: { id: 'new', category: category === 'all' ? 'syrups' : category } })} style={{ flex: 1, height: 44, borderRadius: 11, backgroundColor: '#dcefe1', alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#175d3f', fontSize: 16, fontWeight: '700' }}>＋ Add medicine</Text></Pressable>
        <Pressable accessibilityLabel="Next category" onPress={() => stepCategory(1)} style={{ width: 46, height: 44, borderRadius: 11, backgroundColor: '#eff4f1', alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#315b49', fontSize: 22 }}>›</Text></Pressable>
      </View>
    </View>
  </View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f4f7f6' },
  searchDock: {
    backgroundColor: '#f4f7f6',
    paddingTop: 8,
    paddingBottom: 6,
    paddingHorizontal: 16,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#e0e8e4',
  },
  searchToolbar: { minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: 8 },
  settingsButton: {
    width: 48,
    height: 48,
    borderRadius: 15,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
  },
  listHeader: { paddingTop: 6, paddingHorizontal: 16, gap: 8 },
  sectionHeader: { paddingHorizontal: 16, paddingTop: 16, paddingBottom: 9 },
  breadcrumbRow: {
    minHeight: 30,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-start',
    gap: 6,
    direction: 'ltr',
  },
  breadcrumbChip: {
    minHeight: 30,
    justifyContent: 'center',
    paddingHorizontal: 10,
    borderRadius: 9,
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: '#d9e4df',
    backgroundColor: '#ffffff',
  },
  subcategoryChip: { flexShrink: 1, backgroundColor: '#f8faf9' },
  breadcrumbText: {
    color: '#24443a',
    fontSize: 14,
    fontWeight: '700',
    textAlign: 'left',
    writingDirection: 'auto',
  },
  categoryBreadcrumbText: { writingDirection: 'rtl' },
  breadcrumbSeparator: { color: '#91a099', fontSize: 15, fontWeight: '700' },
  countChip: { minWidth: 34, alignItems: 'center', backgroundColor: '#eaf3ee' },
  countText: { color: '#315b49', fontSize: 13, fontWeight: '800', fontVariant: ['tabular-nums'] },
  cardContainer: { marginHorizontal: 16 },
});
