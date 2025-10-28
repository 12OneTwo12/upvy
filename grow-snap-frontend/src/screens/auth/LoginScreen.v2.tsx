import React, { useEffect } from 'react';
import {
  View,
  Text,
  SafeAreaView,
  ScrollView,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useGoogleAuth } from '@/hooks/useGoogleAuth';
import { useAuthStore } from '@/stores/authStore';
import { Button } from '@/components/common';
import { theme } from '@/theme';
import { showErrorAlert } from '@/utils/errorHandler';
import { responsive, isSmallDevice } from '@/utils/responsive';
import { createStyleSheet } from '@/utils/styles';

/**
 * 로그인 화면 (반응형)
 */
export default function LoginScreen() {
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const { handleGoogleLogin, isLoading, error, isReady } = useGoogleAuth();
  // const { checkAuth } = useAuthStore(); // MVP: Auto-login disabled

  // MVP: Auto-login disabled for now
  // useEffect(() => {
  //   checkAuth();
  // }, []);

  // 에러 처리
  useEffect(() => {
    if (error) {
      showErrorAlert(error, '로그인 실패');
    }
  }, [error]);

  return (
    <SafeAreaView style={styles.safeArea}>
      <ScrollView
        contentContainerStyle={[
          styles.container,
          {
            paddingTop: Math.max(insets.top, theme.spacing[4]),
            paddingBottom: Math.max(insets.bottom, theme.spacing[4]),
          },
        ]}
        showsVerticalScrollIndicator={false}
      >
        {/* 상단: 로고 및 소개 */}
        <View style={styles.content}>
          <View style={styles.logoSection}>
            {/* 로고 */}
            <View style={styles.logoContainer}>
              <Text style={styles.logoText}>G</Text>
            </View>

            <Text style={styles.title}>GrowSnap</Text>
            <Text style={styles.subtitle}>스크롤 시간을 성장 시간으로</Text>
          </View>

          {/* 서비스 특징 */}
          <View style={styles.featuresContainer}>
            <FeatureItem icon="📚" text="매일 새로운 인사이트" />
            <FeatureItem icon="🎯" text="나만의 성장 여정" />
            <FeatureItem icon="✨" text="재미있는 학습 경험" />
          </View>
        </View>

        {/* 하단: 로그인 버튼 */}
        <View style={styles.bottomSection}>
          <Button
            variant="outline"
            size={responsive({ xs: 'md', md: 'lg', default: 'md' })}
            fullWidth
            onPress={handleGoogleLogin}
            disabled={!isReady}
            loading={isLoading}
          >
            Google로 시작하기
          </Button>

          {/* 약관 동의 */}
          <Text style={styles.termsText}>
            계속 진행하면{' '}
            <Text style={styles.termsLink}>이용약관</Text> 및{' '}
            <Text style={styles.termsLink}>개인정보처리방침</Text>에 동의하는
            것으로 간주됩니다.
          </Text>

          {/* 개발 모드 표시 */}
          {__DEV__ && (
            <View style={styles.devNotice}>
              <Text style={styles.devNoticeText}>
                ⚠️ 개발 모드: Google OAuth 설정이 필요합니다
              </Text>
            </View>
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

/**
 * 특징 아이템 컴포넌트
 */
interface FeatureItemProps {
  icon: string;
  text: string;
}

function FeatureItem({ icon, text }: FeatureItemProps) {
  return (
    <View style={styles.featureItem}>
      <Text style={styles.featureIcon}>{icon}</Text>
      <Text style={styles.featureText}>{text}</Text>
    </View>
  );
}

const useStyles = createStyleSheet({
  safeArea: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },

  container: {
    flexGrow: 1,
    paddingHorizontal: theme.layout.containerPadding,
    paddingVertical: theme.spacing[8],
    justifyContent: 'space-between',
  },

  content: {
    flex: 1,
    justifyContent: 'center',
  },

  // Logo Section
  logoSection: {
    alignItems: 'center',
    marginBottom: theme.spacing[12],
  },

  logoContainer: {
    width: responsive({ xs: 72, md: 80, default: 72 }),
    height: responsive({ xs: 72, md: 80, default: 72 }),
    backgroundColor: theme.colors.primary[500],
    borderRadius: theme.borderRadius.xl,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: theme.spacing[6],
    ...theme.shadows.md,
  },

  logoText: {
    fontSize: responsive({ xs: 36, md: 40, default: 36 }),
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.inverse,
  },

  title: {
    fontSize: responsive({
      xs: theme.typography.fontSize['3xl'],
      md: theme.typography.fontSize['4xl'],
      default: theme.typography.fontSize['3xl'],
    }),
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[3],
  },

  subtitle: {
    fontSize: theme.typography.fontSize.lg,
    color: theme.colors.text.secondary,
    textAlign: 'center',
    paddingHorizontal: theme.spacing[8],
  },

  // Features
  featuresContainer: {
    marginTop: theme.spacing[8],
    gap: theme.spacing[3],
  },

  featureItem: {
    flexDirection: 'row',
    alignItems: 'center',
  },

  featureIcon: {
    fontSize: isSmallDevice() ? 20 : 24,
    marginRight: theme.spacing[3],
  },

  featureText: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.secondary,
  },

  // Bottom Section
  bottomSection: {
    gap: theme.spacing[4],
  },

  termsText: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
    textAlign: 'center',
    lineHeight: theme.typography.lineHeight.relaxed * theme.typography.fontSize.xs,
  },

  termsLink: {
    color: theme.colors.primary[600],
    textDecorationLine: 'underline',
  },

  // Dev Notice
  devNotice: {
    padding: theme.spacing[3],
    backgroundColor: '#fef3c7',
    borderRadius: theme.borderRadius.base,
  },

  devNoticeText: {
    fontSize: theme.typography.fontSize.xs,
    color: '#92400e',
    textAlign: 'center',
  },
});
