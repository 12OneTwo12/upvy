import React, { useState } from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  Switch,
  Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import { RootStackParamList } from '@/types/navigation.types';
import { useAuthStore } from '@/stores/authStore';
import { useLanguageStore } from '@/stores/languageStore';
import { supportedLanguages } from '@/locales';
import { theme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';
import { deleteUser } from '@/api/user.api';
import { withErrorHandling } from '@/utils/errorHandler';

type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'Settings'>;

const useStyles = createStyleSheet({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  backButton: {
    width: 40,
    height: 40,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: theme.spacing[3],
  },
  headerTitle: {
    fontSize: theme.typography.fontSize.xl,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
  },
  scrollView: {
    flex: 1,
  },
  section: {
    marginTop: theme.spacing[4],
  },
  sectionTitle: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.secondary,
    paddingHorizontal: theme.spacing[4],
    marginBottom: theme.spacing[2],
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  settingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[4],
    backgroundColor: theme.colors.background.primary,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  settingRowDisabled: {
    opacity: 0.5,
  },
  settingLeft: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
  },
  settingIcon: {
    marginRight: theme.spacing[3],
  },
  settingContent: {
    flex: 1,
  },
  settingLabel: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    fontWeight: theme.typography.fontWeight.medium,
  },
  settingSubtitle: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    marginTop: theme.spacing[1],
  },
  comingSoonBadge: {
    backgroundColor: theme.colors.gray[200],
    paddingHorizontal: theme.spacing[2],
    paddingVertical: 2,
    borderRadius: theme.borderRadius.sm,
    marginLeft: theme.spacing[2],
  },
  comingSoonText: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.secondary,
    fontWeight: theme.typography.fontWeight.medium,
  },
  divider: {
    height: 8,
    backgroundColor: theme.colors.background.secondary,
  },
  logoutSection: {
    paddingHorizontal: theme.spacing[4],
    paddingTop: theme.spacing[6],
    paddingBottom: theme.spacing[4],
  },
  logoutButton: {
    backgroundColor: theme.colors.primary[500],
    paddingVertical: theme.spacing[4],
    borderRadius: theme.borderRadius.base,
    alignItems: 'center',
  },
  logoutButtonText: {
    color: theme.colors.text.inverse,
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
  },
  withdrawContainer: {
    alignItems: 'center',
    marginTop: theme.spacing[6],
    marginBottom: theme.spacing[4],
  },
  withdrawButton: {
    paddingVertical: theme.spacing[2],
  },
  withdrawText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.gray[400],
    textDecorationLine: 'underline',
  },
});

/**
 * Settings Screen
 * Account, privacy, and preferences settings
 */
