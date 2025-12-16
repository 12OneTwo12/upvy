import React from 'react';
import {
  TouchableOpacity,
  Text,
  ActivityIndicator,
  ViewStyle,
  Image,
  View,
} from 'react-native';
import { theme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';

export type OAuthProvider = 'google' | 'apple';

export interface OAuthButtonProps {
  /** OAuth 제공자 */
  provider: OAuthProvider;
  /** 버튼 텍스트 */
  children: React.ReactNode;
  /** 클릭 핸들러 */
  onPress: () => void;
  /** 로딩 상태 */
  loading?: boolean;
  /** 비활성화 상태 */
  disabled?: boolean;
  /** 커스텀 스타일 */
  style?: ViewStyle;
}

/**
 * OAuth 로그인 버튼 컴포넌트
 * Apple, Google 등의 OAuth 제공자 브랜딩 가이드라인을 준수
 */
export const OAuthButton: React.FC<OAuthButtonProps> = ({
  provider,
  children,
  onPress,
  loading = false,
  disabled = false,
  style,
}) => {
  const styles = useStyles();
  const isDisabled = disabled || loading;

  // 제공자별 설정
  const providerConfig = {
    google: {
      backgroundColor: '#FFFFFF',
      textColor: '#1F1F1F',
      borderColor: '#E0E0E0',
      logo: require('@/../assets/images/oauth/google-logo.png'),
      logoTintColor: undefined, // Google 로고는 원본 색상 유지
      loadingColor: theme.colors.text.primary,
    },
    apple: {
      backgroundColor: '#000000',
      textColor: '#FFFFFF',
      borderColor: '#000000',
      logo: require('@/../assets/images/oauth/apple-logo.png'),
      logoTintColor: undefined, // Apple 로고는 이미 흰색 이미지
      loadingColor: theme.colors.text.inverse,
    },
  };

  const config = providerConfig[provider];

  const buttonStyle = [
    styles.button,
    {
      backgroundColor: config.backgroundColor,
      borderColor: config.borderColor,
    },
    isDisabled && styles.disabled,
    style,
  ];

  const textStyle = [
    styles.text,
    {
      color: config.textColor,
    },
    isDisabled && styles.disabledText,
  ];

  return (
    <TouchableOpacity
      style={buttonStyle}
      onPress={onPress}
      disabled={isDisabled}
      activeOpacity={0.7}
    >
      {loading ? (
        <ActivityIndicator size="small" color={config.loadingColor} />
      ) : (
        <View style={styles.content}>
          <Image
            source={config.logo}
            style={[
              styles.logo,
              config.logoTintColor && { tintColor: config.logoTintColor },
            ]}
            resizeMode="contain"
          />
          <Text style={textStyle}>{children}</Text>
        </View>
      )}
    </TouchableOpacity>
  );
};

const useStyles = createStyleSheet({
  button: {
    borderRadius: theme.borderRadius.xl,
    paddingHorizontal: theme.spacing[6],
    paddingVertical: theme.spacing[4],
    minHeight: 56,
    width: '100%',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    ...theme.shadows.sm,
  },

  content: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: theme.spacing[3],
  },

  logo: {
    width: 24,
    height: 24,
  },

  text: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.semibold,
  },

  disabled: {
    opacity: 0.5,
  },

  disabledText: {
    opacity: 0.7,
  },
});
