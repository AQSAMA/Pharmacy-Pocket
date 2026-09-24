import { router } from 'expo-router';
import React, { memo } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';

import { actionHaptic, selectionHaptic } from '@/components/haptics';
import { categoryById } from '@/data/categories';
import { formatAddedDate, formatPrice, hasArabic, type Medicine } from '@/data/medicine';

type Props = { item: Medicine; large: boolean; currency: string; first: boolean; last: boolean; onFavorite(item: Medicine): Promise<void> };

function MedicineCardComponent({ item, large, currency, first, last, onFavorite }: Props) {
  const rtl = hasArabic(item.name);
  const category = categoryById(item.category);

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${item.name}, official price ${item.official} ${currency}`}
      onPress={() => {
        actionHaptic();
        router.navigate({ pathname: '/medicine/[id]', params: { id: item.id } });
      }}
      style={({ pressed }) => [styles.card, first && styles.firstCard, last && styles.lastCard, pressed && styles.pressedCard]}
    >
      <View style={[styles.accent, { backgroundColor: category.color }]} />
      <View style={styles.content}>
        <View style={styles.topRow}>
          <View style={styles.titleArea}>
            <Text
              numberOfLines={2}
              style={{
                color: '#173c30',
                fontSize: large ? 27 : 20,
                fontWeight: '700',
                lineHeight: large ? 37 : 28,
                textAlign: rtl ? 'right' : 'left',
                writingDirection: rtl ? 'rtl' : 'ltr',
              }}
            >
              {item.name}
            </Text>
            {!large && item.note ? <Text numberOfLines={2} style={styles.note}>{item.note}</Text> : null}
          </View>
          <View style={styles.actions}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`Edit ${item.name}`}
              onPress={(event) => {
                event.stopPropagation();
                actionHaptic();
                router.push({ pathname: '/edit', params: { id: item.id } });
              }}
              style={({ pressed }) => [styles.iconButton, pressed && styles.iconPressed]}
            >
              <Text style={styles.editGlyph}>✎</Text>
            </Pressable>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`${item.favorite ? 'Remove' : 'Add'} favorite ${item.name}`}
              accessibilityState={{ selected: Boolean(item.favorite) }}
              onPress={(event) => {
                event.stopPropagation();
                selectionHaptic();
                void onFavorite(item).catch(() => Alert.alert('Could not update favorite', 'Your saved medicines are unchanged. Please try again.'));
              }}
              style={({ pressed }) => [styles.iconButton, item.favorite && styles.favoriteButton, pressed && styles.iconPressed]}
            >
              <Text style={[styles.favoriteGlyph, item.favorite && styles.favoriteGlyphActive]}>{item.favorite ? '★' : '☆'}</Text>
            </Pressable>
          </View>
        </View>

        <View style={styles.priceRow}>
          <View style={styles.priceBlock}>
            <Text style={styles.priceLabel}>OFFICIAL</Text>
            <View style={styles.priceValueRow}>
              <Text style={[styles.officialPrice, { fontSize: large ? 28 : 22 }]}>{formatPrice(item.official)}</Text>
              <Text style={styles.currency}>{currency}</Text>
            </View>
          </View>
          {item.discounted !== null ? (
            <View style={[styles.priceBlock, styles.secondaryPriceBlock]}>
              <Text style={[styles.priceLabel, item.discounted > item.official && styles.verifyLabel]}>{item.discounted > item.official ? 'VERIFY' : 'IF ASKED'}</Text>
              <Text style={[styles.discountPrice, { fontSize: large ? 26 : 20 }]}>{formatPrice(item.discounted)}</Text>
            </View>
          ) : null}
        </View>

        <View style={styles.metaRow}>
          <Text style={styles.metaText}>{category.label}</Text>
          <Text style={styles.metaDot}>•</Text>
          <Text style={styles.metaText}>Added {formatAddedDate(item.createdAt)}</Text>
        </View>
      </View>
    </Pressable>
  );
}

export const MedicineCard = memo(MedicineCardComponent);

const styles = StyleSheet.create({
  card: {
    position: 'relative',
    overflow: 'hidden',
    backgroundColor: '#ffffff',
    borderBottomColor: '#e5ece8',
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  firstCard: { borderTopLeftRadius: 18, borderTopRightRadius: 18, borderCurve: 'continuous' },
  lastCard: { borderBottomLeftRadius: 18, borderBottomRightRadius: 18, borderBottomWidth: 0, borderCurve: 'continuous' },
  pressedCard: { backgroundColor: '#f1f7f4' },
  accent: { position: 'absolute', left: 0, top: 0, bottom: 0, width: 4 },
  content: { paddingVertical: 14, paddingLeft: 17, paddingRight: 10, gap: 12 },
  topRow: { flexDirection: 'row', gap: 8, alignItems: 'flex-start' },
  titleArea: { flex: 1, minWidth: 0, gap: 3 },
  note: { color: '#71827a', fontSize: 13, lineHeight: 19 },
  actions: { flexDirection: 'row', gap: 2 },
  iconButton: { width: 48, height: 48, borderRadius: 15, alignItems: 'center', justifyContent: 'center' },
  iconPressed: { backgroundColor: '#e8f0ec' },
  favoriteButton: { backgroundColor: '#fff8e6' },
  editGlyph: { color: '#55746a', fontSize: 19 },
  favoriteGlyph: { color: '#70867e', fontSize: 21 },
  favoriteGlyphActive: { color: '#a87311' },
  priceRow: { flexDirection: 'row', gap: 10, flexWrap: 'wrap' },
  priceBlock: { minWidth: 118, gap: 2 },
  secondaryPriceBlock: { borderLeftWidth: 1, borderLeftColor: '#e7ece9', paddingLeft: 12 },
  priceLabel: { color: '#81928b', fontSize: 10, fontWeight: '800', letterSpacing: 0.8 },
  verifyLabel: { color: '#a94e36' },
  priceValueRow: { flexDirection: 'row', alignItems: 'baseline', gap: 5 },
  officialPrice: { color: '#1c7352', fontWeight: '800', fontVariant: ['tabular-nums'] },
  discountPrice: { color: '#a66c14', fontWeight: '800', fontVariant: ['tabular-nums'] },
  currency: { color: '#81928b', fontSize: 11, fontWeight: '700' },
  metaRow: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 6 },
  metaText: { color: '#687c74', fontSize: 12 },
  metaDot: { color: '#a4b1ac', fontSize: 10 },
});
