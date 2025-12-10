/**
 * Sentry 설정 파일
 *
 * 환경별 설정 및 베스트 프랙티스 적용
 */

import * as Sentry from '@sentry/react-native';
import Constants from 'expo-constants';
import { Platform } from 'react-native';

/**
 * Sentry 초기화
 *
 * 베스트 프랙티스:
 * - 환경 변수로 DSN 관리
 * - 개발 환경에서는 비활성화 또는 별도 프로젝트
 * - Release 및 Environment 정보 설정
 * - 민감 정보 필터링
 * - 성능 모니터링 설정
 */
export const initializeSentry = () => {
  // 환경 변수에서 Sentry 설정 가져오기
  // EAS Build에서는 app.config.js를 통해 Constants.expoConfig.extra로 전달됨
  const sentryDsn =
    Constants.expoConfig?.extra?.sentryDsn ||
    'https://2a06b60a8ad086d85995ad82208c530c@o4510507886313472.ingest.us.sentry.io/4510507889065984';

  const sentryEnabled = Constants.expoConfig?.extra?.sentryEnabled !== false; // 기본값: true

  const environment =
    Constants.expoConfig?.extra?.sentryEnvironment ||
    (__DEV__ ? 'development' : 'production');

  // 개발 환경에서 Sentry를 비활성화하려면 환경 변수 설정
  if (!sentryEnabled) {
    console.log('[Sentry] Disabled by environment variable');
    return;
  }

  // Sentry 초기화
  Sentry.init({
    dsn: sentryDsn,

    // Environment 설정 (development, staging, production)
    environment,

    // Release 정보 (앱 버전 + OTA 버전)
    release: `upvy@${Constants.expoConfig?.version || '1.0.0'}+${Constants.expoConfig?.extra?.otaVersion || '1.0.0'}`,

    // Distribution (플랫폼별 빌드 번호)
    dist: Platform.OS === 'ios'
      ? Constants.expoConfig?.ios?.buildNumber
      : Constants.expoConfig?.android?.versionCode?.toString(),

    // 성능 모니터링 (프로덕션에서는 10%, 개발에서는 100%)
    tracesSampleRate: __DEV__ ? 1.0 : 0.1,

    // Session Replay (에러 발생 시 100%, 정상 세션은 10%)
    replaysSessionSampleRate: __DEV__ ? 0.5 : 0.1,
    replaysOnErrorSampleRate: 1.0,

    // 통합 기능
    integrations: [
      Sentry.mobileReplayIntegration({
        maskAllText: true, // 민감 정보 마스킹
        maskAllImages: true,
      }),
      Sentry.feedbackIntegration(),
    ],

    // 개발 환경에서 Spotlight 활성화 (디버깅 도구)
    spotlight: __DEV__,

    // PII (개인 식별 정보) 전송 비활성화 (GDPR 준수)
    sendDefaultPii: false,

    // 로그 활성화
    enableLogs: true,

    // 에러 전송 전 필터링 (민감 정보 제거)
    beforeSend(event, hint) {
      // 개발 환경에서는 콘솔에도 출력
      if (__DEV__) {
        console.log('[Sentry] Capturing event:', event);
      }

      // 민감한 정보가 포함된 키 필터링
      const sensitiveKeys = [
        'password',
        'token',
        'accessToken',
        'refreshToken',
        'authorization',
        'cookie',
        'session',
        'secret',
        'apiKey',
        'creditCard',
      ];

      // Event의 모든 데이터에서 민감 정보 제거
      if (event.request?.headers) {
        sensitiveKeys.forEach(key => {
          if (event.request?.headers?.[key]) {
            event.request.headers[key] = '[Filtered]';
          }
        });
      }

      // Extra 데이터에서 민감 정보 제거
      if (event.extra) {
        Object.keys(event.extra).forEach(key => {
          if (sensitiveKeys.some(sensitive => key.toLowerCase().includes(sensitive))) {
            event.extra![key] = '[Filtered]';
          }
        });
      }

      return event;
    },

    // 브레드크럼 필터링
    beforeBreadcrumb(breadcrumb, hint) {
      // HTTP 요청 브레드크럼에서 민감 정보 제거
      if (breadcrumb.category === 'http') {
        // Authorization 헤더 제거
        if (breadcrumb.data?.headers?.Authorization) {
          breadcrumb.data.headers.Authorization = '[Filtered]';
        }
      }

      return breadcrumb;
    },

    // 특정 에러 무시 (앱 정상 동작에 영향 없는 에러)
    ignoreErrors: [
      // 네트워크 에러 (사용자 네트워크 문제)
      'Network request failed',
      'Network Error',
      'NetworkError',

      // 취소된 요청
      'AbortError',
      'Request aborted',

      // 타임아웃
      'Timeout',
      'Request timed out',

      // 권한 관련 (사용자가 거부한 경우)
      'User denied',
      'Permission denied',

      // ResizeObserver (무해한 브라우저 에러)
      'ResizeObserver loop limit exceeded',
    ],
  });

  // 개발 환경 로그
  if (__DEV__) {
    console.log('[Sentry] Initialized with:', {
      environment,
      release: `upvy@${Constants.expoConfig?.version || '1.0.0'}`,
      enabled: sentryEnabled,
    });
  }
};

/**
 * 사용자 컨텍스트 설정
 *
 * 로그인 시 호출하여 사용자 정보를 Sentry에 설정
 */
export const setSentryUser = (user: {
  id: string;
  email?: string;
  username?: string;
}) => {
  Sentry.setUser({
    id: user.id,
    email: user.email,
    username: user.username,
  });

  if (__DEV__) {
    console.log('[Sentry] User context set:', user.id);
  }
};

/**
 * 사용자 컨텍스트 제거
 *
 * 로그아웃 시 호출
 */
export const clearSentryUser = () => {
  Sentry.setUser(null);

  if (__DEV__) {
    console.log('[Sentry] User context cleared');
  }
};

/**
 * 커스텀 태그 설정
 *
 * 에러 그룹핑 및 필터링에 유용
 */
export const setSentryTag = (key: string, value: string) => {
  Sentry.setTag(key, value);
};

/**
 * 커스텀 컨텍스트 설정
 *
 * 추가 디버깅 정보 제공
 */
export const setSentryContext = (name: string, context: Record<string, any>) => {
  Sentry.setContext(name, context);
};

/**
 * 브레드크럼 추가
 *
 * 사용자 행동 추적 (에러 발생 전 흐름 파악)
 */
export const addSentryBreadcrumb = (message: string, category?: string, level?: Sentry.SeverityLevel, data?: Record<string, any>) => {
  Sentry.addBreadcrumb({
    message,
    category: category || 'custom',
    level: level || 'info',
    data,
  });
};

/**
 * 에러 수동 캡처
 *
 * try-catch에서 잡은 에러를 Sentry에 전송
 */
export const captureException = (error: Error, context?: Record<string, any>) => {
  if (context) {
    Sentry.withScope((scope) => {
      Object.entries(context).forEach(([key, value]) => {
        scope.setContext(key, value);
      });
      Sentry.captureException(error);
    });
  } else {
    Sentry.captureException(error);
  }
};

/**
 * 메시지 캡처
 *
 * 에러가 아닌 중요 이벤트 기록
 */
export const captureMessage = (message: string, level?: Sentry.SeverityLevel) => {
  Sentry.captureMessage(message, level || 'info');
};

// Sentry 인스턴스 export
export { Sentry };
