import { router, useLocalSearchParams } from 'expo-router';
import React, { useMemo, useState } from 'react';
import { Alert, KeyboardAvoidingView, Pressable, ScrollView, Text, View } from 'react-native';

import { FormField } from '@/components/form-field';
import { categories } from '@/data/categories';
import type { Medicine } from '@/data/medicine';
import { useMedicines } from '@/data/medicine-store';

const makeId = () => `med-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

export default function EditMedicineScreen() {
  const params = useLocalSearchParams<{ id?: string; category?: string }>();
  const { items, currency, save } = useMedicines();
  const existing = useMemo(() => items.find((item) => item.id === params.id), [items, params.id]);
  const [name, setName] = useState(existing?.name ?? '');
  const [category, setCategory] = useState(existing?.category ?? params.category ?? 'syrups');
  const [subcategory, setSubcategory] = useState(existing?.subcategory ?? 'General');
  const [official, setOfficial] = useState(existing ? String(existing.official) : '');
  const [discounted, setDiscounted] = useState(existing?.discounted == null ? '' : String(existing.discounted));
  const [note, setNote] = useState(existing?.note ?? '');
  const [description, setDescription] = useState(existing?.description ?? '');
  const [saving, setSaving] = useState(false);
  const existingSubcategories = useMemo(() => {
    const values = items
      .filter((item) => item.category === category)
      .map((item) => item.subcategory.trim())
      .filter(Boolean);
    return [...new Map(values.map((value) => [value.toLocaleLowerCase(), value])).values()]
      .sort((left, right) => left.localeCompare(right, ['ar', 'en'], { sensitivity: 'base' }));
  }, [category, items]);

  const submit = async () => {
    if (saving) return;
    const officialNumber = Number(official);
    const discountedNumber = discounted.trim() ? Number(discounted) : null;
    if (!name.trim() || !official.trim() || !Number.isSafeInteger(officialNumber) || officialNumber < 0 || (discountedNumber !== null && (!Number.isSafeInteger(discountedNumber) || discountedNumber < 0))) {
      Alert.alert('Check the details', `Enter a medicine name and whole-number ${currency} prices.`);
      return;
    }
    const item: Medicine = {
      id: existing?.id ?? makeId(),
      name: name.trim(),
      category,
      subcategory: subcategory.trim() || 'General',
      official: officialNumber,
      discounted: discountedNumber,
      note: note.trim(),
      description: description.trim(),
      revision: existing?.revision ?? 0,
      favorite: existing?.favorite,
      createdAt: existing?.createdAt ?? Date.now(),
    };
    setSaving(true);
    try {
      await save(item);
      router.back();
    } catch {
      Alert.alert('Could not save', 'The medicine remains open. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  return <KeyboardAvoidingView behavior={process.env.EXPO_OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
    <ScrollView contentInsetAdjustmentBehavior="automatic" keyboardShouldPersistTaps="handled" contentContainerStyle={{ padding: 18, paddingBottom: 50, gap: 18 }}>
      <FormField label="Medicine / brand" value={name} onChangeText={setName} autoFocus={!existing} />
      <View style={{ gap: 8 }}>
        <Text selectable style={{ color: '#37544a', fontWeight: '700', fontSize: 14 }}>Category</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 7 }}>
          {categories.slice(1).map((item) => <Pressable key={item.id} onPress={() => setCategory(item.id)} style={{ paddingHorizontal: 14, minHeight: 44, justifyContent: 'center', borderRadius: 11, backgroundColor: category === item.id ? '#103e3b' : '#e7eeea' }}><Text style={{ color: category === item.id ? '#ffffff' : '#435f55', fontWeight: '600' }}>{item.arabic}</Text></Pressable>)}
        </ScrollView>
      </View>
      <FormField label="Subcategory" value={subcategory} onChangeText={setSubcategory} />
      {existingSubcategories.length ? <View style={{ gap: 8 }}>
        <Text selectable style={{ color: '#63776f', fontSize: 13 }}>Or select an existing subcategory</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} keyboardShouldPersistTaps="handled" contentContainerStyle={{ gap: 7 }}>
          {existingSubcategories.map((value) => <Pressable key={value} onPress={() => setSubcategory(value)} style={{ minHeight: 42, justifyContent: 'center', paddingHorizontal: 13, borderRadius: 11, backgroundColor: subcategory === value ? '#dceee4' : '#ffffff', borderWidth: 1, borderColor: subcategory === value ? '#83b39e' : '#d7e2dd' }}><Text style={{ color: '#315b49', fontWeight: '600' }}>{value}</Text></Pressable>)}
        </ScrollView>
      </View> : null}
      <View style={{ flexDirection: 'row', gap: 11 }}>
        <View style={{ flex: 1 }}><FormField label="Official price" value={official} onChangeText={setOfficial} keyboardType="number-pad" /></View>
        <View style={{ flex: 1 }}><FormField label="Discounted price" value={discounted} onChangeText={setDiscounted} keyboardType="number-pad" placeholder="Optional" /></View>
      </View>
      <FormField label="Supplied note" value={note} onChangeText={setNote} multiline numberOfLines={3} style={{ minHeight: 88, textAlignVertical: 'top' }} />
      <FormField label="Description" value={description} onChangeText={setDescription} multiline numberOfLines={5} placeholder="Details shown on the medicine page" style={{ minHeight: 116, textAlignVertical: 'top' }} />
      <Text selectable style={{ color: '#7b8984', fontSize: 13, lineHeight: 19 }}>Prices use {currency}. A blank discounted price means no second price was supplied.</Text>
      <Pressable disabled={saving} onPress={() => void submit()} style={({ pressed }) => ({ minHeight: 52, borderRadius: 13, backgroundColor: '#103e3b', alignItems: 'center', justifyContent: 'center', opacity: saving || pressed ? 0.65 : 1 })}>
        <Text style={{ color: '#ffffff', fontSize: 17, fontWeight: '800' }}>{saving ? 'Saving…' : 'Save medicine'}</Text>
      </Pressable>
    </ScrollView>
  </KeyboardAvoidingView>;
}
