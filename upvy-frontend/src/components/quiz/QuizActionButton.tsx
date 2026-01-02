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
          <View style={styles.checkmarkBackground}>
            <Ionicons
              name="checkmark-circle"
              size={22}
              color="#FFFFFF"
            />
          </View>
        </View>
      )}

      {/* Top: Filled Icon */}
      <View style={styles.iconCircle}>
        <Ionicons
          name="help-circle"
          size={22}
          color="#FFFFFF"
        />
      </View>

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
    gap: 5, // spacing[1.25] equivalent: 4 + (8-4) * 0.25
    paddingVertical: theme.spacing[1.5],
    paddingHorizontal: theme.spacing[1],
    backgroundColor: 'rgba(255, 255, 255, 0.75)', // Semi-transparent white
    borderRadius: theme.borderRadius.lg,
    // Strong shadow for better visibility on video background
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.5,
    shadowRadius: 6,
    elevation: 8, // Android shadow
  },
  badge: {
    position: 'absolute',
    top: -7,
    right: -7,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.3,
    shadowRadius: 3,
    elevation: 5,
  },
  checkmarkBackground: {
    width: 22,
    height: 22,
    borderRadius: 12,
    backgroundColor: '#22c55e', // Green filled background
    justifyContent: 'center',
    alignItems: 'center',
  },
  iconCircle: {
    width: 22,
    height: 22,
    borderRadius: 12,
    backgroundColor: '#22c55e', // Green filled circle
    justifyContent: 'center',
    alignItems: 'center',
  },
  label: {
    fontSize: 11,
    fontWeight: theme.typography.fontWeight.semibold,
    color: '#525252',
  },
}));
