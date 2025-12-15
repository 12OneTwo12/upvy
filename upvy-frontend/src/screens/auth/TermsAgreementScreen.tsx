import React, { useState } from 'react';
import {
  View,
  Text,
  SafeAreaView,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Ionicons } from '@expo/vector-icons';
import { agreeToTerms } from '@/api/auth.api';
import { useAuthStore } from '@/stores/authStore';
import { Button } from '@/components/common';
import { theme } from '@/theme';
import { showErrorAlert, withErrorHandling } from '@/utils/errorHandler';
import { createStyleSheet } from '@/utils/styles';
import type { RootStackParamList } from '@/types/navigation.types';

type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'TermsAgreement'>;

/**
 * 약관 동의 화면
 * Apple App Store Guideline 1.2 준수 (UGC 정책)
 */
export default function TermsAgreementScreen() {
  const { t } = useTranslation('auth');
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const navigation = useNavigation<NavigationProp>();
  const { setHasAgreedToTerms } = useAuthStore();

  const [serviceTermsAgreed, setServiceTermsAgreed] = useState(false);
  const [privacyPolicyAgreed, setPrivacyPolicyAgreed] = useState(false);
  const [communityGuidelinesAgreed, setCommunityGuidelinesAgreed] = useState(false);
  const [marketingAgreed, setMarketingAgreed] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  /**
   * 전체 동의 여부
   */
  const isAllAgreed =
    serviceTermsAgreed &&
    privacyPolicyAgreed &&
    communityGuidelinesAgreed &&
    marketingAgreed;

  /**
   * 필수 약관 동의 여부
   */
  const isRequiredAgreed =
    serviceTermsAgreed && privacyPolicyAgreed && communityGuidelinesAgreed;

  /**
   * 전체 동의 토글
   */
  const handleToggleAll = () => {
    const newValue = !isAllAgreed;
    setServiceTermsAgreed(newValue);
    setPrivacyPolicyAgreed(newValue);
    setCommunityGuidelinesAgreed(newValue);
    setMarketingAgreed(newValue);
  };

  /**
   * 약관 보기 (Terms of Service)
   */
  const handleViewTerms = () => {
    navigation.navigate('TermsOfService');
  };

  /**
   * 개인정보 처리방침 보기 (Privacy Policy)
   */
  const handleViewPrivacy = () => {
    navigation.navigate('PrivacyPolicy');
  };

  /**
   * 커뮤니티 가이드라인 보기 (Community Guidelines)
   */
  const handleViewCommunityGuidelines = () => {
    navigation.navigate('CommunityGuidelines');
  };

  /**
   * 약관 동의 제출
   */
  const handleSubmit = async () => {
    if (!isRequiredAgreed) {
      showErrorAlert(
        t('termsAgreement.error.requiredTerms'),
        t('termsAgreement.error.title')
      );
      return;
    }

    setIsSubmitting(true);
    const result = await withErrorHandling(
      async () =>
        await agreeToTerms({
          serviceTermsAgreed,
          privacyPolicyAgreed,
          communityGuidelinesAgreed,
          marketingAgreed,
        }),
      {
        showAlert: true,
        alertTitle: t('termsAgreement.error.agreementFailed'),
        logContext: 'TermsAgreementScreen.agreeToTerms',
      }
    );
    setIsSubmitting(false);

    if (result) {
      // Terms agreement successful, update store and navigate to profile setup
      setHasAgreedToTerms(result.isAllRequiredAgreed);
      navigation.navigate('ProfileSetup');
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <ScrollView
        contentContainerStyle={[
          styles.container,
          {
            paddingTop: Math.max(insets.top, theme.spacing[4]),
            paddingBottom: Math.max(insets.bottom, theme.spacing[6]),
          },
        ]}
        showsVerticalScrollIndicator={false}
      >
        {/* 헤더 */}
        <View style={styles.header}>
          <Text style={styles.title}>{t('termsAgreement.title')}</Text>
          <Text style={styles.subtitle}>{t('termsAgreement.subtitle')}</Text>
        </View>

        {/* 전체 동의 */}
        <TouchableOpacity
          style={styles.allAgreeContainer}
          onPress={handleToggleAll}
          activeOpacity={0.7}
        >
          <View style={styles.checkboxRow}>
            <View style={[styles.checkbox, isAllAgreed && styles.checkboxChecked]}>
              {isAllAgreed && (
                <Ionicons name="checkmark" size={18} color={theme.colors.white} />
              )}
            </View>
            <Text style={styles.allAgreeText}>{t('termsAgreement.allAgree')}</Text>
          </View>
        </TouchableOpacity>

        <View style={styles.divider} />

        {/* 약관 목록 */}
        <View style={styles.termsList}>
          {/* 서비스 이용약관 (필수) */}
          <View style={styles.termItem}>
            <TouchableOpacity
              style={styles.checkboxRow}
              onPress={() => setServiceTermsAgreed(!serviceTermsAgreed)}
              activeOpacity={0.7}
            >
              <View
                style={[
                  styles.checkbox,
                  serviceTermsAgreed && styles.checkboxChecked,
                ]}
              >
                {serviceTermsAgreed && (
                  <Ionicons name="checkmark" size={18} color={theme.colors.white} />
                )}
              </View>
              <Text style={styles.termLabel}>
                {t('termsAgreement.checkboxes.serviceTerms.label')}{' '}
                <Text style={styles.required}>
                  {t('termsAgreement.checkboxes.serviceTerms.required')}
                </Text>
              </Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={handleViewTerms} activeOpacity={0.6}>
              <Text style={styles.viewLink}>
                {t('termsAgreement.checkboxes.serviceTerms.viewLink')}
              </Text>
            </TouchableOpacity>
          </View>

          {/* 개인정보 처리방침 (필수) */}
          <View style={styles.termItem}>
            <TouchableOpacity
              style={styles.checkboxRow}
              onPress={() => setPrivacyPolicyAgreed(!privacyPolicyAgreed)}
              activeOpacity={0.7}
            >
              <View
                style={[
                  styles.checkbox,
                  privacyPolicyAgreed && styles.checkboxChecked,
                ]}
              >
                {privacyPolicyAgreed && (
                  <Ionicons name="checkmark" size={18} color={theme.colors.white} />
                )}
              </View>
              <Text style={styles.termLabel}>
                {t('termsAgreement.checkboxes.privacyPolicy.label')}{' '}
                <Text style={styles.required}>
                  {t('termsAgreement.checkboxes.privacyPolicy.required')}
                </Text>
              </Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={handleViewPrivacy} activeOpacity={0.6}>
              <Text style={styles.viewLink}>
                {t('termsAgreement.checkboxes.privacyPolicy.viewLink')}
              </Text>
            </TouchableOpacity>
          </View>

          {/* 커뮤니티 가이드라인 (필수) */}
          <View style={styles.termItem}>
            <TouchableOpacity
              style={styles.checkboxRow}
              onPress={() =>
                setCommunityGuidelinesAgreed(!communityGuidelinesAgreed)
              }
              activeOpacity={0.7}
            >
              <View
                style={[
                  styles.checkbox,
                  communityGuidelinesAgreed && styles.checkboxChecked,
                ]}
              >
                {communityGuidelinesAgreed && (
                  <Ionicons name="checkmark" size={18} color={theme.colors.white} />
                )}
              </View>
              <Text style={styles.termLabel}>
                {t('termsAgreement.checkboxes.communityGuidelines.label')}{' '}
                <Text style={styles.required}>
                  {t('termsAgreement.checkboxes.communityGuidelines.required')}
                </Text>
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              onPress={handleViewCommunityGuidelines}
              activeOpacity={0.6}
            >
              <Text style={styles.viewLink}>
                {t('termsAgreement.checkboxes.communityGuidelines.viewLink')}
              </Text>
            </TouchableOpacity>
          </View>

          {/* 마케팅 정보 수신 동의 (선택) */}
          <View style={styles.termItem}>
            <TouchableOpacity
              style={styles.checkboxRow}
              onPress={() => setMarketingAgreed(!marketingAgreed)}
              activeOpacity={0.7}
            >
              <View
                style={[styles.checkbox, marketingAgreed && styles.checkboxChecked]}
              >
                {marketingAgreed && (
                  <Ionicons name="checkmark" size={18} color={theme.colors.white} />
                )}
              </View>
              <Text style={styles.termLabel}>
                {t('termsAgreement.checkboxes.marketing.label')}{' '}
                <Text style={styles.optional}>
                  {t('termsAgreement.checkboxes.marketing.optional')}
                </Text>
              </Text>
            </TouchableOpacity>
            <View style={{ width: 40 }} />
          </View>
        </View>

        {/* Spacer */}
        <View style={{ flex: 1, minHeight: theme.spacing[8] }} />

        {/* 완료 버튼 */}
        <Button
          variant="primary"
          size="lg"
          fullWidth
          onPress={handleSubmit}
          disabled={!isRequiredAgreed || isSubmitting}
          loading={isSubmitting}
        >
          {t('termsAgreement.continueButton')}
        </Button>
      </ScrollView>
    </SafeAreaView>
  );
}

const useStyles = createStyleSheet({
  safeArea: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },

  container: {
    flexGrow: 1,
    paddingHorizontal: theme.spacing[6],
    paddingVertical: theme.spacing[6],
  },

  // Header
  header: {
    marginBottom: theme.spacing[8],
  },

  title: {
    fontSize: theme.typography.fontSize['2xl'],
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[2],
  },

  subtitle: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.secondary,
    lineHeight:
      theme.typography.lineHeight.relaxed * theme.typography.fontSize.base,
  },

  // All Agree
  allAgreeContainer: {
    backgroundColor: theme.colors.background.secondary,
    paddingVertical: theme.spacing[4],
    paddingHorizontal: theme.spacing[4],
    borderRadius: theme.borderRadius.lg,
    marginBottom: theme.spacing[4],
  },

  allAgreeText: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
  },

  divider: {
    height: 1,
    backgroundColor: theme.colors.border.light,
    marginBottom: theme.spacing[6],
  },

  // Terms List
  termsList: {
    gap: theme.spacing[5],
  },

  termItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },

  checkboxRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },

  checkbox: {
    width: 24,
    height: 24,
    borderRadius: theme.borderRadius.base,
    borderWidth: 2,
    borderColor: theme.colors.border.medium,
    marginRight: theme.spacing[3],
    alignItems: 'center',
    justifyContent: 'center',
  },

  checkboxChecked: {
    backgroundColor: theme.colors.primary[600],
    borderColor: theme.colors.primary[600],
  },

  termLabel: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    flex: 1,
  },

  required: {
    color: theme.colors.error,
    fontSize: theme.typography.fontSize.sm,
  },

  optional: {
    color: theme.colors.text.tertiary,
    fontSize: theme.typography.fontSize.sm,
  },

  viewLink: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.primary[600],
    fontWeight: theme.typography.fontWeight.medium,
    textDecorationLine: 'underline',
  },
});
