import { router } from 'expo-router';
import React, { memo } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';

import { formatAddedDate, formatPrice, hasArabic, type Medicine } from '@/data/medicine';

type Props = { item: Medicine; large: boolean; currency: string; first: boolean; last: boolean; onFavorite(item: Medicine): Promise<void> };

function MedicineCardComponent({ item, large, currency, first, last, onFavorite }: Props) {
  const rtl = hasArabic(item.name);
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={`${item.name}, official price ${item.official} ${currency}`} onPress={() => router.navigate({ pathname: '/medicine/[id]', params: { id: item.id } })} style={({ pressed }) => [styles.card, first && styles.firstCard, last && styles.lastCard, pressed && styles.pressedCard]}>
      <View style={{ flexDirection: 'row', gap: 10, alignItems: 'flex-start' }}>
        <View style={{ flex: 1, gap: 3 }}>
          <Text style={{ color: '#173c30', fontSize: large ? 27 : 20, fontWeight: '700', lineHeight: large ? 39 : 30, textAlign: rtl ? 'right' : 'left', writingDirection: rtl ? 'rtl' : 'ltr' }}>{item.name}</Text>
          {!large && item.note ? <Text style={{ color: '#71827a', fontSize: 13, lineHeight: 19 }}>{item.note}</Text> : null}
        </View>
        <View style={{ flexDirection: 'row', gap: 4 }}>
          <Pressable accessibilityRole="button" accessibilityLabel={`Edit ${item.name}`} onPress={(event) => { event.stopPropagation(); router.push({ pathname: '/edit', params: { id: item.id } }); }} hitSlop={8} style={({ pressed }) => ({ padding: 9, opacity: pressed ? 0.5 : 1 })}><Text style={{ color: '#55746a', fontSize: 19 }}>✎</Text></Pressable>
          <Pressable accessibilityRole="button" accessibilityLabel={`${item.favorite ? 'Remove' : 'Add'} favorite ${item.name}`} onPress={(event) => { event.stopPropagation(); void onFavorite(item).catch(() => Alert.alert('Could not update favorite', 'Your saved medicines are unchanged. Please try again.')); }} hitSlop={8} style={({ pressed }) => ({ padding: 9, opacity: pressed ? 0.5 : 1 })}><Text style={{ color: item.favorite ? '#b17d18' : '#70867e', fontSize: 20 }}>{item.favorite ? '★' : '☆'}</Text></Pressable>
        </View>
      </View>
      <View style={{ flexDirection: 'row', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end' }}>
        <View style={{ flexDirection: 'row', gap: 7, alignItems: 'baseline' }}>
          <Text style={{ color: '#71827a', fontSize: 12 }}>Official</Text>
          <Text style={{ color: '#1c7352', fontSize: large ? 28 : 22, fontWeight: '800', fontVariant: ['tabular-nums'] }}>{formatPrice(item.official)}</Text>
          <Text style={{ color: '#81928b', fontSize: 11 }}>{currency}</Text>
        </View>
        {item.discounted !== null ? <View style={{ flexDirection: 'row', gap: 7, alignItems: 'baseline' }}>
          <Text style={{ color: item.discounted > item.official ? '#a94e36' : '#9a7632', fontSize: 12 }}>{item.discounted > item.official ? 'Verify' : 'If asked'}</Text>
          <Text style={{ color: '#a66c14', fontSize: large ? 28 : 22, fontWeight: '800', fontVariant: ['tabular-nums'] }}>{formatPrice(item.discounted)}</Text>
        </View> : null}
      </View>
      <Text style={{ color: '#60766d', fontSize: large ? 15 : 12 }}>Added {formatAddedDate(item.createdAt)}</Text>
    </Pressable>
  );
}

export const MedicineCard = memo(MedicineCardComponent);

const styles = StyleSheet.create({
  card: { backgroundColor: '#ffffff', borderBottomColor: '#e7eeea', borderBottomWidth: 1, padding: 15, gap: 11 },
  firstCard: { borderTopLeftRadius: 15, borderTopRightRadius: 15 },
  lastCard: { borderBottomLeftRadius: 15, borderBottomRightRadius: 15, borderBottomWidth: 0 },
  pressedCard: { backgroundColor: '#f0f7f3' },
});
