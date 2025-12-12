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
  Alert,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/common';
import { theme } from '@/theme';
import { showErrorAlert, logError } from '@/utils/errorHandler';
import { createStyleSheet } from '@/utils/styles';
import { changePassword } from '@/api/auth.api';
import type { RootStackParamList } from '@/types/navigation.types';

type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'PasswordChange'>;

/**
 * 비밀번호 변경 화면
 * 로그인 후 설정 메뉴에서 접근합니다
 * 현재 비밀번호를 알고 있을 때 사용합니다
 */
export default function PasswordChangeScreen() {
  const { t } = useTranslation('settings');
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const navigation = useNavigation<NavigationProp>();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [showPasswordError, setShowPasswordError] = useState(false);

  const passwordTimerRef = useRef<NodeJS.Timeout | null>(null);

  // Debounce password validation
  useEffect(() => {
    if (passwordTimerRef.current) {
      clearTimeout(passwordTimerRef.current);
    }

    if (newPassword.length > 0) {
      passwordTimerRef.current = setTimeout(() => {
        setShowPasswordError(!validatePassword(newPassword).isValid);
      }, 1000);
    } else {
      setShowPasswordError(false);
    }

    return () => {
      if (passwordTimerRef.current) {
        clearTimeout(passwordTimerRef.current);
      }
    };
  }, [newPassword]);

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

  const handleSubmit = async () => {
    try {
      if (!currentPassword.trim()) {
        showErrorAlert(t('passwordChange.enterCurrentPassword'), t('passwordChange.error'));
        return;
      }

      if (!newPassword.trim()) {
        showErrorAlert(t('passwordChange.enterNewPassword'), t('passwordChange.error'));
        return;
      }

      const passwordValidation = validatePassword(newPassword);
      if (!passwordValidation.isValid) {
        showErrorAlert(t('passwordChange.passwordMinLength'), t('passwordChange.error'));
        return;
      }

      if (newPassword !== confirmPassword) {
        showErrorAlert(t('passwordChange.passwordMismatch'), t('passwordChange.error'));
        return;
      }

      if (currentPassword === newPassword) {
        showErrorAlert(t('passwordChange.passwordSame'), t('passwordChange.error'));
        return;
      }

      setIsLoading(true);

      await changePassword({
        currentPassword,
        newPassword,
      });

      Alert.alert(
        t('passwordChange.successTitle'),
        t('passwordChange.successMessage'),
        [
          {
            text: t('passwordChange.confirm'),
            onPress: () => navigation.goBack(),
          },
        ]
      );
    } catch (error: any) {
      logError(error, 'PasswordChangeScreen.handleSubmit');
      showErrorAlert(error, t('passwordChange.failed'));
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
            <Text style={styles.title}>{t('passwordChange.title')}</Text>
            <Text style={styles.subtitle}>{t('passwordChange.subtitle')}</Text>
          </View>

          {/* Form */}
          <View style={styles.form}>
            {/* 현재 비밀번호 */}
            <View style={styles.inputGroup}>
              <Text style={styles.label}>{t('passwordChange.currentPassword')}</Text>
              <TextInput
                value={currentPassword}
                onChangeText={setCurrentPassword}
                placeholder={t('passwordChange.currentPasswordPlaceholder')}
                secureTextEntry
                style={styles.input}
              />
            </View>

            {/* 새 비밀번호 */}
            <View style={styles.inputGroup}>
              <Text style={styles.label}>{t('passwordChange.newPassword')}</Text>
              <TextInput
                value={newPassword}
                onChangeText={setNewPassword}
                placeholder={t('passwordChange.newPasswordPlaceholder')}
                secureTextEntry
                style={styles.input}
              />
              {newPassword.length === 0 && (
                <Text style={styles.hint}>{t('passwordChange.passwordHint')}</Text>
              )}
              {showPasswordError && (
                <Text style={styles.errorHint}>{t('passwordChange.passwordError')}</Text>
              )}
            </View>

            {/* 비밀번호 확인 */}
            <View style={styles.inputGroup}>
              <Text style={styles.label}>{t('passwordChange.confirmPassword')}</Text>
              <TextInput
                value={confirmPassword}
                onChangeText={setConfirmPassword}
                placeholder={t('passwordChange.confirmPasswordPlaceholder')}
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
              onPress={handleSubmit}
              loading={isLoading}
              disabled={
                isLoading ||
                !currentPassword.trim() ||
                !newPassword.trim() ||
                !confirmPassword.trim() ||
                !validatePassword(newPassword).isValid ||
                newPassword !== confirmPassword
              }
            >
              {t('passwordChange.submit')}
            </Button>
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
});
