import React from 'react';
import {
  View,
  ActivityIndicator,
  StyleSheet,
  ViewStyle,
  Text,
} from 'react-native';
import { theme } from '@/theme';

export interface LoadingSpinnerProps {
  /** 크기 */
  size?: 'small' | 'large';
  /** 색상 */
  color?: string;
  /** 로딩 메시지 */
  message?: string;
  /** 전체 화면 */
  fullScreen?: boolean;
  /** 컨테이너 스타일 */
  containerStyle?: ViewStyle;
}

/**
 * 로딩 스피너 컴포넌트
 */
export const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  size = 'large',
  color = theme.colors.primary[500],
  message,
  fullScreen = false,
  containerStyle,
}) => {
  const container = fullScreen ? styles.fullScreen : styles.container;

  return (
    <View style={[container, containerStyle]}>
      <ActivityIndicator size={size} color={color} />
      {message && <Text style={styles.message}>{message}</Text>}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
    padding: theme.spacing[4],
  },

  fullScreen: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.colors.background.primary,
  },

  message: {
    marginTop: theme.spacing[3],
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.secondary,
    textAlign: 'center',
  },
});
