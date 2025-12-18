/**
 * Firebase Analytics Event Types and Parameters
 *
 * 이 파일은 Firebase Analytics의 모든 이벤트 타입과 파라미터를 정의합니다.
 * Firebase 권장사항:
 * - 이벤트명: snake_case (예: content_view, sign_up)
 * - 최대 500개의 고유 이벤트 타입
 * - 이벤트당 최대 25개 파라미터
 * - 파라미터 값은 100자 제한
 */

// ===== 표준 Firebase 이벤트 =====
// https://firebase.google.com/docs/reference/android/com/google/firebase/analytics/FirebaseAnalytics.Event

export const AnalyticsEvents = {
  // 인증 관련
  LOGIN: 'login',
  SIGN_UP: 'sign_up',
  LOGOUT: 'logout',

  // 콘텐츠 상호작용
  CONTENT_VIEW: 'content_view',
  LIKE: 'like',
  UNLIKE: 'unlike',
  SAVE: 'save',
  UNSAVE: 'unsave',
  COMMENT: 'comment',
  SHARE: 'share',

  // 사용자 행동
  SEARCH: 'search',
  PROFILE_VIEW: 'profile_view',
  FOLLOW: 'follow',
  UNFOLLOW: 'unfollow',

  // 콘텐츠 생성
  CONTENT_UPLOAD_START: 'content_upload_start',
  CONTENT_UPLOAD_COMPLETE: 'content_upload_complete',
  CONTENT_UPLOAD_FAILED: 'content_upload_failed',

  // 비디오 재생
  VIDEO_PLAY: 'video_play',
  VIDEO_PAUSE: 'video_pause',
  VIDEO_COMPLETE: 'video_complete',

  // 화면 추적 (자동)
  SCREEN_VIEW: 'screen_view',

  // 에러 추적
  ERROR: 'error',
} as const;

export type AnalyticsEventName = (typeof AnalyticsEvents)[keyof typeof AnalyticsEvents];

// ===== 인증 관련 파라미터 =====

export type AuthMethod = 'google' | 'apple' | 'email';

export interface LoginParams {
  method: AuthMethod;
}

export interface SignUpParams {
  method: AuthMethod;
}

// ===== 콘텐츠 상호작용 파라미터 =====

export type ContentType = 'video' | 'image' | 'text';

export interface ContentViewParams {
  content_id: string;
  content_type: ContentType;
  category?: string;
  creator_id?: string;
  duration?: number;
}

export interface LikeParams {
  content_id: string;
  content_type: ContentType;
  creator_id?: string;
}

export interface UnlikeParams {
  content_id: string;
  content_type: ContentType;
}

export interface SaveParams {
  content_id: string;
  content_type: ContentType;
}

export interface UnsaveParams {
  content_id: string;
  content_type: ContentType;
}

export interface CommentParams {
  content_id: string;
  comment_length: number;
}

export type ShareMethod = 'link' | 'social' | 'message';

export interface ShareParams {
  content_id: string;
  content_type: ContentType;
  method: ShareMethod;
}

// ===== 사용자 행동 파라미터 =====

export interface SearchParams {
  search_term: string;
  result_count: number;
  category?: string;
}

export interface ProfileViewParams {
  user_id: string;
  is_self: boolean;
}

export interface FollowParams {
  user_id: string;
}

export interface UnfollowParams {
  user_id: string;
}

// ===== 콘텐츠 업로드 파라미터 =====

export interface ContentUploadStartParams {
  content_type: ContentType;
  category?: string;
}

export interface ContentUploadCompleteParams {
  content_type: ContentType;
  category?: string;
  duration?: number;
  file_size?: number;
}

export interface ContentUploadFailedParams {
  error_message: string;
}

// ===== 비디오 재생 파라미터 =====

export interface VideoPlayParams {
  content_id: string;
  position: number;
  duration: number;
}

export interface VideoPauseParams {
  content_id: string;
  position: number;
}

export interface VideoCompleteParams {
  content_id: string;
  watch_time: number;
  duration: number;
}

// ===== 화면 추적 파라미터 =====

export type ScreenName =
  | 'Home'
  | 'Feed'
  | 'Upload'
  | 'Profile'
  | 'ContentViewer'
  | 'Search'
  | 'Notifications'
  | 'Settings'
  | 'Login'
  | 'ProfileSetup'
  | 'EditProfile'
  | 'FollowerList'
  | 'FollowingList';

export interface ScreenViewParams {
  screen_name: ScreenName;
  screen_class?: string;
}

// ===== 에러 추적 파라미터 =====

export interface ErrorParams {
  error_message: string;
  error_code?: string;
  screen_name?: string;
  fatal?: boolean;
}

// ===== 사용자 속성 =====

export interface UserProperties {
  account_type?: 'free' | 'premium';
  preferred_language?: 'ko' | 'en' | 'ja';
  content_preference?: string[];
  total_followers?: number;
  total_following?: number;
  total_uploads?: number;
}
