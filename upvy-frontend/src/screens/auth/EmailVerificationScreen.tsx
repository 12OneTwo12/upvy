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
import { verifyEmailCode, resendVerificationCode, getCurrentUser, getMyProfile } from '@/api/auth.api';
import { setAccessToken, setRefreshToken } from '@/utils/storage';
import { useAuthStore } from '@/stores/authStore';
import type { AuthStackParamList } from '@/types/navigation.types';

type NavigationProp = NativeStackNavigationProp<AuthStackParamList, 'EmailVerification'>;
type RoutePropType = RouteProp<AuthStackParamList, 'EmailVerification'>;

/**
 * 이메일 인증 코드 입력 화면
 */
export default function EmailVerificationScreen() {
  const { t } = useTranslation('auth');
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const navigation = useNavigation<NavigationProp>();
  const route = useRoute<RoutePropType>();
  const authStore = useAuthStore();

  const { email } = route.params;

  const [code, setCode] = useState(['', '', '', '', '', '']);
  const [isLoading, setIsLoading] = useState(false);
  const [isResending, setIsResending] = useState(false);

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

  const handleVerify = async () => {
    try {
      const codeString = code.join('');

      if (codeString.length !== 6) {
        showErrorAlert(t('emailVerification.codeLabel'), t('emailVerification.error'));
        return;
      }

      setIsLoading(true);

      const response = await verifyEmailCode({
        email,
        code: codeString,
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

      // 프로필 설정 화면으로 이동 (또는 메인 화면)
      // navigation.replace('ProfileSetup');
    } catch (error: any) {
      logError(error, 'EmailVerificationScreen.handleVerify');
      showErrorAlert(error, t('emailVerification.failed'));
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

      Alert.alert(t('emailVerification.resendSuccess'), t('emailVerification.resendSuccessMessage'));
    } catch (error: any) {
      logError(error, 'EmailVerificationScreen.handleResend');
      showErrorAlert(error, t('emailVerification.resendFailed'));
    } finally{
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
            <Text style={styles.title}>{t('emailVerification.title')}</Text>
            <Text style={styles.subtitle}>{t('emailVerification.subtitle', { email })}</Text>
          </View>

          {/* Code Input */}
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
              />
            ))}
          </View>

          {/* Buttons */}
          <View style={styles.buttonContainer}>
            <Button
              variant="primary"
              size="lg"
              fullWidth
              onPress={handleVerify}
              loading={isLoading}
              disabled={isLoading || code.join('').length !== 6}
            >
              {t('emailVerification.verify')}
            </Button>

            <TouchableOpacity
              onPress={handleResend}
              style={styles.resendButton}
              disabled={isResending}
            >
              <Text style={styles.resendText}>
                {isResending ? t('emailVerification.resending') : t('emailVerification.resendCode')}
              </Text>
            </TouchableOpacity>
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
    marginBottom: theme.spacing[10],
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

  buttonContainer: {
    gap: theme.spacing[4],
    marginTop: theme.spacing[8],
  },

  resendButton: {
    alignItems: 'center',
    paddingVertical: theme.spacing[2],
  },

  resendText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.primary[500],
    fontWeight: theme.typography.fontWeight.medium,
  },
});
