/**
 * 좋아요 애니메이션 컴포넌트
 *
 * 더블탭 시 화면 중앙에 하트 애니메이션 표시
 * - 페이드인 + 스케일 애니메이션
 * - 0.8초 후 자동으로 사라짐
 */

import React, { useEffect, useRef } from 'react';
import { View, Animated, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

interface LikeAnimationProps {
  show: boolean;
  onComplete?: () => void;
}

export const LikeAnimation: React.FC<LikeAnimationProps> = ({ show, onComplete }) => {
  const scaleAnim = useRef(new Animated.Value(0)).current;
  const opacityAnim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (show) {
      // 애니메이션 시퀀스: 페이드인 → 대기 → 페이드아웃
      Animated.sequence([
        // 1. 페이드인 + 스케일업
        Animated.parallel([
          Animated.timing(scaleAnim, {
            toValue: 1,
            duration: 300,
            useNativeDriver: true,
          }),
          Animated.timing(opacityAnim, {
            toValue: 1,
            duration: 200,
            useNativeDriver: true,
          }),
        ]),
        // 2. 대기
        Animated.delay(300),
        // 3. 페이드아웃 + 약간 더 스케일업
        Animated.parallel([
          Animated.timing(scaleAnim, {
            toValue: 1.2,
            duration: 200,
            useNativeDriver: true,
          }),
          Animated.timing(opacityAnim, {
            toValue: 0,
            duration: 200,
            useNativeDriver: true,
          }),
        ]),
      ]).start(() => {
        // 애니메이션 리셋
        scaleAnim.setValue(0);
        opacityAnim.setValue(0);
        onComplete?.();
      });
    }
  }, [show, scaleAnim, opacityAnim, onComplete]);

  if (!show) {
    return null;
  }

  return (
    <View style={styles.container} pointerEvents="none">
      <Animated.View
        style={[
          styles.heartContainer,
          {
            transform: [{ scale: scaleAnim }],
            opacity: opacityAnim,
          },
        ]}
      >
        <Ionicons name="heart" size={120} color="#FF4458" />
      </Animated.View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 1000,
  },
  heartContainer: {
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
    elevation: 5,
  },
});
