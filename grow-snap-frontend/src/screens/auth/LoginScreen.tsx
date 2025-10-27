import React, { useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  SafeAreaView,
  Image,
} from 'react-native';
import { useGoogleAuth } from '@/hooks/useGoogleAuth';
import { useAuthStore } from '@/stores/authStore';

export default function LoginScreen() {
  const { handleGoogleLogin, isLoading, error, isReady } = useGoogleAuth();
  const { checkAuth } = useAuthStore();

  // 앱 시작 시 자동 로그인 체크
  useEffect(() => {
    checkAuth();
  }, []);

  // 에러 처리
  useEffect(() => {
    if (error) {
      Alert.alert('로그인 실패', error, [{ text: '확인' }]);
    }
  }, [error]);

  return (
    <SafeAreaView className="flex-1 bg-white">
      <View className="flex-1 px-6 justify-between py-12">
        {/* 상단: 로고 및 소개 */}
        <View className="flex-1 justify-center items-center">
          <View className="items-center mb-12">
            {/* 로고 영역 (추후 이미지로 교체) */}
            <View className="w-20 h-20 bg-primary-500 rounded-3xl items-center justify-center mb-6">
              <Text className="text-white text-3xl font-bold">G</Text>
            </View>

            <Text className="text-4xl font-bold text-gray-900 mb-3">GrowSnap</Text>
            <Text className="text-lg text-gray-600 text-center px-8">
              스크롤 시간을 성장 시간으로
            </Text>
          </View>

          {/* 서비스 특징 */}
          <View className="w-full mt-8 space-y-3">
            <FeatureItem icon="📚" text="매일 새로운 인사이트" />
            <FeatureItem icon="🎯" text="나만의 성장 여정" />
            <FeatureItem icon="✨" text="재미있는 학습 경험" />
          </View>
        </View>

        {/* 하단: 로그인 버튼 및 약관 */}
        <View className="w-full">
          {/* Google 로그인 버튼 */}
          <TouchableOpacity
            className="w-full bg-white border-2 border-gray-300 rounded-xl py-4 flex-row items-center justify-center mb-4"
            onPress={handleGoogleLogin}
            disabled={isLoading || !isReady}
          >
            {isLoading ? (
              <ActivityIndicator color="#4285F4" />
            ) : (
              <>
                {/* Google 아이콘 (추후 실제 이미지로 교체) */}
                <View className="w-6 h-6 bg-blue-500 rounded-full mr-3" />
                <Text className="text-gray-900 text-base font-semibold">
                  Google로 시작하기
                </Text>
              </>
            )}
          </TouchableOpacity>

          {/* 약관 동의 */}
          <View className="items-center">
            <Text className="text-xs text-gray-500 text-center leading-5">
              계속 진행하면{' '}
              <Text className="text-primary-600 underline">이용약관</Text> 및{' '}
              <Text className="text-primary-600 underline">개인정보처리방침</Text>에
              동의하는 것으로 간주됩니다.
            </Text>
          </View>

          {/* 개발 모드 표시 */}
          {__DEV__ && (
            <View className="mt-6 p-3 bg-yellow-50 rounded-lg">
              <Text className="text-xs text-yellow-800 text-center">
                ⚠️ 개발 모드: Google OAuth 설정이 필요합니다
              </Text>
            </View>
          )}
        </View>
      </View>
    </SafeAreaView>
  );
}

/**
 * 특징 아이템 컴포넌트
 */
interface FeatureItemProps {
  icon: string;
  text: string;
}

function FeatureItem({ icon, text }: FeatureItemProps) {
  return (
    <View className="flex-row items-center">
      <Text className="text-2xl mr-3">{icon}</Text>
      <Text className="text-base text-gray-700">{text}</Text>
    </View>
  );
}
