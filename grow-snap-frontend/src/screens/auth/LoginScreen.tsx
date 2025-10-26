import React from 'react';
import { View, Text } from 'react-native';

export default function LoginScreen() {
  return (
    <View className="flex-1 items-center justify-center bg-white">
      <Text className="text-2xl font-bold">GrowSnap</Text>
      <Text className="text-gray-600 mt-2">로그인 화면</Text>
      <Text className="text-sm text-gray-400 mt-4">Google OAuth 구현 예정</Text>
    </View>
  );
}
