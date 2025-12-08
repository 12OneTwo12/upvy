/**
 * Expo Notifications Hook
 *
 * 푸시 알림 권한 요청, 토큰 등록, 알림 처리를 관리합니다.
 */

import { useEffect, useRef, useState, useCallback } from 'react';
import { Platform, Alert, AppState, Linking } from 'react-native';
import * as Notifications from 'expo-notifications';
import * as Device from 'expo-device';
import Constants from 'expo-constants';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { registerPushToken, deleteAllPushTokens } from '@/api/notification.api';
import { useNotificationStore } from '@/stores/notificationStore';
import { useAuthStore } from '@/stores/authStore';
import type { RootStackParamList } from '@/types/navigation.types';
import type { DeviceType, NotificationTargetType } from '@/types/notification.types';

// 알림 핸들러 설정 (포그라운드에서 알림 표시)
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
    shouldShowBanner: true,
    shouldShowList: true,
  }),
});

interface UseNotificationsResult {
  /** 푸시 토큰 */
  expoPushToken: string | null;
  /** 푸시 알림 권한 상태 */
  permissionStatus: Notifications.PermissionStatus | null;
  /** 권한 요청 함수 */
  requestPermission: () => Promise<boolean>;
  /** 푸시 토큰 등록 함수 */
  registerToken: () => Promise<void>;
  /** 푸시 토큰 삭제 함수 (로그아웃 시) */
  unregisterToken: () => Promise<void>;
}

/**
 * 디바이스 타입 가져오기
 */
function getDeviceType(): DeviceType {
  if (Platform.OS === 'ios') {
    return 'IOS';
  }
  if (Platform.OS === 'android') {
    return 'ANDROID';
  }
  if (Platform.OS === 'web') {
    return 'WEB';
  }
  return 'UNKNOWN';
}

const DEVICE_ID_STORAGE_KEY = '@upvy/device_id';

/**
 * 고유 디바이스 ID 생성 또는 조회
 *
 * AsyncStorage에 저장된 ID가 있으면 재사용하고,
 * 없으면 새로 생성하여 저장합니다.
 * (앱 재설치 시에만 새로운 ID 생성)
 */
async function getDeviceId(): Promise<string> {
  try {
    // 저장된 deviceId가 있는지 확인
    const storedDeviceId = await AsyncStorage.getItem(DEVICE_ID_STORAGE_KEY);
    if (storedDeviceId) {
      return storedDeviceId;
    }

    // 새로운 deviceId 생성
    const deviceName = Device.deviceName || 'unknown';
    const osName = Device.osName || Platform.OS;
    const osVersion = Device.osVersion || '';
    const uniqueId = Math.random().toString(36).substring(2, 15);
    const newDeviceId = `${osName}-${osVersion}-${deviceName}-${uniqueId}`;

    // AsyncStorage에 저장
    await AsyncStorage.setItem(DEVICE_ID_STORAGE_KEY, newDeviceId);
    console.log('New device ID generated:', newDeviceId);

    return newDeviceId;
  } catch (error) {
    console.error('Failed to get/set device ID:', error);
    // 에러 시 임시 ID 반환 (저장 실패해도 동작은 함)
    return `${Platform.OS}-${Date.now()}`;
  }
}

/**
 * Expo Push Token 가져오기
 */
async function getExpoPushToken(): Promise<string | null> {
  try {
    // 실제 디바이스인지 확인
    if (!Device.isDevice) {
      console.log('Push notifications only work on physical devices');
      return null;
    }

    // Android 채널 설정
    if (Platform.OS === 'android') {
      await Notifications.setNotificationChannelAsync('default', {
        name: 'default',
        importance: Notifications.AndroidImportance.MAX,
        vibrationPattern: [0, 250, 250, 250],
        lightColor: '#22c55e',
      });
    }

    // Expo 프로젝트 ID 가져오기
    const projectId = Constants.expoConfig?.extra?.eas?.projectId;
    if (!projectId) {
      console.warn('EAS project ID not found');
      return null;
    }

    // 푸시 토큰 가져오기
    const { data: token } = await Notifications.getExpoPushTokenAsync({
      projectId,
    });

    return token;
  } catch (error) {
    console.error('Failed to get push token:', error);
    return null;
  }
}

/**
 * 푸시 알림 훅
 */
