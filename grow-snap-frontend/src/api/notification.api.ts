/**
 * 알림 API 클라이언트
 *
 * 백엔드 API 스펙 기준:
 * - NotificationController.kt (/api/v1/notifications)
 * - NotificationSettingsController.kt (/api/v1/notifications/settings)
 * - PushTokenController.kt (/api/v1/push-tokens)
 */

import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';
import type {
  NotificationListResponse,
  NotificationResponse,
  UnreadNotificationCountResponse,
  NotificationSettingsResponse,
  UpdateNotificationSettingsRequest,
  RegisterPushTokenRequest,
  PushTokenResponse,
} from '@/types/notification.types';

/**
 * 알림 목록 조회 (커서 기반 페이징)
 *
 * @param cursor 마지막으로 조회한 알림 ID (없으면 undefined)
 * @param limit 조회할 개수 (기본값: 20, 최대: 50)
 * @returns 알림 목록 응답
 */
export async function getNotifications(
  cursor?: number,
  limit: number = 20
): Promise<NotificationListResponse> {
  const params = new URLSearchParams();
  if (cursor !== undefined) {
    params.append('cursor', cursor.toString());
  }
  params.append('limit', Math.min(limit, 50).toString());

  const { data } = await apiClient.get<NotificationListResponse>(
    `${API_ENDPOINTS.NOTIFICATION.LIST}?${params.toString()}`
  );
  return data;
}

/**
 * 읽지 않은 알림 수 조회
 *
 * @returns 읽지 않은 알림 수
 */
export async function getUnreadNotificationCount(): Promise<UnreadNotificationCountResponse> {
  const { data } = await apiClient.get<UnreadNotificationCountResponse>(
    API_ENDPOINTS.NOTIFICATION.UNREAD_COUNT
  );
  return data;
}

/**
 * 개별 알림 읽음 처리
 *
 * @param notificationId 알림 ID
 * @returns 읽음 처리된 알림
 */
export async function markNotificationAsRead(
  notificationId: number
): Promise<NotificationResponse> {
  const { data } = await apiClient.patch<NotificationResponse>(
    API_ENDPOINTS.NOTIFICATION.MARK_AS_READ(notificationId)
  );
  return data;
}

/**
 * 모든 알림 읽음 처리
 */
export async function markAllNotificationsAsRead(): Promise<void> {
  await apiClient.patch(API_ENDPOINTS.NOTIFICATION.MARK_ALL_AS_READ);
}

/**
 * 개별 알림 삭제
 *
 * @param notificationId 알림 ID
 */
export async function deleteNotification(notificationId: number): Promise<void> {
  await apiClient.delete(API_ENDPOINTS.NOTIFICATION.DELETE(notificationId));
}

/**
 * 알림 설정 조회
 *
 * @returns 알림 설정
 */
export async function getNotificationSettings(): Promise<NotificationSettingsResponse> {
  const { data } = await apiClient.get<NotificationSettingsResponse>(
    API_ENDPOINTS.NOTIFICATION.SETTINGS
  );
  return data;
}

/**
 * 알림 설정 수정
 *
 * @param request 수정할 알림 설정
 * @returns 수정된 알림 설정
 */
export async function updateNotificationSettings(
  request: UpdateNotificationSettingsRequest
): Promise<NotificationSettingsResponse> {
  const { data } = await apiClient.patch<NotificationSettingsResponse>(
    API_ENDPOINTS.NOTIFICATION.UPDATE_SETTINGS,
    request
  );
  return data;
}

/**
 * 푸시 토큰 등록/갱신
 *
 * @param request 푸시 토큰 등록 요청
 * @returns 등록된 푸시 토큰 정보
 */
export async function registerPushToken(
  request: RegisterPushTokenRequest
): Promise<PushTokenResponse> {
  const { data } = await apiClient.post<PushTokenResponse>(
    API_ENDPOINTS.PUSH_TOKEN.REGISTER,
    request
  );
  return data;
}

/**
 * 특정 디바이스의 푸시 토큰 삭제
 *
 * @param deviceId 삭제할 디바이스 ID
 */
export async function deletePushToken(deviceId: string): Promise<void> {
  await apiClient.delete(API_ENDPOINTS.PUSH_TOKEN.DELETE(deviceId));
}

/**
 * 모든 디바이스의 푸시 토큰 삭제
 * 로그아웃 시 모든 디바이스에서 푸시 알림을 받지 않도록 합니다.
 */
export async function deleteAllPushTokens(): Promise<void> {
  await apiClient.delete(API_ENDPOINTS.PUSH_TOKEN.DELETE_ALL);
}
