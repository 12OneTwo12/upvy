import React, { useEffect } from 'react';
import { StatusBar } from 'expo-status-bar';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import RootNavigator from './src/navigation/RootNavigator';
import { ErrorBoundary } from './src/components/common';
import { logError } from './src/utils/errorHandler';
import { useAuthStore } from './src/stores/authStore';
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
