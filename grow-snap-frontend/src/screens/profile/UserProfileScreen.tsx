import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  RefreshControl,
  Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { ProfileHeader, FollowButton } from '@/components/profile';
import { LoadingSpinner } from '@/components/common';
import { useAuthStore } from '@/stores/authStore';
import {
  getProfileByUserId,
  checkFollowing,
  followUser,
  unfollowUser,
} from '@/api/auth.api';
import { UserProfile } from '@/types/auth.types';
import { theme } from '@/theme';
import { withErrorHandling } from '@/utils/errorHandler';

type RouteParams = {
  UserProfile: {
    userId: string;
  };
};

/**
 * 다른 사용자 프로필 화면
 * 인스타그램 스타일의 사용자 프로필 보기
 */
export default function UserProfileScreen() {
  const navigation = useNavigation();
  const route = useRoute<RouteProp<RouteParams, 'UserProfile'>>();
  const { userId } = route.params;
  const { user: currentUser } = useAuthStore();

  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [isFollowing, setIsFollowing] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [followLoading, setFollowLoading] = useState(false);

  // 프로필 및 팔로우 상태 로드
  const loadProfile = async (showLoading = true) => {
    if (showLoading) setLoading(true);

    // 프로필 조회
    const profileResult = await withErrorHandling(
      async () => await getProfileByUserId(userId),
      {
        showAlert: true,
        alertTitle: '프로필 조회 실패',
        logContext: 'UserProfileScreen.loadProfile',
      }
    );

    if (profileResult) {
      setProfile(profileResult);

      // 팔로우 상태 확인 (본인이 아닐 때만)
      if (currentUser?.id !== userId) {
        const followResult = await withErrorHandling(
          async () => await checkFollowing(userId),
          {
            showAlert: false,
            logContext: 'UserProfileScreen.checkFollowing',
          }
        );

        if (followResult) {
          setIsFollowing(followResult.isFollowing);
        }
      }
    }

    if (showLoading) setLoading(false);
  };

  // 새로고침
  const handleRefresh = async () => {
    setRefreshing(true);
    await loadProfile(false);
    setRefreshing(false);
  };

  // 초기 로드
  useEffect(() => {
    loadProfile();
  }, [userId]);

  // 팔로우/언팔로우 토글
  const handleFollowToggle = async () => {
    setFollowLoading(true);

    const result = isFollowing
      ? await withErrorHandling(async () => await unfollowUser(userId), {
          showAlert: true,
          alertTitle: '언팔로우 실패',
          logContext: 'UserProfileScreen.unfollow',
        })
      : await withErrorHandling(async () => await followUser(userId), {
          showAlert: true,
          alertTitle: '팔로우 실패',
          logContext: 'UserProfileScreen.follow',
        });

    if (result !== null) {
      // 성공 시 상태 업데이트
      setIsFollowing(!isFollowing);

      // 프로필 재로드 (팔로워 수 업데이트)
      await loadProfile(false);
    }

    setFollowLoading(false);
  };

  // 팔로워 목록으로 이동
  const handleFollowersPress = () => {
    navigation.navigate('FollowList' as never, {
      userId,
      initialTab: 'followers',
    } as never);
  };

  // 팔로잉 목록으로 이동
  const handleFollowingPress = () => {
    navigation.navigate('FollowList' as never, {
      userId,
      initialTab: 'following',
    } as never);
  };

  // 메시지 보내기 (추후 구현)
  const handleMessage = () => {
    Alert.alert('메시지', '메시지 기능은 추후 구현 예정입니다.');
  };

  if (loading || !profile) {
    return (
      <SafeAreaView style={styles.loadingContainer} edges={['top']}>
        <LoadingSpinner />
      </SafeAreaView>
    );
  }

  const isOwnProfile = currentUser?.id === userId;

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          style={styles.backButton}
        >
          <Ionicons name="arrow-back" size={24} color={theme.colors.text.primary} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{profile.nickname}</Text>
        <View style={styles.headerRight} />
      </View>

      <ScrollView
        style={styles.scrollView}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />}
        showsVerticalScrollIndicator={false}
      >
        {/* 프로필 헤더 */}
        <ProfileHeader
          profile={profile}
          isOwnProfile={false}
          onFollowersPress={handleFollowersPress}
          onFollowingPress={handleFollowingPress}
        />

        {/* 액션 버튼들 */}
        {!isOwnProfile && (
          <View style={styles.actionsContainer}>
            <FollowButton
              isFollowing={isFollowing}
              onPress={handleFollowToggle}
              loading={followLoading}
            />
            <TouchableOpacity
              onPress={handleMessage}
              style={styles.messageButton}
              activeOpacity={0.7}
            >
              <Ionicons name="mail-outline" size={20} color={theme.colors.text.primary} />
            </TouchableOpacity>
          </View>
        )}

        {/* 콘텐츠 그리드 (추후 구현) */}
        <View style={styles.contentSection}>
          <View style={styles.contentHeader}>
            <Ionicons name="grid-outline" size={24} color={theme.colors.text.primary} />
          </View>
          <View style={styles.emptyContent}>
            <Ionicons
              name="images-outline"
              size={64}
              color={theme.colors.gray[300]}
              style={styles.emptyIcon}
            />
            <Text style={styles.emptyText}>아직 업로드한 콘텐츠가 없습니다</Text>
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },
  loadingContainer: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
    justifyContent: 'center',
    alignItems: 'center',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
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
  },
  headerTitle: {
    fontSize: theme.typography.fontSize.xl,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
  },
  headerRight: {
    width: 40,
  },
  scrollView: {
    flex: 1,
  },
  actionsContainer: {
    flexDirection: 'row',
    paddingHorizontal: theme.spacing[4],
    paddingBottom: theme.spacing[4],
    gap: theme.spacing[2],
  },
  messageButton: {
    width: 44,
    height: 36,
    borderRadius: theme.borderRadius.md,
    backgroundColor: theme.colors.background.secondary,
    borderWidth: 1,
    borderColor: theme.colors.border.medium,
    justifyContent: 'center',
    alignItems: 'center',
  },
  contentSection: {
    flex: 1,
    paddingTop: theme.spacing[4],
    borderTopWidth: 1,
    borderTopColor: theme.colors.border.light,
  },
  contentHeader: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingBottom: theme.spacing[4],
  },
  emptyContent: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: theme.spacing[12],
    paddingHorizontal: theme.spacing[6],
  },
  emptyIcon: {
    marginBottom: theme.spacing[4],
  },
  emptyText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.medium,
    color: theme.colors.text.secondary,
    textAlign: 'center',
  },
});
