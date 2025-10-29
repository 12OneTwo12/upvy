import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
  TouchableOpacity,
  Alert,
  ActivityIndicator,
  TextInput,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import * as ImagePicker from 'expo-image-picker';
import { ProfileAvatar } from '@/components/profile';
import { useAuthStore } from '@/stores/authStore';
import {
  checkNickname,
  createProfile,
  uploadProfileImage,
} from '@/api/auth.api';
import { theme } from '@/theme';
import { showErrorAlert, withErrorHandling } from '@/utils/errorHandler';
import { createStyleSheet } from '@/utils/styles';

/**
 * 프로필 수정 화면
 * 인스타그램 스타일의 모던하고 깔끔한 프로필 수정
 */
const useStyles = createStyleSheet({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
    borderBottomWidth: 0.5,
    borderBottomColor: theme.colors.gray[200],
  },
  headerTitle: {
    fontSize: theme.typography.fontSize.xl,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    letterSpacing: -0.5,
  },
  headerButton: {
    minWidth: 50,
    height: 40,
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerButtonText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.primary[600],
  },
  cancelText: {
    color: theme.colors.text.primary,
  },
  keyboardAvoid: {
    flex: 1,
  },
  scrollView: {
    flex: 1,
  },
  avatarSection: {
    alignItems: 'center',
    paddingVertical: theme.spacing[10],
    backgroundColor: theme.colors.background.primary,
  },
  avatarContainer: {
    position: 'relative',
  },
  avatarOverlay: {
    position: 'absolute',
    bottom: 0,
    right: 0,
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: theme.colors.primary[600],
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 3,
    borderColor: theme.colors.background.primary,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 3,
    elevation: 3,
  },
  changePhotoButton: {
    marginTop: theme.spacing[5],
  },
  changePhotoText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.primary[600],
    letterSpacing: -0.3,
  },
  formSection: {
    marginTop: theme.spacing[2],
  },
  fieldRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: theme.spacing[4],
    paddingHorizontal: theme.spacing[5],
    backgroundColor: theme.colors.background.primary,
    borderBottomWidth: 0.5,
    borderBottomColor: theme.colors.gray[200],
    minHeight: 56,
  },
  bioFieldRow: {
    alignItems: 'flex-start',
    paddingTop: theme.spacing[4],
    paddingBottom: theme.spacing[3],
    minHeight: 120,
  },
  label: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.medium,
    color: theme.colors.text.primary,
    width: 90,
    letterSpacing: -0.3,
  },
  inputWrapper: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
  },
  input: {
    flex: 1,
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    paddingVertical: 0,
    paddingHorizontal: 0,
    letterSpacing: -0.2,
  },
  bioInputWrapper: {
    flex: 1,
    minHeight: 80,
  },
  bioInput: {
    flex: 1,
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    paddingVertical: 0,
    paddingHorizontal: 0,
    textAlignVertical: 'top',
    letterSpacing: -0.2,
  },
  checkButton: {
    marginLeft: theme.spacing[2],
    paddingHorizontal: theme.spacing[3],
    paddingVertical: theme.spacing[2],
    backgroundColor: theme.colors.primary[600],
    borderRadius: theme.borderRadius.md,
    minWidth: 60,
    alignItems: 'center',
  },
  checkButtonDisabled: {
    backgroundColor: theme.colors.gray[200],
  },
  checkButtonText: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.inverse,
  },
  statusContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginLeft: theme.spacing[2],
  },
  statusIcon: {
    marginRight: theme.spacing[1],
  },
  successText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.success,
    fontWeight: theme.typography.fontWeight.medium,
  },
  errorText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.error,
    fontWeight: theme.typography.fontWeight.medium,
  },
  helperTextRow: {
    paddingHorizontal: theme.spacing[5],
    paddingVertical: theme.spacing[2],
    backgroundColor: theme.colors.background.primary,
    borderBottomWidth: 0.5,
    borderBottomColor: theme.colors.gray[200],
  },
  helperText: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
    textAlign: 'right',
  },
  infoSection: {
    paddingHorizontal: theme.spacing[5],
    paddingVertical: theme.spacing[6],
    backgroundColor: theme.colors.background.secondary,
  },
  infoText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.tertiary,
    lineHeight: 20,
    letterSpacing: -0.2,
  },
});

