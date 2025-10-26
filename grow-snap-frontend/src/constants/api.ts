import Constants from 'expo-constants';

/**
 * API Base URL
 * Development: localhost:8080
 * Production: api.growsnap.com
 */
export const API_BASE_URL =
  Constants.expoConfig?.extra?.apiUrl ||
  (__DEV__ ? 'http://localhost:8080/api/v1' : 'https://api.growsnap.com/api/v1');

/**
 * API Endpoints
 */
export const API_ENDPOINTS = {
  // Auth
  AUTH: {
    GOOGLE_LOGIN: '/auth/google',
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
    BY_USER_ID: (userId: string) => `/profiles/user/${userId}`,
    BY_NICKNAME: (nickname: string) => `/profiles/nickname/${nickname}`,
    UPDATE: '/profiles/me',
    CHECK_NICKNAME: '/profiles/check-nickname',
    UPLOAD_IMAGE: '/profiles/me/image',
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
    MAIN: '/feed/main',
    FOLLOWING: '/feed/following',
    REFRESH: '/feed/refresh',
  },

  // Like
  LIKE: {
    TOGGLE: (contentId: string) => `/likes/${contentId}`,
    COUNT: (contentId: string) => `/likes/${contentId}/count`,
  },

  // Comment
  COMMENT: {
    CREATE: '/comments',
    LIST: (contentId: string) => `/comments/${contentId}`,
    DELETE: (commentId: string) => `/comments/${commentId}`,
  },

  // Save
  SAVE: {
    TOGGLE: (contentId: string) => `/saves/${contentId}`,
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
