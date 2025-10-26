import AsyncStorage from '@react-native-async-storage/async-storage';

/**
 * Storage Keys
 */
export const STORAGE_KEYS = {
  ACCESS_TOKEN: '@grow-snap/access-token',
  REFRESH_TOKEN: '@grow-snap/refresh-token',
  USER_INFO: '@grow-snap/user-info',
} as const;

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
 */
export const getAccessToken = (): Promise<string | null> => {
  return getItem<string>(STORAGE_KEYS.ACCESS_TOKEN);
};

/**
 * Set access token
 */
export const setAccessToken = (token: string): Promise<void> => {
  return setItem(STORAGE_KEYS.ACCESS_TOKEN, token);
};

/**
 * Get refresh token
 */
export const getRefreshToken = (): Promise<string | null> => {
  return getItem<string>(STORAGE_KEYS.REFRESH_TOKEN);
};

/**
 * Set refresh token
 */
export const setRefreshToken = (token: string): Promise<void> => {
  return setItem(STORAGE_KEYS.REFRESH_TOKEN, token);
};

/**
 * Remove tokens
 */
export const removeTokens = async (): Promise<void> => {
  await removeItem(STORAGE_KEYS.ACCESS_TOKEN);
  await removeItem(STORAGE_KEYS.REFRESH_TOKEN);
};
