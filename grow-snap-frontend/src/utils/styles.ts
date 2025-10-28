import { StyleSheet } from 'react-native';

/**
 * Lazy StyleSheet creator for New Architecture compatibility
 *
 * New Architecture (Bridgeless)에서는 StyleSheet.create()가 네이티브 모듈이
 * 준비되기 전에 호출되면 "runtime not ready" 에러가 발생합니다.
 *
 * 이 함수는 StyleSheet를 lazy evaluation으로 생성하여
 * 실제로 필요할 때만 StyleSheet.create()를 호출합니다.
 *
 * @example
 * const useStyles = createStyleSheet({
 *   container: {
 *     flex: 1,
 *     backgroundColor: '#fff',
 *   },
 * });
 *
 * function MyComponent() {
 *   const styles = useStyles();
 *   return <View style={styles.container} />;
 * }
 */
export function createStyleSheet<T extends StyleSheet.NamedStyles<T>>(
  styles: T | StyleSheet.NamedStyles<T>
): () => T {
  let cached: T | null = null;

  return () => {
    if (cached === null) {
      cached = StyleSheet.create(styles);
    }
    return cached;
  };
}
