import { router, useLocalSearchParams } from 'expo-router';
import React from 'react';
import { Pressable, Text, View } from 'react-native';

import { formatPrice, hasArabic } from '@/data/medicine';
import { useMedicines } from '@/data/medicine-store';

export default function CustomerPriceScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { items } = useMedicines();
  const item = items.find((medicine) => medicine.id === id);
  if (!item) return <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}><Text>Medicine not found</Text></View>;
  const rtl = hasArabic(item.name);
  return <View style={{ flex: 1, backgroundColor: '#103e3b', paddingHorizontal: 24, paddingVertical: 40, justifyContent: 'center', alignItems: 'center', gap: 18 }}>
    <Text selectable style={{ color: '#ffffff', fontSize: 30, fontWeight: '800', lineHeight: 45, textAlign: 'center', writingDirection: rtl ? 'rtl' : 'ltr' }}>{item.name}</Text>
    <Text selectable style={{ color: '#a3cfb9', fontSize: 15 }}>OFFICIAL PRICE · IQD</Text>
    <Text selectable adjustsFontSizeToFit numberOfLines={1} style={{ color: '#b8f0cb', fontSize: 72, fontWeight: '900', fontVariant: ['tabular-nums'], letterSpacing: -2 }}>{formatPrice(item.official)}</Text>
    <Text selectable style={{ color: '#ffffff', fontSize: 23, writingDirection: 'rtl' }}>السعر الرسمي</Text>
    <Pressable onPress={() => router.back()} style={{ marginTop: 26, minWidth: 180, minHeight: 52, borderRadius: 14, backgroundColor: '#ffffff1a', alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#ffffff', fontSize: 17, fontWeight: '700' }}>Done</Text></Pressable>
  </View>;
}
