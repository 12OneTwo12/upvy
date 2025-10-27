import React, { useState } from 'react';
import {
  View,
  TextInput,
  Text,
  StyleSheet,
  ViewStyle,
  TextStyle,
  TextInputProps,
  TouchableOpacity,
} from 'react-native';
import { theme } from '@/theme';

export interface InputProps extends TextInputProps {
  /** 라벨 */
  label?: string;
  /** 에러 메시지 */
  error?: string;
  /** 도움말 메시지 */
  helperText?: string;
  /** 필수 항목 */
  required?: boolean;
  /** 컨테이너 스타일 */
  containerStyle?: ViewStyle;
  /** 입력 필드 스타일 */
  inputStyle?: TextStyle;
  /** 왼쪽 아이콘 */
  leftIcon?: React.ReactNode;
  /** 오른쪽 아이콘 또는 버튼 */
  rightElement?: React.ReactNode;
  /** 비활성화 */
  disabled?: boolean;
}

/**
 * 공통 입력 컴포넌트
 * 라벨, 에러 메시지, 헬퍼 텍스트를 지원하며 반응형 디자인 적용
 */
export const Input = React.forwardRef<TextInput, InputProps>(
  (
    {
      label,
      error,
      helperText,
      required = false,
      containerStyle,
      inputStyle,
      leftIcon,
      rightElement,
      disabled = false,
      ...props
    },
    ref
  ) => {
    const [isFocused, setIsFocused] = useState(false);

    const inputContainerStyle = [
      styles.inputContainer,
      isFocused && styles.inputContainerFocused,
      error && styles.inputContainerError,
      disabled && styles.inputContainerDisabled,
    ];

    return (
      <View style={[styles.container, containerStyle]}>
        {/* Label */}
        {label && (
          <Text style={styles.label}>
            {label}
            {required && <Text style={styles.required}> *</Text>}
          </Text>
        )}

        {/* Input Container */}
        <View style={inputContainerStyle}>
          {/* Left Icon */}
          {leftIcon && <View style={styles.leftIconContainer}>{leftIcon}</View>}

          {/* Text Input */}
          <TextInput
            ref={ref}
            style={[
              styles.input,
              leftIcon ? styles.inputWithLeftIcon : null,
              inputStyle,
            ]}
            placeholderTextColor={theme.colors.text.tertiary}
            onFocus={() => setIsFocused(true)}
            onBlur={() => setIsFocused(false)}
            editable={!disabled}
            {...props}
          />

          {/* Right Element */}
          {rightElement && (
            <View style={styles.rightElementContainer}>{rightElement}</View>
          )}
        </View>

        {/* Error Message */}
        {error && <Text style={styles.error}>{error}</Text>}

        {/* Helper Text */}
        {helperText && !error && (
          <Text style={styles.helperText}>{helperText}</Text>
        )}
      </View>
    );
  }
);

Input.displayName = 'Input';

const styles = StyleSheet.create({
  container: {
    marginBottom: theme.spacing[4],
  },

  label: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.secondary,
    marginBottom: theme.spacing[2],
  },

  required: {
    color: theme.colors.error,
  },

  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 2,
    borderColor: theme.colors.border.light,
    borderRadius: theme.borderRadius.xl,
    backgroundColor: theme.colors.background.primary,
    paddingHorizontal: theme.spacing[4],
  },

  inputContainerFocused: {
    borderColor: theme.colors.primary[500],
  },

  inputContainerError: {
    borderColor: theme.colors.error,
  },

  inputContainerDisabled: {
    backgroundColor: theme.colors.background.tertiary,
    borderColor: theme.colors.border.light,
  },

  input: {
    flex: 1,
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    paddingVertical: theme.spacing[3],
    minHeight: 48,
  },

  inputWithLeftIcon: {
    paddingLeft: theme.spacing[2],
  },

  leftIconContainer: {
    marginRight: theme.spacing[2],
  },

  rightElementContainer: {
    marginLeft: theme.spacing[2],
  },

  error: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.error,
    marginTop: theme.spacing[2],
  },

  helperText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.tertiary,
    marginTop: theme.spacing[2],
  },
});

/**
 * 비밀번호 입력 컴포넌트
 */
export interface PasswordInputProps extends Omit<InputProps, 'secureTextEntry' | 'rightElement'> {}

export const PasswordInput = React.forwardRef<TextInput, PasswordInputProps>(
  (props, ref) => {
    const [showPassword, setShowPassword] = useState(false);

    return (
      <Input
        ref={ref}
        secureTextEntry={!showPassword}
        rightElement={
          <TouchableOpacity onPress={() => setShowPassword(!showPassword)}>
            <Text
              style={{
                fontSize: theme.typography.fontSize.sm,
                color: theme.colors.primary[500],
                fontWeight: theme.typography.fontWeight.semibold,
              }}
            >
              {showPassword ? '숨기기' : '보기'}
            </Text>
          </TouchableOpacity>
        }
        {...props}
      />
    );
  }
);

PasswordInput.displayName = 'PasswordInput';
