/**
 * 퀴즈 토글 버튼 컴포넌트
 *
 * 퀴즈 자동 표시 ON/OFF 토글 버튼
 * 피드 스크롤 시 퀴즈가 있는 콘텐츠에서 자동으로 퀴즈를 표시할지 여부 제어
 */

import React from 'react';
import { TouchableOpacity, Text, View } from 'react-native';
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

  return (
    <TouchableOpacity
      style={[
        styles.container,
        isEnabled ? styles.containerEnabled : styles.containerDisabled,
      ]}
      onPress={onToggle}
      activeOpacity={0.8}
      accessibilityLabel={t('toggleButton.label')}
      accessibilityRole="switch"
      accessibilityState={{ checked: isEnabled }}
      accessibilityHint={isEnabled ? 'Disable auto quiz display' : 'Enable auto quiz display'}
    >
      <View style={styles.iconContainer}>
        <Ionicons
          name={isEnabled ? "bulb" : "bulb-outline"}
          size={20}
          color={isEnabled ? theme.colors.white : '#1f2937'}
          style={styles.icon}
        />
      </View>
      <Text
        style={[
          styles.text,
          isEnabled ? styles.textEnabled : styles.textDisabled,
        ]}
      >
        {t('toggleButton.label')}
      </Text>
    </TouchableOpacity>
  );
};

const useStyles = createStyleSheet((theme) => ({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[3],
    paddingVertical: theme.spacing[2],
    borderRadius: theme.borderRadius.full,
    gap: theme.spacing[1],
    // Shadow for better visibility on video background
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.4,
    shadowRadius: 4,
    elevation: 5, // Android shadow
  },
  containerEnabled: {
    backgroundColor: '#22c55e', // Bright green for enabled state
  },
  containerDisabled: {
    backgroundColor: 'rgba(255, 255, 255, 0.95)', // White background
    borderWidth: 1.5,
    borderColor: 'rgba(0, 0, 0, 0.15)',
  },
  text: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.semibold,
    // Text shadow for better readability
    textShadowColor: 'rgba(0, 0, 0, 0.1)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 1,
  },
  textEnabled: {
    color: theme.colors.white,
  },
  textDisabled: {
    color: '#1f2937', // Dark gray text on white background
  },
  iconContainer: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  icon: {
    textShadowColor: 'rgba(0, 0, 0, 0.15)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 1,
  },
}));
