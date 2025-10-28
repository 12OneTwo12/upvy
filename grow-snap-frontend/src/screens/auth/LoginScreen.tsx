import React, { useEffect } from 'react';
import {
  View,
  Text,
  SafeAreaView,
  StyleSheet,
  ScrollView,
  Dimensions,
  Image,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useGoogleAuth } from '@/hooks/useGoogleAuth';
import { useAuthStore } from '@/stores/authStore';
import { Button } from '@/components/common';
import { theme } from '@/theme';
import { showErrorAlert, logError } from '@/utils/errorHandler';
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
      logError(new Error(error), 'LoginScreen.googleAuth');
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
              <Image
                source={require('@/../assets/images/logo.png')}
                style={styles.logoImage}
                resizeMode="contain"
              />
            </View>
            <Text style={styles.title}>GrowSnap</Text>
            <Text style={styles.tagline}>성장을 위한 첫 걸음</Text>
          </View>

          {/* 가치 제안 */}
          <View style={styles.valuePropsContainer}>
            <ValueProp
              title="가볍게 성장하는 쇼츠"
              description="나도 모르는 사이 성장해 있는 숏폼"
            />
            <ValueProp
              title="매일 성장하는 습관"
              description="짧고 깊이있는 콘텐츠로 매일 배우는 즐거움"
            />
            <ValueProp
              title="나만의 학습 여정"
              description="관심사에 맞춘 개인화된 추천"
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
              <Button
                variant="outline"
                size="sm"
                onPress={async () => {
                  const { logout } = useAuthStore.getState();
                  await logout();
                  showErrorAlert('AsyncStorage 데이터가 모두 삭제되었습니다.', '초기화 완료');
                }}
                style={{ marginTop: theme.spacing[2] }}
              >
                데이터 초기화 (개발용)
              </Button>
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
    width: responsive({ xs: 140, md: 160, default: 140 }),
    height: responsive({ xs: 140, md: 160, default: 140 }),
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: theme.spacing[5],
  },

  logoImage: {
    width: '100%',
    height: '100%',
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
