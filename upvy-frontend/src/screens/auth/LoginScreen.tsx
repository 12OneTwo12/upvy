import React, { useEffect, useMemo } from 'react';
import {
  View,
  Text,
  SafeAreaView,
  ScrollView,
  Image,
  TouchableOpacity,
  Platform,
  useWindowDimensions,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import { useGoogleAuth } from '@/hooks/useGoogleAuth';
import { useAppleAuth } from '@/hooks/useAppleAuth';
import { useAuthStore } from '@/stores/authStore';
import { Button } from '@/components/common';
import { OAuthButton } from '@/components/auth/OAuthButton';
import { theme } from '@/theme';
import { showErrorAlert, logError } from '@/utils/errorHandler';
import { responsive, isSmallDevice } from '@/utils/responsive';
import { createStyleSheet } from '@/utils/styles';
import type { AuthStackParamList } from '@/types/navigation.types';

type NavigationProp = NativeStackNavigationProp<AuthStackParamList, 'Login'>;

/**
 * 화면 높이 기반 간격 계산
 * 작은 화면일수록 간격을 줄여서 스크롤 없이 모든 요소가 보이도록 함
 */
const getSpacingScale = (screenHeight: number) => {
  if (screenHeight < 700) return 0.6; // 매우 작은 화면 (iPhone SE)
  if (screenHeight < 800) return 0.75; // 작은 화면
  if (screenHeight < 900) return 0.9; // 중간 화면
  return 1.0; // 큰 화면 (Pro Max 등)
};

/**
 * 로그인 화면 (인스타그램 스타일)
 * 깔끔하고 미니멀한 디자인으로 전문적인 느낌
 */
export default function LoginScreen() {
  const { height: screenHeight } = useWindowDimensions();
  const { t } = useTranslation('auth');
  const insets = useSafeAreaInsets();
  const navigation = useNavigation<NavigationProp>();
  const { handleGoogleLogin, isLoading, error, isReady } = useGoogleAuth();
  const {
    handleAppleLogin,
    isLoading: isAppleLoading,
    error: appleError,
    isAvailable: isAppleAvailable
  } = useAppleAuth();
  // const { checkAuth } = useAuthStore(); // MVP: Auto-login disabled

  // 화면 높이에 따른 동적 간격 계산 (화면 회전 대응)
  const SPACING_SCALE = useMemo(() => getSpacingScale(screenHeight), [screenHeight]);

  const styles = useStyles(SPACING_SCALE);

  // MVP: Auto-login disabled for now
  // useEffect(() => {
  //   checkAuth();
  // }, []);

  // Google 에러 처리
  useEffect(() => {
    if (error) {
      logError(new Error(error), 'LoginScreen.googleAuth');
      showErrorAlert(error, t('login.loginFailed'));
    }
  }, [error, t]);

  // Apple 에러 처리
  useEffect(() => {
    if (appleError) {
      logError(new Error(appleError), 'LoginScreen.appleAuth');
      showErrorAlert(appleError, t('login.loginFailed'));
    }
  }, [appleError, t]);

  return (
    <SafeAreaView style={styles.safeArea}>
      <ScrollView
        contentContainerStyle={[
          styles.container,
          {
            paddingTop: Math.max(insets.top, theme.spacing[8]),
            paddingBottom: Math.max(insets.bottom, theme.spacing[6]),
          },
        ]}
        showsVerticalScrollIndicator={false}
      >
        {/* 중앙 컨텐츠 */}
        <View style={styles.content}>
          {/* 로고 */}
          <View style={styles.logoSection}>
            <View style={styles.logoContainer}>
              <Image
                source={require('@/../assets/images/upvy-mascot-noback.png')}
                style={styles.logoImage}
                resizeMode="contain"
              />
            </View>
            <Text style={styles.title}>{t('login.appName')}</Text>
            <Text style={styles.tagline}>{t('login.tagline')}</Text>
          </View>

          {/* 가치 제안 */}
          <View style={styles.valuePropsContainer}>
            <ValueProp
              title={t('login.valueProps.shorts.title')}
              description={t('login.valueProps.shorts.description')}
              styles={styles}
            />
            <ValueProp
              title={t('login.valueProps.habit.title')}
              description={t('login.valueProps.habit.description')}
              styles={styles}
            />
            <ValueProp
              title={t('login.valueProps.journey.title')}
              description={t('login.valueProps.journey.description')}
              styles={styles}
            />
          </View>
        </View>

        {/* 하단: 로그인 버튼 */}
        <View style={styles.bottomSection}>
          {/* Google 로그인 버튼 */}
          <OAuthButton
            provider="google"
            onPress={handleGoogleLogin}
            disabled={!isReady}
            loading={isLoading}
          >
            {t('login.googleLogin')}
          </OAuthButton>

          {/* Apple 로그인 버튼 (iOS만) */}
          {Platform.OS === 'ios' && isAppleAvailable && (
            <OAuthButton
              provider="apple"
              onPress={handleAppleLogin}
              disabled={!isAppleAvailable}
              loading={isAppleLoading}
            >
              {t('login.appleLogin')}
            </OAuthButton>
          )}

          {/* OR 구분선 */}
          <View style={styles.dividerContainer}>
            <View style={styles.divider} />
            <Text style={styles.dividerText}>{t('login.or')}</Text>
            <View style={styles.divider} />
          </View>

          {/* 이메일 로그인 버튼 */}
          <Button
            variant="outline"
            size="lg"
            fullWidth
            onPress={() => navigation.navigate('EmailSignIn')}
            style={styles.emailButton}
          >
            {t('login.emailLogin')}
          </Button>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

/**
 * 가치 제안 컴포넌트
 */
interface ValuePropProps {
  title: string;
  description: string;
  styles: ReturnType<typeof useStyles>;
}

function ValueProp({ title, description, styles }: ValuePropProps) {
  return (
    <View style={styles.valueProp}>
      <View style={styles.valuePropDot} />
      <View style={styles.valuePropContent}>
        <Text style={styles.valuePropTitle}>{title}</Text>
        <Text style={styles.valuePropDescription}>{description}</Text>
      </View>
    </View>
  );
}

const useStyles = (spacingScale: number) => createStyleSheet({
  safeArea: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },

  container: {
    flexGrow: 1,
    paddingHorizontal: theme.spacing[6],
    justifyContent: 'space-between',
  },

  content: {
    // OAuth 버튼이 스크롤 없이 보여야 함
    justifyContent: 'center',
    paddingTop: Math.round(theme.spacing[4] * spacingScale),
  },

  // Logo Section
  logoSection: {
    alignItems: 'center',
    marginBottom: Math.round(theme.spacing[8] * spacingScale),
  },

  logoContainer: {
    width: responsive({ xs: 120, md: 140, default: 160 }),
    height: responsive({ xs: 120, md: 140, default: 160 }),
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: Math.round(theme.spacing[4] * spacingScale),
  },

  logoImage: {
    width: '100%',
    height: '100%',
  },

  title: {
    fontSize: responsive({
      xs: 32,
      md: 36,
      default: 40,
    }),
    fontWeight: '700',
    color: theme.colors.text.primary,
    marginBottom: Math.round(theme.spacing[2] * spacingScale),
    letterSpacing: -0.5,
  },

  tagline: {
    fontSize: responsive({
      xs: theme.typography.fontSize.base,
      md: theme.typography.fontSize.base,
      default: theme.typography.fontSize.lg,
    }),
    color: theme.colors.text.secondary,
    textAlign: 'center',
  },

  // Value Props
  valuePropsContainer: {
    gap: Math.round(theme.spacing[4] * spacingScale),
    paddingHorizontal: theme.spacing[2],
    marginBottom: Math.round(theme.spacing[2] * spacingScale),
  },

  valueProp: {
    flexDirection: 'row',
    alignItems: 'flex-start',
  },

  valuePropDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: theme.colors.primary[500],
    marginTop: 7,
    marginRight: theme.spacing[3],
  },

  valuePropContent: {
    flex: 1,
  },

  valuePropTitle: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: Math.round(theme.spacing[1] * spacingScale),
  },

  valuePropDescription: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    lineHeight: theme.typography.lineHeight.normal * theme.typography.fontSize.sm,
  },

  // Bottom Section
  bottomSection: {
    gap: Math.round(theme.spacing[3] * spacingScale),
    paddingTop: Math.round(theme.spacing[4] * spacingScale),
  },

  dividerContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: theme.spacing[2],
  },

  divider: {
    flex: 1,
    height: 1,
    backgroundColor: theme.colors.border.light,
  },

  dividerText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.tertiary,
  },

  // 이메일 버튼 - outline이지만 명확하게
  emailButton: {
    borderWidth: 1.5,
    borderColor: theme.colors.primary[500],
  },
});
