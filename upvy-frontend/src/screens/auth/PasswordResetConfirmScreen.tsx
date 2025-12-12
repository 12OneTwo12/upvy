import React, { useState, useRef } from 'react';
import {
  View,
  Text,
  SafeAreaView,
  ScrollView,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
  TextInput,
  Alert,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/common';
import { theme } from '@/theme';
import { showErrorAlert, logError } from '@/utils/errorHandler';
import { createStyleSheet } from '@/utils/styles';
import { resetPasswordVerifyCode, resetPasswordConfirm, resendVerificationCode } from '@/api/auth.api';
import type { AuthStackParamList } from '@/types/navigation.types';

type NavigationProp = NativeStackNavigationProp<AuthStackParamList, 'PasswordResetConfirm'>;
type RoutePropType = RouteProp<AuthStackParamList, 'PasswordResetConfirm'>;

/**
 * 비밀번호 재설정 확정 화면
 * 6자리 인증 코드와 새 비밀번호를 입력받습니다
 */
export default function PasswordResetConfirmScreen() {
  const { t } = useTranslation('auth');
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const navigation = useNavigation<NavigationProp>();
  const route = useRoute<RoutePropType>();

  const { email } = route.params;

  const [code, setCode] = useState(['', '', '', '', '', '']);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isResending, setIsResending] = useState(false);
  const [isCodeVerified, setIsCodeVerified] = useState(false);

  const inputRefs = useRef<Array<TextInput | null>>([]);

  const handleCodeChange = (index: number, value: string) => {
    if (value.length > 1) {
      value = value.charAt(0);
    }

    const newCode = [...code];
    newCode[index] = value;
    setCode(newCode);

    // 자동으로 다음 입력 필드로 이동
    if (value && index < 5) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyPress = (index: number, key: string) => {
    if (key === 'Backspace' && !code[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }
  };

  const validatePassword = (password: string): boolean => {
    return password.length >= 8;
  };

  const handleVerifyCode = async () => {
    try {
      const codeString = code.join('');

      if (codeString.length !== 6) {
        showErrorAlert(t('passwordResetConfirm.enterCode'), t('passwordResetConfirm.error'));
        return;
      }

      setIsLoading(true);

      await resetPasswordVerifyCode({
        email,
        code: codeString,
      });

      setIsCodeVerified(true);
      Alert.alert(t('passwordResetConfirm.verifySuccess'), t('passwordResetConfirm.verifySuccessMessage'));
    } catch (error: any) {
      logError(error, 'PasswordResetConfirmScreen.handleVerifyCode');
      showErrorAlert(error, t('passwordResetConfirm.verifyFailed'));
    } finally {
      setIsLoading(false);
    }
  };

  const handleSubmit = async () => {
    try {
      const codeString = code.join('');

      if (codeString.length !== 6) {
        showErrorAlert(t('passwordResetConfirm.enterCode'), t('passwordResetConfirm.error'));
        return;
      }

      if (!newPassword.trim()) {
        showErrorAlert(t('passwordResetConfirm.enterNewPassword'), t('passwordResetConfirm.error'));
        return;
      }

      if (!validatePassword(newPassword)) {
        showErrorAlert(t('passwordResetConfirm.passwordMinLength'), t('passwordResetConfirm.error'));
        return;
      }

      if (newPassword !== confirmPassword) {
        showErrorAlert(t('passwordResetConfirm.passwordMismatch'), t('passwordResetConfirm.error'));
        return;
      }

      setIsLoading(true);

      await resetPasswordConfirm({
        email,
        code: codeString,
        newPassword,
      });

      Alert.alert(
        t('passwordResetConfirm.successTitle'),
        t('passwordResetConfirm.successMessage'),
        [
          {
            text: t('passwordResetConfirm.confirm'),
            onPress: () => navigation.navigate('EmailSignIn'),
          },
        ]
      );
    } catch (error: any) {
      logError(error, 'PasswordResetConfirmScreen.handleSubmit');
      showErrorAlert(error, t('passwordResetConfirm.failed'));
    } finally {
      setIsLoading(false);
    }
  };

  const handleResend = async () => {
    try {
      setIsResending(true);

      await resendVerificationCode({
        email,
        language: 'ko',
      });

      Alert.alert(t('passwordResetConfirm.resendSuccessTitle'), t('passwordResetConfirm.resendSuccessMessage'));
    } catch (error: any) {
      logError(error, 'PasswordResetConfirmScreen.handleResend');
      showErrorAlert(error, t('passwordResetConfirm.resendFailed'));
    } finally {
      setIsResending(false);
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
            <Text style={styles.title}>{t('passwordResetConfirm.title')}</Text>
            <Text style={styles.subtitle}>{t('passwordResetConfirm.subtitle', { email })}</Text>
          </View>

          {/* Code Input */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('passwordResetConfirm.step1')}</Text>
            <View style={styles.codeContainer}>
              {code.map((digit, index) => (
                <TextInput
                  key={index}
                  ref={(ref) => {
                    inputRefs.current[index] = ref;
                  }}
                  style={[styles.codeInput, digit && styles.codeInputFilled]}
                  value={digit}
                  onChangeText={(value) => handleCodeChange(index, value)}
                  onKeyPress={(e) => handleKeyPress(index, e.nativeEvent.key)}
                  keyboardType="number-pad"
                  maxLength={1}
                  selectTextOnFocus
                  editable={!isCodeVerified}
                />
              ))}
            </View>

            {!isCodeVerified && (
              <>
                <TouchableOpacity
                  onPress={handleResend}
                  style={styles.resendButton}
                  disabled={isResending}
                >
                  <Text style={styles.resendText}>
                    {isResending ? t('passwordResetConfirm.resending') : t('passwordResetConfirm.resendCode')}
                  </Text>
                </TouchableOpacity>

                <Button
                  variant="outline"
                  size="md"
                  fullWidth
                  onPress={handleVerifyCode}
                  loading={isLoading}
                  disabled={isLoading || code.join('').length !== 6}
                  style={styles.verifyButton}
                >
                  {t('passwordResetConfirm.verifyCode')}
                </Button>
              </>
            )}

            {isCodeVerified && (
              <View style={styles.successBadge}>
                <Text style={styles.successText}>{t('passwordResetConfirm.verified')}</Text>
              </View>
            )}
          </View>

          {/* Password Input */}
          {isCodeVerified && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>{t('passwordResetConfirm.step2')}</Text>
              <View style={styles.inputGroup}>
                <Text style={styles.label}>{t('passwordResetConfirm.newPassword')}</Text>
                <TextInput
                  value={newPassword}
                  onChangeText={setNewPassword}
                  placeholder={t('passwordResetConfirm.newPasswordPlaceholder')}
                  secureTextEntry
                  style={styles.input}
                />
              </View>

              <View style={styles.inputGroup}>
                <Text style={styles.label}>{t('passwordResetConfirm.confirmPassword')}</Text>
                <TextInput
                  value={confirmPassword}
                  onChangeText={setConfirmPassword}
                  placeholder={t('passwordResetConfirm.confirmPasswordPlaceholder')}
                  secureTextEntry
                  style={styles.input}
                />
              </View>
            </View>
          )}

          {/* Submit Button */}
          {isCodeVerified && (
            <View style={styles.buttonContainer}>
              <Button
                variant="primary"
                size="lg"
                fullWidth
                onPress={handleSubmit}
                loading={isLoading}
                disabled={isLoading}
              >
                {t('passwordResetConfirm.submit')}
              </Button>
            </View>
          )}
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
    lineHeight: theme.typography.lineHeight.relaxed * theme.typography.fontSize.base,
  },

  section: {
    marginBottom: theme.spacing[8],
  },

  sectionTitle: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[4],
  },

  codeContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: theme.spacing[4],
    gap: theme.spacing[2],
  },

  codeInput: {
    flex: 1,
    height: 56,
    borderWidth: 2,
    borderColor: theme.colors.border.light,
    borderRadius: theme.borderRadius.md,
    textAlign: 'center',
    fontSize: theme.typography.fontSize.xl,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    backgroundColor: theme.colors.background.primary,
  },

  codeInputFilled: {
    borderColor: theme.colors.text.primary,
  },

  resendButton: {
    alignItems: 'center',
    paddingVertical: theme.spacing[2],
    marginBottom: theme.spacing[4],
  },

  resendText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.primary[500],
    fontWeight: theme.typography.fontWeight.medium,
  },

  verifyButton: {
    marginTop: theme.spacing[2],
  },

  successBadge: {
    backgroundColor: theme.colors.background.secondary,
    borderRadius: theme.borderRadius.md,
    paddingVertical: theme.spacing[3],
    paddingHorizontal: theme.spacing[4],
    alignItems: 'center',
  },

  successText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.primary[500],
  },

  inputGroup: {
    gap: theme.spacing[2],
    marginBottom: theme.spacing[4],
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

  buttonContainer: {
    marginTop: theme.spacing[4],
  },
});
