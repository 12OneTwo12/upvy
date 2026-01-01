/**
 * 퀴즈 액션 버튼 컴포넌트
 *
 * "문제 보기" 버튼 (퀴즈가 있는 콘텐츠에 표시)
 * - 시도한 퀴즈는 체크마크 배지 표시
 * - 시도하지 않은 퀴즈는 배지 없음
 */

import React from 'react';
import { TouchableOpacity, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';

interface QuizActionButtonProps {
  hasAttempted: boolean;
  onPress: () => void;
}

export const QuizActionButton: React.FC<QuizActionButtonProps> = ({
  hasAttempted,
  onPress,
}) => {
  const { t } = useTranslation('quiz');
  const styles = useStyles();
  const theme = useTheme();

  return (
    <TouchableOpacity
      style={styles.container}
      onPress={onPress}
      activeOpacity={0.7}
      accessibilityLabel={t('actionButton.label')}
      accessibilityRole="button"
      accessibilityHint={hasAttempted ? 'Already attempted' : 'View quiz questions'}
    >
      {/* Icon Container with Badge */}
      <View style={styles.iconContainer}>
        <Ionicons
          name="help-circle"
          size={32}
          color="#22c55e"
          style={styles.icon}
        />

        {/* Checkmark Badge (shown when attempted) */}
        {hasAttempted && (
          <View style={styles.badge}>
            <Ionicons
              name="checkmark-circle"
              size={18}
              color={theme.colors.success}
            />
          </View>
        )}
      </View>

      {/* Label */}
      <Text style={styles.label}>{t('actionButton.label')}</Text>
    </TouchableOpacity>
  );
};

const useStyles = createStyleSheet((theme) => ({
  container: {
    alignItems: 'center',
    gap: theme.spacing[1],
    // Shadow for better visibility
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.3,
    shadowRadius: 4,
    elevation: 4, // Android shadow
  },
  iconContainer: {
    position: 'relative',
    width: 48,
    height: 48,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.85)', // White background
    borderRadius: theme.borderRadius.full,
    borderWidth: 1.5,
    borderColor: 'rgba(0, 0, 0, 0.15)',
  },
  icon: {
    // Add subtle shadow
    textShadowColor: 'rgba(0, 0, 0, 0.2)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 2,
  },
  badge: {
    position: 'absolute',
    top: -2,
    right: -2,
    backgroundColor: theme.colors.white,
    borderRadius: theme.borderRadius.full,
    padding: 1,
    ...theme.shadows.sm,
  },
  label: {
    fontSize: theme.typography.fontSize.xs,
    fontWeight: theme.typography.fontWeight.semibold,
    color: '#1f2937',
    paddingHorizontal: 8,
    paddingVertical: 4,
    backgroundColor: 'rgba(255, 255, 255, 0.85)',
    borderRadius: theme.borderRadius.md,
    overflow: 'hidden',
    // Add shadow for better visibility
    textShadowColor: 'rgba(0, 0, 0, 0.1)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 1,
  },
}));
