import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import React from 'react';
import { Alert, Pressable, ScrollView, Switch, Text, View } from 'react-native';

import { isMedicine } from '@/data/medicine';
import { useMedicines } from '@/data/medicine-store';

function SettingButton({ label, onPress }: { label: string; onPress(): void }) {
  return <Pressable onPress={onPress} style={({ pressed }) => ({ minHeight: 52, borderRadius: 13, paddingHorizontal: 15, backgroundColor: pressed ? '#dceae3' : '#ffffff', justifyContent: 'center' })}><Text style={{ color: '#234a3c', fontSize: 16, fontWeight: '600' }}>{label}</Text></Pressable>;
}

export default function SettingsScreen() {
  const { items, largeText, setLargeText, merge } = useMedicines();

  const backup = async () => {
    try {
      if (!(await Sharing.isAvailableAsync())) throw new Error('Sharing is not available on this device.');
      const uri = `${FileSystem.cacheDirectory}pharmacy-pocket-${new Date().toISOString().slice(0, 10)}.json`;
      await FileSystem.writeAsStringAsync(uri, JSON.stringify({ version: 1, exportedAt: new Date().toISOString(), medicines: items }, null, 2));
      await Sharing.shareAsync(uri, { mimeType: 'application/json', dialogTitle: 'Save Pharmacy Pocket backup' });
    } catch (error) {
      Alert.alert('Backup failed', error instanceof Error ? error.message : 'Please try again.');
    }
  };

  const restore = async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({ type: 'application/json', copyToCacheDirectory: true });
      if (result.canceled) return;
      const data = JSON.parse(await FileSystem.readAsStringAsync(result.assets[0].uri));
      if (!Array.isArray(data.medicines) || data.medicines.length > 5000 || !data.medicines.every(isMedicine)) throw new Error('This is not a valid Pharmacy Pocket backup.');
      Alert.alert('Merge backup?', 'Matching medicines will be replaced with the values in this backup.', [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Merge', onPress: () => void merge(data.medicines).then(() => Alert.alert('Backup restored')) },
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
    <SettingButton label="Download / share backup" onPress={() => void backup()} />
    <SettingButton label="Restore / merge backup" onPress={() => void restore()} />
    <View style={{ backgroundColor: '#e7efea', borderRadius: 15, padding: 16, gap: 8 }}>
      <Text selectable style={{ color: '#315247', fontWeight: '800' }}>About your data</Text>
      <Text selectable style={{ color: '#60766d', fontSize: 13, lineHeight: 20 }}>Arabic names and prices were imported from your supplied list. Download a backup after important changes.</Text>
      <Text selectable style={{ color: '#60766d', fontSize: 13, lineHeight: 20 }}>Descriptions are reference notes and are not verified clinical guidance.</Text>
    </View>
  </ScrollView>;
}