export function useNotifications(): UseNotificationsResult {
  const [expoPushToken, setExpoPushToken] = useState<string | null>(null);
  const [permissionStatus, setPermissionStatus] =
    useState<Notifications.PermissionStatus | null>(null);

  const notificationListener = useRef<Notifications.EventSubscription | null>(null);
  const responseListener = useRef<Notifications.EventSubscription | null>(null);

  const { fetchUnreadCount } = useNotificationStore();
  const { isAuthenticated } = useAuthStore();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();

  /**
   * 알림 권한 요청
   */
  const requestPermission = useCallback(async (): Promise<boolean> => {
    try {
      const { status: existingStatus } = await Notifications.getPermissionsAsync();
      setPermissionStatus(existingStatus);

      if (existingStatus === 'granted') {
        return true;
      }

      const { status } = await Notifications.requestPermissionsAsync();
      setPermissionStatus(status);

      return status === 'granted';
    } catch (error) {
      console.error('Failed to request notification permission:', error);
      return false;
    }
  }, []);

  /**
   * 푸시 토큰 등록
   */
  const registerToken = useCallback(async (): Promise<void> => {
    try {
      // 권한 확인
      const hasPermission = await requestPermission();
      if (!hasPermission) {
        console.log('Notification permission denied');
        return;
      }

      // 토큰 가져오기
      const token = await getExpoPushToken();
      if (!token) {
        console.log('Failed to get push token');
        return;
      }

      setExpoPushToken(token);

      // 백엔드에 토큰 등록
      const deviceId = await getDeviceId();
      await registerPushToken({
        token,
        deviceId,
        deviceType: getDeviceType(),
        provider: 'EXPO',
      });

      console.log('Push token registered:', token);
    } catch (error) {
      console.error('Failed to register push token:', error);
    }
  }, [requestPermission]);

  /**
   * 푸시 토큰 삭제 (로그아웃 시)
   */
  const unregisterToken = useCallback(async (): Promise<void> => {
    try {
      await deleteAllPushTokens();
      setExpoPushToken(null);
      console.log('All push tokens deleted');
    } catch (error) {
      console.error('Failed to delete push tokens:', error);
    }
  }, []);

  /**
   * 알림 클릭 시 화면 라우팅
   */
  const handleNotificationResponse = useCallback(
    (response: Notifications.NotificationResponse) => {
      const data = response.notification.request.content.data as {
        targetType?: NotificationTargetType;
        targetId?: string;
      };

      if (!data.targetType || !data.targetId) {
        // 데이터가 없으면 알림 목록으로 이동
        navigation.navigate('NotificationList');
        return;
      }

      // 타겟 타입에 따라 화면 이동
      switch (data.targetType) {
        case 'CONTENT':
          // 콘텐츠 상세로 이동
          navigation.navigate('Main', {
            screen: 'Feed',
            params: {
              screen: 'ContentViewer',
              params: { contentId: data.targetId },
            },
          });
          break;
        case 'COMMENT':
          // 댓글이 있는 콘텐츠로 이동 (댓글 ID를 통해 콘텐츠 ID를 알아야 함)
          // 현재는 알림 목록으로 이동
          navigation.navigate('NotificationList');
          break;
        case 'USER':
          // 사용자 프로필로 이동
          navigation.navigate('Main', {
            screen: 'Profile',
            params: {
              screen: 'UserProfile',
              params: { userId: data.targetId },
            },
          });
          break;
        default:
          navigation.navigate('NotificationList');
      }
    },
    [navigation]
  );

  // 알림 리스너 설정
  useEffect(() => {
    // 포그라운드에서 알림 수신 시
    notificationListener.current = Notifications.addNotificationReceivedListener(
      (notification: Notifications.Notification) => {
        console.log('Notification received:', notification);
        // 읽지 않은 알림 수 새로고침
        fetchUnreadCount();
      }
    );

    // 알림 클릭 시
    responseListener.current = Notifications.addNotificationResponseReceivedListener(
      handleNotificationResponse
    );

    return () => {
      if (notificationListener.current) {
        notificationListener.current.remove();
      }
      if (responseListener.current) {
        responseListener.current.remove();
      }
    };
  }, [fetchUnreadCount, handleNotificationResponse]);

  // 인증 상태 변경 시 토큰 등록
  useEffect(() => {
    if (isAuthenticated) {
      registerToken();
    }
  }, [isAuthenticated, registerToken]);

  // 앱 상태 변경 시 읽지 않은 알림 수 새로고침
  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextAppState) => {
      if (nextAppState === 'active' && isAuthenticated) {
        fetchUnreadCount();
      }
    });

    return () => {
      subscription.remove();
    };
  }, [isAuthenticated, fetchUnreadCount]);

  return {
    expoPushToken,
    permissionStatus,
    requestPermission,
    registerToken,
    unregisterToken,
  };
}

/**
 * 알림 권한 상태 확인 (설정 화면 안내용)
 */
export async function checkNotificationPermission(): Promise<{
  status: Notifications.PermissionStatus;
  canAskAgain: boolean;
}> {
  const { status, canAskAgain } = await Notifications.getPermissionsAsync();
  return { status, canAskAgain };
}

/**
 * 시스템 설정으로 이동 안내
 */
export function showSettingsAlert(): void {
  Alert.alert(
    '알림 권한 필요',
    '푸시 알림을 받으려면 설정에서 알림을 허용해주세요.',
    [
      { text: '취소', style: 'cancel' },
      { text: '설정으로 이동', onPress: () => Linking.openSettings() },
    ]
  );
}
