import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
  SafeAreaView,
  KeyboardAvoidingView,
  Platform,
  Alert,
} from 'react-native';
import { checkNickname, createProfile } from '@/api/auth.api';
import { useAuthStore } from '@/stores/authStore';

export default function ProfileSetupScreen() {
  const { updateProfile } = useAuthStore();
  const [nickname, setNickname] = useState('');
  const [bio, setBio] = useState('');
  const [isCheckingNickname, setIsCheckingNickname] = useState(false);
  const [nicknameAvailable, setNicknameAvailable] = useState<boolean | null>(null);
  const [isCreating, setIsCreating] = useState(false);

  /**
   * 닉네임 중복 확인
   */
  const handleCheckNickname = async () => {
    if (!nickname || nickname.length < 2) {
      Alert.alert('알림', '닉네임은 2자 이상이어야 합니다.');
      return;
    }

    try {
      setIsCheckingNickname(true);
      const result = await checkNickname(nickname);
      setNicknameAvailable(result.available);

      if (!result.available) {
        Alert.alert('알림', result.message || '이미 사용 중인 닉네임입니다.');
      }
    } catch (error) {
      Alert.alert('오류', '닉네임 확인 중 오류가 발생했습니다.');
    } finally {
      setIsCheckingNickname(false);
    }
  };

  /**
   * 프로필 생성
   */
  const handleCreateProfile = async () => {
    if (!nickname || nicknameAvailable !== true) {
      Alert.alert('알림', '닉네임 중복 확인이 필요합니다.');
      return;
    }

    try {
      setIsCreating(true);
      const result = await createProfile({ nickname, bio: bio || undefined });
      updateProfile(result.profile);

      // 환영 메시지
      Alert.alert('환영합니다! 🎉', `${nickname}님, GrowSnap에 오신 것을 환영합니다!`, [
        { text: '시작하기' },
      ]);
    } catch (error) {
      Alert.alert('오류', '프로필 생성 중 오류가 발생했습니다.');
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <SafeAreaView className="flex-1 bg-white">
      <KeyboardAvoidingView
        className="flex-1"
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <View className="flex-1 px-6 py-8">
          {/* 헤더 */}
          <View className="mb-8">
            <Text className="text-3xl font-bold text-gray-900 mb-2">프로필 설정</Text>
            <Text className="text-base text-gray-600">
              GrowSnap에서 사용할 닉네임을 설정해주세요
            </Text>
          </View>

          {/* 프로필 이미지 (추후 구현) */}
          <View className="items-center mb-8">
            <View className="w-24 h-24 bg-gray-200 rounded-full items-center justify-center">
              <Text className="text-4xl">👤</Text>
            </View>
            <TouchableOpacity className="mt-3">
              <Text className="text-primary-600 text-sm font-semibold">
                프로필 이미지 변경
              </Text>
            </TouchableOpacity>
          </View>

          {/* 닉네임 입력 */}
          <View className="mb-6">
            <Text className="text-sm font-semibold text-gray-700 mb-2">
              닉네임 <Text className="text-red-500">*</Text>
            </Text>
            <View className="flex-row">
              <View className="flex-1 mr-2">
                <TextInput
                  className="border-2 border-gray-300 rounded-xl px-4 py-3 text-base"
                  placeholder="2-20자 사이로 입력"
                  value={nickname}
                  onChangeText={(text) => {
                    setNickname(text);
                    setNicknameAvailable(null);
                  }}
                  maxLength={20}
                  autoCapitalize="none"
                />
              </View>
              <TouchableOpacity
                className="bg-primary-500 rounded-xl px-4 justify-center"
                onPress={handleCheckNickname}
                disabled={isCheckingNickname || !nickname}
              >
                {isCheckingNickname ? (
                  <ActivityIndicator color="white" size="small" />
                ) : (
                  <Text className="text-white font-semibold">확인</Text>
                )}
              </TouchableOpacity>
            </View>

            {/* 닉네임 사용 가능 여부 */}
            {nicknameAvailable === true && (
              <Text className="text-green-600 text-sm mt-2">✓ 사용 가능한 닉네임입니다</Text>
            )}
            {nicknameAvailable === false && (
              <Text className="text-red-600 text-sm mt-2">✗ 이미 사용 중인 닉네임입니다</Text>
            )}
          </View>

          {/* 자기소개 입력 (선택) */}
          <View className="mb-8">
            <Text className="text-sm font-semibold text-gray-700 mb-2">
              자기소개 (선택)
            </Text>
            <TextInput
              className="border-2 border-gray-300 rounded-xl px-4 py-3 text-base"
              placeholder="간단한 자기소개를 입력해주세요"
              value={bio}
              onChangeText={setBio}
              multiline
              numberOfLines={3}
              maxLength={500}
              textAlignVertical="top"
            />
            <Text className="text-xs text-gray-500 mt-1 text-right">
              {bio.length}/500
            </Text>
          </View>

          {/* Spacer */}
          <View className="flex-1" />

          {/* 완료 버튼 */}
          <TouchableOpacity
            className={`w-full rounded-xl py-4 items-center ${
              nicknameAvailable === true && !isCreating
                ? 'bg-primary-500'
                : 'bg-gray-300'
            }`}
            onPress={handleCreateProfile}
            disabled={nicknameAvailable !== true || isCreating}
          >
            {isCreating ? (
              <ActivityIndicator color="white" />
            ) : (
              <Text className="text-white text-base font-semibold">시작하기</Text>
            )}
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}
