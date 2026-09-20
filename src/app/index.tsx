import { router } from 'expo-router';
import React, { useMemo, useRef, useState } from 'react';
import { ActivityIndicator, PanResponder, Pressable, ScrollView, SectionList, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { MedicineCard } from '@/components/medicine-card';
import { categories, categoryById } from '@/data/categories';
import { normalize, type Medicine } from '@/data/medicine';
import { useMedicines } from '@/data/medicine-store';

type MedicineSection = { key: string; title: string; category: string; data: Medicine[] };

export default function HomeScreen() {
  const insets = useSafeAreaInsets();
  const { items, ready, largeText, setLargeText, favorite } = useMedicines();
  const [category, setCategory] = useState('all');
  const [query, setQuery] = useState('');
  const [favoritesOnly, setFavoritesOnly] = useState(false);
  const categoryStrip = useRef<ScrollView>(null);

  const sections = useMemo(() => {
    const needle = normalize(query.trim());
    const groups = new Map<string, MedicineSection>();
    for (const item of items) {
      if (category !== 'all' && item.category !== category) continue;
      if (favoritesOnly && !item.favorite) continue;
      if (needle && !normalize(`${item.name} ${item.note} ${item.subcategory}`).includes(needle)) continue;
      const key = `${item.category}|${item.subcategory}`;
      const title = item.subcategory === 'General' ? categoryById(item.category).label : item.subcategory;
      const section = groups.get(key) ?? { key, title, category: item.category, data: [] };
      section.data.push(item);
      groups.set(key, section);
    }
    return [...groups.values()];
  }, [items, category, query, favoritesOnly]);

  const visibleCount = sections.reduce((count, section) => count + section.data.length, 0);
  const stepCategory = (direction: number) => {
    const current = categories.findIndex((item) => item.id === category);
    setCategory(categories[(current + direction + categories.length) % categories.length].id);
  };
  const pan = useMemo(() => PanResponder.create({
    onMoveShouldSetPanResponder: (_, gesture) => Math.abs(gesture.dx) > 25 && Math.abs(gesture.dx) > Math.abs(gesture.dy) * 1.5,
    onPanResponderRelease: (_, gesture) => { if (Math.abs(gesture.dx) > 75 && !query) stepCategory(gesture.dx < 0 ? 1 : -1); },
  }), [category, query]);

  if (!ready) return <View style={{ flex: 1, backgroundColor: '#f4f7f6', justifyContent: 'center' }}><ActivityIndicator color="#126052" size="large" /></View>;

  return <View style={{ flex: 1, backgroundColor: '#f4f7f6' }} {...pan.panHandlers}>
    <SectionList
      sections={sections}
      keyExtractor={(item) => item.id}
      keyboardShouldPersistTaps="handled"
      stickySectionHeadersEnabled
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={{ paddingBottom: 146 + insets.bottom }}
      ListHeaderComponent={<View style={{ paddingTop: insets.top + 13, paddingHorizontal: 16, gap: 16 }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            <View style={{ width: 42, height: 42, borderRadius: 13, backgroundColor: '#103e3b', alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#b8f0cb', fontSize: 23 }}>✚</Text></View>
            <View><Text selectable style={{ color: '#103e3b', fontSize: 20, fontWeight: '800' }}>Pharmacy Pocket</Text><Text selectable style={{ color: '#75877f', fontSize: 12 }}>Your prices · IQD</Text></View>
          </View>
          <Pressable accessibilityLabel="Settings" onPress={() => router.push('/settings')} style={{ padding: 12 }}><Text style={{ color: '#587067', fontSize: 22 }}>•••</Text></Pressable>
        </View>
        <TextInput value={query} onChangeText={setQuery} placeholder="Search Arabic, English, use…" placeholderTextColor="#81908a" autoCapitalize="none" autoCorrect={false} returnKeyType="search" style={{ height: 54, borderWidth: 1, borderColor: '#ceddd5', backgroundColor: '#ffffff', borderRadius: 15, paddingHorizontal: 16, color: '#173c30', fontSize: 17, textAlign: 'auto' }} />
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingBottom: 8 }}>
          <Text selectable style={{ color: '#71827a', fontSize: 13 }}>{visibleCount} medicines</Text>
          <View style={{ flexDirection: 'row', gap: 7 }}>
            <Pressable onPress={() => setFavoritesOnly((value) => !value)} style={{ backgroundColor: favoritesOnly ? '#dceee4' : '#ffffff', borderRadius: 10, paddingHorizontal: 11, paddingVertical: 9 }}><Text style={{ color: '#315b49', fontWeight: '600' }}>☆ Favorites</Text></Pressable>
            <Pressable onPress={() => setLargeText(!largeText)} style={{ backgroundColor: largeText ? '#dceee4' : '#ffffff', borderRadius: 10, paddingHorizontal: 11, paddingVertical: 9 }}><Text style={{ color: '#315b49', fontWeight: '600' }}>T Large</Text></Pressable>
          </View>
        </View>
      </View>}
      renderSectionHeader={({ section }) => { const selected = categoryById(section.category); return <View style={{ backgroundColor: '#f4f7f6', paddingHorizontal: 16, paddingTop: 16, paddingBottom: 9, flexDirection: 'row', alignItems: 'center', gap: 9 }}><View style={{ width: 5, height: 19, borderRadius: 4, backgroundColor: selected.color }} /><Text selectable style={{ color: '#203b34', fontSize: 15, fontWeight: '800', flex: 1 }}>{section.title}</Text><Text selectable style={{ color: '#81928b', fontSize: 12 }}>{section.data.length}</Text><Text selectable style={{ color: '#81928b', fontSize: 13, writingDirection: 'rtl' }}>{selected.arabic}</Text></View>; }}
      renderItem={({ item, index, section }) => <View style={{ marginHorizontal: 16, overflow: 'hidden', borderTopLeftRadius: index === 0 ? 15 : 0, borderTopRightRadius: index === 0 ? 15 : 0, borderBottomLeftRadius: index === section.data.length - 1 ? 15 : 0, borderBottomRightRadius: index === section.data.length - 1 ? 15 : 0 }}><MedicineCard item={item} large={largeText} onFavorite={() => void favorite(item)} /></View>}
      ListEmptyComponent={<View style={{ alignItems: 'center', padding: 48, gap: 11 }}><Text style={{ fontSize: 30 }}>{items.length ? '⌕' : '＋'}</Text><Text selectable style={{ color: '#24443a', fontSize: 18, fontWeight: '700' }}>{items.length ? 'No medicines found' : 'Your pocket is empty'}</Text><Text selectable style={{ color: '#71827a', textAlign: 'center', lineHeight: 21 }}>{items.length ? 'Try a shorter name or another category.' : 'Import your web app JSON from Settings, or add your first medicine.'}</Text>{!items.length ? <Pressable onPress={() => router.push('/settings')} style={{ backgroundColor: '#103e3b', borderRadius: 12, paddingHorizontal: 18, minHeight: 46, justifyContent: 'center' }}><Text style={{ color: '#ffffff', fontWeight: '700' }}>Import JSON</Text></Pressable> : null}</View>}
    />
    <View style={{ position: 'absolute', left: 0, right: 0, bottom: 0, paddingBottom: Math.max(insets.bottom, 9), paddingTop: 9, backgroundColor: '#ffffff', borderTopWidth: 1, borderTopColor: '#dce5e1', gap: 7 }}>
      <ScrollView ref={categoryStrip} horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 10, gap: 6 }}>
        {categories.map((item) => <Pressable key={item.id} onPress={() => setCategory(item.id)} style={{ minHeight: 42, justifyContent: 'center', paddingHorizontal: 15, borderRadius: 11, backgroundColor: category === item.id ? '#103e3b' : '#eff4f1' }}><Text selectable style={{ color: category === item.id ? '#ffffff' : '#536c62', fontSize: 16, writingDirection: 'rtl' }}>{item.arabic}</Text></Pressable>)}
      </ScrollView>
      <View style={{ flexDirection: 'row', gap: 7, paddingHorizontal: 10 }}>
        <Pressable accessibilityLabel="Previous category" onPress={() => stepCategory(-1)} style={{ width: 46, height: 44, borderRadius: 11, backgroundColor: '#eff4f1', alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#315b49', fontSize: 22 }}>‹</Text></Pressable>
        <Pressable onPress={() => router.push({ pathname: '/edit', params: { id: 'new', category: category === 'all' ? 'syrups' : category } })} style={{ flex: 1, height: 44, borderRadius: 11, backgroundColor: '#dcefe1', alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#175d3f', fontSize: 16, fontWeight: '700' }}>＋ Add medicine</Text></Pressable>
        <Pressable accessibilityLabel="Next category" onPress={() => stepCategory(1)} style={{ width: 46, height: 44, borderRadius: 11, backgroundColor: '#eff4f1', alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#315b49', fontSize: 22 }}>›</Text></Pressable>
      </View>
    </View>
  </View>;
}
