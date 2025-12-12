import React, { useState, useEffect, useRef } from 'react';
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
import { emailSignup } from '@/api/auth.api';
import type { AuthStackParamList } from '@/types/navigation.types';
import type { EmailSignupRequest } from '@/types/auth.types';

type NavigationProp = NativeStackNavigationProp<AuthStackParamList, 'EmailSignUp'>;

/**
 * 이메일 회원가입 화면
 */
export default function EmailSignUpScreen() {
  const { t, i18n } = useTranslation('auth');
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const navigation = useNavigation<NavigationProp>();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [showEmailError, setShowEmailError] = useState(false);
  const [showPasswordError, setShowPasswordError] = useState(false);

  const emailTimerRef = useRef<NodeJS.Timeout | null>(null);
  const passwordTimerRef = useRef<NodeJS.Timeout | null>(null);

  // Debounce email validation
  useEffect(() => {
    if (emailTimerRef.current) {
      clearTimeout(emailTimerRef.current);
    }

    if (email.length > 0) {
      emailTimerRef.current = setTimeout(() => {
        setShowEmailError(!validateEmail(email));
      }, 1000);
    } else {
      setShowEmailError(false);
    }

    return () => {
      if (emailTimerRef.current) {
        clearTimeout(emailTimerRef.current);
      }
    };
  }, [email]);

  // Debounce password validation
  useEffect(() => {
    if (passwordTimerRef.current) {
      clearTimeout(passwordTimerRef.current);
    }

    if (password.length > 0) {
      passwordTimerRef.current = setTimeout(() => {
        setShowPasswordError(!validatePassword(password).isValid);
      }, 1000);
    } else {
      setShowPasswordError(false);
    }

    return () => {
      if (passwordTimerRef.current) {
        clearTimeout(passwordTimerRef.current);
      }
    };
  }, [password]);

  const validateEmail = (email: string): boolean => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  };

  const validatePassword = (password: string): { isValid: boolean; hasLength: boolean; hasLetterAndNumber: boolean } => {
    const hasLength = password.length >= 8;
    const hasLetter = /[a-zA-Z]/.test(password);
    const hasNumber = /[0-9]/.test(password);
    const hasLetterAndNumber = hasLetter && hasNumber;

    return {
      isValid: hasLength && hasLetterAndNumber,
      hasLength,
      hasLetterAndNumber,
    };
  };

  const handleSignUp = async () => {
    try {
      // 유효성 검증
      if (!email.trim()) {
        showErrorAlert(t('emailSignup.emailError'), '');
        return;
      }

      if (!validateEmail(email)) {
        showErrorAlert(t('emailSignup.emailError'), '');
        return;
      }

      if (!password.trim()) {
        showErrorAlert(t('emailSignup.passwordError'), '');
        return;
      }

      const passwordValidation = validatePassword(password);
      if (!passwordValidation.isValid) {
        showErrorAlert(t('emailSignup.passwordError'), '');
        return;
      }

      if (password !== confirmPassword) {
        showErrorAlert(t('emailSignup.confirmPasswordPlaceholder'), '');
        return;
      }

      setIsLoading(true);

      const request: EmailSignupRequest = {
        email: email.trim(),
        password,
        language: i18n.language,
      };

      await emailSignup(request);

      // 성공 시 이메일 인증 화면으로 이동
      navigation.navigate('EmailVerification', { email: email.trim() });
    } catch (error: any) {
      logError(error, 'EmailSignUpScreen.handleSignUp');
      showErrorAlert(error, t('emailSignup.failed'));
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
            <Text style={styles.title}>{t('emailSignup.title')}</Text>
            <Text style={styles.subtitle}>{t('emailSignup.subtitle')}</Text>
          </View>

          {/* Form */}
          <View style={styles.form}>
            {/* 이메일 */}
            <View style={styles.inputGroup}>
              <Text style={styles.label}>{t('emailSignup.email')}</Text>
              <TextInput
                value={email}
                onChangeText={setEmail}
                placeholder={t('emailSignup.emailPlaceholder')}
                autoCapitalize="none"
                autoCorrect={false}
                keyboardType="email-address"
                style={styles.input}
              />
              {showEmailError && (
                <Text style={styles.errorHint}>{t('emailSignup.emailError')}</Text>
              )}
            </View>

            {/* 비밀번호 */}
            <View style={styles.inputGroup}>
              <Text style={styles.label}>{t('emailSignup.password')}</Text>
              <TextInput
                value={password}
                onChangeText={setPassword}
                placeholder={t('emailSignup.passwordPlaceholder')}
                secureTextEntry
                style={styles.input}
              />
              {password.length === 0 && (
                <Text style={styles.hint}>{t('emailSignup.passwordHint')}</Text>
              )}
              {showPasswordError && (
                <Text style={styles.errorHint}>{t('emailSignup.passwordError')}</Text>
              )}
            </View>

            {/* 비밀번호 확인 */}
            <View style={styles.inputGroup}>
              <Text style={styles.label}>{t('emailSignup.confirmPassword')}</Text>
              <TextInput
                value={confirmPassword}
                onChangeText={setConfirmPassword}
                placeholder={t('emailSignup.confirmPasswordPlaceholder')}
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
              onPress={handleSignUp}
              loading={isLoading}
              disabled={
                isLoading ||
                !email.trim() ||
                !validateEmail(email) ||
                !password.trim() ||
                !confirmPassword.trim() ||
                !validatePassword(password).isValid ||
                password !== confirmPassword
              }
            >
              {t('emailSignup.signup')}
            </Button>

            <View style={styles.linkContainer}>
              <Text style={styles.linkLabel}>{t('emailSignup.alreadyHaveAccount')} </Text>
              <TouchableOpacity onPress={() => navigation.navigate('EmailSignIn')}>
                <Text style={styles.linkText}>{t('emailSignup.signin')}</Text>
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

  label: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.medium,
    color: theme.colors.text.primary,
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

  hint: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
  },

  errorHint: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.error,
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
