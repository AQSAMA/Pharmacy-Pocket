import React, { useEffect, useRef, useState } from 'react';
import { Animated, Keyboard, Pressable, Text, TextInput, useWindowDimensions, View } from 'react-native';

type Props = {
  query: string;
  onChangeQuery(value: string): void;
  suggestions: string[];
  top: number;
};

export function FloatingSearch({ query, onChangeQuery, suggestions, top }: Props) {
  const { width: screenWidth } = useWindowDimensions();
  const [expanded, setExpanded] = useState(false);
  const input = useRef<TextInput>(null);
  const width = useRef(new Animated.Value(56)).current;
  const expandedWidth = Math.min(360, screenWidth - 28);

  useEffect(() => {
    Animated.timing(width, { toValue: expanded ? expandedWidth : 56, duration: 180, useNativeDriver: false }).start();
    if (expanded) requestAnimationFrame(() => input.current?.focus());
  }, [expanded, expandedWidth, width]);

  const collapse = () => {
    Keyboard.dismiss();
    setExpanded(false);
  };

  return <View pointerEvents="box-none" style={{ position: 'absolute', top, right: 14, zIndex: 20, alignItems: 'flex-end' }}>
    <Animated.View style={{ width, minHeight: 56, borderRadius: 28, borderCurve: 'continuous', overflow: 'hidden', backgroundColor: 'rgba(16, 62, 59, 0.88)', boxShadow: '0 5px 16px rgba(18, 63, 52, 0.22)' }}>
      {expanded ? <View style={{ height: 56, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 8, gap: 3 }}>
        <Pressable accessibilityRole="button" accessibilityLabel="Close search" onPress={collapse} hitSlop={8} style={{ width: 40, height: 40, alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#ffffff', fontSize: 22 }}>‹</Text></Pressable>
        <TextInput ref={input} value={query} onChangeText={onChangeQuery} placeholder="Search medicines…" placeholderTextColor="rgba(255,255,255,0.72)" autoCapitalize="none" autoCorrect={false} returnKeyType="search" onSubmitEditing={collapse} style={{ flex: 1, color: '#ffffff', fontSize: 17, paddingVertical: 0 }} />
        {query ? <Pressable accessibilityRole="button" accessibilityLabel="Clear search text" onPress={() => { onChangeQuery(''); input.current?.focus(); }} hitSlop={8} style={{ width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(255,255,255,0.14)' }}><Text style={{ color: '#ffffff', fontSize: 18 }}>×</Text></Pressable> : null}
      </View> : <Pressable accessibilityRole="button" accessibilityLabel="Search medicines" onPress={() => setExpanded(true)} style={{ width: 56, height: 56, alignItems: 'center', justifyContent: 'center' }}><Text style={{ color: '#ffffff', fontSize: 25 }}>⌕</Text></Pressable>}
    </Animated.View>
    {expanded && query.trim() && suggestions.length ? <View style={{ width: expandedWidth, marginTop: 7, borderRadius: 16, borderCurve: 'continuous', overflow: 'hidden', backgroundColor: 'rgba(255,255,255,0.97)', boxShadow: '0 6px 20px rgba(18, 63, 52, 0.18)' }}>
      {suggestions.map((suggestion, index) => <Pressable key={suggestion} onPress={() => { onChangeQuery(suggestion); collapse(); }} style={({ pressed }) => ({ minHeight: 46, justifyContent: 'center', paddingHorizontal: 16, backgroundColor: pressed ? '#eaf3ee' : 'transparent', borderTopWidth: index ? 1 : 0, borderTopColor: '#e4ece8' })}><Text numberOfLines={1} style={{ color: '#24443a', fontSize: 16 }}>{suggestion}</Text></Pressable>)}
    </View> : null}
  </View>;
}
