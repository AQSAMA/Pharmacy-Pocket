import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import React, { useEffect, useState } from 'react';
import { Alert, Pressable, ScrollView, Switch, Text, TextInput, View } from 'react-native';

import { createBackup, parseBackup, type ParsedBackup } from '@/data/backup';
import { useMedicines } from '@/data/medicine-store';

function SettingButton({ label, onPress }: { label: string; onPress(): void }) {
  return <Pressable onPress={onPress} style={({ pressed }) => ({ minHeight: 52, borderRadius: 13, paddingHorizontal: 15, backgroundColor: pressed ? '#dceae3' : '#ffffff', justifyContent: 'center' })}><Text style={{ color: '#234a3c', fontSize: 16, fontWeight: '600' }}>{label}</Text></Pressable>;
}

export default function SettingsScreen() {
  const { items, largeText, currency, setLargeText, setCurrency, merge, replace } = useMedicines();
  const [currencyDraft, setCurrencyDraft] = useState(currency);

  useEffect(() => setCurrencyDraft(currency), [currency]);

  const backup = async () => {
    try {
      if (!(await Sharing.isAvailableAsync())) throw new Error('Sharing is not available on this device.');
      const uri = `${FileSystem.cacheDirectory}pharmacy-pocket-${new Date().toISOString().slice(0, 10)}.json`;
      await FileSystem.writeAsStringAsync(uri, JSON.stringify(createBackup(items, currency), null, 2));
      await Sharing.shareAsync(uri, { mimeType: 'application/json', dialogTitle: 'Export Pharmacy Pocket JSON' });
    } catch (error) {
      Alert.alert('Backup failed', error instanceof Error ? error.message : 'Please try again.');
    }
  };

  const applyImport = (data: ParsedBackup, mode: 'merge' | 'replace') => {
    const action = mode === 'replace' ? replace(data.medicines) : merge(data.medicines);
    void action
      .then(() => {
        setCurrency(data.currency);
        Alert.alert('Import complete', `${data.medicines.length} medicines and ${data.sections.length} sections were imported.`);
      })
      .catch((error) => Alert.alert('Import failed', error instanceof Error ? error.message : 'Please try again.'));
  };

  const restore = async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({ type: 'application/json', copyToCacheDirectory: true });
      if (result.canceled) return;
      const data = parseBackup(JSON.parse(await FileSystem.readAsStringAsync(result.assets[0].uri)));
      Alert.alert('Import Pharmacy Pocket JSON', `${data.medicines.length} medicines · ${data.sections.length} sections\n\nMerge keeps medicines already on this phone. Replace makes the app match the file exactly.`, [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Merge', onPress: () => applyImport(data, 'merge') },
        { text: 'Replace', style: 'destructive', onPress: () => applyImport(data, 'replace') },
      ]);
    } catch (error) {
      Alert.alert('Import failed', error instanceof Error ? error.message : 'Please try again.');
    }
  };

  return <ScrollView contentInsetAdjustmentBehavior="automatic" contentContainerStyle={{ padding: 18, paddingBottom: 50, gap: 14 }}>
    <View style={{ backgroundColor: '#103e3b', borderRadius: 18, padding: 20, gap: 5 }}>
      <Text selectable style={{ color: '#b8f0cb', fontSize: 30, fontWeight: '900', fontVariant: ['tabular-nums'] }}>{items.length}</Text>
      <Text selectable style={{ color: '#ffffff', fontSize: 16, fontWeight: '700' }}>medicines stored on this device</Text>
      <Text selectable style={{ color: '#b7cec4', fontSize: 13, lineHeight: 19 }}>The app works without an internet connection.</Text>
    </View>
    <View style={{ minHeight: 56, borderRadius: 13, backgroundColor: '#ffffff', paddingHorizontal: 15, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
      <View style={{ flex: 1, paddingRight: 15 }}><Text selectable style={{ color: '#234a3c', fontSize: 16, fontWeight: '700' }}>Large text</Text><Text selectable style={{ color: '#75857f', fontSize: 13 }}>For reading from farther away</Text></View>
      <Switch value={largeText} onValueChange={setLargeText} trackColor={{ false: '#ced8d3', true: '#78ad96' }} thumbColor={largeText ? '#103e3b' : '#ffffff'} />
    </View>
    <View style={{ borderRadius: 13, backgroundColor: '#ffffff', padding: 15, gap: 8 }}>
      <Text selectable style={{ color: '#234a3c', fontSize: 16, fontWeight: '700' }}>Currency name</Text>
      <TextInput value={currencyDraft} onChangeText={setCurrencyDraft} onBlur={() => setCurrency(currencyDraft)} onSubmitEditing={() => setCurrency(currencyDraft)} placeholder="IQD" maxLength={24} returnKeyType="done" autoCapitalize="characters" selectTextOnFocus style={{ minHeight: 48, borderWidth: 1, borderColor: '#ceddd5', borderRadius: 11, paddingHorizontal: 13, color: '#173c30', fontSize: 17 }} />
      <Text selectable style={{ color: '#75857f', fontSize: 13 }}>Leave it blank to use IQD.</Text>
    </View>
    <SettingButton label="Export one JSON file" onPress={() => void backup()} />
    <SettingButton label="Import JSON · merge or replace" onPress={() => void restore()} />
    <View style={{ backgroundColor: '#e7efea', borderRadius: 15, padding: 16, gap: 8 }}>
      <Text selectable style={{ color: '#315247', fontWeight: '800' }}>About your data</Text>
      <Text selectable style={{ color: '#60766d', fontSize: 13, lineHeight: 20 }}>One JSON file contains all medicines, category sections, order, and favorites. Files exported by the original web app are supported.</Text>
      <Text selectable style={{ color: '#60766d', fontSize: 13, lineHeight: 20 }}>Descriptions are reference notes and are not verified clinical guidance.</Text>
    </View>
  </ScrollView>;
}
