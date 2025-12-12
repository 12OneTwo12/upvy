import React, { useEffect } from 'react';
import {
  View,
  Text,
  SafeAreaView,
  ScrollView,
  Dimensions,
  Image,
  TouchableOpacity,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useTranslation, Trans } from 'react-i18next';
import { useGoogleAuth } from '@/hooks/useGoogleAuth';
import { useAuthStore } from '@/stores/authStore';
import { Button } from '@/components/common';
import { theme } from '@/theme';
import { showErrorAlert, logError } from '@/utils/errorHandler';
import { responsive, isSmallDevice } from '@/utils/responsive';
import { createStyleSheet } from '@/utils/styles';
import type { AuthStackParamList } from '@/types/navigation.types';

type NavigationProp = NativeStackNavigationProp<AuthStackParamList, 'Login'>;

const { width } = Dimensions.get('window');

/**
 * 로그인 화면 (인스타그램 스타일)
 * 깔끔하고 미니멀한 디자인으로 전문적인 느낌
 */
export default function LoginScreen() {
  const { t } = useTranslation('auth');
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const navigation = useNavigation<NavigationProp>();
  const { handleGoogleLogin, isLoading, error, isReady } = useGoogleAuth();
  // const { checkAuth } = useAuthStore(); // MVP: Auto-login disabled

  // MVP: Auto-login disabled for now
  // useEffect(() => {
  //   checkAuth();
  // }, []);

  // 에러 처리
  useEffect(() => {
    if (error) {
      logError(new Error(error), 'LoginScreen.googleAuth');
      showErrorAlert(error, t('login.loginFailed'));
    }
  }, [error, t]);

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
            />
            <ValueProp
              title={t('login.valueProps.habit.title')}
              description={t('login.valueProps.habit.description')}
            />
            <ValueProp
              title={t('login.valueProps.journey.title')}
              description={t('login.valueProps.journey.description')}
            />
          </View>
        </View>

        {/* 하단: 로그인 버튼 */}
        <View style={styles.bottomSection}>
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

          {/* OR 구분선 */}
          <View style={styles.dividerContainer}>
            <View style={styles.divider} />
            <Text style={styles.dividerText}>{t('login.or')}</Text>
            <View style={styles.divider} />
          </View>

          {/* Google 로그인 버튼 */}
          <Button
            variant="primary"
            size="lg"
            fullWidth
            onPress={handleGoogleLogin}
            disabled={!isReady}
            loading={isLoading}
            style={styles.googleButton}
          >
            {t('login.googleLogin')}
          </Button>

          {/* 약관 동의 */}
          <Text style={styles.termsText}>
            <Trans
              i18nKey="login.termsAgree"
              t={t}
              components={{
                terms: (
                  <Text
                    style={styles.termsLink}
                    onPress={() => navigation.navigate('TermsOfService')}
                  />
                ),
                policy: (
                  <Text
                    style={styles.termsLink}
                    onPress={() => navigation.navigate('PrivacyPolicy')}
                  />
                ),
              }}
            />
          </Text>

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
}

function ValueProp({ title, description }: ValuePropProps) {
  const styles = useStyles();
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

const useStyles = createStyleSheet({
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
    flex: 1,
    justifyContent: 'center',
    paddingTop: theme.spacing[12],
  },

  // Logo Section
  logoSection: {
    alignItems: 'center',
    marginBottom: theme.spacing[12],
  },

  logoContainer: {
    width: responsive({ xs: 140, md: 160, default: 140 }),
    height: responsive({ xs: 140, md: 160, default: 140 }),
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: theme.spacing[5],
  },

  logoImage: {
    width: '100%',
    height: '100%',
  },

  title: {
    fontSize: responsive({
      xs: 32,
      md: 36,
      default: 32,
    }),
    fontWeight: '700',
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[2],
    letterSpacing: -0.5,
  },

  tagline: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.secondary,
    textAlign: 'center',
  },

  // Value Props
  valuePropsContainer: {
    gap: theme.spacing[5],
    paddingHorizontal: theme.spacing[2],
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
    marginTop: 8,
    marginRight: theme.spacing[3],
  },

  valuePropContent: {
    flex: 1,
  },

  valuePropTitle: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[1],
  },

  valuePropDescription: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    lineHeight: theme.typography.lineHeight.relaxed * theme.typography.fontSize.sm,
  },

  // Bottom Section
  bottomSection: {
    gap: theme.spacing[4],
    paddingTop: theme.spacing[8],
  },

  emailButton: {
    borderWidth: 2,
    borderColor: theme.colors.primary[500],
  },

  dividerContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: theme.spacing[3],
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

  googleButton: {
    ...theme.shadows.sm,
  },

  termsText: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
    textAlign: 'center',
    lineHeight: theme.typography.lineHeight.relaxed * theme.typography.fontSize.xs,
    paddingHorizontal: theme.spacing[2],
  },

  termsLink: {
    color: theme.colors.primary[500],
    fontWeight: theme.typography.fontWeight.medium,
    textDecorationLine: 'underline',
  },
});
