import React from 'react';
import {
  TouchableOpacity,
  Text,
  ActivityIndicator,
  StyleSheet,
  ViewStyle,
  TextStyle,
  TouchableOpacityProps,
} from 'react-native';
import { theme } from '@/theme';

export interface ButtonProps extends TouchableOpacityProps {
  /** 버튼 텍스트 */
  children: React.ReactNode;
  /** 버튼 변형 */
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
  /** 버튼 크기 */
  size?: 'sm' | 'md' | 'lg';
  /** 로딩 상태 */
  loading?: boolean;
  /** 비활성화 상태 */
  disabled?: boolean;
  /** 전체 너비 */
  fullWidth?: boolean;
  /** 커스텀 스타일 */
  style?: ViewStyle;
  /** 텍스트 스타일 */
  textStyle?: TextStyle;
}

/**
 * 공통 버튼 컴포넌트
 * 다양한 변형과 크기를 지원하며 반응형 디자인 적용
 */
export const Button: React.FC<ButtonProps> = ({
  children,
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled = false,
  fullWidth = false,
  style,
  textStyle,
  ...props
}) => {
  const isDisabled = disabled || loading;

  const buttonStyle = [
    styles.base,
    styles[variant],
    styles[`${size}Size`],
    fullWidth && styles.fullWidth,
    isDisabled && styles.disabled,
    isDisabled && styles[`${variant}Disabled`],
    style,
  ];

  const textStyles = [
    styles.text,
    styles[`${variant}Text`],
    styles[`${size}Text`],
    isDisabled && styles.disabledText,
    textStyle,
  ];

  return (
    <TouchableOpacity
      style={buttonStyle}
      disabled={isDisabled}
      activeOpacity={0.7}
      {...props}
    >
      {loading ? (
        <ActivityIndicator
          size="small"
          color={
            variant === 'primary' || variant === 'danger'
              ? theme.colors.text.inverse
              : theme.colors.primary[500]
          }
        />
      ) : (
        <Text style={textStyles}>{children}</Text>
      )}
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  // Base
  base: {
    borderRadius: theme.borderRadius.xl,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
  },
  text: {
    fontWeight: theme.typography.fontWeight.semibold,
  },
  fullWidth: {
    width: '100%',
  },
  disabled: {
    opacity: 0.5,
  },
  disabledText: {
    opacity: 0.7,
  },

  // Variants
  primary: {
    backgroundColor: theme.colors.primary[500],
  },
  primaryText: {
    color: theme.colors.text.inverse,
  },
  primaryDisabled: {
    backgroundColor: theme.colors.gray[300],
  },

  secondary: {
    backgroundColor: theme.colors.gray[100],
  },
  secondaryText: {
    color: theme.colors.text.primary,
  },
  secondaryDisabled: {
    backgroundColor: theme.colors.gray[50],
  },

  outline: {
    backgroundColor: 'transparent',
    borderWidth: 2,
    borderColor: theme.colors.primary[500],
  },
  outlineText: {
    color: theme.colors.primary[500],
  },
  outlineDisabled: {
    borderColor: theme.colors.gray[300],
  },

  ghost: {
    backgroundColor: 'transparent',
  },
  ghostText: {
    color: theme.colors.primary[500],
  },
  ghostDisabled: {
    backgroundColor: 'transparent',
  },

  danger: {
    backgroundColor: theme.colors.error,
  },
  dangerText: {
    color: theme.colors.text.inverse,
  },
  dangerDisabled: {
    backgroundColor: theme.colors.gray[300],
  },

  // Sizes
  smSize: {
    paddingHorizontal: theme.spacing[3],
    paddingVertical: theme.spacing[2],
    minHeight: 36,
  },
  smText: {
    fontSize: theme.typography.fontSize.sm,
  },

  mdSize: {
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
    minHeight: 48,
  },
  mdText: {
    fontSize: theme.typography.fontSize.base,
  },

  lgSize: {
    paddingHorizontal: theme.spacing[6],
    paddingVertical: theme.spacing[4],
    minHeight: 56,
  },
  lgText: {
    fontSize: theme.typography.fontSize.lg,
  },
});
