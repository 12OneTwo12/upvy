import { AxiosError } from 'axios';
import { Alert } from 'react-native';
import { captureException, setSentryContext, addSentryBreadcrumb } from '@/config/sentry';
import i18n from '@/locales';

/**
 * API 에러 응답 타입
 */
export interface ApiErrorResponse {
  message: string;
  code?: string;
  details?: unknown;
}

/**
 * 백엔드 에러 코드를 i18n 키로 매핑
 *
 * 백엔드는 에러 코드를 SCREAMING_SNAKE_CASE로 반환하고,
 * 프론트엔드는 이를 camelCase i18n 키로 변환하여 다국어 메시지를 표시합니다.
 */
const ERROR_CODE_TO_I18N_KEY: Record<string, string> = {
  // Auth Errors (백엔드 AuthException)
  'INVALID_CREDENTIALS': 'errors:auth.invalidCredentials',
  'EMAIL_NOT_VERIFIED': 'errors:auth.emailNotVerified',
  'EMAIL_ALREADY_EXISTS': 'errors:auth.emailAlreadyExists',
  'INVALID_VERIFICATION_TOKEN': 'errors:auth.invalidVerificationToken',
  'TOKEN_EXPIRED': 'errors:auth.tokenExpired',
  'TOO_MANY_REQUESTS': 'errors:auth.tooManyRequests',
  'OAUTH_ONLY_USER': 'errors:auth.oauthOnlyUser',

  // Validation Errors
  'VALIDATION_ERROR': 'errors:validation.validationError',

  // Block Errors
  'SELF_BLOCK_NOT_ALLOWED': 'errors:block.selfBlockNotAllowed',
  'DUPLICATE_USER_BLOCK': 'errors:block.duplicateUserBlock',
  'DUPLICATE_CONTENT_BLOCK': 'errors:block.duplicateContentBlock',
  'USER_BLOCK_NOT_FOUND': 'errors:block.userBlockNotFound',
  'CONTENT_BLOCK_NOT_FOUND': 'errors:block.contentBlockNotFound',
};

/**
 * 에러에서 사용자 친화적인 메시지 추출
 *
 * 다국어 지원을 위해 i18n을 사용합니다.
 * 백엔드 에러 코드를 i18n 키로 매핑하여 현재 언어에 맞는 메시지를 반환합니다.
 */
export const getErrorMessage = (error: unknown): string => {
  if (!error) {
    return i18n.t('errors:general.unknown');
  }

  // Axios Error
  if (error instanceof AxiosError) {
    // Network Error
    if (!error.response) {
      if (error.code === 'ECONNABORTED') {
        return i18n.t('errors:network.timeout');
      }
      return i18n.t('errors:network.message');
    }

    // HTTP Status Code
    const status = error.response.status;
    const data = error.response.data as ApiErrorResponse | undefined;
    const errorCode = data?.code;

    // 1순위: 백엔드 에러 코드를 i18n 키로 변환
    if (errorCode && ERROR_CODE_TO_I18N_KEY[errorCode]) {
      return i18n.t(ERROR_CODE_TO_I18N_KEY[errorCode]);
    }

    // 2순위: HTTP Status Code 기반 다국어 메시지
    switch (status) {
      case 400:
        return i18n.t('errors:validation.validationError');
      case 401:
        return i18n.t('errors:auth.sessionExpired');
      case 403:
        return i18n.t('errors:auth.unauthorized');
      case 404:
        return i18n.t('errors:server.notFound');
      case 409:
        // 409는 보통 중복 에러이지만, 구체적인 에러 코드가 없으면 일반 메시지 표시
        return i18n.t('errors:validation.validationError');
      case 429:
        return i18n.t('errors:auth.tooManyRequests');
      case 500:
      case 502:
      case 503:
      case 504:
        return i18n.t('errors:server.error');
      default:
        return i18n.t('errors:general.unknown');
    }
  }

  // Error Object
  if (error instanceof Error) {
    return error.message;
  }

  // String
  if (typeof error === 'string') {
    return error;
  }

  return i18n.t('errors:general.unknown');
};

/**
 * 에러 알림 표시
 *
 * 다국어 지원을 위해 i18n을 사용합니다.
 */
export const showErrorAlert = (error: unknown, title?: string): void => {
  const message = getErrorMessage(error);
  const alertTitle = title || i18n.t('errors:general.title');
  const confirmText = i18n.t('common:button.confirm');
  Alert.alert(alertTitle, message, [{ text: confirmText }]);
};

/**
 * 에러 로깅 (Sentry 통합)
 *
 * 개발 환경: 콘솔 출력
 * 프로덕션 환경: Sentry로 전송
 */
export const logError = (error: unknown, context?: string): void => {
  const message = getErrorMessage(error);
  const timestamp = new Date().toISOString();

  // 개발 환경에서는 콘솔 출력
  if (__DEV__) {
    console.error(`[${timestamp}] ${context ? `[${context}] ` : ''}${message}`, error);
  }

  // Sentry로 에러 전송
  if (error instanceof Error) {
    // 에러 컨텍스트 설정
    const errorContext: Record<string, any> = {
      timestamp,
      message,
    };

    if (context) {
      errorContext.context = context;
    }

    // Axios 에러인 경우 추가 정보
    if (isAxiosError(error)) {
      errorContext.axios = {
        method: error.config?.method,
        url: error.config?.url,
        status: error.response?.status,
        statusText: error.response?.statusText,
        data: error.response?.data,
      };

      // 네트워크 에러 태그
      if (isNetworkError(error)) {
        setSentryContext('error_type', { type: 'network' });
      }

      // 인증 에러 태그
      if (isAuthError(error)) {
        setSentryContext('error_type', { type: 'auth' });
      }

      // 유효성 검사 에러 태그
      if (isValidationError(error)) {
        setSentryContext('error_type', { type: 'validation' });
      }
    }

    // Sentry에 에러 전송
    captureException(error, errorContext);
  } else {
    // Error 객체가 아닌 경우 브레드크럼만 추가
    addSentryBreadcrumb(
      `Non-error thrown: ${message}`,
      context || 'error',
      'error',
      { error }
    );
  }
};

/**
 * Try-Catch 래퍼 with 에러 핸들링
 */
export const withErrorHandling = async <T>(
  fn: () => Promise<T>,
  options?: {
    showAlert?: boolean;
    alertTitle?: string;
    logContext?: string;
    onError?: (error: unknown) => void;
  }
): Promise<T | null> => {
  try {
    return await fn();
  } catch (error) {
    // 로깅
    if (options?.logContext) {
      logError(error, options.logContext);
    }

    // 알림 표시
    if (options?.showAlert) {
      showErrorAlert(error, options.alertTitle);
    }

    // 커스텀 에러 핸들러
    options?.onError?.(error);

    return null;
  }
};

/**
 * 에러 타입 체크
 */
export const isAxiosError = (error: unknown): error is AxiosError => {
  return error instanceof AxiosError;
};

export const isNetworkError = (error: unknown): boolean => {
  if (!isAxiosError(error)) return false;
  return !error.response;
};

export const isAuthError = (error: unknown): boolean => {
  if (!isAxiosError(error)) return false;
  return error.response?.status === 401 || error.response?.status === 403;
};

export const isValidationError = (error: unknown): boolean => {
  if (!isAxiosError(error)) return false;
  return error.response?.status === 400;
};
