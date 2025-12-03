import React, { useState } from 'react';
import {
  View,
  Text,
  SafeAreaView,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  TouchableOpacity,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';
import { checkNickname, createProfile } from '@/api/auth.api';
import { useAuthStore } from '@/stores/authStore';
import { Button, Input } from '@/components/common';
import { theme } from '@/theme';
import { showErrorAlert, withErrorHandling } from '@/utils/errorHandler';
import { createStyleSheet } from '@/utils/styles';

/**
 * 프로필 설정 화면 (인스타그램 스타일)
 * 깔끔하고 직관적인 프로필 설정 경험
 */
export default function ProfileSetupScreen() {
  const { t } = useTranslation('auth');
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const { updateProfile } = useAuthStore();

  const [nickname, setNickname] = useState('');
  const [bio, setBio] = useState('');
  const [isCheckingNickname, setIsCheckingNickname] = useState(false);
  const [nicknameAvailable, setNicknameAvailable] = useState<boolean | null>(
    null
  );
  const [isCreating, setIsCreating] = useState(false);

  /**
   * 닉네임 중복 확인
   */
  const handleCheckNickname = async () => {
    if (!nickname || nickname.length < 2) {
      showErrorAlert(t('profileSetup.error.nicknameTooShort'), t('profileSetup.error.alert'));
      return;
    }

    setIsCheckingNickname(true);
    const result = await withErrorHandling(
      async () => await checkNickname(nickname),
      {
        showAlert: true,
        alertTitle: t('profileSetup.error.nicknameCheckFailed'),
        logContext: 'ProfileSetupScreen.checkNickname',
      }
    );
    setIsCheckingNickname(false);

    if (result) {
      // isDuplicated: true면 중복, false면 사용 가능
      setNicknameAvailable(!result.isDuplicated);
      if (result.isDuplicated) {
        showErrorAlert(
          t('profileSetup.nicknameTaken'),
          t('profileSetup.error.alert')
        );
      }
    }
  };

  /**
   * 프로필 생성
   */
  const handleCreateProfile = async () => {
    if (!nickname || nicknameAvailable !== true) {
      showErrorAlert(t('profileSetup.error.nicknameCheckRequired'), t('profileSetup.error.alert'));
      return;
    }

    setIsCreating(true);
    const result = await withErrorHandling(
      async () =>
        await createProfile({ nickname, bio: bio || undefined }),
      {
        showAlert: true,
        alertTitle: t('profileSetup.error.profileCreationFailed'),
        logContext: 'ProfileSetupScreen.createProfile',
      }
    );
    setIsCreating(false);

    if (result) {
      // 백엔드는 UserProfileResponse를 직접 반환
      updateProfile(result);
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <KeyboardAvoidingView
        style={styles.keyboardAvoid}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={Platform.OS === 'ios' ? 0 : 0}
      >
        <ScrollView
          contentContainerStyle={[
            styles.container,
            {
              paddingTop: Math.max(insets.top, theme.spacing[4]),
              paddingBottom: Math.max(insets.bottom, theme.spacing[6]),
            },
          ]}
          showsVerticalScrollIndicator={false}
          keyboardShouldPersistTaps="handled"
        >
          {/* 헤더 */}
          <View style={styles.header}>
            <Text style={styles.title}>{t('profileSetup.title')}</Text>
            <Text style={styles.subtitle}>
              {t('profileSetup.subtitle')}
            </Text>
          </View>

          {/* 프로필 이미지 */}
          <View style={styles.profileImageSection}>
            <TouchableOpacity style={styles.profileImageContainer}>
              <Text style={styles.profileImagePlaceholder}>👤</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.changePhotoButton}>
              <Text style={styles.changePhotoText}>{t('profileSetup.changePhoto')}</Text>
            </TouchableOpacity>
          </View>

          {/* 폼 */}
          <View style={styles.form}>
            {/* 닉네임 입력 */}
            <View style={styles.inputGroup}>
              <Text style={styles.label}>
                {t('profileSetup.nickname')} <Text style={styles.required}>{t('profileSetup.nicknameRequired')}</Text>
              </Text>
              <View style={styles.nicknameInputContainer}>
                <Input
                  placeholder={t('profileSetup.nicknamePlaceholder')}
                  value={nickname}
                  onChangeText={(text) => {
                    setNickname(text);
                    setNicknameAvailable(null);
                  }}
                  maxLength={20}
                  containerStyle={styles.nicknameInput}
                  error={
                    nicknameAvailable === false
                      ? t('profileSetup.nicknameTaken')
                      : undefined
                  }
                />
                <Button
                  variant="outline"
                  size="md"
                  onPress={handleCheckNickname}
                  disabled={isCheckingNickname || !nickname}
                  loading={isCheckingNickname}
                  style={styles.checkButton}
                >
                  {t('profileSetup.checkNickname')}
                </Button>
              </View>
              {nicknameAvailable === true && (
                <View style={styles.successMessage}>
                  <Text style={styles.successText}>
                    {t('profileSetup.nicknameAvailable')}
                  </Text>
                </View>
              )}
              <Text style={styles.helperText}>{t('profileSetup.nicknameHelper')}</Text>
            </View>

            {/* 자기소개 입력 */}
            <View style={styles.inputGroup}>
              <Text style={styles.label}>{t('profileSetup.bio')}</Text>
              <Input
                placeholder={t('profileSetup.bioPlaceholder')}
                value={bio}
                onChangeText={setBio}
                multiline
                numberOfLines={3}
                maxLength={500}
                containerStyle={styles.bioInput}
                inputStyle={styles.bioInputField}
              />
              <Text style={styles.characterCount}>
                {t('profileSetup.characterCount', { current: bio.length, max: 500 })}
              </Text>
            </View>
          </View>

          {/* Spacer */}
          <View style={{ flex: 1, minHeight: theme.spacing[8] }} />

          {/* 완료 버튼 */}
          <Button
            variant="primary"
            size="lg"
            fullWidth
            onPress={handleCreateProfile}
            disabled={nicknameAvailable !== true}
            loading={isCreating}
          >
            {t('profileSetup.startButton')}
          </Button>
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

  keyboardAvoid: {
    flex: 1,
  },

  container: {
    flexGrow: 1,
    paddingHorizontal: theme.spacing[6],
    paddingVertical: theme.spacing[6],
  },

  // Header
  header: {
    marginBottom: theme.spacing[8],
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
    lineHeight:
      theme.typography.lineHeight.relaxed * theme.typography.fontSize.base,
  },

  // Profile Image
  profileImageSection: {
    alignItems: 'center',
    marginBottom: theme.spacing[10],
  },

  profileImageContainer: {
    width: 96,
    height: 96,
    borderRadius: 48,
    backgroundColor: theme.colors.background.tertiary,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: theme.colors.border.light,
    marginBottom: theme.spacing[3],
  },

  profileImagePlaceholder: {
    fontSize: 40,
  },

  changePhotoButton: {
    paddingVertical: theme.spacing[2],
    paddingHorizontal: theme.spacing[4],
  },

  changePhotoText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.primary[600],
    fontWeight: theme.typography.fontWeight.semibold,
  },

  // Form
  form: {
    gap: theme.spacing[6],
  },

  inputGroup: {
    gap: theme.spacing[2],
  },

  label: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
  },

  required: {
    color: theme.colors.error,
  },

  nicknameInputContainer: {
    flexDirection: 'row',
    gap: theme.spacing[2],
    alignItems: 'flex-start',
  },

  nicknameInput: {
    flex: 1,
    marginBottom: 0,
  },

  checkButton: {
    minWidth: 70,
  },

  successMessage: {
    backgroundColor: theme.colors.primary[50],
    paddingVertical: theme.spacing[2],
    paddingHorizontal: theme.spacing[3],
    borderRadius: theme.borderRadius.base,
    borderWidth: 1,
    borderColor: theme.colors.primary[200],
  },

  successText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.primary[700],
    fontWeight: theme.typography.fontWeight.medium,
  },

  helperText: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
  },

  bioInput: {
    marginBottom: 0,
  },

  bioInputField: {
    minHeight: 80,
    paddingTop: theme.spacing[3],
  },

  characterCount: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
    textAlign: 'right',
  },
});
