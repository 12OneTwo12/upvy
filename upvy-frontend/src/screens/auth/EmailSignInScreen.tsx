import React, { useState } from 'react';
import {
  View,
  Text,
  SafeAreaView,
  ScrollView,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
  TextInput,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/common';
import { theme } from '@/theme';
import { showErrorAlert, logError } from '@/utils/errorHandler';
import { createStyleSheet } from '@/utils/styles';
import { emailSignin, getCurrentUser, getMyProfile } from '@/api/auth.api';
import { setAccessToken, setRefreshToken } from '@/utils/storage';
import { useAuthStore } from '@/stores/authStore';
import type { AuthStackParamList } from '@/types/navigation.types';

type NavigationProp = NativeStackNavigationProp<AuthStackParamList, 'EmailSignIn'>;

/**
 * 이메일 로그인 화면
 */
export default function EmailSignInScreen() {
  const { t } = useTranslation('auth');
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const navigation = useNavigation<NavigationProp>();
  const authStore = useAuthStore();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleSignIn = async () => {
    try {
      if (!email.trim()) {
        showErrorAlert(t('emailSignin.emailPlaceholder'), t('emailSignin.error'));
        return;
      }

      if (!password.trim()) {
        showErrorAlert(t('emailSignin.passwordPlaceholder'), t('emailSignin.error'));
        return;
      }

      setIsLoading(true);

      const response = await emailSignin({
        email: email.trim(),
        password,
      });

      // 토큰 저장
      await setAccessToken(response.accessToken);
      await setRefreshToken(response.refreshToken);

      // 전체 사용자 정보 조회
      const user = await getCurrentUser();

      // 프로필 조회 시도 (프로필이 없을 수 있음)
      let profile = null;
      try {
        profile = await getMyProfile();
      } catch (profileError) {
        // 프로필이 없는 경우는 무시 (첫 로그인)
      }

      // 인증 상태 업데이트
      if (authStore.login) {
        await authStore.login(response.accessToken, response.refreshToken, user, profile || undefined);
      }

      // 메인 화면으로 이동
      // navigation.replace('Main');
    } catch (error: any) {
      logError(error, 'EmailSignInScreen.handleSignIn');
      showErrorAlert(error, t('emailSignin.failed'));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.container}
      >
        <ScrollView
          contentContainerStyle={[
            styles.scrollContent,
            {
              paddingTop: Math.max(insets.top, theme.spacing[4]),
              paddingBottom: Math.max(insets.bottom, theme.spacing[6]),
            },
          ]}
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
        >
          {/* Header */}
          <View style={styles.header}>
            <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
              <Text style={styles.backButtonText}>←</Text>
            </TouchableOpacity>
            <Text style={styles.title}>{t('emailSignin.title')}</Text>
            <Text style={styles.subtitle}>{t('emailSignin.subtitle')}</Text>
          </View>

          {/* Form */}
          <View style={styles.form}>
            {/* 이메일 */}
            <View style={styles.inputGroup}>
              <Text style={styles.label}>{t('emailSignin.email')}</Text>
              <TextInput
                value={email}
                onChangeText={setEmail}
                placeholder={t('emailSignin.emailPlaceholder')}
                autoCapitalize="none"
                autoCorrect={false}
                keyboardType="email-address"
                style={styles.input}
              />
            </View>

            {/* 비밀번호 */}
            <View style={styles.inputGroup}>
              <View style={styles.labelRow}>
                <Text style={styles.label}>{t('emailSignin.password')}</Text>
                <TouchableOpacity onPress={() => navigation.navigate('PasswordReset')}>
                  <Text style={styles.forgotPassword}>{t('emailSignin.forgotPassword')}</Text>
                </TouchableOpacity>
              </View>
              <TextInput
                value={password}
                onChangeText={setPassword}
                placeholder={t('emailSignin.passwordPlaceholder')}
                secureTextEntry
                style={styles.input}
              />
            </View>
          </View>

          {/* Submit Button */}
          <View style={styles.buttonContainer}>
            <Button
              variant="primary"
              size="lg"
              fullWidth
              onPress={handleSignIn}
              loading={isLoading}
              disabled={isLoading}
            >
              {t('emailSignin.signin')}
            </Button>

            <View style={styles.linkContainer}>
              <Text style={styles.linkLabel}>{t('emailSignin.noAccount')} </Text>
              <TouchableOpacity onPress={() => navigation.navigate('EmailSignUp')}>
                <Text style={styles.linkText}>{t('emailSignin.signup')}</Text>
              </TouchableOpacity>
            </View>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const useStyles = createStyleSheet({
  safeArea: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },

  container: {
    flex: 1,
  },

  scrollContent: {
    flexGrow: 1,
    paddingHorizontal: theme.spacing[6],
  },

  header: {
    marginBottom: theme.spacing[8],
  },

  backButton: {
    width: 44,
    height: 44,
    justifyContent: 'center',
    marginBottom: theme.spacing[4],
  },

  backButtonText: {
    fontSize: 28,
    color: theme.colors.text.primary,
  },

  title: {
    fontSize: theme.typography.fontSize['2xl'],
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[2],
  },

  subtitle: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.secondary,
  },

  form: {
    gap: theme.spacing[5],
    marginBottom: theme.spacing[8],
  },

  inputGroup: {
    gap: theme.spacing[2],
  },

  labelRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },

  label: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.medium,
    color: theme.colors.text.primary,
  },

  forgotPassword: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.primary[500],
    fontWeight: theme.typography.fontWeight.medium,
  },

  input: {
    height: 48,
    borderWidth: 2,
    borderColor: theme.colors.border.light,
    borderRadius: theme.borderRadius.md,
    paddingHorizontal: theme.spacing[4],
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    backgroundColor: theme.colors.background.primary,
  },

  buttonContainer: {
    gap: theme.spacing[4],
  },

  linkContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
  },

  linkLabel: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
  },

  linkText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.primary[500],
    fontWeight: theme.typography.fontWeight.medium,
  },
});
