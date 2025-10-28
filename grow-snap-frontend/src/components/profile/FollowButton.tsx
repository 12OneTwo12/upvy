import React from 'react';
import { TouchableOpacity, Text, StyleSheet, ActivityIndicator } from 'react';
import { theme } from '@/theme';

interface FollowButtonProps {
  isFollowing: boolean;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
}

/**
 * 팔로우/언팔로우 버튼 컴포넌트
 * 인스타그램 스타일의 팔로우 버튼
 */
export default function FollowButton({
  isFollowing,
  onPress,
  loading = false,
  disabled = false,
}: FollowButtonProps) {
  return (
    <TouchableOpacity
      style={[
        styles.button,
        isFollowing ? styles.buttonFollowing : styles.buttonNotFollowing,
        disabled && styles.buttonDisabled,
      ]}
      onPress={onPress}
      disabled={disabled || loading}
      activeOpacity={0.7}
    >
      {loading ? (
        <ActivityIndicator
          size="small"
          color={isFollowing ? theme.colors.text.primary : theme.colors.text.inverse}
        />
      ) : (
        <Text
          style={[
            styles.buttonText,
            isFollowing ? styles.buttonTextFollowing : styles.buttonTextNotFollowing,
          ]}
        >
          {isFollowing ? '팔로잉' : '팔로우'}
        </Text>
      )}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  button: {
    paddingHorizontal: theme.spacing[6],
    paddingVertical: theme.spacing[2],
    borderRadius: theme.borderRadius.md,
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 100,
    height: 36,
  },
  buttonNotFollowing: {
    backgroundColor: theme.colors.primary[600],
  },
  buttonFollowing: {
    backgroundColor: theme.colors.background.secondary,
    borderWidth: 1,
    borderColor: theme.colors.border.medium,
  },
  buttonDisabled: {
    opacity: 0.5,
  },
  buttonText: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.semibold,
  },
  buttonTextNotFollowing: {
    color: theme.colors.text.inverse,
  },
  buttonTextFollowing: {
    color: theme.colors.text.primary,
  },
});
