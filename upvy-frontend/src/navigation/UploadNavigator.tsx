/**
 * 업로드 네비게이터
 *
 * 크리에이터 스튜디오의 모든 화면을 관리하는 네비게이터
 */

import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import type { UploadStackParamList } from '@/types/navigation.types';

import UploadMainScreen from '@/screens/upload/UploadMainScreen';
import VideoEditScreen from '@/screens/upload/VideoEditScreen';
import PhotoEditScreen from '@/screens/upload/PhotoEditScreen';
import ContentMetadataScreen from '@/screens/upload/ContentMetadataScreen';
import ContentManagementScreen from '@/screens/upload/ContentManagementScreen';

const Stack = createNativeStackNavigator<UploadStackParamList>();

export default function UploadNavigator() {
  const { t } = useTranslation(['upload']);

  return (
    <Stack.Navigator
      screenOptions={{
        headerShown: false,
        animation: 'slide_from_right',
      }}
    >
      <Stack.Screen
        name="UploadMain"
        component={UploadMainScreen}
        options={{ title: t('upload:title') }}
      />
      <Stack.Screen
        name="VideoEdit"
        component={VideoEditScreen}
        options={{ title: t('upload:edit.video') }}
      />
      <Stack.Screen
        name="PhotoEdit"
        component={PhotoEditScreen}
        options={{ title: t('upload:edit.photo') }}
      />
      <Stack.Screen
        name="ContentMetadata"
        component={ContentMetadataScreen}
        options={{ title: t('upload:metadata.title') }}
      />
      <Stack.Screen
        name="ContentManagement"
        component={ContentManagementScreen}
        options={{ title: t('upload:management.title') }}
      />
    </Stack.Navigator>
  );
}
