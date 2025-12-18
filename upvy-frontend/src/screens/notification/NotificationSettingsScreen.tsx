/**
 * 알림 설정 화면
 *
 * 알림 타입별 토글 스위치로 알림 설정을 관리합니다.
 * - 전체 알림 활성화/비활성화
 * - 좋아요 알림
 * - 댓글 및 답글 알림
 * - 팔로우 알림
 */

import React, { useEffect, useCallback } from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  Switch,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';

import { useTheme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';
import { RootStackParamList } from '@/types/navigation.types';
import { useNotificationStore } from '@/stores/notificationStore';
import { checkNotificationPermission, showSettingsAlert } from '@/hooks/useNotifications';

type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'NotificationSettings'>;

const useStyles = createStyleSheet((theme) => ({
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
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
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
  divider: {
    height: 8,
    backgroundColor: theme.colors.background.secondary,
  },
  infoSection: {
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[4],
    backgroundColor: theme.colors.background.secondary,
    marginTop: theme.spacing[4],
  },
  infoText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    lineHeight: 20,
  },
  permissionWarning: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
    backgroundColor: theme.colors.warning + '15',
    marginHorizontal: theme.spacing[4],
    marginTop: theme.spacing[4],
    borderRadius: theme.borderRadius.base,
  },
  permissionWarningIcon: {
    marginRight: theme.spacing[3],
  },
  permissionWarningText: {
    flex: 1,
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.warning,
  },
  openSettingsButton: {
    paddingHorizontal: theme.spacing[3],
    paddingVertical: theme.spacing[2],
  },
  openSettingsText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.primary[500],
    fontWeight: theme.typography.fontWeight.medium,
  },
}));

/**
 * 알림 설정 화면
 */
