/**
 * 프로필 네비게이터
 *
 * 프로필 탭 내에서의 화면 전환을 관리하는 네비게이터
 * 탭바를 유지하면서 콘텐츠 뷰어로 이동할 수 있습니다.
 */

import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import type { ProfileStackParamList } from '@/types/navigation.types';

import ProfileScreen from '@/screens/profile/ProfileScreen';
import ContentViewerScreen from '@/screens/content/ContentViewerScreen';
import UserProfileScreen from '@/screens/profile/UserProfileScreen';
import FollowListScreen from '@/screens/profile/FollowListScreen';

const Stack = createNativeStackNavigator<ProfileStackParamList>();

export default function ProfileNavigator() {
  return (
    <Stack.Navigator
      screenOptions={{
        headerShown: false,
      }}
    >
      <Stack.Screen
        name="ProfileMain"
        component={ProfileScreen}
        options={{ title: '프로필' }}
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
