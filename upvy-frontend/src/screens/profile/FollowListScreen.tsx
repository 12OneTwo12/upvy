import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  RefreshControl,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation, useRoute, RouteProp, CompositeNavigationProp } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import { useTranslation } from 'react-i18next';
import { RootStackParamList, MainTabParamList } from '@/types/navigation.types';
import { ProfileAvatar, FollowButton } from '@/components/profile';
import { LoadingSpinner } from '@/components/common';
import { useAuthStore } from '@/stores/authStore';
import {
  getFollowers,
  getFollowing,
  checkFollowing,
  followUser,
  unfollowUser,
  getProfileByUserId,
} from '@/api/auth.api';
import { UserProfile } from '@/types/auth.types';
import { theme } from '@/theme';
import { withErrorHandling } from '@/utils/errorHandler';
import { createStyleSheet } from '@/utils/styles';

type NavigationProp = CompositeNavigationProp<
  NativeStackNavigationProp<RootStackParamList, 'FollowList'>,
  BottomTabNavigationProp<MainTabParamList>
>;

type UserWithFollowState = UserProfile & {
  isFollowing: boolean;
};

/**
 * 팔로워/팔로잉 목록 화면
 * 인스타그램 스타일의 사용자 목록
 */
const useStyles = createStyleSheet({
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
  tabContainer: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  tab: {
    flex: 1,
    paddingVertical: theme.spacing[3],
    alignItems: 'center',
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },
  activeTab: {
    borderBottomColor: theme.colors.text.primary,
  },
  tabText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.medium,
    color: theme.colors.text.tertiary,
  },
  activeTabText: {
    color: theme.colors.text.primary,
    fontWeight: theme.typography.fontWeight.bold,
  },
  listContent: {
    flexGrow: 1,
  },
  userItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
  },
  userInfo: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    marginRight: theme.spacing[3],
  },
  userText: {
    flex: 1,
    marginLeft: theme.spacing[3],
  },
  nickname: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[1],
  },
  bio: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
  },
  emptyContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: theme.spacing[16],
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

