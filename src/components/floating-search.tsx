import React, { useRef } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { actionHaptic } from '@/components/haptics';

type Props = {
  query: string;
  onChangeQuery(value: string): void;
};

export function FloatingSearch({ query, onChangeQuery }: Props) {
  const input = useRef<TextInput>(null);
  const hasActiveQuery = Boolean(query.trim());

  return (
    <View style={[styles.container, hasActiveQuery && styles.containerActive]}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Focus medicine search"
        hitSlop={6}
        onPress={() => {
          actionHaptic();
          input.current?.focus();
        }}
        style={styles.searchIcon}
      >
        <Text style={styles.searchGlyph}>⌕</Text>
      </Pressable>
      <TextInput
        ref={input}
        value={query}
        onChangeText={onChangeQuery}
        placeholder="Search medicines…"
        placeholderTextColor="#84958e"
        autoCapitalize="none"
        autoCorrect={false}
        returnKeyType="search"
        clearButtonMode="never"
        style={styles.input}
      />
      {query ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Clear search text"
          onPress={() => {
            actionHaptic();
            onChangeQuery('');
            input.current?.focus();
          }}
          hitSlop={6}
          style={({ pressed }) => [styles.clearButton, pressed && styles.clearPressed]}
        >
          <Text style={styles.clearGlyph}>×</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    minHeight: 50,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 6,
    borderRadius: 16,
    borderCurve: 'continuous',
    borderWidth: 1,
    borderColor: '#d7e3dd',
    backgroundColor: '#ffffff',
  },
  containerActive: {
    borderColor: '#7da997',
    backgroundColor: '#fbfdfc',
  },
  searchIcon: {
    width: 42,
    height: 42,
    alignItems: 'center',
    justifyContent: 'center',
  },
  searchGlyph: { color: '#315b49', fontSize: 23 },
  input: {
    flex: 1,
    minWidth: 0,
    color: '#173c30',
    fontSize: 16,
    paddingVertical: 0,
  },
  clearButton: {
    width: 42,
    height: 42,
    borderRadius: 13,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#eef4f1',
  },
  clearPressed: { backgroundColor: '#dfeae5' },
  clearGlyph: { color: '#536c62', fontSize: 20, fontWeight: '700' },
});
