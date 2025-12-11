/**
 * Expo App Configuration
 *
 * app.json을 app.config.js로 변환하여 동적 환경 변수 지원
 */

module.exports = {
  expo: {
    name: 'Upvy',
    slug: 'upvy',
    version: '1.0.0',
    orientation: 'portrait',
    icon: './assets/icon.png',
    userInterfaceStyle: 'light',
    newArchEnabled: true,
    splash: {
      image: './assets/splash-icon.png',
      resizeMode: 'contain',
      backgroundColor: '#ffffff',
    },
    ios: {
      supportsTablet: false,
      bundleIdentifier: 'com.upvy.app',
      infoPlist: {
        ITSAppUsesNonExemptEncryption: false,
        UIViewControllerBasedStatusBarAppearance: false,
      },
    },
    android: {
      adaptiveIcon: {
        foregroundImage: './assets/adaptive-icon.png',
        backgroundColor: '#ffffff',
      },
      package: 'com.upvy.app',
      edgeToEdgeEnabled: true,
      predictiveBackGestureEnabled: false,
    },
    web: {
      favicon: './assets/favicon.png',
    },
    scheme: 'upvy',
    plugins: [
      'expo-web-browser',
      'expo-dev-client',
      'expo-video',
      [
        '@sentry/react-native/expo',
        {
          url: 'https://sentry.io/',
          project: 'upvy-app',
          organization: 'upvy',
        },
      ],
    ],
    updates: {
      url: 'https://u.expo.dev/4eeab4c9-332d-496a-a23d-f63e4726f221',
    },
    runtimeVersion: {
      policy: 'appVersion',
    },
    extra: {
      otaVersion: '1.0.2',
      apiUrl: 'https://api.upvy.org',
      eas: {
        projectId: '4eeab4c9-332d-496a-a23d-f63e4726f221',
      },
      // 환경 변수 주입 (EAS Build에서 자동으로 설정됨)
      sentryEnvironment: process.env.SENTRY_ENVIRONMENT || 'development',
      sentryDsn: process.env.SENTRY_DSN,
      sentryEnabled: process.env.SENTRY_ENABLED !== 'false',
    },
    owner: 'grow-snap',
  },
};
