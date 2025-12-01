/**
 * 검색 네비게이터
 *
 * 검색 탭 내에서의 화면 전환을 관리하는 네비게이터
 * 탭바를 유지하면서 콘텐츠 뷰어 및 프로필로 이동할 수 있습니다.
 */

import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import type { SearchStackParamList } from '@/types/navigation.types';

import SearchScreen from '@/screens/search/SearchScreen';
import ContentViewerScreen from '@/screens/content/ContentViewerScreen';
import UserProfileScreen from '@/screens/profile/UserProfileScreen';
import FollowListScreen from '@/screens/profile/FollowListScreen';

const Stack = createNativeStackNavigator<SearchStackParamList>();

export default function SearchNavigator() {
  return (
    <Stack.Navigator
      screenOptions={{
        headerShown: false,
      }}
    >
      <Stack.Screen
        name="SearchMain"
        component={SearchScreen}
        options={{ title: '검색' }}
      />
      <Stack.Screen
        name="ContentViewer"
        component={ContentViewerScreen}
        options={{
          title: '콘텐츠',
          animation: 'slide_from_bottom',
        }}
      />
      <Stack.Screen
        name="UserProfile"
        component={UserProfileScreen}
        options={{
          title: '프로필',
          animation: 'simple_push',
        }}
      />
      <Stack.Screen
        name="FollowList"
        component={FollowListScreen}
        options={{
          title: '팔로우',
        }}
      />
    </Stack.Navigator>
  );
}
