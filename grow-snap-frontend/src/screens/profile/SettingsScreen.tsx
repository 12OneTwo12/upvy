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
import { RootStackParamList } from '@/types/navigation.types';
import { useAuthStore } from '@/stores/authStore';
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
  const { logout } = useAuthStore();

  // 백엔드 API 준비 후 실제 값으로 대체될 임시 상태
  const [notificationsEnabled, setNotificationsEnabled] = useState(true);
  const [privateAccount, setPrivateAccount] = useState(false);

  /**
   * 로그아웃 처리
   */
  const handleLogout = () => {
    Alert.alert('로그아웃', '정말 로그아웃하시겠습니까?', [
      {
        text: '취소',
        style: 'cancel',
      },
      {
        text: '로그아웃',
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
      '회원 탈퇴',
      '정말 탈퇴하시겠습니까? 모든 데이터가 삭제되며 복구할 수 없습니다.',
      [
        {
          text: '취소',
          style: 'cancel',
        },
        {
          text: '탈퇴',
          onPress: async () => {
            const result = await withErrorHandling(
              async () => await deleteUser(),
              {
                showAlert: true,
                alertTitle: '회원 탈퇴 실패',
                logContext: 'SettingsScreen.deleteAccount',
              }
            );

            if (result) {
              Alert.alert('탈퇴 완료', '회원 탈퇴가 완료되었습니다.', [
                {
                  text: '확인',
                  onPress: async () => {
                    await logout();
                  },
                },
              ]);
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
      '준비 중',
      `${featureName} 기능은 현재 개발 중입니다.\n곧 사용 가능합니다.`,
      [{ text: '확인' }]
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
        <Text style={styles.headerTitle}>설정</Text>
      </View>

      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        {/* Account Settings */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>계정</Text>

          <TouchableOpacity style={styles.settingRow} onPress={handleEditProfile}>
            <View style={styles.settingLeft}>
              <Ionicons
                name="person-outline"
                size={22}
                color={theme.colors.text.secondary}
                style={styles.settingIcon}
              />
              <View style={styles.settingContent}>
                <Text style={styles.settingLabel}>프로필 편집</Text>
                <Text style={styles.settingSubtitle}>닉네임, 소개 등 수정</Text>
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
          <Text style={styles.sectionTitle}>개인정보 및 보안</Text>

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
                  <Text style={styles.settingLabel}>비공개 계정</Text>
                  <View style={styles.comingSoonBadge}>
                    <Text style={styles.comingSoonText}>준비중</Text>
                  </View>
                </View>
                <Text style={styles.settingSubtitle}>
                  팔로워 승인 필요
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
            style={[styles.settingRow, styles.settingRowDisabled]}
            onPress={() => showComingSoonAlert('차단한 사용자')}
            disabled={true}
          >
            <View style={styles.settingLeft}>
              <Ionicons
                name="ban-outline"
                size={22}
                color={theme.colors.text.secondary}
                style={styles.settingIcon}
              />
              <View style={styles.settingContent}>
                <Text style={styles.settingLabel}>차단한 사용자</Text>
                <Text style={styles.settingSubtitle}>
                  차단한 사용자 관리
                </Text>
              </View>
              <View style={styles.comingSoonBadge}>
                <Text style={styles.comingSoonText}>준비중</Text>
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
          <Text style={styles.sectionTitle}>알림</Text>

          <View
            style={[styles.settingRow, styles.settingRowDisabled]}
          >
            <View style={styles.settingLeft}>
              <Ionicons
                name="notifications-outline"
                size={22}
                color={theme.colors.text.secondary}
                style={styles.settingIcon}
              />
              <View style={styles.settingContent}>
                <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                  <Text style={styles.settingLabel}>알림</Text>
                  <View style={styles.comingSoonBadge}>
                    <Text style={styles.comingSoonText}>준비중</Text>
                  </View>
                </View>
                <Text style={styles.settingSubtitle}>
                  좋아요, 댓글, 팔로우 알림
                </Text>
              </View>
            </View>
            <Switch
              value={notificationsEnabled}
              onValueChange={setNotificationsEnabled}
              disabled={true}
              trackColor={{
                false: theme.colors.gray[300],
                true: theme.colors.primary[300],
              }}
              thumbColor={notificationsEnabled ? theme.colors.primary[500] : theme.colors.gray[200]}
            />
          </View>
        </View>

        <View style={styles.divider} />

        {/* General */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>일반</Text>

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
              <Text style={styles.settingLabel}>서비스 약관</Text>
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
              <Text style={styles.settingLabel}>개인정보 보호정책</Text>
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
              <Text style={styles.settingLabel}>도움말 및 지원</Text>
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
                <Text style={styles.settingLabel}>버전 정보</Text>
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
            <Text style={styles.logoutButtonText}>로그아웃</Text>
          </TouchableOpacity>

          <View style={styles.withdrawContainer}>
            <TouchableOpacity
              style={styles.withdrawButton}
              onPress={handleDeleteAccount}
            >
              <Text style={styles.withdrawText}>회원 탈퇴</Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* Bottom spacing */}
        <View style={{ height: theme.spacing[8] }} />
      </ScrollView>
    </SafeAreaView>
  );
}
