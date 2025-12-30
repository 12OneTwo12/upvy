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
          size={40}
          color={theme.colors.white}
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
  },
  iconContainer: {
    position: 'relative',
    width: 48,
    height: 48,
    justifyContent: 'center',
    alignItems: 'center',
  },
  icon: {
    // Add shadow for better visibility
    textShadowColor: 'rgba(0, 0, 0, 0.75)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 3,
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
    color: theme.colors.white,
    // Add shadow for better visibility
    textShadowColor: 'rgba(0, 0, 0, 0.75)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 3,
  },
}));
