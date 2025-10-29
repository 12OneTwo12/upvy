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

  // Like (백엔드 스펙: POST/DELETE 방식)
  LIKE: {
    CREATE: (contentId: string) => `/likes/${contentId}`,
    DELETE: (contentId: string) => `/likes/${contentId}`,
    CHECK: (contentId: string) => `/likes/${contentId}/check`,
    COUNT: (contentId: string) => `/likes/${contentId}/count`,
  },

  // Comment
  COMMENT: {
    CREATE: '/comments',
    LIST: (contentId: string) => `/comments/${contentId}`,
    DELETE: (commentId: string) => `/comments/${commentId}`,
  },

  // Save (백엔드 스펙: POST/DELETE 방식)
  SAVE: {
    CREATE: (contentId: string) => `/saves/${contentId}`,
    DELETE: (contentId: string) => `/saves/${contentId}`,
    CHECK: (contentId: string) => `/saves/${contentId}/check`,
    LIST: '/saves/me',
  },

  // Share
  SHARE: {
    CREATE: (contentId: string) => `/shares/${contentId}`,
    GET_LINK: (contentId: string) => `/shares/${contentId}/link`,
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
