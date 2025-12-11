import React, { useEffect } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import { RootStackParamList } from '@/types/navigation.types';
import { useAuthStore } from '@/stores/authStore';
import { LoadingSpinner } from '@/components/common';
import { useNotifications } from '@/hooks/useNotifications';
import AuthNavigator from './AuthNavigator';
import MainNavigator from './MainNavigator';
import ProfileSetupScreen from '@/screens/auth/ProfileSetupScreen';
import EditProfileScreen from '@/screens/profile/EditProfileScreen';
import SettingsScreen from '@/screens/profile/SettingsScreen';
import LanguageSelectorScreen from '@/screens/settings/LanguageSelectorScreen';
import TermsOfServiceScreen from '@/screens/settings/TermsOfServiceScreen';
import PrivacyPolicyScreen from '@/screens/settings/PrivacyPolicyScreen';
import HelpSupportScreen from '@/screens/settings/HelpSupportScreen';
import { BlockManagementScreen } from '@/screens/settings/BlockManagementScreen';
import NotificationListScreen from '@/screens/notification/NotificationListScreen';
import NotificationSettingsScreen from '@/screens/notification/NotificationSettingsScreen';
import { SentryTestScreen } from '@/screens/dev/SentryTestScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();

/**
 * 푸시 알림 초기화 컴포넌트
 * 인증된 사용자에게 푸시 알림 권한을 요청하고 토큰을 등록합니다.
 */
function NotificationHandler() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const profile = useAuthStore((state) => state.profile);
  const { registerToken } = useNotifications();

  useEffect(() => {
    // 로그인 완료 및 프로필 설정 완료 시 푸시 토큰 등록
    if (isAuthenticated && profile) {
      registerToken();
    }
  }, [isAuthenticated, profile, registerToken]);

  return null;
}

/**
 * Root Navigator
 * 인증 상태와 프로필 존재 여부에 따라 화면을 표시합니다.
 */
export default function RootNavigator() {
  const { t } = useTranslation('common');
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
    return <LoadingSpinner fullScreen message={t('label.loading')} />;
  }

  return (
    <NavigationContainer>
      <NotificationHandler />
      <Stack.Navigator
        screenOptions={{
          headerShown: false,
          statusBarStyle: 'dark',
        }}
      >
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
            <Stack.Screen name="Settings" component={SettingsScreen} />
            <Stack.Screen name="LanguageSelector" component={LanguageSelectorScreen} />
            <Stack.Screen name="TermsOfService" component={TermsOfServiceScreen} />
            <Stack.Screen name="PrivacyPolicy" component={PrivacyPolicyScreen} />
            <Stack.Screen name="HelpSupport" component={HelpSupportScreen} />
            <Stack.Screen
              name="BlockManagement"
              component={BlockManagementScreen}
            />
            <Stack.Screen
              name="NotificationList"
              component={NotificationListScreen}
            />
            <Stack.Screen
              name="NotificationSettings"
              component={NotificationSettingsScreen}
            />

            {/* Developer Screens - Only in DEV mode */}
            {__DEV__ && (
              <Stack.Screen
                name="SentryTest"
                component={SentryTestScreen}
                options={{ title: 'Sentry 테스트' }}
              />
            )}
          </>
        )}
      </Stack.Navigator>
    </NavigationContainer>
  );
}
