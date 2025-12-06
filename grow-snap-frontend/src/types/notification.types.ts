/**
 * 알림 관련 타입 정의
 *
 * 백엔드 API 스펙 기준:
 * - NotificationController.kt
 * - NotificationSettingsController.kt
 * - PushTokenController.kt
 * - NotificationDto.kt
 * - NotificationSettingsDto.kt
 * - PushTokenDto.kt
 */

/**
 * 알림 유형
 * @see NotificationType.kt
 */
export type NotificationType = 'LIKE' | 'COMMENT' | 'REPLY' | 'FOLLOW';

/**
 * 알림 타겟 유형
 * @see NotificationTargetType.kt
 */
export type NotificationTargetType = 'CONTENT' | 'COMMENT' | 'USER';

/**
 * 디바이스 타입
 * @see DeviceType.kt
 */
export type DeviceType = 'IOS' | 'ANDROID' | 'WEB' | 'UNKNOWN';

/**
 * 푸시 알림 제공자
 * @see PushProvider.kt
 */
export type PushProvider = 'EXPO' | 'FCM' | 'APNS';

/**
 * 알림 응답 DTO
 * @see NotificationResponse
 */
export interface NotificationResponse {
  id: number;
  type: NotificationType;
  title: string;
  body: string;
  data: string | null;
  isRead: boolean;
  actorId: string | null;
  actorNickname: string | null;
  actorProfileImageUrl: string | null;
  targetType: NotificationTargetType | null;
  targetId: string | null;
  createdAt: string; // ISO 8601
}

/**
 * 알림 목록 응답 DTO (커서 기반 페이징)
 * @see NotificationListResponse
 */
export interface NotificationListResponse {
  notifications: NotificationResponse[];
  nextCursor: number | null;
  hasNext: boolean;
}

/**
 * 읽지 않은 알림 수 응답 DTO
 * @see UnreadNotificationCountResponse
 */
export interface UnreadNotificationCountResponse {
  unreadCount: number;
}

/**
 * 알림 설정 응답 DTO
 * @see NotificationSettingsResponse
 */
export interface NotificationSettingsResponse {
  allNotificationsEnabled: boolean;
  likeNotificationsEnabled: boolean;
  commentNotificationsEnabled: boolean;
  followNotificationsEnabled: boolean;
  updatedAt: string; // ISO 8601
}

/**
 * 알림 설정 수정 요청 DTO
 * @see UpdateNotificationSettingsRequest
 */
export interface UpdateNotificationSettingsRequest {
  allNotificationsEnabled?: boolean;
  likeNotificationsEnabled?: boolean;
  commentNotificationsEnabled?: boolean;
  followNotificationsEnabled?: boolean;
}

/**
 * 푸시 토큰 등록 요청 DTO
 * @see RegisterPushTokenRequest
 */
export interface RegisterPushTokenRequest {
  token: string;
  deviceId: string;
  deviceType?: DeviceType;
  provider?: PushProvider;
}

/**
 * 푸시 토큰 응답 DTO
 * @see PushTokenResponse
 */
export interface PushTokenResponse {
  token: string;
  deviceId: string;
  deviceType: DeviceType;
  provider: PushProvider;
}
