import { router, useLocalSearchParams } from 'expo-router';
import React from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { actionHaptic, selectionHaptic } from '@/components/haptics';
import { categoryById } from '@/data/categories';
import { formatAddedDate, formatPrice, hasArabic } from '@/data/medicine';
import { subcategoryLabel } from '@/data/medicine-query';
import { useMedicines } from '@/data/medicine-store';

export default function CustomerPriceScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { items, currency, largeText, favorite } = useMedicines();
  const item = items.find((medicine) => medicine.id === id);

  if (!item) {
    return (
      <View style={styles.missing}>
        <Text style={styles.missingTitle}>Medicine not found</Text>
        <Pressable onPress={() => router.back()} style={styles.missingButton}><Text style={styles.missingButtonText}>Go back</Text></Pressable>
      </View>
    );
  }

  const rtl = hasArabic(item.name);
  const category = categoryById(item.category);
  const subcategory = subcategoryLabel(item.subcategory);

  return (
    <ScrollView contentInsetAdjustmentBehavior="automatic" contentContainerStyle={styles.content}>
      <View style={styles.hero}>
        <View style={styles.heroTopRow}>
          <View style={[styles.categoryPill, { borderColor: category.color }]}>
            <Text style={styles.categoryPillText}>{category.label}</Text>
          </View>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={item.favorite ? 'Remove from favorites' : 'Add to favorites'}
            accessibilityState={{ selected: Boolean(item.favorite) }}
            onPress={() => {
              selectionHaptic();
              void favorite(item).catch(() => Alert.alert('Could not update favorite', 'Please try again.'));
            }}
            style={({ pressed }) => [styles.favoriteButton, item.favorite && styles.favoriteButtonActive, pressed && styles.pressed]}
          >
            <Text style={[styles.favoriteText, item.favorite && styles.favoriteTextActive]}>{item.favorite ? '★ Favorite' : '☆ Favorite'}</Text>
          </Pressable>
        </View>

        <Text selectable style={[styles.name, { writingDirection: rtl ? 'rtl' : 'ltr' }]}>{item.name}</Text>
        {item.note ? <Text selectable style={styles.note}>{item.note}</Text> : null}

        <View style={styles.primaryPriceCard}>
          <Text style={styles.priceEyebrow}>OFFICIAL PRICE · {currency}</Text>
          <Text selectable adjustsFontSizeToFit numberOfLines={1} style={styles.primaryPrice}>{formatPrice(item.official)}</Text>
        </View>

        {item.discounted !== null ? (
          <View style={styles.secondaryPriceCard}>
            <Text style={[styles.secondaryLabel, item.discounted > item.official && styles.verifyText]}>
              {item.discounted > item.official ? 'VERIFY THIS PRICE' : 'IF CUSTOMER ASKS'}
            </Text>
            <Text selectable style={styles.secondaryPrice}>{formatPrice(item.discounted)} <Text style={styles.secondaryCurrency}>{currency}</Text></Text>
          </View>
        ) : null}
      </View>

      <View style={styles.infoCard}>
        <Text style={styles.sectionTitle}>Medicine info</Text>
        <View style={styles.infoGrid}>
          <View style={styles.infoItem}>
            <Text style={styles.infoLabel}>CATEGORY</Text>
            <Text selectable style={[styles.infoValue, largeText && styles.infoValueLarge]}>{category.label}</Text>
          </View>
          <View style={styles.infoItem}>
            <Text style={styles.infoLabel}>SUBCATEGORY</Text>
            <Text selectable style={[styles.infoValue, largeText && styles.infoValueLarge]}>{subcategory}</Text>
          </View>
          <View style={styles.infoItem}>
            <Text style={styles.infoLabel}>DATE ADDED</Text>
            <Text selectable style={[styles.infoValue, largeText && styles.infoValueLarge]}>{formatAddedDate(item.createdAt)}</Text>
          </View>
        </View>
        <Text style={styles.legacyNote}>Older records may show the date they were imported or migrated.</Text>
      </View>

      {item.description ? (
        <View style={styles.infoCard}>
          <Text style={styles.sectionTitle}>Description</Text>
          <Text selectable style={[styles.description, { writingDirection: hasArabic(item.description) ? 'rtl' : 'ltr', textAlign: hasArabic(item.description) ? 'right' : 'left' }]}>{item.description}</Text>
        </View>
      ) : null}

      <View style={styles.actionRow}>
        <Pressable
          onPress={() => {
            actionHaptic();
            router.push({ pathname: '/edit', params: { id: item.id } });
          }}
          style={({ pressed }) => [styles.primaryAction, pressed && styles.pressed]}
        >
          <Text style={styles.primaryActionText}>✎ Edit medicine</Text>
        </Pressable>
        <Pressable onPress={() => { actionHaptic(); router.back(); }} style={({ pressed }) => [styles.secondaryAction, pressed && styles.pressed]}>
          <Text style={styles.secondaryActionText}>Done</Text>
        </Pressable>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: 18, paddingBottom: 36, gap: 14 },
  missing: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 14, backgroundColor: '#f4f7f6' },
  missingTitle: { color: '#24443a', fontSize: 19, fontWeight: '800' },
  missingButton: { minHeight: 48, paddingHorizontal: 18, borderRadius: 13, backgroundColor: '#103e3b', justifyContent: 'center' },
  missingButtonText: { color: '#ffffff', fontWeight: '800' },
  hero: { backgroundColor: '#103e3b', borderRadius: 26, borderCurve: 'continuous', padding: 20, gap: 14 },
  heroTopRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 10 },
  categoryPill: { minHeight: 36, paddingHorizontal: 12, borderRadius: 12, borderWidth: 1, justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.08)' },
  categoryPillText: { color: '#e8f4ef', fontSize: 12, fontWeight: '800' },
  favoriteButton: { minHeight: 44, paddingHorizontal: 13, borderRadius: 13, justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.09)' },
  favoriteButtonActive: { backgroundColor: '#fff2bf' },
  favoriteText: { color: '#d3e4dc', fontSize: 13, fontWeight: '800' },
  favoriteTextActive: { color: '#8a610e' },
  name: { color: '#ffffff', fontSize: 30, fontWeight: '800', lineHeight: 40, textAlign: 'center' },
  note: { color: '#c4d9d0', fontSize: 14, lineHeight: 21, textAlign: 'center' },
  primaryPriceCard: { alignItems: 'center', paddingVertical: 14, paddingHorizontal: 10, borderRadius: 20, backgroundColor: 'rgba(0,0,0,0.11)' },
  priceEyebrow: { color: '#a3cfb9', fontSize: 12, fontWeight: '800', letterSpacing: 0.8 },
  primaryPrice: { color: '#b8f0cb', fontSize: 64, fontWeight: '900', fontVariant: ['tabular-nums'], letterSpacing: -2 },
  secondaryPriceCard: { alignItems: 'center', gap: 3, paddingVertical: 8 },
  secondaryLabel: { color: '#dfc68c', fontSize: 11, fontWeight: '800', letterSpacing: 0.7 },
  verifyText: { color: '#ffb09c' },
  secondaryPrice: { color: '#ffe2a2', fontSize: 27, fontWeight: '800', fontVariant: ['tabular-nums'] },
  secondaryCurrency: { fontSize: 12, color: '#d7c691' },
  infoCard: { backgroundColor: '#ffffff', borderRadius: 20, borderCurve: 'continuous', padding: 18, gap: 14 },
  sectionTitle: { color: '#24443a', fontSize: 16, fontWeight: '800' },
  infoGrid: { gap: 14 },
  infoItem: { gap: 3 },
  infoLabel: { color: '#8a9993', fontSize: 10, fontWeight: '800', letterSpacing: 0.7 },
  infoValue: { color: '#315247', fontSize: 16, fontWeight: '600' },
  infoValueLarge: { fontSize: 21 },
  legacyNote: { color: '#75877f', fontSize: 12, lineHeight: 18 },
  description: { color: '#536a61', fontSize: 16, lineHeight: 25 },
  actionRow: { gap: 9 },
  primaryAction: { minHeight: 52, borderRadius: 15, backgroundColor: '#dcefe1', alignItems: 'center', justifyContent: 'center' },
  primaryActionText: { color: '#175d3f', fontSize: 16, fontWeight: '800' },
  secondaryAction: { minHeight: 50, borderRadius: 15, backgroundColor: '#ffffff', alignItems: 'center', justifyContent: 'center' },
  secondaryActionText: { color: '#60766d', fontSize: 16, fontWeight: '800' },
  pressed: { opacity: 0.68 },
});
