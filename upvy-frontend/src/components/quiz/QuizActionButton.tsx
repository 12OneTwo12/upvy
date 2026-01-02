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
      {/* Checkmark Badge (shown when attempted) */}
      {hasAttempted && (
        <View style={styles.badge}>
          <Ionicons
            name="checkmark-circle"
            size={24}
            color={theme.colors.success}
          />
        </View>
      )}

      {/* Top: Icon */}
      <Ionicons
        name="help-circle"
        size={28}
        color="#22c55e"
        style={styles.icon}
      />

      {/* Bottom: Label */}
      <Text style={styles.label}>{t('actionButton.labelShort')}</Text>
    </TouchableOpacity>
  );
};

const useStyles = createStyleSheet((theme) => ({
  container: {
    position: 'relative',
    width: 52,
    height: 52,
    justifyContent: 'center',
    alignItems: 'center',
    gap: theme.spacing[1.5],
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
  icon: {
    // Add subtle shadow
    textShadowColor: 'rgba(0, 0, 0, 0.2)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 2,
  },
  badge: {
    position: 'absolute',
    top: -8,
    right: -8,
    backgroundColor: theme.colors.white,
    borderRadius: theme.borderRadius.full,
    padding: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.3,
    shadowRadius: 3,
    elevation: 5,
  },
  label: {
    fontSize: 11,
    fontWeight: theme.typography.fontWeight.semibold,
    color: '#1f2937',
  },
}));
