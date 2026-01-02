/**
 * 퀴즈 토글 버튼 컴포넌트
 *
 * 퀴즈 자동 표시 ON/OFF 토글 스위치
 * 피드 스크롤 시 퀴즈가 있는 콘텐츠에서 자동으로 퀴즈를 표시할지 여부 제어
 */

import React, { useEffect, useRef } from 'react';
import { TouchableOpacity, View, Animated, Text } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';

interface QuizToggleButtonProps {
  isEnabled: boolean;
  onToggle: () => void;
}

export const QuizToggleButton: React.FC<QuizToggleButtonProps> = ({
  isEnabled,
  onToggle,
}) => {
  const { t } = useTranslation('quiz');
  const styles = useStyles();
  const theme = useTheme();
  const translateX = useRef(new Animated.Value(isEnabled ? 1 : 0)).current;

  useEffect(() => {
    Animated.spring(translateX, {
      toValue: isEnabled ? 1 : 0,
      useNativeDriver: true,
      friction: 6,
      tension: 40,
    }).start();
  }, [isEnabled, translateX]);

  const thumbTranslateX = translateX.interpolate({
    inputRange: [0, 1],
    outputRange: [1, 15], // 1px to 15px (track width 28px - thumb 12px - padding 1px)
  });

  return (
    <TouchableOpacity
      style={styles.container}
      onPress={onToggle}
      activeOpacity={0.7}
      accessibilityLabel={t('toggleButton.label')}
      accessibilityRole="switch"
      accessibilityState={{ checked: isEnabled }}
      accessibilityHint={isEnabled ? 'Disable auto quiz display' : 'Enable auto quiz display'}
    >
      {/* Top: Icon + Label */}
      <View style={styles.topSection}>
        <Ionicons
          name="bulb"
          size={14}
          color="#1f2937"
        />
        <Text style={styles.label}>{t('toggleButton.label')}</Text>
      </View>

      {/* Bottom: Small Toggle Switch */}
      <View style={[styles.track, isEnabled ? styles.trackEnabled : styles.trackDisabled]}>
        <Animated.View
          style={[
            styles.thumb,
            {
              transform: [{ translateX: thumbTranslateX }],
            },
          ]}
        />
      </View>
    </TouchableOpacity>
  );
};

const useStyles = createStyleSheet((theme) => ({
  container: {
    width: 52,
    height: 52,
    justifyContent: 'center',
    alignItems: 'center',
    gap: theme.spacing[2],
    paddingVertical: theme.spacing[1.5],
    paddingHorizontal: theme.spacing[1],
    backgroundColor: 'rgba(255, 255, 255, 0.85)', // Semi-transparent white
    borderRadius: theme.borderRadius.lg,
    // Strong shadow for better visibility on video background
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.5,
    shadowRadius: 6,
    elevation: 8, // Android shadow
  },
  topSection: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: theme.spacing[0.5],
  },
  label: {
    fontSize: 11,
    fontWeight: theme.typography.fontWeight.semibold,
    color: '#1f2937',
  },
  track: {
    width: 28,
    height: 14,
    borderRadius: 7,
    justifyContent: 'center',
    position: 'relative',
  },
  trackDisabled: {
    backgroundColor: '#9ca3af', // Darker gray (gray-400)
  },
  trackEnabled: {
    backgroundColor: '#22c55e', // Green
  },
  thumb: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#FFFFFF',
    position: 'absolute',
    // Additional shadow for thumb
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.3,
    shadowRadius: 1,
    elevation: 2,
  },
}));
