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
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import * as ImagePicker from 'expo-image-picker';
import { ProfileAvatar } from '@/components/profile';
import { Button, Input } from '@/components/common';
import { useAuthStore } from '@/stores/authStore';
import {
  checkNickname,
  createProfile,
  uploadProfileImage,
} from '@/api/auth.api';
import { theme } from '@/theme';
import { showErrorAlert, withErrorHandling } from '@/utils/errorHandler';

/**
 * 프로필 수정 화면
 * 인스타그램 스타일의 프로필 수정
 */
export default function EditProfileScreen() {
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
        >
          <Ionicons name="close" size={28} color={theme.colors.text.primary} />
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
            <Ionicons
              name="checkmark"
              size={28}
              color={theme.colors.primary[600]}
            />
          )}
        </TouchableOpacity>
      </View>

      <KeyboardAvoidingView
        style={styles.keyboardAvoid}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
          {/* 프로필 이미지 */}
          <View style={styles.avatarSection}>
            <ProfileAvatar
              imageUrl={profileImageUrl}
              size="xlarge"
              showBorder={false}
            />
            <TouchableOpacity
              onPress={showImageOptions}
              style={styles.changePhotoButton}
              disabled={isUploadingImage}
            >
              {isUploadingImage ? (
                <ActivityIndicator size="small" color={theme.colors.primary[600]} />
              ) : (
                <Text style={styles.changePhotoText}>사진 변경</Text>
              )}
            </TouchableOpacity>
          </View>

          {/* 폼 */}
          <View style={styles.formSection}>
            {/* 닉네임 */}
            <View style={styles.fieldContainer}>
              <Text style={styles.label}>닉네임</Text>
              <View style={styles.nicknameRow}>
                <Input
                  value={nickname}
                  onChangeText={setNickname}
                  placeholder="닉네임을 입력하세요"
                  maxLength={20}
                  style={styles.nicknameInput}
                  autoCapitalize="none"
                />
                <Button
                  variant="outline"
                  size="small"
                  onPress={handleCheckNickname}
                  loading={isCheckingNickname}
                  disabled={
                    isCheckingNickname ||
                    !nickname ||
                    nickname === storeProfile?.nickname
                  }
                  style={styles.checkButton}
                >
                  중복 확인
                </Button>
              </View>
              {nicknameAvailable === true && nickname !== storeProfile?.nickname && (
                <Text style={styles.successText}>사용 가능한 닉네임입니다</Text>
              )}
              {nicknameAvailable === false && (
                <Text style={styles.errorText}>이미 사용 중인 닉네임입니다</Text>
              )}
            </View>

            {/* 자기소개 */}
            <View style={styles.fieldContainer}>
              <Text style={styles.label}>자기소개</Text>
              <Input
                value={bio}
                onChangeText={setBio}
                placeholder="자기소개를 입력하세요"
                multiline
                numberOfLines={4}
                maxLength={150}
                style={styles.bioInput}
              />
              <Text style={styles.helperText}>{bio.length}/150</Text>
            </View>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
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
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  headerTitle: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
  },
  headerButton: {
    width: 40,
    height: 40,
    justifyContent: 'center',
    alignItems: 'center',
  },
  keyboardAvoid: {
    flex: 1,
  },
  scrollView: {
    flex: 1,
  },
  avatarSection: {
    alignItems: 'center',
    paddingVertical: theme.spacing[8],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  changePhotoButton: {
    marginTop: theme.spacing[4],
    paddingVertical: theme.spacing[2],
    paddingHorizontal: theme.spacing[4],
  },
  changePhotoText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.primary[600],
  },
  formSection: {
    paddingHorizontal: theme.spacing[4],
    paddingTop: theme.spacing[6],
  },
  fieldContainer: {
    marginBottom: theme.spacing[6],
  },
  label: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[2],
  },
  nicknameRow: {
    flexDirection: 'row',
    gap: theme.spacing[2],
  },
  nicknameInput: {
    flex: 1,
  },
  checkButton: {
    paddingHorizontal: theme.spacing[3],
  },
  bioInput: {
    height: 100,
    textAlignVertical: 'top',
  },
  successText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.success,
    marginTop: theme.spacing[1],
  },
  errorText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.error,
    marginTop: theme.spacing[1],
  },
  helperText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.tertiary,
    marginTop: theme.spacing[1],
    textAlign: 'right',
  },
});
