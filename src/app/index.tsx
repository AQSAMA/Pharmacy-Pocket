import { router } from 'expo-router';
import React, { useCallback, useDeferredValue, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, ScrollView, StyleSheet, Text, View, type ListRenderItemInfo } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { FloatingSearch } from '@/components/floating-search';
import { actionHaptic, selectionHaptic } from '@/components/haptics';
import { MedicineCard } from '@/components/medicine-card';
import { categoryById } from '@/data/categories';
import { medicineSortOptions, type Medicine, type MedicineSort } from '@/data/medicine';
import { buildMedicineSearchIndex, filterSortedMedicines, listSubcategories, sortMedicineSearchIndex, subcategoryKey, subcategoryLabel, type MedicineFilters } from '@/data/medicine-query';
import { useMedicines } from '@/data/medicine-store';

type ListRow = { key: string; kind: 'header'; section: MedicineSection } | { key: string; kind: 'medicine'; item: Medicine; first: boolean; last: boolean };

type MedicineSection = { key: string; groupKey: string; title: string; category: string; data: Medicine[] };

export default function HomeScreen() {
  const insets = useSafeAreaInsets();
  const { items, categories, ready, largeText, currency, setLargeText, favorite } = useMedicines();
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

  useEffect(() => {
    if (category !== 'all' && !categories.some((item) => item.id === category)) {
      setCategory('all');
      setSelectedSubcategoryKey(null);
    }
  }, [categories, category]);

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
  const favoriteCount = useMemo(() => items.reduce((count, item) => count + Number(Boolean(item.favorite)), 0), [items]);
  const categoryCounts = useMemo(() => {
    const counts = new Map<string, number>();
    counts.set('all', items.length);
    for (const item of items) {
      if (item.category === 'all') continue;
      counts.set(item.category, (counts.get(item.category) ?? 0) + 1);
    }
    return counts;
  }, [items]);
  const activeControlCount = Number(favoritesOnly) + Number(sort !== 'default') + Number(category !== 'all') + Number(selectedSubcategoryKey !== null) + Number(Boolean(query.trim()));
  const stepCategory = useCallback((direction: number) => {
    selectionHaptic();
    const current = categories.findIndex((item) => item.id === category);
    setCategory(categories[(current + direction + categories.length) % categories.length].id);
    setSelectedSubcategoryKey(null);
  }, [categories, category]);
  const selectCategory = useCallback((next: string) => {
    selectionHaptic();
    setCategory(next);
    setSelectedSubcategoryKey(null);
  }, []);
  const clearViewFilters = useCallback(() => {
    actionHaptic();
    setQuery('');
    setCategory('all');
    setSelectedSubcategoryKey(null);
    setFavoritesOnly(false);
    setSort('default');
  }, []);
  const rows = useMemo<ListRow[]>(() => sections.flatMap((section) => [
    { key: 'header:' + section.key, kind: 'header' as const, section },
    ...section.data.map((item, index) => ({ key: 'medicine:' + item.id, kind: 'medicine' as const, item, first: index === 0, last: index === section.data.length - 1 })),
  ]), [sections]);

  const renderRow = useCallback(({ item: row }: ListRenderItemInfo<ListRow>) => {
    if (row.kind === 'header') {
      const selected = categoryById(row.section.category, categories);
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
          <View accessible accessibilityLabel={`${row.section.data.length} medicines`} style={[styles.breadcrumbChip, styles.countChip]}>
            <Text style={styles.countText}>{row.section.data.length}</Text>
          </View>
        </View>
      </View>;
    }
    return <View style={styles.cardContainer}><MedicineCard item={row.item} category={categoryById(row.item.category, categories)} large={largeText} currency={currency} first={row.first} last={row.last} onFavorite={favorite} /></View>;
  }, [categories, currency, favorite, largeText]);

  if (!ready) return <View style={{ flex: 1, backgroundColor: '#f4f7f6', justifyContent: 'center' }}><ActivityIndicator color="#126052" size="large" /></View>;

  return <View style={[styles.screen, { paddingTop: insets.top }]}>
    <View style={styles.searchDock}>
      <View style={styles.searchToolbar}>
        <Pressable accessibilityRole="button" accessibilityLabel="Settings" onPress={() => { actionHaptic(); router.push('/settings'); }} style={({ pressed }) => [styles.settingsButton, pressed && styles.settingsButtonPressed]}><Text style={styles.settingsGlyph}>⚙</Text></Pressable>
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
        <View style={styles.overviewRow}>
          <View style={{ flex: 1, minWidth: 0 }}>
            <Text style={styles.resultsTitle}>{visibleCount === items.length ? `${visibleCount} medicines` : `${visibleCount} of ${items.length} medicines`}</Text>
            <Text numberOfLines={1} style={styles.resultsSubtitle}>{category === 'all' ? 'All categories' : categoryById(category, categories).label}{selectedSubcategoryKey ? ' · filtered subcategory' : ''}</Text>
          </View>
          <Pressable accessibilityRole="togglebutton" accessibilityLabel={`Show favorites only, ${favoriteCount} favorites`} accessibilityState={{ checked: favoritesOnly }} onPress={() => { selectionHaptic(); setFavoritesOnly((value) => !value); }} style={({ pressed }) => [styles.quickFavorite, favoritesOnly && styles.quickFavoriteActive, pressed && styles.quickControlPressed]}><Text style={[styles.quickFavoriteText, favoritesOnly && styles.quickFavoriteTextActive]}>★ {favoriteCount}</Text></Pressable>
          <Pressable accessibilityRole="button" accessibilityState={{ expanded: controlsOpen }} onPress={() => { actionHaptic(); setControlsOpen((value) => !value); }} style={({ pressed }) => [styles.filterButton, controlsOpen && styles.filterButtonActive, pressed && !controlsOpen && styles.quickControlPressed]}><Text style={[styles.filterButtonText, controlsOpen && styles.filterButtonTextActive]}>{'Tune' + (activeControlCount ? ' · ' + activeControlCount : '')}</Text></Pressable>
        </View>
        {controlsOpen ? <ScrollView horizontal showsHorizontalScrollIndicator={false} keyboardShouldPersistTaps="handled" contentContainerStyle={{ gap: 6, paddingBottom: 2 }}>
          <Pressable accessibilityRole="togglebutton" accessibilityState={{ checked: largeText }} onPress={() => { selectionHaptic(); setLargeText(!largeText); }} style={styles.controlChip}><Text style={styles.controlChipText}>{largeText ? 'T Large ✓' : 'T Large'}</Text></Pressable>
          {medicineSortOptions.map((option) => <Pressable key={option.id} accessibilityRole="button" accessibilityState={{ selected: sort === option.id }} onPress={() => { selectionHaptic(); setSort(option.id); }} style={[styles.controlChip, sort === option.id && styles.controlChipActive]}><Text style={[styles.controlChipText, sort === option.id && styles.controlChipTextActive]}>{option.label}</Text></Pressable>)}
          {activeControlCount ? <Pressable accessibilityRole="button" onPress={clearViewFilters} style={[styles.controlChip, styles.resetChip]}><Text style={styles.resetChipText}>Clear view</Text></Pressable> : null}
        </ScrollView> : null}
      </View>}
      renderItem={renderRow}
      ListEmptyComponent={<View style={{ alignItems: 'center', padding: 48, gap: 11 }}><Text style={{ fontSize: 30 }}>{items.length ? '⌕' : '＋'}</Text><Text selectable style={{ color: '#24443a', fontSize: 18, fontWeight: '700' }}>{items.length ? 'No medicines found' : 'Your pocket is empty'}</Text><Text selectable style={{ color: '#71827a', textAlign: 'center', lineHeight: 21 }}>{items.length ? 'Try a shorter name or another category.' : 'Import your web app JSON from Settings, or add your first medicine.'}</Text>{!items.length ? <Pressable onPress={() => router.push('/settings')} style={{ backgroundColor: '#103e3b', borderRadius: 12, paddingHorizontal: 18, minHeight: 48, justifyContent: 'center' }}><Text style={{ color: '#ffffff', fontWeight: '700' }}>Import JSON</Text></Pressable> : null}</View>}
    />
    <View style={{ paddingBottom: Math.max(insets.bottom, 9), paddingTop: 9, backgroundColor: '#ffffff', borderTopWidth: 1, borderTopColor: '#dce5e1', gap: 7 }}>
      <ScrollView horizontal style={{ flexGrow: 0, flexShrink: 0 }} showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 10, gap: 6 }}>
        {categories.map((item) => <Pressable key={item.id} accessibilityRole="button" accessibilityState={{ selected: category === item.id }} onPress={() => selectCategory(item.id)} style={[styles.categoryChip, category === item.id && styles.categoryChipActive]}>{item.id !== 'all' ? <View style={[styles.categoryDot, { backgroundColor: item.color }]} /> : null}<Text style={[styles.categoryChipLabel, category === item.id && styles.categoryChipLabelActive]}>{item.arabic}</Text><View style={[styles.categoryCount, category === item.id && styles.categoryCountActive]}><Text style={[styles.categoryCountText, category === item.id && styles.categoryCountTextActive]}>{categoryCounts.get(item.id) ?? 0}</Text></View></Pressable>)}
      </ScrollView>
      {subcategories.length ? <ScrollView horizontal style={{ flexGrow: 0, flexShrink: 0 }} showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 10, gap: 6 }}>
        <Pressable onPress={() => { selectionHaptic(); setSelectedSubcategoryKey(null); }} style={[styles.subcategoryChipButton, selectedSubcategoryKey === null && styles.subcategoryChipButtonActive]}><Text style={[styles.subcategoryChipText, selectedSubcategoryKey === null && styles.subcategoryChipTextActive]}>All subcategories</Text></Pressable>
        {subcategories.map((option) => <Pressable key={option.key} onPress={() => { selectionHaptic(); setSelectedSubcategoryKey(option.key); }} style={[styles.subcategoryChipButton, selectedSubcategoryKey === option.key && styles.subcategoryChipButtonActive]}><Text style={[styles.subcategoryChipText, selectedSubcategoryKey === option.key && styles.subcategoryChipTextActive]}>{option.label}</Text></Pressable>)}
      </ScrollView> : null}
      <View style={{ flexDirection: 'row', gap: 7, paddingHorizontal: 10 }}>
        <Pressable accessibilityLabel="Previous category" onPress={() => stepCategory(-1)} style={{ width: 48, height: 48, borderRadius: 11, backgroundColor: '#eff4f1', alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#315b49', fontSize: 22 }}>‹</Text></Pressable>
        <Pressable onPress={() => { actionHaptic(); router.push({ pathname: '/edit', params: { id: 'new', category: category === 'all' ? 'syrups' : category } }); }} style={({ pressed }) => [styles.addButton, pressed && styles.addButtonPressed]}><Text style={styles.addButtonText}>＋ Add medicine</Text></Pressable>
        <Pressable accessibilityLabel="Next category" onPress={() => stepCategory(1)} style={{ width: 48, height: 48, borderRadius: 11, backgroundColor: '#eff4f1', alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#315b49', fontSize: 22 }}>›</Text></Pressable>
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
    width: 50,
    height: 50,
    borderRadius: 16,
    borderCurve: 'continuous',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#ffffff',
    borderWidth: 1,
    borderColor: '#d7e3dd',
  },
  settingsButtonPressed: { backgroundColor: '#e6efeb' },
  settingsGlyph: { color: '#315b49', fontSize: 20 },
  listHeader: { paddingTop: 8, paddingHorizontal: 16, gap: 9 },
  overviewRow: { minHeight: 50, flexDirection: 'row', alignItems: 'center', gap: 8 },
  resultsTitle: { color: '#24443a', fontSize: 15, fontWeight: '800', fontVariant: ['tabular-nums'] },
  resultsSubtitle: { marginTop: 2, color: '#7a8a84', fontSize: 12 },
  quickFavorite: { minWidth: 54, height: 48, borderRadius: 14, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 10, backgroundColor: '#ffffff', borderWidth: 1, borderColor: '#dce5e1' },
  quickFavoriteActive: { backgroundColor: '#fff6da', borderColor: '#ead291' },
  quickFavoriteText: { color: '#73857e', fontSize: 13, fontWeight: '800' },
  quickFavoriteTextActive: { color: '#94680f' },
  filterButton: { minHeight: 48, justifyContent: 'center', paddingHorizontal: 14, borderRadius: 14, backgroundColor: '#ffffff', borderWidth: 1, borderColor: '#dce5e1' },
  filterButtonActive: { backgroundColor: '#103e3b', borderColor: '#103e3b' },
  filterButtonText: { color: '#315b49', fontSize: 13, fontWeight: '800' },
  filterButtonTextActive: { color: '#ffffff' },
  quickControlPressed: { opacity: 0.65 },
  controlChip: { minHeight: 48, justifyContent: 'center', paddingHorizontal: 13, borderRadius: 12, backgroundColor: '#ffffff', borderWidth: 1, borderColor: '#e0e8e4' },
  controlChipActive: { backgroundColor: '#103e3b', borderColor: '#103e3b' },
  controlChipText: { color: '#536c62', fontSize: 13, fontWeight: '700' },
  controlChipTextActive: { color: '#ffffff' },
  resetChip: { backgroundColor: '#f4ece6', borderColor: '#ead9cd' },
  resetChipText: { color: '#795f4c', fontSize: 13, fontWeight: '800' },
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
  categoryBreadcrumbText: { writingDirection: 'auto' },
  breadcrumbSeparator: { color: '#91a099', fontSize: 15, fontWeight: '700' },
  countChip: { minWidth: 34, alignItems: 'center', backgroundColor: '#eaf3ee' },
  countText: { color: '#315b49', fontSize: 13, fontWeight: '800', fontVariant: ['tabular-nums'] },
  cardContainer: { marginHorizontal: 16 },
  categoryChip: { minHeight: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, paddingHorizontal: 13, borderRadius: 13, backgroundColor: '#eff4f1' },
  categoryChipActive: { backgroundColor: '#103e3b' },
  categoryDot: { width: 9, height: 9, borderRadius: 5 },
  categoryChipLabel: { color: '#536c62', fontSize: 15, writingDirection: 'auto', fontWeight: '600' },
  categoryChipLabelActive: { color: '#ffffff' },
  categoryCount: { minWidth: 24, height: 24, paddingHorizontal: 6, borderRadius: 12, alignItems: 'center', justifyContent: 'center', backgroundColor: '#ffffff' },
  categoryCountActive: { backgroundColor: 'rgba(255,255,255,0.14)' },
  categoryCountText: { color: '#667b72', fontSize: 11, fontWeight: '800', fontVariant: ['tabular-nums'] },
  categoryCountTextActive: { color: '#ffffff' },
  subcategoryChipButton: { minHeight: 48, justifyContent: 'center', paddingHorizontal: 13, borderRadius: 11, backgroundColor: '#f4f7f6' },
  subcategoryChipButtonActive: { backgroundColor: '#315b49' },
  subcategoryChipText: { color: '#5d736a', fontSize: 14 },
  subcategoryChipTextActive: { color: '#ffffff', fontWeight: '700' },
  addButton: { flex: 1, height: 48, borderRadius: 13, backgroundColor: '#dcefe1', alignItems: 'center', justifyContent: 'center' },
  addButtonPressed: { backgroundColor: '#cbe5d3' },
  addButtonText: { color: '#175d3f', fontSize: 16, fontWeight: '800' },
});
