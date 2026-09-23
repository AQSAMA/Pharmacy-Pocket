import React, { useEffect, useRef, useState } from 'react';
import { Keyboard, Pressable, Text, TextInput, View } from 'react-native';

type Props = {
  query: string;
  onChangeQuery(value: string): void;
  suggestions: string[];
};

export function FloatingSearch({ query, onChangeQuery, suggestions }: Props) {
  const [expanded, setExpanded] = useState(false);
  const input = useRef<TextInput>(null);

  useEffect(() => {
    if (expanded) requestAnimationFrame(() => input.current?.focus());
  }, [expanded]);

  const collapse = () => {
    Keyboard.dismiss();
    setExpanded(false);
  };

  return <View style={{ flex: expanded ? 1 : 0, minWidth: expanded ? 0 : 52, alignItems: 'stretch' }}>
    {expanded ? <View style={{ minHeight: 52, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 6, gap: 2, borderRadius: 16, borderCurve: 'continuous', backgroundColor: '#103e3b', boxShadow: '0 3px 10px rgba(18, 63, 52, 0.16)' }}>
      <Pressable accessibilityRole="button" accessibilityLabel="Close search" onPress={collapse} hitSlop={8} style={{ width: 38, height: 38, alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#ffffff', fontSize: 22 }}>‹</Text></Pressable>
      <TextInput ref={input} value={query} onChangeText={onChangeQuery} placeholder="Search medicines…" placeholderTextColor="rgba(255,255,255,0.72)" autoCapitalize="none" autoCorrect={false} returnKeyType="search" onSubmitEditing={collapse} style={{ flex: 1, color: '#ffffff', fontSize: 16, paddingVertical: 0 }} />
      {query ? <Pressable accessibilityRole="button" accessibilityLabel="Clear search text" onPress={() => { onChangeQuery(''); input.current?.focus(); }} hitSlop={8} style={{ width: 36, height: 36, borderRadius: 18, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.14)' }}><Text style={{ color: '#ffffff', fontSize: 18 }}>×</Text></Pressable> : null}
    </View> : <Pressable accessibilityRole="button" accessibilityLabel="Search medicines" onPress={() => setExpanded(true)} style={({ pressed }) => ({ width: 52, height: 52, borderRadius: 16, borderCurve: 'continuous', backgroundColor: pressed ? '#315b49' : '#103e3b', alignItems: 'center', justifyContent: 'center', boxShadow: '0 3px 10px rgba(18, 63, 52, 0.14)' })}><Text style={{ color: '#ffffff', fontSize: 24 }}>⌕</Text></Pressable>}
    {expanded && query.trim() && suggestions.length ? <View style={{ marginTop: 6, borderRadius: 14, borderCurve: 'continuous', overflow: 'hidden', backgroundColor: '#ffffff', boxShadow: '0 5px 16px rgba(18, 63, 52, 0.14)' }}>
      {suggestions.map((suggestion, index) => <Pressable key={suggestion} onPress={() => { onChangeQuery(suggestion); collapse(); }} style={({ pressed }) => ({ minHeight: 44, justifyContent: 'center', paddingHorizontal: 14, backgroundColor: pressed ? '#eaf3ee' : 'transparent', borderTopWidth: index ? 1 : 0, borderTopColor: '#e4ece8' })}><Text numberOfLines={1} style={{ color: '#24443a', fontSize: 15 }}>{suggestion}</Text></Pressable>)}
    </View> : null}
  </View>;
}
