/**
 * Sentry 테스트 화면
 *
 * 개발 환경에서만 접근 가능
 * Sentry 에러 전송을 테스트하기 위한 화면
 */

import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView, Alert } from 'react-native';
import { captureException, captureMessage, addSentryBreadcrumb, setSentryTag, setSentryContext } from '@/config/sentry';
import { theme } from '@/theme';

export const SentryTestScreen: React.FC = () => {
  // 1. JavaScript 에러 발생
  const testJavaScriptError = () => {
    try {
      throw new Error('Test JavaScript Error - This is intentional for Sentry testing');
    } catch (error) {
      captureException(error as Error, {
        test: {
          type: 'javascript_error',
          timestamp: new Date().toISOString(),
        },
      });
      Alert.alert('Error Sent', 'JavaScript error has been sent to Sentry');
    }
  };

  // 2. Uncaught Error (앱 크래시)
  const testUncaughtError = () => {
    Alert.alert(
      'Warning',
      'This will crash the app. The error will be sent to Sentry and the app will close.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Crash App',
          style: 'destructive',
          onPress: () => {
            throw new Error('Test Uncaught Error - App will crash');
          },
        },
      ]
    );
  };

  // 3. 네트워크 에러 시뮬레이션
  const testNetworkError = () => {
    const error = new Error('Network request failed');
    (error as any).response = {
      status: 500,
      statusText: 'Internal Server Error',
      data: { message: 'Test network error' },
    };

    captureException(error, {
      network: {
        url: 'https://api.upvy.org/test',
        method: 'GET',
        status: 500,
      },
    });

    Alert.alert('Error Sent', 'Network error has been sent to Sentry');
  };

  // 4. 커스텀 메시지 전송
  const testCustomMessage = () => {
    captureMessage('Test custom message from Sentry Test Screen', 'info');
    Alert.alert('Message Sent', 'Custom message has been sent to Sentry');
  };

  // 5. 브레드크럼 테스트
  const testBreadcrumbs = () => {
    addSentryBreadcrumb('User navigated to Sentry Test Screen', 'navigation', 'info');
    addSentryBreadcrumb('User clicked test button', 'user', 'info', {
      button: 'breadcrumbs_test',
    });
    addSentryBreadcrumb('API request started', 'http', 'info', {
      url: 'https://api.upvy.org/test',
      method: 'GET',
    });

    Alert.alert('Breadcrumbs Added', '3 breadcrumbs have been added. Trigger an error to see them in Sentry.');
  };

  // 6. 태그 및 컨텍스트 테스트
  const testTagsAndContext = () => {
    setSentryTag('test_type', 'manual');
    setSentryTag('screen', 'sentry_test');
    setSentryContext('test_info', {
      device: 'simulator',
      test_time: new Date().toISOString(),
      feature: 'sentry_integration',
    });

    captureMessage('Test message with custom tags and context', 'info');
    Alert.alert('Tags & Context Set', 'Custom tags and context have been set and sent to Sentry');
  };

  // 7. 다양한 심각도 레벨 테스트
  const testSeverityLevels = () => {
    captureMessage('Debug level message', 'debug');
    captureMessage('Info level message', 'info');
    captureMessage('Warning level message', 'warning');
    captureMessage('Error level message', 'error');
    captureMessage('Fatal level message', 'fatal');

    Alert.alert('Severity Levels Sent', '5 messages with different severity levels have been sent to Sentry');
  };

  return (
    <ScrollView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Sentry Test Screen</Text>
        <Text style={styles.subtitle}>개발 환경 전용 - 에러 모니터링 테스트</Text>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>기본 에러 테스트</Text>

        <TouchableOpacity style={styles.button} onPress={testJavaScriptError}>
          <Text style={styles.buttonText}>1. JavaScript 에러 (Caught)</Text>
          <Text style={styles.buttonSubtext}>try-catch로 잡힌 에러</Text>
        </TouchableOpacity>

        <TouchableOpacity style={[styles.button, styles.dangerButton]} onPress={testUncaughtError}>
          <Text style={[styles.buttonText, styles.dangerText]}>2. Uncaught 에러 (Crash)</Text>
          <Text style={[styles.buttonSubtext, styles.dangerText]}>앱이 크래시됩니다</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.button} onPress={testNetworkError}>
          <Text style={styles.buttonText}>3. 네트워크 에러</Text>
          <Text style={styles.buttonSubtext}>API 요청 실패 시뮬레이션</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>고급 기능 테스트</Text>

        <TouchableOpacity style={styles.button} onPress={testCustomMessage}>
          <Text style={styles.buttonText}>4. 커스텀 메시지</Text>
          <Text style={styles.buttonSubtext}>에러가 아닌 정보성 메시지</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.button} onPress={testBreadcrumbs}>
          <Text style={styles.buttonText}>5. 브레드크럼</Text>
          <Text style={styles.buttonSubtext}>사용자 행동 추적</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.button} onPress={testTagsAndContext}>
          <Text style={styles.buttonText}>6. 태그 & 컨텍스트</Text>
          <Text style={styles.buttonSubtext}>커스텀 메타데이터</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.button} onPress={testSeverityLevels}>
          <Text style={styles.buttonText}>7. 심각도 레벨</Text>
          <Text style={styles.buttonSubtext}>Debug, Info, Warning, Error, Fatal</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.info}>
        <Text style={styles.infoTitle}>📋 테스트 방법</Text>
        <Text style={styles.infoText}>
          1. 각 버튼을 눌러 에러/메시지 전송{'\n'}
          2. Sentry 대시보드 확인{'\n'}
          3. 에러 상세 정보, 브레드크럼, 태그 확인{'\n'}
          {'\n'}
          🔗 Sentry Dashboard:{'\n'}
          https://sentry.io/organizations/upvy/issues/
        </Text>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },
  header: {
    padding: theme.spacing[4],
    backgroundColor: theme.colors.background.secondary,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  title: {
    fontSize: theme.typography.fontSize['2xl'],
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[1],
  },
  subtitle: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
  },
  section: {
    padding: theme.spacing[4],
  },
  sectionTitle: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[3],
  },
  button: {
    backgroundColor: theme.colors.primary,
    padding: theme.spacing[4],
    borderRadius: theme.borderRadius.md,
    marginBottom: theme.spacing[3],
  },
  dangerButton: {
    backgroundColor: theme.colors.error,
  },
  buttonText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: '#FFFFFF',
    marginBottom: theme.spacing[1],
  },
  buttonSubtext: {
    fontSize: theme.typography.fontSize.sm,
    color: 'rgba(255, 255, 255, 0.8)',
  },
  dangerText: {
    color: '#FFFFFF',
  },
  info: {
    margin: theme.spacing[4],
    padding: theme.spacing[4],
    backgroundColor: theme.colors.background.secondary,
    borderRadius: theme.borderRadius.md,
    borderWidth: 1,
    borderColor: theme.colors.border.light,
  },
  infoTitle: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[2],
  },
  infoText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    lineHeight: theme.typography.fontSize.sm * 1.6,
  },
});
