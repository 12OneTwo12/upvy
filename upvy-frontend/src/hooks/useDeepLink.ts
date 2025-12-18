import * as Linking from 'expo-linking';
import { useEffect } from 'react';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RootStackParamList } from '../types/navigation.types';

/**
 * Universal Links (iOS) and App Links (Android) handler
 *
 * Handles incoming deep links from shared content:
 * - https://api.upvy.org/watch/{contentId}
 *
 * @example
 * // In RootNavigator.tsx
 * import { useDeepLink } from '../hooks/useDeepLink';
 *
 * export const RootNavigator = () => {
 *   useDeepLink();
 *   return <NavigationContainer>...</NavigationContainer>;
 * };
 */
export const useDeepLink = () => {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();

  useEffect(() => {
    // Handle initial URL when app is launched from a link
    Linking.getInitialURL().then((url) => {
      if (url) {
        handleDeepLink(url);
      }
    });

    // Handle URLs while app is already running
    const subscription = Linking.addEventListener('url', ({ url }) => {
      handleDeepLink(url);
    });

    return () => {
      subscription.remove();
    };
  }, []);

  /**
   * Parse deep link URL and navigate to appropriate screen
   *
   * @param url - Deep link URL (e.g., https://api.upvy.org/watch/content-id)
   */
  const handleDeepLink = (url: string) => {
    try {
      const { path } = Linking.parse(url);

      // Match: https://api.upvy.org/watch/{contentId}
      if (path?.startsWith('watch/')) {
        const contentId = path.split('/')[1];

        if (contentId) {
          console.log('[useDeepLink] Navigating to ContentViewer:', contentId);
          navigation.navigate('ContentViewer', { contentId });
        } else {
          console.warn('[useDeepLink] Invalid contentId in URL:', url);
        }
      } else {
        console.log('[useDeepLink] Unhandled deep link path:', path);
      }
    } catch (error) {
      console.error('[useDeepLink] Failed to handle deep link:', url, error);
    }
  };
};