export default function FollowListScreen() {
  const styles = useStyles();
  const { t } = useTranslation('profile');
  const navigation = useNavigation<NavigationProp>();
  const route = useRoute<RouteProp<RootStackParamList, 'FollowList'>>();
  const { userId, initialTab = 'followers' } = route.params;
  const { user: currentUser } = useAuthStore();

  const [activeTab, setActiveTab] = useState<'followers' | 'following'>(initialTab);
  const [followers, setFollowers] = useState<UserWithFollowState[]>([]);
  const [following, setFollowing] = useState<UserWithFollowState[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [followLoadingMap, setFollowLoadingMap] = useState<Record<string, boolean>>({});
  const [userNickname, setUserNickname] = useState<string>('');

  // 데이터 로드
  const loadData = async (showLoading = true) => {
    if (showLoading) setLoading(true);

    // 사용자 프로필 조회 (헤더 닉네임 표시용)
    const profileResult = await withErrorHandling(
      async () => await getProfileByUserId(userId),
      {
        showAlert: false,
        logContext: 'FollowListScreen.loadProfile',
      }
    );

    if (profileResult) {
      setUserNickname(profileResult.nickname);
    }

    // 팔로워 목록 로드
    const followersResult = await withErrorHandling(
      async () => await getFollowers(userId),
      {
        showAlert: true,
        alertTitle: '팔로워 목록 조회 실패',
        logContext: 'FollowListScreen.loadFollowers',
      }
    );

    // 팔로잉 목록 로드
    const followingResult = await withErrorHandling(
      async () => await getFollowing(userId),
      {
        showAlert: true,
        alertTitle: '팔로잉 목록 조회 실패',
        logContext: 'FollowListScreen.loadFollowing',
      }
    );

    if (followersResult && followingResult) {
      // 각 사용자의 팔로우 상태 확인 (현재 로그인한 사용자 기준)
      const followersWithState = await Promise.all(
        followersResult.map(async (user) => {
          if (currentUser?.id === user.userId) {
            return { ...user, isFollowing: false };
          }
          const checkResult = await checkFollowing(user.userId);
          return { ...user, isFollowing: checkResult?.isFollowing || false };
        })
      );

      const followingWithState = await Promise.all(
        followingResult.map(async (user) => {
          if (currentUser?.id === user.userId) {
            return { ...user, isFollowing: false };
          }
          const checkResult = await checkFollowing(user.userId);
          return { ...user, isFollowing: checkResult?.isFollowing || false };
        })
      );

      setFollowers(followersWithState);
      setFollowing(followingWithState);
    }

    if (showLoading) setLoading(false);
  };

  // 새로고침
  const handleRefresh = async () => {
    setRefreshing(true);
    await loadData(false);
    setRefreshing(false);
  };

  // 초기 로드
  useEffect(() => {
    loadData();
  }, [userId]);

  // 팔로우/언팔로우 토글
  const handleFollowToggle = async (targetUser: UserWithFollowState) => {
    const targetUserId = targetUser.userId;

    setFollowLoadingMap((prev) => ({ ...prev, [targetUserId]: true }));

    const result = targetUser.isFollowing
      ? await withErrorHandling(async () => await unfollowUser(targetUserId), {
          showAlert: true,
          alertTitle: '언팔로우 실패',
          logContext: 'FollowListScreen.unfollow',
        })
      : await withErrorHandling(async () => await followUser(targetUserId), {
          showAlert: true,
          alertTitle: '팔로우 실패',
          logContext: 'FollowListScreen.follow',
        });

    if (result !== null) {
      // 상태 업데이트
      const updateList = (list: UserWithFollowState[]) =>
        list.map((user) =>
          user.userId === targetUserId
            ? { ...user, isFollowing: !user.isFollowing }
            : user
        );

      setFollowers(updateList);
      setFollowing(updateList);
    }

    setFollowLoadingMap((prev) => ({ ...prev, [targetUserId]: false }));
  };

  // 사용자 프로필로 이동
  const handleUserPress = (targetUserId: string) => {
    if (currentUser?.id === targetUserId) {
      // 본인 프로필
      navigation.navigate('Main', { screen: 'Profile' });
    } else {
      // 다른 사용자 프로필
      navigation.navigate('UserProfile', { userId: targetUserId });
    }
  };

  // 사용자 아이템 렌더링
  const renderUserItem = ({ item }: { item: UserWithFollowState }) => {
    const isOwnProfile = currentUser?.id === item.userId;

    return (
      <TouchableOpacity
        style={styles.userItem}
        onPress={() => handleUserPress(item.userId)}
        activeOpacity={0.7}
      >
        <View style={styles.userInfo}>
          <ProfileAvatar
            imageUrl={item.profileImageUrl}
            size="medium"
            onPress={() => handleUserPress(item.userId)}
          />
          <View style={styles.userText}>
            <Text style={styles.nickname}>{item.nickname}</Text>
            {item.bio && <Text style={styles.bio} numberOfLines={1}>{item.bio}</Text>}
          </View>
        </View>
        {!isOwnProfile && (
          <FollowButton
            isFollowing={item.isFollowing}
            onPress={() => handleFollowToggle(item)}
            loading={followLoadingMap[item.userId]}
          />
        )}
      </TouchableOpacity>
    );
  };

  const currentList = activeTab === 'followers' ? followers : following;

  if (loading) {
    return (
      <SafeAreaView style={styles.loadingContainer} edges={['top']}>
        <LoadingSpinner />
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
          <Ionicons name="arrow-back" size={24} color={theme.colors.text.primary} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>
          {userNickname ? `@${userNickname}` : t('follow.followList')}
        </Text>
        <View style={styles.headerRight} />
      </View>

      {/* 탭 */}
      <View style={styles.tabContainer}>
        <TouchableOpacity
          style={[styles.tab, activeTab === 'followers' && styles.activeTab]}
          onPress={() => setActiveTab('followers')}
        >
          <Text
            style={[
              styles.tabText,
              activeTab === 'followers' && styles.activeTabText,
            ]}
          >
            {t('follow.followers')}
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tab, activeTab === 'following' && styles.activeTab]}
          onPress={() => setActiveTab('following')}
        >
          <Text
            style={[
              styles.tabText,
              activeTab === 'following' && styles.activeTabText,
            ]}
          >
            {t('follow.following')}
          </Text>
        </TouchableOpacity>
      </View>

      {/* 목록 */}
      <FlatList
        data={currentList}
        renderItem={renderUserItem}
        keyExtractor={(item) => item.userId}
        contentContainerStyle={styles.listContent}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Ionicons
              name="people-outline"
              size={64}
              color={theme.colors.gray[300]}
              style={styles.emptyIcon}
            />
            <Text style={styles.emptyText}>
              {activeTab === 'followers'
                ? t('follow.noFollowers')
                : t('follow.noFollowing')}
            </Text>
          </View>
        }
      />
    </SafeAreaView>
  );
}
