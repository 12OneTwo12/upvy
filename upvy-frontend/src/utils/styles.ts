import { StyleSheet } from 'react-native';
import { useTheme } from '@/theme';
import type { Theme } from '@/theme';

/**
 * Lazy StyleSheet creator for New Architecture compatibility with Dynamic Theme support
 *
 * @example
 * // 동적 스타일 (테마 변경 시 자동 업데이트)
 * const useStyles = createStyleSheet((theme) => ({
 *   container: {
 *     flex: 1,
 *     backgroundColor: theme.colors.background.primary,
 *   },
 * }));
 */
export function createStyleSheet<T extends StyleSheet.NamedStyles<T>>(
  stylesOrFactory: T | StyleSheet.NamedStyles<T> | ((theme: Theme) => T | StyleSheet.NamedStyles<T>)
): () => T {
  // 정적 스타일 (기존 방식)
  if (typeof stylesOrFactory !== 'function') {
    let cached: T | null = null;
    return () => {
      if (cached === null) {
        cached = StyleSheet.create(stylesOrFactory) as T;
      }
      return cached as T;
    };
  }

  // 동적 스타일 (테마별 캐싱)
  const caches = new Map<boolean, T>();

  return () => {
    const theme = useTheme();
    const isDarkMode = theme.colors === require('@/theme/dark').darkColors;

    if (!caches.has(isDarkMode)) {
      const styleObject = stylesOrFactory(theme);
      caches.set(isDarkMode, StyleSheet.create(styleObject) as T);
    }

    return caches.get(isDarkMode)!;
  };
}
