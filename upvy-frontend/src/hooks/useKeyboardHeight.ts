/**
 * 키보드 높이를 감지하는 Hook
 *
 * iOS와 Android 모두에서 키보드가 올라올 때 높이를 반환합니다.
 * 키보드 애니메이션과 완벽하게 동기화된 부드러운 전환을 제공합니다.
 */

import { useEffect, useRef } from 'react';
import { Keyboard, Platform, Animated, Easing } from 'react-native';

export const useKeyboardHeight = () => {
  const keyboardHeight = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    const showEvent = Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow';
    const hideEvent = Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide';

    const onKeyboardShow = (e: any) => {
      Animated.timing(keyboardHeight, {
        toValue: e.endCoordinates.height,
        duration: Platform.OS === 'ios' ? e.duration : 250,
        easing: Platform.OS === 'ios'
          ? Easing.bezier(0.17, 0.59, 0.4, 0.77) // iOS 키보드 기본 easing curve
          : Easing.out(Easing.quad),
        useNativeDriver: false, // paddingBottom은 layout property라서 false 필요
      }).start();
    };

    const onKeyboardHide = (e: any) => {
      Animated.timing(keyboardHeight, {
        toValue: 0,
        duration: Platform.OS === 'ios' ? e.duration : 250,
        easing: Platform.OS === 'ios'
          ? Easing.bezier(0.17, 0.59, 0.4, 0.77) // iOS 키보드 기본 easing curve
          : Easing.out(Easing.quad),
        useNativeDriver: false,
      }).start();
    };

    const showSubscription = Keyboard.addListener(showEvent, onKeyboardShow);
    const hideSubscription = Keyboard.addListener(hideEvent, onKeyboardHide);

    return () => {
      showSubscription.remove();
      hideSubscription.remove();
    };
  }, [keyboardHeight]);

  return keyboardHeight;
};
