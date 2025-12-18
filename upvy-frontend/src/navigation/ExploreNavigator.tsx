/**
 * 탐색 네비게이터
 *
 * 탐색 탭 내에서의 화면 전환을 관리하는 네비게이터
 * 탭바를 유지하면서 콘텐츠 뷰어 및 프로필로 이동할 수 있습니다.
 */

import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import type { ExploreStackParamList } from '@/types/navigation.types';
import { useThemeStore } from '@/stores/themeStore';

import ExploreScreen from '@/screens/explore/ExploreScreen';
import CategoryFeedScreen from '@/screens/explore/CategoryFeedScreen';
import ContentViewerScreen from '@/screens/content/ContentViewerScreen';
import UserProfileScreen from '@/screens/profile/UserProfileScreen';
import FollowListScreen from '@/screens/profile/FollowListScreen';

const Stack = createNativeStackNavigator<ExploreStackParamList>();

export default function ExploreNavigator() {
  const { t } = useTranslation(['common', 'profile']);
  const isDarkMode = useThemeStore((state) => state.isDarkMode);

  return (
    <Stack.Navigator
      screenOptions={{
        headerShown: false,
        statusBarStyle: isDarkMode ? 'light' : 'dark',
      }}
    >
      <Stack.Screen
        name="ExploreMain"
        component={ExploreScreen}
        options={{ title: t('common:navigation.explore') }}
      />
      <Stack.Screen
        name="CategoryFeed"
        component={CategoryFeedScreen}
        options={{
          title: t('common:screen.category'),
          animation: 'simple_push',
          statusBarStyle: 'light', // 비디오 피드는 항상 어두운 배경
        }}
      />
      <Stack.Screen
        name="ContentViewer"
        component={ContentViewerScreen}
        options={{
          title: t('common:screen.content'),
          animation: 'slide_from_bottom',
        }}
      />
      <Stack.Screen
        name="UserProfile"
        component={UserProfileScreen}
        options={{
          title: t('common:navigation.profile'),
          animation: 'simple_push',
        }}
      />
      <Stack.Screen
        name="FollowList"
        component={FollowListScreen}
        options={{
          title: t('profile:follow.followList'),
        }}
      />
    </Stack.Navigator>
  );
}
