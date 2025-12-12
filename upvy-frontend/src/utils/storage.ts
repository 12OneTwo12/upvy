import AsyncStorage from '@react-native-async-storage/async-storage';

/**
 * Storage Keys
 */
export const STORAGE_KEYS = {
  ACCESS_TOKEN: '@upvy/access-token',
  REFRESH_TOKEN: '@upvy/refresh-token',
  USER_INFO: '@upvy/user-info',
} as const;

/**
 * In-memory token cache to avoid AsyncStorage race conditions
 */
let accessTokenCache: string | null = null;
let refreshTokenCache: string | null = null;

/**
 * Get item from AsyncStorage
 */
export const getItem = async <T = string>(key: string): Promise<T | null> => {
  try {
    const value = await AsyncStorage.getItem(key);
    return value ? (JSON.parse(value) as T) : null;
  } catch (error) {
    console.error(`Error getting ${key}:`, error);
    return null;
  }
};

/**
 * Set item to AsyncStorage
 */
export const setItem = async <T = unknown>(key: string, value: T): Promise<void> => {
  try {
    const jsonValue = JSON.stringify(value);
    await AsyncStorage.setItem(key, jsonValue);
  } catch (error) {
    console.error(`Error setting ${key}:`, error);
  }
};

/**
 * Remove item from AsyncStorage
 */
export const removeItem = async (key: string): Promise<void> => {
  try {
    await AsyncStorage.removeItem(key);
  } catch (error) {
    console.error(`Error removing ${key}:`, error);
  }
};

/**
 * Clear all items from AsyncStorage
 */
export const clearAll = async (): Promise<void> => {
  try {
    await AsyncStorage.clear();
  } catch (error) {
    console.error('Error clearing storage:', error);
  }
};

/**
 * Get access token
 * Returns cached value immediately if available, otherwise reads from AsyncStorage
 */
export const getAccessToken = async (): Promise<string | null> => {
  // Return cached value immediately to avoid race conditions
  if (accessTokenCache !== null) {
    return accessTokenCache;
  }

  // Load from AsyncStorage and cache it
  const token = await getItem<string>(STORAGE_KEYS.ACCESS_TOKEN);
  accessTokenCache = token;
  return token;
};

/**
 * Set access token
 * Saves to both memory cache and AsyncStorage
 */
export const setAccessToken = async (token: string): Promise<void> => {
  // Update cache immediately
  accessTokenCache = token;
  // Persist to AsyncStorage
  await setItem(STORAGE_KEYS.ACCESS_TOKEN, token);
};

/**
 * Get refresh token
 * Returns cached value immediately if available, otherwise reads from AsyncStorage
 */
export const getRefreshToken = async (): Promise<string | null> => {
  // Return cached value immediately to avoid race conditions
  if (refreshTokenCache !== null) {
    return refreshTokenCache;
  }

  // Load from AsyncStorage and cache it
  const token = await getItem<string>(STORAGE_KEYS.REFRESH_TOKEN);
  refreshTokenCache = token;
  return token;
};

/**
 * Set refresh token
 * Saves to both memory cache and AsyncStorage
 */
export const setRefreshToken = async (token: string): Promise<void> => {
  // Update cache immediately
  refreshTokenCache = token;
  // Persist to AsyncStorage
  await setItem(STORAGE_KEYS.REFRESH_TOKEN, token);
};

/**
 * Remove tokens
 * Clears both memory cache and AsyncStorage
 */
export const removeTokens = async (): Promise<void> => {
  // Clear cache immediately
  accessTokenCache = null;
  refreshTokenCache = null;
  // Remove from AsyncStorage
  await removeItem(STORAGE_KEYS.ACCESS_TOKEN);
  await removeItem(STORAGE_KEYS.REFRESH_TOKEN);
};
