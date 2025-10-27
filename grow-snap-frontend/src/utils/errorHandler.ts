import { AxiosError } from 'axios';
import { Alert } from 'react-native';

/**
 * API 에러 응답 타입
 */
export interface ApiErrorResponse {
  message: string;
  code?: string;
  details?: unknown;
}

/**
 * 사용자 친화적인 에러 메시지
 */
const ERROR_MESSAGES: Record<string, string> = {
  // Network Errors
  NETWORK_ERROR: '네트워크 연결을 확인해주세요.',
  TIMEOUT_ERROR: '요청 시간이 초과되었습니다. 다시 시도해주세요.',

  // Auth Errors
  UNAUTHORIZED: '인증이 만료되었습니다. 다시 로그인해주세요.',
  FORBIDDEN: '접근 권한이 없습니다.',

  // Validation Errors
  VALIDATION_ERROR: '입력 정보를 확인해주세요.',
  DUPLICATE_NICKNAME: '이미 사용 중인 닉네임입니다.',

  // Server Errors
  SERVER_ERROR: '서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.',
  NOT_FOUND: '요청하신 정보를 찾을 수 없습니다.',

  // Default
  UNKNOWN_ERROR: '알 수 없는 오류가 발생했습니다.',
};

/**
 * 에러에서 사용자 친화적인 메시지 추출
 */
export const getErrorMessage = (error: unknown): string => {
  if (!error) {
    return ERROR_MESSAGES.UNKNOWN_ERROR;
  }

  // Axios Error
  if (error instanceof AxiosError) {
    // Network Error
    if (!error.response) {
      if (error.code === 'ECONNABORTED') {
        return ERROR_MESSAGES.TIMEOUT_ERROR;
      }
      return ERROR_MESSAGES.NETWORK_ERROR;
    }

    // HTTP Status Code
    const status = error.response.status;
    const data = error.response.data as ApiErrorResponse | undefined;

    switch (status) {
      case 400:
        return data?.message || ERROR_MESSAGES.VALIDATION_ERROR;
      case 401:
        return ERROR_MESSAGES.UNAUTHORIZED;
      case 403:
        return ERROR_MESSAGES.FORBIDDEN;
      case 404:
        return data?.message || ERROR_MESSAGES.NOT_FOUND;
      case 409:
        return data?.message || ERROR_MESSAGES.DUPLICATE_NICKNAME;
      case 500:
      case 502:
      case 503:
      case 504:
        return ERROR_MESSAGES.SERVER_ERROR;
      default:
        return data?.message || ERROR_MESSAGES.UNKNOWN_ERROR;
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

  return ERROR_MESSAGES.UNKNOWN_ERROR;
};

/**
 * 에러 알림 표시
 */
export const showErrorAlert = (error: unknown, title = '오류'): void => {
  const message = getErrorMessage(error);
  Alert.alert(title, message, [{ text: '확인' }]);
};

/**
 * 에러 로깅
 */
export const logError = (error: unknown, context?: string): void => {
  const message = getErrorMessage(error);
  const timestamp = new Date().toISOString();

  console.error(`[${timestamp}] ${context ? `[${context}] ` : ''}${message}`, error);

  // TODO: 프로덕션 환경에서는 에러 모니터링 서비스로 전송
  // (예: Sentry, Firebase Crashlytics 등)
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
