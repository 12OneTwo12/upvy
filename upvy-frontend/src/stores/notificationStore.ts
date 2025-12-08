/**
 * 알림 상태 관리 Store
 *
 * Zustand를 사용하여 알림 관련 전역 상태를 관리합니다.
 * - 읽지 않은 알림 수 (배지용)
 * - 알림 설정 상태
 */

import { create } from 'zustand';
import {
  getUnreadNotificationCount,
  getNotificationSettings,
  updateNotificationSettings,
} from '@/api/notification.api';
import type {
  NotificationSettingsResponse,
  UpdateNotificationSettingsRequest,
} from '@/types/notification.types';

interface NotificationState {
  /** 읽지 않은 알림 수 */
  unreadCount: number;
  /** 알림 설정 */
  settings: NotificationSettingsResponse | null;
  /** 로딩 상태 */
  isLoading: boolean;
  /** 에러 메시지 */
  error: string | null;
}

interface NotificationActions {
  /** 읽지 않은 알림 수 조회 */
  fetchUnreadCount: () => Promise<void>;
  /** 읽지 않은 알림 수 감소 */
  decrementUnreadCount: () => void;
  /** 읽지 않은 알림 수 초기화 (모두 읽음 처리 시) */
  resetUnreadCount: () => void;
  /** 알림 설정 조회 */
  fetchSettings: () => Promise<void>;
  /** 알림 설정 수정 */
  updateSettings: (request: UpdateNotificationSettingsRequest) => Promise<boolean>;
  /** 상태 초기화 (로그아웃 시) */
  reset: () => void;
}

const initialState: NotificationState = {
  unreadCount: 0,
  settings: null,
  isLoading: false,
  error: null,
};

export const useNotificationStore = create<NotificationState & NotificationActions>(
  (set, get) => ({
    ...initialState,

    fetchUnreadCount: async () => {
      try {
        const response = await getUnreadNotificationCount();
        set({ unreadCount: response.unreadCount });
      } catch (error) {
        console.error('Failed to fetch unread count:', error);
      }
    },

    decrementUnreadCount: () => {
      const { unreadCount } = get();
      if (unreadCount > 0) {
        set({ unreadCount: unreadCount - 1 });
      }
    },

    resetUnreadCount: () => {
      set({ unreadCount: 0 });
    },

    fetchSettings: async () => {
      set({ isLoading: true, error: null });
      try {
        const settings = await getNotificationSettings();
        set({ settings, isLoading: false });
      } catch (error) {
        console.error('Failed to fetch notification settings:', error);
        set({ error: 'Failed to fetch settings', isLoading: false });
      }
    },

    updateSettings: async (request: UpdateNotificationSettingsRequest) => {
      // 낙관적 업데이트: 먼저 UI를 업데이트하고, 실패 시 롤백
      const previousSettings = get().settings;

      // 즉시 UI 업데이트 (isLoading 변경 없음 - 화면 번쩍임 방지)
      if (previousSettings) {
        set({
          settings: { ...previousSettings, ...request },
          error: null,
        });
      }

      try {
        const settings = await updateNotificationSettings(request);
        set({ settings });
        return true;
      } catch (error) {
        console.error('Failed to update notification settings:', error);
        // 실패 시 이전 상태로 롤백
        set({ settings: previousSettings, error: 'Failed to update settings' });
        return false;
      }
    },

    reset: () => {
      set(initialState);
    },
  })
);
