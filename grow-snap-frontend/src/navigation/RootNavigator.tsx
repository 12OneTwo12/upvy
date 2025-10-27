import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { RootStackParamList } from '@/types/navigation.types';
import { useAuthStore } from '@/stores/authStore';
import { LoadingSpinner } from '@/components/common';
import AuthNavigator from './AuthNavigator';
import MainNavigator from './MainNavigator';
import ProfileSetupScreen from '@/screens/auth/ProfileSetupScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();

/**
 * Root Navigator
 * 인증 상태와 프로필 존재 여부에 따라 화면을 표시합니다.
 */
export default function RootNavigator() {
  const { isAuthenticated, profile, isLoading } = useAuthStore((state) => ({
    isAuthenticated: state.isAuthenticated,
    profile: state.profile,
    isLoading: state.isLoading,
  }));

  // 초기 로딩 중
  if (isLoading) {
    return <LoadingSpinner fullScreen message="로딩 중..." />;
  }

  return (
    <NavigationContainer>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        {!isAuthenticated ? (
          <Stack.Screen name="Auth" component={AuthNavigator} />
        ) : !profile ? (
          <Stack.Screen name="ProfileSetup" component={ProfileSetupScreen} />
        ) : (
          <Stack.Screen name="Main" component={MainNavigator} />
        )}
      </Stack.Navigator>
    </NavigationContainer>
  );
}
