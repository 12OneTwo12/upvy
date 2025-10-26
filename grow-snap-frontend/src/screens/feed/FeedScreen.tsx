import React from 'react';
import { View, Text } from 'react-native';

export default function FeedScreen() {
  return (
    <View className="flex-1 items-center justify-center bg-white">
      <Text className="text-xl font-bold">피드 화면</Text>
      <Text className="text-gray-600 mt-2">숏폼 비디오 피드</Text>
    </View>
  );
}
