import React, { useMemo, useState } from 'react';
import { Alert, KeyboardAvoidingView, Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';

import { actionHaptic, confirmHaptic, rejectHaptic, selectionHaptic } from '@/components/haptics';
import { CATEGORY_COLORS, tintCategoryColor, type Category } from '@/data/categories';
import { useMedicines } from '@/data/medicine-store';

const HEX_COLOR = /^#[0-9a-f]{6}$/i;

function makeCategoryId() {
  return `category-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

export default function CategoriesScreen() {
  const { items, categories, saveCategory } = useMedicines();
  const [draft, setDraft] = useState<Category | null>(null);

  const counts = useMemo(() => {
    const result = new Map<string, number>();
    for (const item of items) result.set(item.category, (result.get(item.category) ?? 0) + 1);
    return result;
  }, [items]);

  const editableCategories = useMemo(() => categories.filter((item) => item.id !== 'all'), [categories]);

  const openNew = () => {
    actionHaptic();
    const color = CATEGORY_COLORS[editableCategories.length % CATEGORY_COLORS.length];
    setDraft({ id: makeCategoryId(), label: '', arabic: '', color });
  };

  const saveDraft = () => {
    if (!draft) return;
    const label = draft.label.trim();
    const chipLabel = draft.arabic.trim() || label;
    const color = draft.color.trim().toLowerCase();
    if (!label || label.length > 48 || chipLabel.length > 32 || !HEX_COLOR.test(color)) {
      rejectHaptic();
      Alert.alert('Check the category', 'Add a name, keep labels reasonably short, and use a color such as #2F856D.');
      return;
    }
    if (categories.some((item) => item.id !== draft.id && item.id !== 'all' && item.label.trim().toLocaleLowerCase() === label.toLocaleLowerCase())) {
      rejectHaptic();
      Alert.alert('Category already exists', 'Choose a different category name.');
      return;
    }
    try {
      saveCategory({ ...draft, label, arabic: chipLabel, color });
      confirmHaptic();
      setDraft(null);
    } catch (error) {
      rejectHaptic();
      Alert.alert('Could not save category', error instanceof Error ? error.message : 'Please try again.');
    }
  };

  return (
    <>
      <ScrollView contentInsetAdjustmentBehavior="automatic" contentContainerStyle={styles.content}>
        <View style={styles.introCard}>
          <Text style={styles.introTitle}>Make categories yours</Text>
          <Text style={styles.introText}>Rename existing categories or create new ones. The color becomes the card accent and a subtle background tint, while text labels remain visible for clarity.</Text>
        </View>

        <Pressable accessibilityRole="button" onPress={openNew} style={({ pressed }) => [styles.addButton, pressed && styles.pressed]}>
          <View style={styles.addIcon}><Text style={styles.addIconText}>＋</Text></View>
          <View style={{ flex: 1 }}>
            <Text style={styles.addTitle}>Add category</Text>
            <Text style={styles.addDescription}>Create a new medicine group with its own color.</Text>
          </View>
        </Pressable>

        <View style={styles.list}>
          {editableCategories.map((category) => {
            const count = counts.get(category.id) ?? 0;
            return (
              <Pressable
                key={category.id}
                accessibilityRole="button"
                accessibilityLabel={`Edit ${category.label}, ${count} medicines`}
                onPress={() => {
                  actionHaptic();
                  setDraft({ ...category });
                }}
                style={({ pressed }) => [
                  styles.categoryCard,
                  { backgroundColor: tintCategoryColor(category.color, 0.085) },
                  pressed && styles.pressed,
                ]}
              >
                <View style={[styles.categoryAccent, { backgroundColor: category.color }]} />
                <View style={styles.categoryBody}>
                  <View style={styles.categoryTop}>
                    <View style={{ flex: 1, minWidth: 0 }}>
                      <Text numberOfLines={1} style={styles.categoryName}>{category.label}</Text>
                      <Text numberOfLines={1} style={styles.categoryChipLabel}>{category.arabic}</Text>
                    </View>
                    <View style={styles.countBadge}><Text style={styles.countText}>{count}</Text></View>
                    <Text style={styles.chevron}>›</Text>
                  </View>
                  <View style={styles.colorMeta}>
                    <View style={[styles.colorDot, { backgroundColor: category.color }]} />
                    <Text style={styles.colorText}>{category.color.toUpperCase()}</Text>
                  </View>
                </View>
              </Pressable>
            );
          })}
        </View>

        <View style={styles.noteCard}>
          <Text style={styles.noteTitle}>Why there is no delete button</Text>
          <Text style={styles.noteText}>Category IDs stay stable when you rename or recolor them, so medicines never lose their category. A safe delete/move flow can be added separately if needed.</Text>
        </View>
      </ScrollView>

      <Modal visible={Boolean(draft)} transparent animationType="fade" onRequestClose={() => setDraft(null)}>
        <KeyboardAvoidingView behavior={process.env.EXPO_OS === 'ios' ? 'padding' : undefined} style={styles.modalBackdrop}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setDraft(null)} />
          {draft ? (
            <View style={styles.editor}>
              <View style={styles.editorHandle} />
              <ScrollView keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false} contentContainerStyle={styles.editorContent}>
              <Text style={styles.editorTitle}>{categories.some((item) => item.id === draft.id) ? 'Edit category' : 'New category'}</Text>

              <View style={[styles.preview, { backgroundColor: tintCategoryColor(draft.color, 0.085) }]}>
                <View style={[styles.previewAccent, { backgroundColor: HEX_COLOR.test(draft.color) ? draft.color : '#758790' }]} />
                <View style={{ flex: 1, gap: 2 }}>
                  <Text style={styles.previewName}>{draft.label.trim() || 'Category name'}</Text>
                  <Text style={styles.previewChip}>{draft.arabic.trim() || draft.label.trim() || 'Short label'}</Text>
                </View>
              </View>

              <View style={styles.field}>
                <Text style={styles.fieldLabel}>Category name</Text>
                <TextInput
                  value={draft.label}
                  onChangeText={(label) => setDraft((current) => current ? { ...current, label } : current)}
                  placeholder="e.g. Inhalers"
                  placeholderTextColor="#8b9994"
                  maxLength={48}
                  autoFocus={!categories.some((item) => item.id === draft.id)}
                  style={styles.input}
                />
              </View>

              <View style={styles.field}>
                <Text style={styles.fieldLabel}>Short / chip label</Text>
                <TextInput
                  value={draft.arabic}
                  onChangeText={(arabic) => setDraft((current) => current ? { ...current, arabic } : current)}
                  placeholder="Arabic or English · defaults to name"
                  placeholderTextColor="#8b9994"
                  maxLength={32}
                  style={styles.input}
                />
              </View>

              <View style={styles.field}>
                <Text style={styles.fieldLabel}>Color</Text>
                <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.palette}>
                  {CATEGORY_COLORS.map((color) => {
                    const selected = color.toLowerCase() === draft.color.toLowerCase();
                    return (
                      <Pressable
                        key={color}
                        accessibilityRole="button"
                        accessibilityLabel={`Use color ${color}`}
                        accessibilityState={{ selected }}
                        onPress={() => {
                          selectionHaptic();
                          setDraft((current) => current ? { ...current, color } : current);
                        }}
                        style={[styles.swatchButton, selected && styles.swatchSelected]}
                      >
                        <View style={[styles.swatch, { backgroundColor: color }]} />
                      </Pressable>
                    );
                  })}
                </ScrollView>
                <TextInput
                  value={draft.color}
                  onChangeText={(color) => setDraft((current) => current ? { ...current, color } : current)}
                  placeholder="#2F856D"
                  placeholderTextColor="#8b9994"
                  autoCapitalize="characters"
                  maxLength={7}
                  style={styles.input}
                />
              </View>

              <View style={styles.editorActions}>
                <Pressable onPress={() => setDraft(null)} style={({ pressed }) => [styles.cancelButton, pressed && styles.pressed]}>
                  <Text style={styles.cancelText}>Cancel</Text>
                </Pressable>
                <Pressable onPress={saveDraft} style={({ pressed }) => [styles.saveButton, pressed && styles.pressed]}>
                  <Text style={styles.saveText}>Save category</Text>
                </Pressable>
              </View>
              </ScrollView>
            </View>
          ) : null}
        </KeyboardAvoidingView>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  content: { padding: 18, paddingBottom: 50, gap: 14 },
  introCard: { padding: 18, borderRadius: 20, borderCurve: 'continuous', backgroundColor: '#103e3b', gap: 6 },
  introTitle: { color: '#ffffff', fontSize: 20, fontWeight: '800' },
  introText: { color: '#bed2c9', fontSize: 13, lineHeight: 20 },
  addButton: { minHeight: 72, flexDirection: 'row', alignItems: 'center', gap: 12, padding: 14, borderRadius: 18, borderCurve: 'continuous', backgroundColor: '#ffffff' },
  addIcon: { width: 46, height: 46, borderRadius: 15, alignItems: 'center', justifyContent: 'center', backgroundColor: '#dcefe1' },
  addIconText: { color: '#175d3f', fontSize: 24, fontWeight: '700' },
  addTitle: { color: '#234a3c', fontSize: 16, fontWeight: '800' },
  addDescription: { marginTop: 2, color: '#75857f', fontSize: 12 },
  list: { gap: 9 },
  categoryCard: { minHeight: 82, overflow: 'hidden', flexDirection: 'row', borderRadius: 18, borderCurve: 'continuous', borderWidth: 1, borderColor: '#e1e9e5' },
  categoryAccent: { width: 5 },
  categoryBody: { flex: 1, padding: 14, gap: 9 },
  categoryTop: { flexDirection: 'row', alignItems: 'center', gap: 9 },
  categoryName: { color: '#1f4538', fontSize: 16, fontWeight: '800' },
  categoryChipLabel: { marginTop: 3, color: '#677b73', fontSize: 13, writingDirection: 'auto' },
  countBadge: { minWidth: 32, height: 32, paddingHorizontal: 8, borderRadius: 11, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.72)' },
  countText: { color: '#536c62', fontSize: 12, fontWeight: '800', fontVariant: ['tabular-nums'] },
  chevron: { color: '#84958e', fontSize: 25 },
  colorMeta: { flexDirection: 'row', alignItems: 'center', gap: 7 },
  colorDot: { width: 10, height: 10, borderRadius: 5 },
  colorText: { color: '#81918b', fontSize: 11, fontWeight: '700', fontVariant: ['tabular-nums'] },
  noteCard: { padding: 16, borderRadius: 17, backgroundColor: '#e7efea', gap: 6 },
  noteTitle: { color: '#315247', fontSize: 14, fontWeight: '800' },
  noteText: { color: '#60766d', fontSize: 12, lineHeight: 19 },
  modalBackdrop: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(12,28,23,0.34)' },
  editor: { maxHeight: '90%', paddingHorizontal: 18, paddingTop: 10, paddingBottom: 12, borderTopLeftRadius: 28, borderTopRightRadius: 28, backgroundColor: '#f4f7f6' },
  editorContent: { paddingBottom: 12, gap: 15 },
  editorHandle: { alignSelf: 'center', width: 40, height: 5, borderRadius: 3, backgroundColor: '#bdcac4' },
  editorTitle: { color: '#1f4538', fontSize: 21, fontWeight: '800' },
  preview: { minHeight: 76, overflow: 'hidden', flexDirection: 'row', alignItems: 'center', borderRadius: 17, borderCurve: 'continuous', borderWidth: 1, borderColor: '#dfe8e3' },
  previewAccent: { alignSelf: 'stretch', width: 5 },
  previewName: { marginLeft: 14, color: '#1f4538', fontSize: 17, fontWeight: '800' },
  previewChip: { marginLeft: 14, color: '#667a72', fontSize: 13, writingDirection: 'auto' },
  field: { gap: 7 },
  fieldLabel: { color: '#37544a', fontSize: 13, fontWeight: '800' },
  input: { minHeight: 50, paddingHorizontal: 14, borderRadius: 13, borderWidth: 1, borderColor: '#cddbd4', backgroundColor: '#ffffff', color: '#173c30', fontSize: 16 },
  palette: { gap: 7, paddingVertical: 1 },
  swatchButton: { width: 48, height: 48, borderRadius: 15, alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: 'transparent' },
  swatchSelected: { borderColor: '#103e3b', backgroundColor: '#ffffff' },
  swatch: { width: 32, height: 32, borderRadius: 11 },
  editorActions: { flexDirection: 'row', gap: 9, marginTop: 2 },
  cancelButton: { flex: 1, minHeight: 52, borderRadius: 15, alignItems: 'center', justifyContent: 'center', backgroundColor: '#ffffff' },
  cancelText: { color: '#60766d', fontSize: 15, fontWeight: '800' },
  saveButton: { flex: 1.5, minHeight: 52, borderRadius: 15, alignItems: 'center', justifyContent: 'center', backgroundColor: '#103e3b' },
  saveText: { color: '#ffffff', fontSize: 15, fontWeight: '800' },
  pressed: { opacity: 0.68 },
});
