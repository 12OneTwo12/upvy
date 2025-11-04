import React, { useEffect } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { RootStackParamList } from '@/types/navigation.types';
import { useAuthStore } from '@/stores/authStore';
import { LoadingSpinner } from '@/components/common';
import AuthNavigator from './AuthNavigator';
import MainNavigator from './MainNavigator';
import ProfileSetupScreen from '@/screens/auth/ProfileSetupScreen';
import EditProfileScreen from '@/screens/profile/EditProfileScreen';
import UserProfileScreen from '@/screens/profile/UserProfileScreen';
import FollowListScreen from '@/screens/profile/FollowListScreen';
import ContentViewerScreen from '@/screens/content/ContentViewerScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();

/**
 * Root Navigator
 * 인증 상태와 프로필 존재 여부에 따라 화면을 표시합니다.
 */
export default function RootNavigator() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const profile = useAuthStore((state) => state.profile);
  const isLoading = useAuthStore((state) => state.isLoading);
  const checkAuth = useAuthStore((state) => state.checkAuth);

  // 앱 시작 시 자동 로그인 체크
  useEffect(() => {
    checkAuth();
  }, [checkAuth]);

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
          <>
            <Stack.Screen name="Main" component={MainNavigator} />
            <Stack.Screen
              name="EditProfile"
              component={EditProfileScreen}
              options={{ presentation: 'modal' }}
            />
            <Stack.Screen name="UserProfile" component={UserProfileScreen} />
            <Stack.Screen name="FollowList" component={FollowListScreen} />
            <Stack.Screen
              name="ContentViewer"
              component={ContentViewerScreen}
              options={{ presentation: 'fullScreenModal' }}
            />
          </>
        )}
      </Stack.Navigator>
    </NavigationContainer>
  );
}