export default function SettingsScreen() {
  const styles = useStyles();
  const navigation = useNavigation<NavigationProp>();
  const { t } = useTranslation('settings');
  const { logout } = useAuthStore();
  const { currentLanguage } = useLanguageStore();

  // 백엔드 API 준비 후 실제 값으로 대체될 임시 상태
  const [privateAccount, setPrivateAccount] = useState(false);

  // 현재 선택된 언어 표시 텍스트 가져오기
  const currentLanguageDisplay = supportedLanguages.find(
    lang => lang.code === currentLanguage
  )?.name || '한국어';

  /**
   * 로그아웃 처리
   */
  const handleLogout = () => {
    Alert.alert(t('logout.title'), t('logout.message'), [
      {
        text: t('common:button.cancel'),
        style: 'cancel',
      },
      {
        text: t('logout.button'),
        onPress: async () => {
          await logout();
        },
        style: 'destructive',
      },
    ]);
  };

  /**
   * 회원 탈퇴 처리
   */
  const handleDeleteAccount = () => {
    Alert.alert(
      t('withdraw.title'),
      t('withdraw.message'),
      [
        {
          text: t('common:button.cancel'),
          style: 'cancel',
        },
        {
          text: t('withdraw.button'),
          onPress: async () => {
            const result = await withErrorHandling(
              async () => await deleteUser(),
              {
                showAlert: true,
                alertTitle: t('withdraw.failed'),
                logContext: 'SettingsScreen.deleteAccount',
              }
            );

            if (result !== null) {
              // 탈퇴 성공 시 즉시 로그아웃 (로그인 화면으로 자동 이동)
              await logout();
            }
          },
          style: 'destructive',
        },
      ]
    );
  };

  /**
   * 프로필 편집 화면으로 이동
   */
  const handleEditProfile = () => {
    navigation.navigate('EditProfile');
  };

  /**
   * 준비 중인 기능 안내
   */
  const showComingSoonAlert = (featureName: string) => {
    Alert.alert(
      t('comingSoon.title'),
      t('comingSoon.message', { feature: featureName }),
      [{ text: t('common:button.confirm') }]
    );
  };

  /**
   * 서비스 약관 화면으로 이동
   */
  const handleTermsOfService = () => {
    navigation.navigate('TermsOfService');
  };

  /**
   * 개인정보 보호정책 화면으로 이동
   */
  const handlePrivacyPolicy = () => {
    navigation.navigate('PrivacyPolicy');
  };

  /**
   * 도움말 및 지원 화면으로 이동
   */
  const handleHelp = () => {
    navigation.navigate('HelpSupport');
  };

  /**
   * 언어 선택 화면으로 이동
   */
  const handleLanguageSelector = () => {
    navigation.navigate('LanguageSelector');
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          style={styles.backButton}
        >
          <Ionicons
            name="arrow-back"
            size={24}
            color={theme.colors.text.primary}
          />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{t('title')}</Text>
      </View>

      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        {/* Account Settings */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>{t('sections.account')}</Text>

          <TouchableOpacity style={styles.settingRow} onPress={handleEditProfile}>
            <View style={styles.settingLeft}>
              <Ionicons
                name="person-outline"
                size={22}
                color={theme.colors.text.secondary}
                style={styles.settingIcon}
              />
              <View style={styles.settingContent}>
                <Text style={styles.settingLabel}>{t('account.editProfile')}</Text>
                <Text style={styles.settingSubtitle}>{t('account.editProfileSubtitle')}</Text>
              </View>
            </View>
            <Ionicons
              name="chevron-forward"
              size={20}
              color={theme.colors.gray[400]}
            />
          </TouchableOpacity>
        </View>

        <View style={styles.divider} />

        {/* Privacy & Security */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>{t('sections.privacy')}</Text>

          <View
            style={[styles.settingRow, styles.settingRowDisabled]}
          >
            <View style={styles.settingLeft}>
              <Ionicons
                name="lock-closed-outline"
                size={22}
                color={theme.colors.text.secondary}
                style={styles.settingIcon}
              />
              <View style={styles.settingContent}>
                <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                  <Text style={styles.settingLabel}>{t('privacy.privateAccount')}</Text>
                  <View style={styles.comingSoonBadge}>
                    <Text style={styles.comingSoonText}>{t('common:label.preparing')}</Text>
                  </View>
                </View>
                <Text style={styles.settingSubtitle}>
                  {t('privacy.privateAccountSubtitle')}
                </Text>
              </View>
            </View>
            <Switch
              value={privateAccount}
              onValueChange={setPrivateAccount}
              disabled={true}
              trackColor={{
                false: theme.colors.gray[300],
                true: theme.colors.primary[300],
              }}
              thumbColor={privateAccount ? theme.colors.primary[500] : theme.colors.gray[200]}
            />
          </View>

          <TouchableOpacity
            style={styles.settingRow}
            onPress={() => navigation.navigate('BlockManagement')}
          >
            <View style={styles.settingLeft}>
              <Ionicons
                name="ban-outline"
                size={22}
                color={theme.colors.text.secondary}
                style={styles.settingIcon}
              />
              <View style={styles.settingContent}>
                <Text style={styles.settingLabel}>{t('privacy.blockManagement')}</Text>
                <Text style={styles.settingSubtitle}>
                  {t('privacy.blockManagementSubtitle')}
                </Text>
              </View>
            </View>
            <Ionicons
              name="chevron-forward"
              size={20}
              color={theme.colors.gray[400]}
            />
          </TouchableOpacity>
        </View>

        <View style={styles.divider} />

        {/* Notifications */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>{t('sections.notifications')}</Text>

          <TouchableOpacity
            style={styles.settingRow}
            onPress={() => navigation.navigate('NotificationSettings')}
          >
            <View style={styles.settingLeft}>
              <Ionicons
                name="notifications-outline"
                size={22}
                color={theme.colors.text.secondary}
                style={styles.settingIcon}
              />
              <View style={styles.settingContent}>
                <Text style={styles.settingLabel}>{t('notifications.notifications')}</Text>
                <Text style={styles.settingSubtitle}>
                  {t('notifications.notificationsSubtitle')}
                </Text>
              </View>
            </View>
            <Ionicons
              name="chevron-forward"
              size={20}
              color={theme.colors.gray[400]}
            />
          </TouchableOpacity>
        </View>

        <View style={styles.divider} />

        {/* General */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>{t('sections.general')}</Text>

          <TouchableOpacity
            style={styles.settingRow}
            onPress={handleLanguageSelector}
          >
            <View style={styles.settingLeft}>
              <Ionicons
                name="language-outline"
                size={22}
                color={theme.colors.text.secondary}
                style={styles.settingIcon}
              />
              <View style={styles.settingContent}>
                <Text style={styles.settingLabel}>{t('general.language')}</Text>
                <Text style={styles.settingSubtitle}>{currentLanguageDisplay}</Text>
              </View>
            </View>
            <Ionicons
              name="chevron-forward"
              size={20}
              color={theme.colors.gray[400]}
            />
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.settingRow}
            onPress={handleTermsOfService}
          >
            <View style={styles.settingLeft}>
              <Ionicons
                name="document-text-outline"
                size={22}
                color={theme.colors.text.secondary}
                style={styles.settingIcon}
              />
              <Text style={styles.settingLabel}>{t('general.termsOfService')}</Text>
            </View>
            <Ionicons
              name="chevron-forward"
              size={20}
              color={theme.colors.gray[400]}
            />
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.settingRow}
            onPress={handlePrivacyPolicy}
          >
            <View style={styles.settingLeft}>
              <Ionicons
                name="shield-checkmark-outline"
                size={22}
                color={theme.colors.text.secondary}
                style={styles.settingIcon}
              />
              <Text style={styles.settingLabel}>{t('general.privacyPolicy')}</Text>
            </View>
            <Ionicons
              name="chevron-forward"
              size={20}
              color={theme.colors.gray[400]}
            />
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.settingRow}
            onPress={handleHelp}
          >
            <View style={styles.settingLeft}>
              <Ionicons
                name="help-circle-outline"
                size={22}
                color={theme.colors.text.secondary}
                style={styles.settingIcon}
              />
              <Text style={styles.settingLabel}>{t('general.helpSupport')}</Text>
            </View>
            <Ionicons
              name="chevron-forward"
              size={20}
              color={theme.colors.gray[400]}
            />
          </TouchableOpacity>

          <View style={styles.settingRow}>
            <View style={styles.settingLeft}>
              <Ionicons
                name="information-circle-outline"
                size={22}
                color={theme.colors.text.secondary}
                style={styles.settingIcon}
              />
              <View style={styles.settingContent}>
                <Text style={styles.settingLabel}>{t('general.version')}</Text>
                <Text style={styles.settingSubtitle}>v1.0.0</Text>
              </View>
            </View>
          </View>
        </View>

        <View style={styles.divider} />

        {/* Account Management */}
        <View style={styles.logoutSection}>
          <TouchableOpacity
            style={styles.logoutButton}
            onPress={handleLogout}
          >
            <Text style={styles.logoutButtonText}>{t('logout.button')}</Text>
          </TouchableOpacity>

          <View style={styles.withdrawContainer}>
            <TouchableOpacity
              style={styles.withdrawButton}
              onPress={handleDeleteAccount}
            >
              <Text style={styles.withdrawText}>{t('withdraw.link')}</Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* Bottom spacing */}
        <View style={{ height: theme.spacing[8] }} />
      </ScrollView>
    </SafeAreaView>
  );
}
