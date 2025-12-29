import React, { useState } from 'react';
import {
  View,
  Text,
  SafeAreaView,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  TouchableOpacity,
  Alert,
  ActivityIndicator,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';
import * as ImagePicker from 'expo-image-picker';
import { checkNickname, createProfile, uploadProfileImage } from '@/api/auth.api';
import { useAuthStore } from '@/stores/authStore';
import { Button, Input } from '@/components/common';
import { ProfileAvatar } from '@/components/profile';
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

  const [profileImageUrl, setProfileImageUrl] = useState('');
  const [nickname, setNickname] = useState('');
  const [bio, setBio] = useState('');
  const [isUploadingImage, setIsUploadingImage] = useState(false);
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
   * 이미지 선택 (갤러리 또는 카메라)
   */
  const pickImage = async (useCamera: boolean = false) => {
    try {
      // 권한 요청
      const permission = useCamera
        ? await ImagePicker.requestCameraPermissionsAsync()
        : await ImagePicker.requestMediaLibraryPermissionsAsync();

      if (!permission.granted) {
        Alert.alert(
          t('profileSetup.error.permissionDenied'),
          t('profileSetup.error.permissionMessage')
        );
        return;
      }

      // 이미지 선택 또는 촬영
      const result = useCamera
        ? await ImagePicker.launchCameraAsync({
            mediaTypes: ImagePicker.MediaTypeOptions.Images,
            allowsEditing: true,
            aspect: [1, 1],
            quality: 0.8,
          })
        : await ImagePicker.launchImageLibraryAsync({
            mediaTypes: ImagePicker.MediaTypeOptions.Images,
            allowsEditing: true,
            aspect: [1, 1],
            quality: 0.8,
          });

      if (!result.canceled && result.assets[0]) {
        const imageUri = result.assets[0].uri;

        // 이미지 업로드
        setIsUploadingImage(true);
        const uploadResult = await withErrorHandling(
          async () => await uploadProfileImage(imageUri),
          {
            showAlert: true,
            alertTitle: t('profileSetup.error.uploadFailed'),
            logContext: 'ProfileSetupScreen.uploadImage',
          }
        );
        setIsUploadingImage(false);

        if (uploadResult) {
          setProfileImageUrl(uploadResult.imageUrl);
        }
      }
    } catch (error) {
      setIsUploadingImage(false);
      console.error('Image picker error:', error);
      showErrorAlert(
        t('profileSetup.error.imagePickerError'),
        t('profileSetup.error.alert')
      );
    }
  };

  /**
   * 이미지 변경 옵션 표시
   */
  const showImageOptions = () => {
    Alert.alert(t('profileSetup.changePhoto'), t('profileSetup.selectImageSource'), [
      {
        text: t('profileSetup.camera'),
        onPress: () => pickImage(true),
      },
      {
        text: t('profileSetup.gallery'),
        onPress: () => pickImage(false),
      },
      {
        text: t('profileSetup.cancel'),
        style: 'cancel',
      },
    ]);
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
        await createProfile({
          nickname,
          profileImageUrl: profileImageUrl || undefined,
          bio: bio || undefined,
        }),
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
            <TouchableOpacity
              style={styles.profileImageContainer}
              onPress={showImageOptions}
              disabled={isUploadingImage}
              activeOpacity={0.7}
            >
              {profileImageUrl ? (
                <ProfileAvatar
                  imageUrl={profileImageUrl}
                  nickname={nickname}
                  size="xlarge"
                  showBorder={true}
                />
              ) : (
                <Text style={styles.profileImagePlaceholder}>👤</Text>
              )}
              {isUploadingImage && (
                <View style={styles.uploadingOverlay}>
                  <ActivityIndicator size="large" color={theme.colors.primary[600]} />
                </View>
              )}
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.changePhotoButton}
              onPress={showImageOptions}
              disabled={isUploadingImage}
              activeOpacity={0.6}
            >
              <Text style={styles.changePhotoText}>
                {isUploadingImage ? t('profileSetup.uploading') : t('profileSetup.changePhoto')}
              </Text>
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

  uploadingOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(255, 255, 255, 0.8)',
    borderRadius: 48,
    alignItems: 'center',
    justifyContent: 'center',
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
