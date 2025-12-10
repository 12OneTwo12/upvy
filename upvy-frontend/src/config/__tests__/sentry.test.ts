/**
 * Sentry 설정 테스트
 */

import { initializeSentry, setSentryUser, clearSentryUser, captureException } from '../sentry';

describe('Sentry Configuration', () => {
  it('should export all required functions', () => {
    expect(initializeSentry).toBeDefined();
    expect(setSentryUser).toBeDefined();
    expect(clearSentryUser).toBeDefined();
    expect(captureException).toBeDefined();
  });

  it('should initialize Sentry without errors', () => {
    expect(() => initializeSentry()).not.toThrow();
  });

  it('should set user context without errors', () => {
    expect(() => setSentryUser({
      id: 'test-user-id',
      email: 'test@example.com',
      username: 'testuser',
    })).not.toThrow();
  });

  it('should clear user context without errors', () => {
    expect(() => clearSentryUser()).not.toThrow();
  });

  it('should capture exception without errors', () => {
    const testError = new Error('Test error');
    expect(() => captureException(testError)).not.toThrow();
  });
});