export default function EditProfileScreen() {
  const styles = useStyles();
  const navigation = useNavigation();
  const { profile: storeProfile, updateProfile } = useAuthStore();

  const [profileImageUrl, setProfileImageUrl] = useState(
    storeProfile?.profileImageUrl || ''
  );
  const [nickname, setNickname] = useState(storeProfile?.nickname || '');
  const [bio, setBio] = useState(storeProfile?.bio || '');

  const [isUploadingImage, setIsUploadingImage] = useState(false);
  const [isCheckingNickname, setIsCheckingNickname] = useState(false);
  const [nicknameAvailable, setNicknameAvailable] = useState<boolean | null>(
    storeProfile?.nickname === nickname ? true : null
  );
  const [isSaving, setIsSaving] = useState(false);

  // 닉네임 변경 감지
  useEffect(() => {
    if (nickname !== storeProfile?.nickname) {
      setNicknameAvailable(null);
    } else {
      setNicknameAvailable(true);
    }
  }, [nickname, storeProfile?.nickname]);

  // 이미지 선택 (갤러리 또는 카메라)
  const pickImage = async (useCamera: boolean = false) => {
    try {
      // 권한 요청
      const permission = useCamera
        ? await ImagePicker.requestCameraPermissionsAsync()
        : await ImagePicker.requestMediaLibraryPermissionsAsync();

      if (!permission.granted) {
        Alert.alert(
          '권한 필요',
          `${useCamera ? '카메라' : '갤러리'} 접근 권한이 필요합니다.`
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
            alertTitle: '이미지 업로드 실패',
            logContext: 'EditProfileScreen.uploadImage',
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
        '이미지를 선택하는 중 오류가 발생했습니다.',
        '오류'
      );
    }
  };

  // 이미지 변경 옵션 표시
  const showImageOptions = () => {
    Alert.alert('프로필 이미지 변경', '이미지를 어떻게 선택하시겠습니까?', [
      {
        text: '카메라로 촬영',
        onPress: () => pickImage(true),
      },
      {
        text: '갤러리에서 선택',
        onPress: () => pickImage(false),
      },
      {
        text: '취소',
        style: 'cancel',
      },
    ]);
  };

  // 닉네임 중복 확인
  const handleCheckNickname = async () => {
    if (!nickname || nickname.length < 2) {
      showErrorAlert('닉네임은 2자 이상이어야 합니다.', '알림');
      return;
    }

    if (nickname === storeProfile?.nickname) {
      setNicknameAvailable(true);
      return;
    }

    setIsCheckingNickname(true);
    const result = await withErrorHandling(
      async () => await checkNickname(nickname),
      {
        showAlert: true,
        alertTitle: '닉네임 확인 실패',
        logContext: 'EditProfileScreen.checkNickname',
      }
    );
    setIsCheckingNickname(false);

    if (result) {
      setNicknameAvailable(!result.isDuplicated);
      if (result.isDuplicated) {
        showErrorAlert('이미 사용 중인 닉네임입니다.', '알림');
      }
    }
  };

  // 저장
  const handleSave = async () => {
    if (!nickname || nicknameAvailable !== true) {
      showErrorAlert('닉네임을 입력하고 중복 확인을 해주세요.', '알림');
      return;
    }

    setIsSaving(true);
    const result = await withErrorHandling(
      async () =>
        await createProfile({
          nickname,
          profileImageUrl: profileImageUrl || undefined,
          bio: bio || undefined,
        }),
      {
        showAlert: true,
        alertTitle: '프로필 수정 실패',
        logContext: 'EditProfileScreen.save',
      }
    );
    setIsSaving(false);

    if (result) {
      updateProfile(result);
      Alert.alert('성공', '프로필이 수정되었습니다.', [
        {
          text: '확인',
          onPress: () => navigation.goBack(),
        },
      ]);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          style={styles.headerButton}
          disabled={isSaving}
        >
          <Text style={[styles.headerButtonText, styles.cancelText]}>취소</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>프로필 수정</Text>
        <TouchableOpacity
          onPress={handleSave}
          style={styles.headerButton}
          disabled={isSaving}
        >
          {isSaving ? (
            <ActivityIndicator size="small" color={theme.colors.primary[600]} />
          ) : (
            <Text style={styles.headerButtonText}>완료</Text>
          )}
        </TouchableOpacity>
      </View>

      <KeyboardAvoidingView
        style={styles.keyboardAvoid}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
          {/* 프로필 이미지 섹션 */}
          <View style={styles.avatarSection}>
            <TouchableOpacity
              onPress={showImageOptions}
              style={styles.avatarContainer}
              disabled={isUploadingImage}
              activeOpacity={0.7}
            >
              <ProfileAvatar
                imageUrl={profileImageUrl}
                size="xlarge"
                showBorder={true}
              />
              <View style={styles.avatarOverlay}>
                {isUploadingImage ? (
                  <ActivityIndicator size="small" color={theme.colors.text.inverse} />
                ) : (
                  <Ionicons name="camera" size={20} color={theme.colors.text.inverse} />
                )}
              </View>
            </TouchableOpacity>
            <TouchableOpacity
              onPress={showImageOptions}
              style={styles.changePhotoButton}
              disabled={isUploadingImage}
              activeOpacity={0.6}
            >
              <Text style={styles.changePhotoText}>
                {isUploadingImage ? '업로드 중...' : '프로필 사진 변경'}
              </Text>
            </TouchableOpacity>
          </View>

          {/* 폼 필드들 */}
          <View style={styles.formSection}>
            {/* 닉네임 필드 */}
            <View style={styles.fieldRow}>
              <Text style={styles.label}>닉네임</Text>
              <View style={styles.inputWrapper}>
                <TextInput
                  value={nickname}
                  onChangeText={setNickname}
                  placeholder="닉네임을 입력하세요"
                  placeholderTextColor={theme.colors.text.tertiary}
                  maxLength={20}
                  style={styles.input}
                  autoCapitalize="none"
                />
                {nickname !== storeProfile?.nickname && nickname.length >= 2 && (
                  <>
                    {isCheckingNickname ? (
                      <ActivityIndicator size="small" color={theme.colors.primary[600]} />
                    ) : nicknameAvailable === null ? (
                      <TouchableOpacity
                        style={styles.checkButton}
                        onPress={handleCheckNickname}
                        activeOpacity={0.7}
                      >
                        <Text style={styles.checkButtonText}>확인</Text>
                      </TouchableOpacity>
                    ) : nicknameAvailable ? (
                      <View style={styles.statusContainer}>
                        <Ionicons
                          name="checkmark-circle"
                          size={18}
                          color={theme.colors.success}
                          style={styles.statusIcon}
                        />
                        <Text style={styles.successText}>사용가능</Text>
                      </View>
                    ) : (
                      <View style={styles.statusContainer}>
                        <Ionicons
                          name="close-circle"
                          size={18}
                          color={theme.colors.error}
                          style={styles.statusIcon}
                        />
                        <Text style={styles.errorText}>중복됨</Text>
                      </View>
                    )}
                  </>
                )}
              </View>
            </View>

            {/* 자기소개 필드 */}
            <View style={[styles.fieldRow, styles.bioFieldRow]}>
              <Text style={styles.label}>자기소개</Text>
              <View style={styles.bioInputWrapper}>
                <TextInput
                  value={bio}
                  onChangeText={setBio}
                  placeholder="자기소개를 입력하세요"
                  placeholderTextColor={theme.colors.text.tertiary}
                  multiline
                  maxLength={150}
                  style={styles.bioInput}
                />
              </View>
            </View>

            {/* 글자수 표시 */}
            <View style={styles.helperTextRow}>
              <Text style={styles.helperText}>{bio.length}/150자</Text>
            </View>
          </View>

          {/* 추가 정보 */}
          <View style={styles.infoSection}>
            <Text style={styles.infoText}>
              프로필 정보는 GrowSnap을 사용하는 모든 사용자에게 공개됩니다.
            </Text>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
