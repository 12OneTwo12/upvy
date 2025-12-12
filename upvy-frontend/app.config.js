/**
 * Expo App Configuration
 *
 * app.json을 app.config.js로 변환하여 동적 환경 변수 지원
 */

module.exports = {
  expo: {
    name: 'Upvy',
    slug: 'upvy',
    version: '1.1.0',
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
        UIViewControllerBasedStatusBarAppearance: true,
        NSPhotoLibraryUsageDescription:
          'Upvy needs access to your photo library to select photos and videos for uploading and sharing.',
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
      [
        './plugins/withInfoPlistStrings',
        {
          ko: {
            NSPhotoLibraryUsageDescription:
              'Upvy는 사진 및 동영상 업로드를 위해 사진 라이브러리 접근 권한이 필요합니다.',
          },
          en: {
            NSPhotoLibraryUsageDescription:
              'Upvy needs access to your photo library to select photos and videos for uploading and sharing.',
          },
          ja: {
            NSPhotoLibraryUsageDescription:
              'Upvyは写真と動画のアップロードのため、フォトライブラリへのアクセス権限が必要です。',
          },
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
      otaVersion: '1.1.0',
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
