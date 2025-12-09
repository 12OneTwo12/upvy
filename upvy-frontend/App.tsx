import React, { useEffect } from 'react';
import { StatusBar } from 'expo-status-bar';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import * as Updates from 'expo-updates';
import RootNavigator from './src/navigation/RootNavigator';
import { ErrorBoundary } from './src/components/common';
import { logError } from './src/utils/errorHandler';
import { useLanguageStore } from './src/stores/languageStore';
import './src/locales'; // Initialize i18n

// React Query Client
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5분
      gcTime: 1000 * 60 * 10, // 10분 (구 cacheTime)
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export default function App() {
  // Initialize language on app start
  useEffect(() => {
    useLanguageStore.getState().initializeLanguage();
  }, []);

  // MVP: Auto-login disabled for now
  // useEffect(() => {
  //   useAuthStore.getState().checkAuth();
  // }, []);

  // 백그라운드에서 조용히 업데이트 체크 (사용자에게 보이지 않음)
  useEffect(() => {
    async function checkForUpdates() {
      // 개발 모드나 시뮬레이터에서는 업데이트 체크 안 함
      if (__DEV__ || !Updates.isEnabled) {
        return;
      }

      try {
        // 업데이트 체크
        const update = await Updates.checkForUpdateAsync();

        if (update.isAvailable) {
          // 백그라운드에서 조용히 다운로드
          await Updates.fetchUpdateAsync();
          // 다음 앱 재시작 시 자동 적용됨 (즉시 재시작하지 않음)
        }
      } catch (error) {
        // 에러가 있어도 조용히 무시 (사용자 경험 방해하지 않음)
        logError(error as Error, 'App.checkForUpdates');
      }
    }

    checkForUpdates();
  }, []);

  return (
    <ErrorBoundary onError={(error, errorInfo) => logError(error, 'App')}>
      <GestureHandlerRootView style={{ flex: 1 }}>
        <SafeAreaProvider>
          <QueryClientProvider client={queryClient}>
            <RootNavigator />
            <StatusBar style="dark" />
          </QueryClientProvider>
        </SafeAreaProvider>
      </GestureHandlerRootView>
    </ErrorBoundary>
  );
}
