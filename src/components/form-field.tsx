import React from 'react';
import { Text, TextInput, type TextInputProps, View } from 'react-native';

export function FormField({ label, ...props }: TextInputProps & { label: string }) {
  return <View style={{ gap: 7 }}>
    <Text selectable style={{ color: '#37544a', fontWeight: '700', fontSize: 14 }}>{label}</Text>
    <TextInput
      placeholderTextColor="#86958f"
      {...props}
      style={[
        { minHeight: 50, borderWidth: 1, borderColor: '#cddbd4', borderRadius: 12, backgroundColor: '#ffffff', color: '#173c30', fontSize: 17, paddingHorizontal: 14, paddingVertical: 11 },
        props.style,
      ]}
    />
  </View>;
}
