import * as Haptics from 'expo-haptics';
import { Platform } from 'react-native';

function ignoreHapticError(promise: Promise<void>) {
  void promise.catch(() => undefined);
}

export function selectionHaptic() {
  if (Platform.OS === 'android') {
    ignoreHapticError(Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Segment_Frequent_Tick));
  } else if (Platform.OS === 'ios') {
    ignoreHapticError(Haptics.selectionAsync());
  }
}

export function actionHaptic() {
  if (Platform.OS === 'android') {
    ignoreHapticError(Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Context_Click));
  } else if (Platform.OS === 'ios') {
    ignoreHapticError(Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light));
  }
}

export function confirmHaptic() {
  if (Platform.OS === 'android') {
    ignoreHapticError(Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Confirm));
  } else if (Platform.OS === 'ios') {
    ignoreHapticError(Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success));
  }
}

export function rejectHaptic() {
  if (Platform.OS === 'android') {
    ignoreHapticError(Haptics.performAndroidHapticsAsync(Haptics.AndroidHaptics.Reject));
  } else if (Platform.OS === 'ios') {
    ignoreHapticError(Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error));
  }
}
