import React, { useEffect } from 'react';
import {
  View,
  Text,
  SafeAreaView,
  StyleSheet,
  ScrollView,
  Dimensions,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useGoogleAuth } from '@/hooks/useGoogleAuth';
import { useAuthStore } from '@/stores/authStore';
import { Button } from '@/components/common';
import { theme } from '@/theme';
import { showErrorAlert } from '@/utils/errorHandler';
import { responsive, isSmallDevice } from '@/utils/responsive';

const { width } = Dimensions.get('window');

/**
 * 로그인 화면 (인스타그램 스타일)
 * 깔끔하고 미니멀한 디자인으로 전문적인 느낌
 */
export default function LoginScreen() {
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
            paddingTop: Math.max(insets.top, theme.spacing[8]),
            paddingBottom: Math.max(insets.bottom, theme.spacing[6]),
          },
        ]}
        showsVerticalScrollIndicator={false}
      >
        {/* 중앙 컨텐츠 */}
        <View style={styles.content}>
          {/* 로고 */}
          <View style={styles.logoSection}>
            <View style={styles.logoContainer}>
              <Text style={styles.logoEmoji}>🌱</Text>
            </View>
            <Text style={styles.title}>GrowSnap</Text>
            <Text style={styles.tagline}>성장을 위한 첫 걸음</Text>
          </View>

          {/* 가치 제안 */}
          <View style={styles.valuePropsContainer}>
            <ValueProp
              title="매일 성장하는 습관"
              description="짧지만 깊이 있는 콘텐츠로 매일 배우는 즐거움"
            />
            <ValueProp
              title="나만의 학습 여정"
              description="관심사에 맞춘 개인화된 추천"
            />
            <ValueProp
              title="전문가의 인사이트"
              description="검증된 크리에이터의 양질의 콘텐츠"
            />
          </View>
        </View>

        {/* 하단: 로그인 버튼 */}
        <View style={styles.bottomSection}>
          <Button
            variant="primary"
            size="lg"
            fullWidth
            onPress={handleGoogleLogin}
            disabled={!isReady}
            loading={isLoading}
            style={styles.googleButton}
          >
            Google로 계속하기
          </Button>

          {/* 약관 동의 */}
          <Text style={styles.termsText}>
            계속 진행하시면{' '}
            <Text style={styles.termsLink}>서비스 약관</Text> 및{' '}
            <Text style={styles.termsLink}>개인정보 보호정책</Text>에
            동의하시는 것으로 간주됩니다.
          </Text>

          {/* 개발 모드 표시 */}
          {__DEV__ && (
            <View style={styles.devNotice}>
              <Text style={styles.devNoticeText}>
                개발 모드 • Google OAuth 설정 필요
              </Text>
            </View>
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

/**
 * 가치 제안 컴포넌트
 */
interface ValuePropProps {
  title: string;
  description: string;
}

function ValueProp({ title, description }: ValuePropProps) {
  return (
    <View style={styles.valueProp}>
      <View style={styles.valuePropDot} />
      <View style={styles.valuePropContent}>
        <Text style={styles.valuePropTitle}>{title}</Text>
        <Text style={styles.valuePropDescription}>{description}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },

  container: {
    flexGrow: 1,
    paddingHorizontal: theme.spacing[6],
    justifyContent: 'space-between',
  },

  content: {
    flex: 1,
    justifyContent: 'center',
    paddingTop: theme.spacing[12],
  },

  // Logo Section
  logoSection: {
    alignItems: 'center',
    marginBottom: theme.spacing[12],
  },

  logoContainer: {
    width: responsive({ xs: 96, md: 112, default: 96 }),
    height: responsive({ xs: 96, md: 112, default: 96 }),
    borderRadius: responsive({ xs: 48, md: 56, default: 48 }),
    backgroundColor: theme.colors.background.primary,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: theme.spacing[5],
    borderWidth: 1,
    borderColor: theme.colors.gray[200],
  },

  logoEmoji: {
    fontSize: responsive({ xs: 48, md: 56, default: 48 }),
  },

  title: {
    fontSize: responsive({
      xs: 32,
      md: 36,
      default: 32,
    }),
    fontWeight: '700',
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[2],
    letterSpacing: -0.5,
  },

  tagline: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.secondary,
    textAlign: 'center',
  },

  // Value Props
  valuePropsContainer: {
    gap: theme.spacing[5],
    paddingHorizontal: theme.spacing[2],
  },

  valueProp: {
    flexDirection: 'row',
    alignItems: 'flex-start',
  },

  valuePropDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: theme.colors.primary[500],
    marginTop: 8,
    marginRight: theme.spacing[3],
  },

  valuePropContent: {
    flex: 1,
  },

  valuePropTitle: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[1],
  },

  valuePropDescription: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    lineHeight: theme.typography.lineHeight.relaxed * theme.typography.fontSize.sm,
  },

  // Bottom Section
  bottomSection: {
    gap: theme.spacing[4],
    paddingTop: theme.spacing[8],
  },

  googleButton: {
    ...theme.shadows.sm,
  },

  termsText: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
    textAlign: 'center',
    lineHeight: theme.typography.lineHeight.relaxed * theme.typography.fontSize.xs,
    paddingHorizontal: theme.spacing[2],
  },

  termsLink: {
    color: theme.colors.text.secondary,
    fontWeight: theme.typography.fontWeight.medium,
  },

  // Dev Notice
  devNotice: {
    padding: theme.spacing[3],
    backgroundColor: theme.colors.gray[100],
    borderRadius: theme.borderRadius.base,
    borderWidth: 1,
    borderColor: theme.colors.gray[200],
  },

  devNoticeText: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
    textAlign: 'center',
    fontWeight: theme.typography.fontWeight.medium,
  },
});
