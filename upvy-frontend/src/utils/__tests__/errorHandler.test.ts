import { AxiosError } from 'axios';
import {
  getErrorMessage,
  isAxiosError,
  isNetworkError,
  isAuthError,
  isValidationError,
} from '../errorHandler';

describe('errorHandler', () => {
  describe('getErrorMessage', () => {
    it('should return default error message for null', () => {
      const result = getErrorMessage(null);
      expect(result).toBe('알 수 없는 오류가 발생했습니다.');
    });

    it('should return message from Error object', () => {
      const error = new Error('Test error');
      const result = getErrorMessage(error);
      expect(result).toBe('Test error');
    });

    it('should return string error as-is', () => {
      const result = getErrorMessage('String error');
      expect(result).toBe('String error');
    });

    it('should handle Axios error with 401 status', () => {
      const axiosError = {
        isAxiosError: true,
        response: {
          status: 401,
          data: { message: 'Unauthorized' },
        },
      } as unknown as AxiosError;

      const result = getErrorMessage(axiosError);
      expect(result).toBe('인증이 만료되었습니다. 다시 로그인해주세요.');
    });

    it('should handle network error', () => {
      const axiosError = {
        isAxiosError: true,
        response: undefined,
        code: 'NETWORK_ERROR',
      } as unknown as AxiosError;

      const result = getErrorMessage(axiosError);
      expect(result).toBe('네트워크 연결을 확인해주세요.');
    });
  });

  describe('isAxiosError', () => {
    it('should return true for AxiosError', () => {
      const error = {
        isAxiosError: true,
      } as AxiosError;

      expect(isAxiosError(error)).toBe(true);
    });

    it('should return false for regular Error', () => {
      const error = new Error('test');
      expect(isAxiosError(error)).toBe(false);
    });
  });

  describe('isNetworkError', () => {
    it('should return true for network error', () => {
      const error = {
        isAxiosError: true,
        response: undefined,
      } as unknown as AxiosError;

      expect(isNetworkError(error)).toBe(true);
    });

    it('should return false when response exists', () => {
      const error = {
        isAxiosError: true,
        response: { status: 400 },
      } as unknown as AxiosError;

      expect(isNetworkError(error)).toBe(false);
    });
  });

  describe('isAuthError', () => {
    it('should return true for 401', () => {
      const error = {
        isAxiosError: true,
        response: { status: 401 },
      } as unknown as AxiosError;

      expect(isAuthError(error)).toBe(true);
    });

    it('should return true for 403', () => {
      const error = {
        isAxiosError: true,
        response: { status: 403 },
      } as unknown as AxiosError;

      expect(isAuthError(error)).toBe(true);
    });

    it('should return false for other status codes', () => {
      const error = {
        isAxiosError: true,
        response: { status: 400 },
      } as unknown as AxiosError;

      expect(isAuthError(error)).toBe(false);
    });
  });

  describe('isValidationError', () => {
    it('should return true for 400', () => {
      const error = {
        isAxiosError: true,
        response: { status: 400 },
      } as unknown as AxiosError;

      expect(isValidationError(error)).toBe(true);
    });

    it('should return false for other status codes', () => {
      const error = {
        isAxiosError: true,
        response: { status: 500 },
      } as unknown as AxiosError;

      expect(isValidationError(error)).toBe(false);
    });
  });
});
