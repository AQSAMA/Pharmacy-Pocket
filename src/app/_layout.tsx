import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import React from 'react';

import { MedicineProvider } from '@/data/medicine-store';

export default function RootLayout() {
  return (
    <MedicineProvider>
      <StatusBar style="dark" />
      <Stack screenOptions={{ headerStyle: { backgroundColor: '#f4f7f6' }, headerTintColor: '#173c30', headerShadowVisible: false, contentStyle: { backgroundColor: '#f4f7f6' }, headerBackButtonDisplayMode: 'minimal' }}>
        <Stack.Screen name="index" options={{ headerShown: false }} />
        <Stack.Screen name="medicine/[id]" options={{ title: 'Official price', presentation: 'modal' }} />
        <Stack.Screen name="edit" options={{ title: 'Medicine details', presentation: 'modal' }} />
        <Stack.Screen name="settings" options={{ title: 'Settings', presentation: 'modal' }} />
      </Stack>
    </MedicineProvider>
  );
}
