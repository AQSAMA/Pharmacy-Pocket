import { router, useLocalSearchParams } from 'expo-router';
import React from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';

import { formatPrice, hasArabic } from '@/data/medicine';
import { useMedicines } from '@/data/medicine-store';

export default function CustomerPriceScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { items, currency } = useMedicines();
  const item = items.find((medicine) => medicine.id === id);
  if (!item) return <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}><Text>Medicine not found</Text></View>;
  const rtl = hasArabic(item.name);
  return <ScrollView contentInsetAdjustmentBehavior="automatic" contentContainerStyle={{ padding: 18, paddingBottom: 34, gap: 14 }}>
    <View style={{ backgroundColor: '#103e3b', borderRadius: 24, paddingHorizontal: 22, paddingVertical: 28, alignItems: 'center', gap: 13, borderCurve: 'continuous' }}>
      <Text selectable style={{ color: '#ffffff', fontSize: 28, fontWeight: '800', lineHeight: 42, textAlign: 'center', writingDirection: rtl ? 'rtl' : 'ltr' }}>{item.name}</Text>
      {item.note ? <Text selectable style={{ color: '#c4d9d0', fontSize: 14, lineHeight: 21, textAlign: 'center' }}>{item.note}</Text> : null}
      <Text selectable style={{ color: '#a3cfb9', fontSize: 14 }}>OFFICIAL PRICE · {currency}</Text>
      <Text selectable adjustsFontSizeToFit numberOfLines={1} style={{ color: '#b8f0cb', fontSize: 66, fontWeight: '900', fontVariant: ['tabular-nums'], letterSpacing: -2 }}>{formatPrice(item.official)}</Text>
      {item.discounted !== null ? <View style={{ alignItems: 'center', gap: 3 }}><Text selectable style={{ color: '#dfc68c', fontSize: 12 }}>{item.discounted > item.official ? 'VERIFY PRICE' : 'IF CUSTOMER ASKS'}</Text><Text selectable style={{ color: '#ffe2a2', fontSize: 28, fontWeight: '800', fontVariant: ['tabular-nums'] }}>{formatPrice(item.discounted)}</Text></View> : null}
    </View>
    {item.description ? <View style={{ backgroundColor: '#ffffff', borderRadius: 18, padding: 18, gap: 8, borderCurve: 'continuous' }}><Text selectable style={{ color: '#315247', fontSize: 14, fontWeight: '800' }}>Description</Text><Text selectable style={{ color: '#536a61', fontSize: 16, lineHeight: 25, writingDirection: hasArabic(item.description) ? 'rtl' : 'ltr', textAlign: hasArabic(item.description) ? 'right' : 'left' }}>{item.description}</Text></View> : null}
    <Pressable onPress={() => router.push({ pathname: '/edit', params: { id: item.id } })} style={({ pressed }) => ({ minHeight: 50, borderRadius: 14, backgroundColor: pressed ? '#dceae3' : '#ffffff', alignItems: 'center', justifyContent: 'center' })}><Text style={{ color: '#315b49', fontSize: 16, fontWeight: '700' }}>Edit medicine</Text></Pressable>
    <Pressable onPress={() => router.back()} style={{ minHeight: 48, alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#60766d', fontSize: 16, fontWeight: '700' }}>Done</Text></Pressable>
  </ScrollView>;
}
