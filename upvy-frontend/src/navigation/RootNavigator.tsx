import React, { useEffect, useRef } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import type { NavigationState } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import { RootStackParamList } from '@/types/navigation.types';
import { useAuthStore } from '@/stores/authStore';
import { useThemeStore } from '@/stores/themeStore';
import { LoadingSpinner } from '@/components/common';
import { useNotifications } from '@/hooks/useNotifications';
import { useDeepLink } from '@/hooks/useDeepLink';
import { Analytics, type ScreenName } from '@/utils/analytics';
import AuthNavigator from './AuthNavigator';
import MainNavigator from './MainNavigator';
import TermsAgreementScreen from '@/screens/auth/TermsAgreementScreen';
import ProfileSetupScreen from '@/screens/auth/ProfileSetupScreen';
import EditProfileScreen from '@/screens/profile/EditProfileScreen';
import SettingsScreen from '@/screens/profile/SettingsScreen';
import LanguageSelectorScreen from '@/screens/settings/LanguageSelectorScreen';
import ThemeSelectorScreen from '@/screens/settings/ThemeSelectorScreen';
import PasswordChangeScreen from '@/screens/settings/PasswordChangeScreen';
import TermsOfServiceScreen from '@/screens/settings/TermsOfServiceScreen';
import PrivacyPolicyScreen from '@/screens/settings/PrivacyPolicyScreen';
import CommunityGuidelinesScreen from '@/screens/settings/CommunityGuidelinesScreen';
import HelpSupportScreen from '@/screens/settings/HelpSupportScreen';
import { BlockManagementScreen } from '@/screens/settings/BlockManagementScreen';
import NotificationListScreen from '@/screens/notification/NotificationListScreen';
import NotificationSettingsScreen from '@/screens/notification/NotificationSettingsScreen';
import ContentViewerScreen from '@/screens/content/ContentViewerScreen';
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
 * Deep Link 처리 컴포넌트
 * Universal Links (iOS) 및 App Links (Android)를 처리합니다.
 * NavigationContainer 내부에서 렌더링되어야 합니다.
 */
function DeepLinkHandler() {
  useDeepLink();
  return null;
}

/**
 * 현재 화면 이름 추출
 */
function getActiveRouteName(state: NavigationState | undefined): string | undefined {
  if (!state) return undefined;

  const route = state.routes[state.index];

  if (route.state) {
    // Nested navigator가 있는 경우 재귀적으로 탐색
    return getActiveRouteName(route.state as NavigationState);
  }

  return route.name;
}

/**
 * ScreenName 타입에 포함되는지 확인하는 Type Guard
 * Navigator 이름(Auth, Main 등)은 제외하고 실제 화면 이름만 필터링
 */
function isValidScreenName(name: string | undefined): name is ScreenName {
  if (!name) return false;

  const validScreenNames: ScreenName[] = [
    'Home', 'Feed', 'Upload', 'Profile', 'ContentViewer', 'Search', 'Notifications',
    'Settings', 'LanguageSelector', 'PasswordChange', 'HelpSupport',
    'Login', 'EmailSignUp', 'EmailSignIn', 'EmailVerification', 'PasswordReset', 'PasswordResetConfirm',
    'ProfileSetup', 'EditProfile', 'UserProfile', 'FollowerList', 'FollowingList',
    'TermsAgreement', 'TermsOfService', 'PrivacyPolicy', 'CommunityGuidelines',
    'CategoryFeed',
  ];

  return validScreenNames.includes(name as ScreenName);
}

/**
 * Root Navigator
 * 인증 상태와 프로필 존재 여부에 따라 화면을 표시합니다.
 */
export default function RootNavigator() {
  const { t } = useTranslation('common');
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const hasAgreedToTerms = useAuthStore((state) => state.hasAgreedToTerms);
  const profile = useAuthStore((state) => state.profile);
  const isLoading = useAuthStore((state) => state.isLoading);
  const checkAuth = useAuthStore((state) => state.checkAuth);
  const isDarkMode = useThemeStore((state) => state.isDarkMode);

  // 화면 추적을 위한 ref
  const routeNameRef = useRef<string>();

  // 앱 시작 시 자동 로그인 체크
  useEffect(() => {
    checkAuth();
  }, [checkAuth]);

  // 초기 로딩 중
  if (isLoading) {
    return <LoadingSpinner fullScreen message={t('label.loading')} />;
  }

  return (
    <NavigationContainer
      onReady={() => {
        // 앱 시작 시 초기 화면 기록
        const initialRoute = routeNameRef.current;
        if (isValidScreenName(initialRoute)) {
          Analytics.logScreenView(initialRoute);
        }
      }}
      onStateChange={(state) => {
        const previousRouteName = routeNameRef.current;
        const currentRouteName = getActiveRouteName(state);

        if (previousRouteName !== currentRouteName && isValidScreenName(currentRouteName)) {
          // 화면이 변경되면 Analytics 로깅 (Fire-and-Forget - await 없음)
          Analytics.logScreenView(currentRouteName);
        }

        // 다음 변경을 위해 현재 화면 이름 저장
        routeNameRef.current = currentRouteName;
      }}
    >
      <NotificationHandler />
      <DeepLinkHandler />
      <Stack.Navigator
        screenOptions={{
          headerShown: false,
          statusBarStyle: isDarkMode ? 'light' : 'dark',
        }}
      >
        {!isAuthenticated ? (
          // 1. 로그인하지 않은 경우 → Auth (Login screens)
          <Stack.Screen name="Auth" component={AuthNavigator} />
        ) : !hasAgreedToTerms ? (
          // 2. 로그인했지만 약관 동의하지 않은 경우 → TermsAgreement
          <>
            <Stack.Screen name="TermsAgreement" component={TermsAgreementScreen} />
            {/* 약관 보기 화면들 */}
            <Stack.Screen name="TermsOfService" component={TermsOfServiceScreen} />
            <Stack.Screen name="PrivacyPolicy" component={PrivacyPolicyScreen} />
            <Stack.Screen name="CommunityGuidelines" component={CommunityGuidelinesScreen} />
          </>
        ) : !profile ? (
          // 3. 로그인하고 약관 동의했지만 프로필 없는 경우 → ProfileSetup
          <Stack.Screen name="ProfileSetup" component={ProfileSetupScreen} />
        ) : (
          // 4. 모든 설정 완료 → Main (Home)
          <>
            <Stack.Screen name="Main" component={MainNavigator} />
            <Stack.Screen
              name="ContentViewer"
              component={ContentViewerScreen}
            />
            <Stack.Screen
              name="EditProfile"
              component={EditProfileScreen}
              options={{ presentation: 'modal' }}
            />
            <Stack.Screen name="Settings" component={SettingsScreen} />
            <Stack.Screen name="LanguageSelector" component={LanguageSelectorScreen} />
            <Stack.Screen name="ThemeSelector" component={ThemeSelectorScreen} />
            <Stack.Screen name="PasswordChange" component={PasswordChangeScreen} />
            <Stack.Screen name="TermsOfService" component={TermsOfServiceScreen} />
            <Stack.Screen name="PrivacyPolicy" component={PrivacyPolicyScreen} />
            <Stack.Screen name="CommunityGuidelines" component={CommunityGuidelinesScreen} />
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
