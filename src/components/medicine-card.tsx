import { router } from 'expo-router';
import React from 'react';
import { Pressable, Text, View } from 'react-native';

import { formatPrice, hasArabic, type Medicine } from '@/data/medicine';

export function MedicineCard({ item, large, onFavorite }: { item: Medicine; large: boolean; onFavorite(): void }) {
  const rtl = hasArabic(item.name);
  return (
    <View style={{ backgroundColor: '#ffffff', borderBottomColor: '#e7eeea', borderBottomWidth: 1, padding: 15, gap: 11 }}>
      <View style={{ flexDirection: 'row', gap: 10, alignItems: 'flex-start' }}>
        <Pressable accessibilityRole="button" accessibilityLabel={`${item.name}, official price ${item.official} IQD`} onPress={() => router.push({ pathname: '/medicine/[id]', params: { id: item.id } })} style={({ pressed }) => ({ flex: 1, opacity: pressed ? 0.65 : 1, gap: 3 })}>
          <Text selectable style={{ color: '#173c30', fontSize: large ? 27 : 20, fontWeight: '700', lineHeight: large ? 39 : 30, textAlign: rtl ? 'right' : 'left', writingDirection: rtl ? 'rtl' : 'ltr' }}>{item.name}</Text>
          {!large && item.note ? <Text selectable style={{ color: '#71827a', fontSize: 13, lineHeight: 19 }}>{item.note}</Text> : null}
        </Pressable>
        <View style={{ flexDirection: 'row', gap: 4 }}>
          <Pressable accessibilityRole="button" accessibilityLabel={`Edit ${item.name}`} onPress={() => router.push({ pathname: '/edit', params: { id: item.id } })} hitSlop={8} style={({ pressed }) => ({ padding: 9, opacity: pressed ? 0.5 : 1 })}><Text style={{ color: '#55746a', fontSize: 19 }}>✎</Text></Pressable>
          <Pressable accessibilityRole="button" accessibilityLabel={`${item.favorite ? 'Remove' : 'Add'} favorite ${item.name}`} onPress={onFavorite} hitSlop={8} style={({ pressed }) => ({ padding: 9, opacity: pressed ? 0.5 : 1 })}><Text style={{ color: item.favorite ? '#b17d18' : '#70867e', fontSize: 20 }}>{item.favorite ? '★' : '☆'}</Text></Pressable>
        </View>
      </View>
      <View style={{ flexDirection: 'row', gap: 24, alignItems: 'flex-end' }}>
        <View style={{ flexDirection: 'row', gap: 7, alignItems: 'baseline' }}>
          <Text style={{ color: '#71827a', fontSize: 12 }}>Official</Text>
          <Text selectable style={{ color: '#1c7352', fontSize: large ? 28 : 22, fontWeight: '800', fontVariant: ['tabular-nums'] }}>{formatPrice(item.official)}</Text>
        </View>
        {item.discounted !== null ? <View style={{ flexDirection: 'row', gap: 7, alignItems: 'baseline' }}>
          <Text style={{ color: item.discounted > item.official ? '#a94e36' : '#9a7632', fontSize: 12 }}>{item.discounted > item.official ? 'Verify' : 'If asked'}</Text>
          <Text selectable style={{ color: '#a66c14', fontSize: large ? 28 : 22, fontWeight: '800', fontVariant: ['tabular-nums'] }}>{formatPrice(item.discounted)}</Text>
        </View> : null}
      </View>
    </View>
  );
}
