import axios, { AxiosError, AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios';
import { API_BASE_URL, API_TIMEOUT } from '@/constants/api';
import { getAccessToken, getRefreshToken, setAccessToken, removeTokens } from '@/utils/storage';
import { addSentryBreadcrumb } from '@/config/sentry';

/**
 * Axios Instance
 */
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: API_TIMEOUT,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * Request Interceptor: JWT 토큰 자동 추가 + Sentry 브레드크럼
 */
apiClient.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    const token = await getAccessToken();
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // Sentry 브레드크럼: API 요청 시작
    addSentryBreadcrumb(
      `API Request: ${config.method?.toUpperCase()} ${config.url}`,
      'http',
      'info',
      {
        method: config.method,
        url: config.url,
        baseURL: config.baseURL,
      }
    );

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

/**
 * Response Interceptor: Refresh Token 자동 갱신 + Sentry 브레드크럼
 */
apiClient.interceptors.response.use(
  (response) => {
    // Sentry 브레드크럼: API 응답 성공
    addSentryBreadcrumb(
      `API Response: ${response.config.method?.toUpperCase()} ${response.config.url} - ${response.status}`,
      'http',
      'info',
      {
        status: response.status,
        statusText: response.statusText,
      }
    );

    return response;
  },
  async (error: AxiosError) => {
    const originalRequest = error.config as AxiosRequestConfig & { _retry?: boolean };

    // Sentry 브레드크럼: API 응답 에러
    if (error.response) {
      addSentryBreadcrumb(
        `API Error: ${error.config?.method?.toUpperCase()} ${error.config?.url} - ${error.response.status}`,
        'http',
        'error',
        {
          status: error.response.status,
          statusText: error.response.statusText,
          data: error.response.data,
        }
      );
    } else {
      // 네트워크 에러
      addSentryBreadcrumb(
        `Network Error: ${error.config?.method?.toUpperCase()} ${error.config?.url}`,
        'http',
        'error',
        {
          message: error.message,
        }
      );
    }

    // 401 Unauthorized && 재시도 안 함
    if (error.response?.status === 401 && !originalRequest._retry) {
      // 인증이 필요 없는 엔드포인트는 refresh 재시도 스킵
      const publicEndpoints = [
        '/auth/email/signup',
        '/auth/email/signin',
        '/auth/email/verify-code',
        '/auth/email/resend-code',
        '/auth/password/reset/request',
        '/auth/password/reset/verify-code',
        '/auth/password/reset/confirm',
      ];

      const requestUrl = originalRequest.url || '';
      const isPublicEndpoint = publicEndpoints.some(endpoint => requestUrl.includes(endpoint));

      // Public 엔드포인트는 401을 그대로 반환 (잘못된 비밀번호 등)
      if (isPublicEndpoint) {
        return Promise.reject(error);
      }

      originalRequest._retry = true;

      try {
        const refreshToken = await getRefreshToken();
        if (!refreshToken) {
          throw new Error('No refresh token');
        }

        // Refresh Token API 호출
        const response = await axios.post(
          `${API_BASE_URL}/auth/refresh`,
          { refreshToken },
          { timeout: API_TIMEOUT }
        );

        const newAccessToken = response.data.accessToken;
        await setAccessToken(newAccessToken);

        // Sentry 브레드크럼: 토큰 갱신 성공
        addSentryBreadcrumb(
          'Token refreshed successfully',
          'auth',
          'info'
        );

        // 원래 요청 재시도
        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        }
        return apiClient(originalRequest);
      } catch (refreshError) {
        // Refresh Token 만료 → 로그아웃 처리
        await removeTokens();

        // Sentry 브레드크럼: 토큰 갱신 실패
        addSentryBreadcrumb(
          'Token refresh failed - user will be logged out',
          'auth',
          'warning'
        );

        // TODO: Navigate to login screen
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

export default apiClient;
