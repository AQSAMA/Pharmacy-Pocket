import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import { router } from 'expo-router';
import React, { useEffect, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Switch, Text, TextInput, View } from 'react-native';

import { actionHaptic, confirmHaptic, rejectHaptic, selectionHaptic } from '@/components/haptics';
import { createBackup, parseBackup, type ParsedBackup } from '@/data/backup';
import { useMedicines } from '@/data/medicine-store';

function SettingButton({ icon, label, description, onPress }: { icon: string; label: string; description: string; onPress(): void }) {
  return (
    <Pressable onPress={onPress} style={({ pressed }) => [styles.settingButton, pressed && styles.pressed]}>
      <View style={styles.settingIcon}><Text style={styles.settingIconText}>{icon}</Text></View>
      <View style={{ flex: 1, gap: 2 }}>
        <Text style={styles.settingButtonLabel}>{label}</Text>
        <Text style={styles.settingButtonDescription}>{description}</Text>
      </View>
      <Text style={styles.chevron}>›</Text>
    </Pressable>
  );
}

export default function SettingsScreen() {
  const { items, categories, largeText, currency, setLargeText, setCurrency, importCategories, merge, replace } = useMedicines();
  const [currencyDraft, setCurrencyDraft] = useState(currency);

  useEffect(() => setCurrencyDraft(currency), [currency]);

  const backup = async () => {
    actionHaptic();
    try {
      if (!(await Sharing.isAvailableAsync())) throw new Error('Sharing is not available on this device.');
      const uri = `${FileSystem.cacheDirectory}pharmacy-pocket-${new Date().toISOString().slice(0, 10)}.json`;
      await FileSystem.writeAsStringAsync(uri, JSON.stringify(createBackup(items, currency, categories), null, 2));
      await Sharing.shareAsync(uri, { mimeType: 'application/json', dialogTitle: 'Export Pharmacy Pocket JSON' });
      confirmHaptic();
    } catch (error) {
      rejectHaptic();
      Alert.alert('Backup failed', error instanceof Error ? error.message : 'Please try again.');
    }
  };

  const applyImport = (data: ParsedBackup, mode: 'merge' | 'replace') => {
    const action = mode === 'replace' ? replace(data.medicines) : merge(data.medicines);
    void action
      .then(() => {
        setCurrency(data.currency);
        let categoryWarning = '';
        try {
          importCategories(data.categories, mode, data.medicines);
        } catch {
          categoryWarning = '\n\nCategory names and colors could not be saved.';
        }
        confirmHaptic();
        Alert.alert('Import complete', `${data.medicines.length} medicines and ${data.sections.length} sections were imported.${categoryWarning}`);
      })
      .catch((error) => {
        rejectHaptic();
        Alert.alert('Import failed', error instanceof Error ? error.message : 'Please try again.');
      });
  };

  const restore = async () => {
    actionHaptic();
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
      rejectHaptic();
      Alert.alert('Import failed', error instanceof Error ? error.message : 'Please try again.');
    }
  };

  return (
    <ScrollView contentInsetAdjustmentBehavior="automatic" contentContainerStyle={styles.content}>
      <View style={styles.hero}>
        <Text selectable style={styles.heroCount}>{items.length}</Text>
        <Text selectable style={styles.heroTitle}>medicines stored locally</Text>
        <Text selectable style={styles.heroSubtitle}>Fast, offline-first, and fully exportable.</Text>
        <View style={styles.offlineBadge}><Text style={styles.offlineBadgeText}>● Offline ready</Text></View>
      </View>

      <Text style={styles.sectionLabel}>READING</Text>
      <View style={styles.group}>
        <View style={styles.switchRow}>
          <View style={{ flex: 1, paddingRight: 15, gap: 3 }}>
            <Text selectable style={styles.rowTitle}>Large text</Text>
            <Text selectable style={styles.rowDescription}>Increase medicine names and key prices.</Text>
          </View>
          <Switch
            value={largeText}
            onValueChange={(value) => {
              selectionHaptic();
              setLargeText(value);
            }}
            trackColor={{ false: '#ced8d3', true: '#78ad96' }}
            thumbColor={largeText ? '#103e3b' : '#ffffff'}
          />
        </View>
      </View>

      <Text style={styles.sectionLabel}>PRICING</Text>
      <View style={styles.group}>
        <View style={styles.currencyCard}>
          <Text selectable style={styles.rowTitle}>Currency name</Text>
          <TextInput
            value={currencyDraft}
            onChangeText={setCurrencyDraft}
            onBlur={() => setCurrency(currencyDraft)}
            onSubmitEditing={() => {
              actionHaptic();
              setCurrency(currencyDraft);
            }}
            placeholder="IQD"
            maxLength={24}
            returnKeyType="done"
            autoCapitalize="characters"
            selectTextOnFocus
            style={styles.currencyInput}
          />
          <Text selectable style={styles.rowDescription}>Leave it blank to use IQD.</Text>
        </View>
      </View>

      <Text style={styles.sectionLabel}>CATEGORIES</Text>
      <View style={styles.group}>
        <SettingButton
          icon="◈"
          label="Manage categories"
          description={`${Math.max(0, categories.length - 1)} categories · rename, add, and customize colors.`}
          onPress={() => {
            actionHaptic();
            router.push('/categories');
          }}
        />
      </View>

      <Text style={styles.sectionLabel}>DATA</Text>
      <View style={styles.group}>
        <SettingButton icon="⇧" label="Export JSON" description="Create one portable backup file." onPress={() => void backup()} />
        <View style={styles.separator} />
        <SettingButton icon="⇩" label="Import JSON" description="Merge with this phone or replace everything." onPress={() => void restore()} />
      </View>

      <View style={styles.aboutCard}>
        <Text selectable style={styles.aboutTitle}>About your data</Text>
        <Text selectable style={styles.aboutText}>One JSON file contains medicines, category sections, custom category names/colors, order, favorites, descriptions, and currency. Files exported by the original web app remain supported.</Text>
        <Text selectable style={styles.aboutText}>Descriptions are reference notes and are not verified clinical guidance.</Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: 18, paddingBottom: 50, gap: 12 },
  hero: { backgroundColor: '#103e3b', borderRadius: 24, borderCurve: 'continuous', padding: 20, gap: 5 },
  heroCount: { color: '#b8f0cb', fontSize: 34, fontWeight: '900', fontVariant: ['tabular-nums'] },
  heroTitle: { color: '#ffffff', fontSize: 17, fontWeight: '800' },
  heroSubtitle: { color: '#b7cec4', fontSize: 13, lineHeight: 19 },
  offlineBadge: { alignSelf: 'flex-start', marginTop: 7, minHeight: 32, paddingHorizontal: 10, borderRadius: 11, justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.08)' },
  offlineBadgeText: { color: '#cce5da', fontSize: 12, fontWeight: '700' },
  sectionLabel: { marginTop: 7, marginLeft: 3, color: '#83938c', fontSize: 11, fontWeight: '800', letterSpacing: 0.9 },
  group: { overflow: 'hidden', borderRadius: 18, borderCurve: 'continuous', backgroundColor: '#ffffff' },
  switchRow: { minHeight: 68, paddingHorizontal: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  currencyCard: { padding: 16, gap: 8 },
  rowTitle: { color: '#234a3c', fontSize: 16, fontWeight: '800' },
  rowDescription: { color: '#75857f', fontSize: 13, lineHeight: 18 },
  currencyInput: { minHeight: 50, borderWidth: 1, borderColor: '#ceddd5', borderRadius: 13, paddingHorizontal: 13, color: '#173c30', fontSize: 17, backgroundColor: '#fbfdfc' },
  settingButton: { minHeight: 72, paddingHorizontal: 14, flexDirection: 'row', alignItems: 'center', gap: 12 },
  settingIcon: { width: 44, height: 44, borderRadius: 14, alignItems: 'center', justifyContent: 'center', backgroundColor: '#e9f2ed' },
  settingIconText: { color: '#315b49', fontSize: 21, fontWeight: '800' },
  settingButtonLabel: { color: '#234a3c', fontSize: 16, fontWeight: '800' },
  settingButtonDescription: { color: '#75857f', fontSize: 12, lineHeight: 17 },
  chevron: { color: '#91a099', fontSize: 26 },
  separator: { height: StyleSheet.hairlineWidth, marginLeft: 70, backgroundColor: '#e5ece8' },
  aboutCard: { marginTop: 4, backgroundColor: '#e7efea', borderRadius: 18, borderCurve: 'continuous', padding: 16, gap: 8 },
  aboutTitle: { color: '#315247', fontWeight: '800' },
  aboutText: { color: '#60766d', fontSize: 13, lineHeight: 20 },
  pressed: { backgroundColor: '#eef4f1' },
});