export default function NotificationSettingsScreen() {
  const styles = useStyles();
  const dynamicTheme = useTheme();
  const navigation = useNavigation<NavigationProp>();
  const { t } = useTranslation('notification');

  const { settings, isLoading, fetchSettings, updateSettings } = useNotificationStore();

  const [permissionGranted, setPermissionGranted] = React.useState<boolean | null>(null);

  /**
   * 시스템 알림 권한 확인
   */
  const checkPermission = useCallback(async () => {
    const { status } = await checkNotificationPermission();
    setPermissionGranted(status === 'granted');
  }, []);

  /**
   * 초기 로딩
   */
  useEffect(() => {
    fetchSettings();
    checkPermission();
  }, [fetchSettings, checkPermission]);

  /**
   * 전체 알림 토글
   */
  const handleAllNotificationsToggle = useCallback(
    async (value: boolean) => {
      await updateSettings({ allNotificationsEnabled: value });
    },
    [updateSettings]
  );

  /**
   * 좋아요 알림 토글
   */
  const handleLikeNotificationsToggle = useCallback(
    async (value: boolean) => {
      await updateSettings({ likeNotificationsEnabled: value });
    },
    [updateSettings]
  );

  /**
   * 댓글 알림 토글
   */
  const handleCommentNotificationsToggle = useCallback(
    async (value: boolean) => {
      await updateSettings({ commentNotificationsEnabled: value });
    },
    [updateSettings]
  );

  /**
   * 팔로우 알림 토글
   */
  const handleFollowNotificationsToggle = useCallback(
    async (value: boolean) => {
      await updateSettings({ followNotificationsEnabled: value });
    },
    [updateSettings]
  );

  /**
   * 스위치 비활성화 조건 (전체 알림이 꺼져있으면 개별 설정 비활성화)
   */
  const isIndividualSettingsDisabled = !settings?.allNotificationsEnabled;

  if (isLoading || !settings) {
    return (
      <SafeAreaView style={styles.container} edges={['top']}>
        <View style={styles.header}>
          <TouchableOpacity
            onPress={() => navigation.goBack()}
            style={styles.backButton}
          >
            <Ionicons
              name="arrow-back"
              size={24}
              color={dynamicTheme.colors.text.primary}
            />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>{t('settings.title')}</Text>
        </View>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={dynamicTheme.colors.primary[500]} />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          style={styles.backButton}
        >
          <Ionicons
            name="arrow-back"
            size={24}
            color={dynamicTheme.colors.text.primary}
          />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{t('settings.title')}</Text>
      </View>

      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        {/* 시스템 권한 경고 */}
        {permissionGranted === false && (
          <View style={styles.permissionWarning}>
            <Ionicons
              name="warning-outline"
              size={20}
              color={dynamicTheme.colors.warning}
              style={styles.permissionWarningIcon}
            />
            <Text style={styles.permissionWarningText}>
              {t('settings.permissionWarning')}
            </Text>
            <TouchableOpacity
              onPress={showSettingsAlert}
              style={styles.openSettingsButton}
            >
              <Text style={styles.openSettingsText}>
                {t('settings.openSettings')}
              </Text>
            </TouchableOpacity>
          </View>
        )}

        {/* 전체 알림 설정 */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>{t('settings.sections.general')}</Text>

          <View style={styles.settingRow}>
            <View style={styles.settingLeft}>
              <Ionicons
                name="notifications-outline"
                size={22}
                color={dynamicTheme.colors.text.secondary}
                style={styles.settingIcon}
              />
              <View style={styles.settingContent}>
                <Text style={styles.settingLabel}>{t('settings.allNotifications')}</Text>
                <Text style={styles.settingSubtitle}>
                  {t('settings.allNotificationsSubtitle')}
                </Text>
              </View>
            </View>
            <Switch
              value={settings.allNotificationsEnabled}
              onValueChange={handleAllNotificationsToggle}
              trackColor={{
                false: dynamicTheme.colors.gray[300],
                true: dynamicTheme.colors.primary[300],
              }}
              thumbColor={
                settings.allNotificationsEnabled
                  ? dynamicTheme.colors.primary[500]
                  : dynamicTheme.colors.gray[200]
              }
            />
          </View>
        </View>

        <View style={styles.divider} />

        {/* 알림 유형별 설정 */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>{t('settings.sections.types')}</Text>

          {/* 좋아요 알림 */}
          <View style={[styles.settingRow, isIndividualSettingsDisabled && { opacity: 0.5 }]}>
            <View style={styles.settingLeft}>
              <Ionicons
                name="heart-outline"
                size={22}
                color={dynamicTheme.colors.text.secondary}
                style={styles.settingIcon}
              />
              <View style={styles.settingContent}>
                <Text style={styles.settingLabel}>{t('settings.likeNotifications')}</Text>
                <Text style={styles.settingSubtitle}>
                  {t('settings.likeNotificationsSubtitle')}
                </Text>
              </View>
            </View>
            <Switch
              value={settings.likeNotificationsEnabled}
              onValueChange={handleLikeNotificationsToggle}
              disabled={isIndividualSettingsDisabled}
              trackColor={{
                false: dynamicTheme.colors.gray[300],
                true: dynamicTheme.colors.primary[300],
              }}
              thumbColor={
                settings.likeNotificationsEnabled
                  ? dynamicTheme.colors.primary[500]
                  : dynamicTheme.colors.gray[200]
              }
            />
          </View>

          {/* 댓글 알림 */}
          <View style={[styles.settingRow, isIndividualSettingsDisabled && { opacity: 0.5 }]}>
            <View style={styles.settingLeft}>
              <Ionicons
                name="chatbubble-outline"
                size={22}
                color={dynamicTheme.colors.text.secondary}
                style={styles.settingIcon}
              />
              <View style={styles.settingContent}>
                <Text style={styles.settingLabel}>{t('settings.commentNotifications')}</Text>
                <Text style={styles.settingSubtitle}>
                  {t('settings.commentNotificationsSubtitle')}
                </Text>
              </View>
            </View>
            <Switch
              value={settings.commentNotificationsEnabled}
              onValueChange={handleCommentNotificationsToggle}
              disabled={isIndividualSettingsDisabled}
              trackColor={{
                false: dynamicTheme.colors.gray[300],
                true: dynamicTheme.colors.primary[300],
              }}
              thumbColor={
                settings.commentNotificationsEnabled
                  ? dynamicTheme.colors.primary[500]
                  : dynamicTheme.colors.gray[200]
              }
            />
          </View>

          {/* 팔로우 알림 */}
          <View style={[styles.settingRow, isIndividualSettingsDisabled && { opacity: 0.5 }]}>
            <View style={styles.settingLeft}>
              <Ionicons
                name="person-add-outline"
                size={22}
                color={dynamicTheme.colors.text.secondary}
                style={styles.settingIcon}
              />
              <View style={styles.settingContent}>
                <Text style={styles.settingLabel}>{t('settings.followNotifications')}</Text>
                <Text style={styles.settingSubtitle}>
                  {t('settings.followNotificationsSubtitle')}
                </Text>
              </View>
            </View>
            <Switch
              value={settings.followNotificationsEnabled}
              onValueChange={handleFollowNotificationsToggle}
              disabled={isIndividualSettingsDisabled}
              trackColor={{
                false: dynamicTheme.colors.gray[300],
                true: dynamicTheme.colors.primary[300],
              }}
              thumbColor={
                settings.followNotificationsEnabled
                  ? dynamicTheme.colors.primary[500]
                  : dynamicTheme.colors.gray[200]
              }
            />
          </View>
        </View>

        {/* 안내 메시지 */}
        <View style={styles.infoSection}>
          <Text style={styles.infoText}>{t('settings.info')}</Text>
        </View>

        {/* 하단 여백 */}
        <View style={{ height: dynamicTheme.spacing[8] }} />
      </ScrollView>
    </SafeAreaView>
  );
}
