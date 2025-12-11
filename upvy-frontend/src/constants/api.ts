// @ts-ignore
import Constants from 'expo-constants';

/**
 * API Base URL
 * Development: localhost:8080
 * Production: api.upvy.org
 */
export const API_HOST =
  Constants.expoConfig?.extra?.apiUrl ||
  (__DEV__ ? 'http://localhost:8080' : 'https://api.upvy.org');

export const API_BASE_URL = `${API_HOST}/api/v1`;

/**
 * API Endpoints
 */
export const API_ENDPOINTS = {
  // Auth
  AUTH: {
    REFRESH: '/auth/refresh',
    LOGOUT: '/auth/logout',
  },

  // User
  USER: {
    ME: '/users/me',
    BY_ID: (id: string) => `/users/${id}`,
  },

  // Profile
  PROFILE: {
    ME: '/profiles/me',
    BY_USER_ID: (userId: string) => `/profiles/${userId}`,
    BY_NICKNAME: (nickname: string) => `/profiles/nickname/${nickname}`,
    UPDATE: '/profiles',
    CHECK_NICKNAME: (nickname: string) => `/profiles/check/nickname/${nickname}`,
    UPLOAD_IMAGE: '/profiles/image',
  },

  // Follow
  FOLLOW: {
    FOLLOW: (userId: string) => `/follows/${userId}`,
    UNFOLLOW: (userId: string) => `/follows/${userId}`,
    CHECK: (userId: string) => `/follows/check/${userId}`,
    STATS: (userId: string) => `/follows/stats/${userId}`,
    STATS_ME: '/follows/stats/me',
    FOLLOWERS: (userId: string) => `/follows/followers/${userId}`,
    FOLLOWING: (userId: string) => `/follows/following/${userId}`,
  },

  // Feed
  FEED: {
    MAIN: '/feed',
    FOLLOWING: '/feed/following',
    REFRESH: '/feed/refresh',
    CATEGORY: (category: string) => `/feed/categories/${category}`,
    CATEGORY_REFRESH: (category: string) => `/feed/categories/${category}/refresh`,
  },

  // Like (백엔드: LikeController)
  LIKE: {
    CREATE: (contentId: string) => `/contents/${contentId}/like`,
    DELETE: (contentId: string) => `/contents/${contentId}/like`,
    STATUS: (contentId: string) => `/contents/${contentId}/like/status`,
    COUNT: (contentId: string) => `/contents/${contentId}/likes`,
  },

  // Comment (백엔드: CommentController)
  COMMENT: {
    CREATE: (contentId: string) => `/contents/${contentId}/comments`,
    LIST: (contentId: string) => `/contents/${contentId}/comments`,
    REPLIES: (commentId: string) => `/comments/${commentId}/replies`,
    DELETE: (commentId: string) => `/comments/${commentId}`,
  },

  // Comment Like (백엔드: CommentLikeController)
  COMMENT_LIKE: {
    CREATE: (commentId: string) => `/comments/${commentId}/likes`,
    DELETE: (commentId: string) => `/comments/${commentId}/likes`,
    COUNT: (commentId: string) => `/comments/${commentId}/likes/count`,
    STATUS: (commentId: string) => `/comments/${commentId}/likes/check`,
  },

  // Save (백엔드: SaveController)
  SAVE: {
    CREATE: (contentId: string) => `/contents/${contentId}/save`,
    DELETE: (contentId: string) => `/contents/${contentId}/save`,
    STATUS: (contentId: string) => `/contents/${contentId}/save/status`,
    LIST: '/users/me/saved-contents',
  },

  // Share (백엔드: ShareController)
  SHARE: {
    CREATE: (contentId: string) => `/contents/${contentId}/share`,
    GET_LINK: (contentId: string) => `/contents/${contentId}/share-link`,
  },

  // Analytics
  ANALYTICS: {
    TRACK_VIEW: '/analytics/views',
  },

  // Content (크리에이터 스튜디오)
  CONTENT: {
    // S3 Presigned Upload URL 생성
    UPLOAD_URL: '/contents/upload-url',
    // 콘텐츠 생성
    CREATE: '/contents',
    // 콘텐츠 조회
    GET: (contentId: string) => `/contents/${contentId}`,
    // 내 콘텐츠 목록
    MY_CONTENTS: '/contents/me',
    // 콘텐츠 수정
    UPDATE: (contentId: string) => `/contents/${contentId}`,
    // 콘텐츠 삭제
    DELETE: (contentId: string) => `/contents/${contentId}`,
  },

  // Search (검색)
  SEARCH: {
    // 콘텐츠 검색
    CONTENTS: '/search/contents',
    // 사용자 검색
    USERS: '/search/users',
    // 자동완성
    AUTOCOMPLETE: '/search/autocomplete',
    // 인기 검색어
    TRENDING: '/search/trending',
    // 검색 기록 조회
    HISTORY: '/search/history',
    // 특정 검색어 삭제
    DELETE_HISTORY: (keyword: string) => `/search/history/${keyword}`,
  },

  // Notification (알림)
  NOTIFICATION: {
    // 알림 목록 조회 (커서 기반 페이징)
    LIST: '/notifications',
    // 읽지 않은 알림 수 조회
    UNREAD_COUNT: '/notifications/unread-count',
    // 개별 알림 읽음 처리
    MARK_AS_READ: (notificationId: number) => `/notifications/${notificationId}/read`,
    // 모든 알림 읽음 처리
    MARK_ALL_AS_READ: '/notifications/read-all',
    // 개별 알림 삭제
    DELETE: (notificationId: number) => `/notifications/${notificationId}`,
    // 알림 설정 조회
    SETTINGS: '/notifications/settings',
    // 알림 설정 수정
    UPDATE_SETTINGS: '/notifications/settings',
  },

  // Push Token (푸시 토큰)
  PUSH_TOKEN: {
    // 푸시 토큰 등록/갱신
    REGISTER: '/push-tokens',
    // 특정 디바이스 푸시 토큰 삭제
    DELETE: (deviceId: string) => `/push-tokens/${deviceId}`,
    // 모든 디바이스 푸시 토큰 삭제
    DELETE_ALL: '/push-tokens',
  },
} as const;

/**
 * API Timeout (ms)
 */
export const API_TIMEOUT = 10000; // 10 seconds

/**
 * Upload API Timeout (ms)
 * 파일 업로드는 일반 API보다 오래 걸리므로 별도 타임아웃 설정
 */
export const API_UPLOAD_TIMEOUT = 120000; // 120 seconds (2 minutes)
