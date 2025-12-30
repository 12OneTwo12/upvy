/**
 * 퀴즈 토글 버튼 컴포넌트
 *
 * 퀴즈 자동 표시 ON/OFF 토글 버튼
 * 피드 스크롤 시 퀴즈가 있는 콘텐츠에서 자동으로 퀴즈를 표시할지 여부 제어
 */

import React from 'react';
import { TouchableOpacity, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
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
    >
      <Ionicons
        name={isEnabled ? "bulb" : "bulb-outline"}
        size={20}
        color={isEnabled ? theme.colors.white : theme.colors.text.secondary}
      />
      <Text
        style={[
          styles.text,
          isEnabled ? styles.textEnabled : styles.textDisabled,
        ]}
      >
        퀴즈
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
  },
  containerEnabled: {
    backgroundColor: theme.colors.primary,
  },
  containerDisabled: {
    backgroundColor: theme.colors.background.secondary,
    borderWidth: 1,
    borderColor: theme.colors.border.light,
  },
  text: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.semibold,
  },
  textEnabled: {
    color: theme.colors.white,
  },
  textDisabled: {
    color: theme.colors.text.secondary,
  },
}));
