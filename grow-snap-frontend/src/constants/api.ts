// @ts-ignore
import Constants from 'expo-constants';

/**
 * API Base URL
 * Development: 10.0.2.2:8080 (Android Emulator) or localhost:8080
 * Production: api.growsnap.com
 */
export const API_HOST =
  Constants.expoConfig?.extra?.apiUrl ||
  (__DEV__ ? 'http://localhost:8080' : 'https://api.growsnap.com');

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
    TRACK_VIEW: '/analytics/view',
  },
} as const;

/**
 * API Timeout (ms)
 */
export const API_TIMEOUT = 10000; // 10 seconds
