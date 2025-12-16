import React, { useEffect, useState, useMemo } from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  RefreshControl,
  Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery, useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { RootStackParamList } from '@/types/navigation.types';
import { ProfileHeader, FollowButton, ContentGrid } from '@/components/profile';
import { LoadingSpinner } from '@/components/common';
import { useAuthStore } from '@/stores/authStore';
import {
  getProfileByUserId,
  checkFollowing,
  followUser,
  unfollowUser,
} from '@/api/auth.api';
import { getUserContents } from '@/api/content.api';
import { UserProfile } from '@/types/auth.types';
import type { ContentResponse } from '@/types/content.types';
import { theme } from '@/theme';
import { withErrorHandling } from '@/utils/errorHandler';
import { createStyleSheet } from '@/utils/styles';
import { ReportModal } from '@/components/report/ReportModal';
import { ActionSheet, ActionSheetOption } from '@/components/common/ActionSheet';

type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'UserProfile'>;

/**
 * 다른 사용자 프로필 화면
 * 인스타그램 스타일의 사용자 프로필 보기
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
    height: 40,
    justifyContent: 'center',
    alignItems: 'center',
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

export default function UserProfileScreen() {
  const styles = useStyles();
  const { t } = useTranslation('profile');
  const navigation = useNavigation<NavigationProp>();
  const route = useRoute<RouteProp<RootStackParamList, 'UserProfile'>>();
  const { userId } = route.params;
  const { user: currentUser } = useAuthStore();
  const queryClient = useQueryClient();

  const [isFollowing, setIsFollowing] = useState(false);
  const [followStatusLoading, setFollowStatusLoading] = useState(true); // 팔로우 상태 로딩 중
  const [refreshing, setRefreshing] = useState(false);
  const [followLoading, setFollowLoading] = useState(false);
  const [showActionSheet, setShowActionSheet] = useState(false);
  const [showReportModal, setShowReportModal] = useState(false);

  // 사용자 프로필 조회 (React Query)
  const { data: profile, isLoading: profileLoading, refetch: refetchProfile } = useQuery({
    queryKey: ['userProfile', userId],
    queryFn: () => getProfileByUserId(userId),
    enabled: !!userId,
    staleTime: 1000 * 60 * 5,
    gcTime: 1000 * 60 * 30,
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
  });

  // 사용자 콘텐츠 목록 조회 (React Query - 무한 스크롤, 커서 기반 페이징)
  const {
    data: userContentsData,
    isLoading: contentsLoading,
    refetch: refetchContents,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: ['userContents', userId],
    queryFn: ({ pageParam }) => getUserContents(userId, pageParam ? { cursor: pageParam } : undefined),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.nextCursor : undefined,
    enabled: !!userId,
    staleTime: 1000 * 60 * 5,
    gcTime: 1000 * 60 * 30,
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
  });
  const userContents = userContentsData?.pages.flatMap(page => page.content) || [];

  // 팔로우 상태 로드
  const loadFollowStatus = async () => {
    // 본인이 아닐 때만 팔로우 상태 확인
    if (currentUser?.id !== userId && profile) {
      setFollowStatusLoading(true);
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
      setFollowStatusLoading(false);
    } else {
      setFollowStatusLoading(false);
    }
  };

  // 새로고침
  const handleRefresh = async () => {
    setRefreshing(true);
    await Promise.all([
      refetchProfile(),
      refetchContents(),
    ]);
    await loadFollowStatus();
    setRefreshing(false);
  };

  // 프로필 로드 후 팔로우 상태 확인
  useEffect(() => {
    if (profile && currentUser?.id !== userId) {
      loadFollowStatus();
    }
  }, [userId, profile?.id]); // profile 대신 profile.id 사용

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
      await refetchProfile();
    }

    setFollowLoading(false);
  };

  // 팔로워 목록으로 이동
  const handleFollowersPress = () => {
    navigation.navigate('FollowList', {
      userId,
      initialTab: 'followers',
    });
  };

  // 팔로잉 목록으로 이동
  const handleFollowingPress = () => {
    navigation.navigate('FollowList', {
      userId,
      initialTab: 'following',
    });
  };

  // 메시지 보내기 (추후 구현)
  const handleMessage = () => {
    Alert.alert(t('buttons.message'), t('message.comingSoon'));
  };

  // 콘텐츠 클릭 핸들러
  const handleContentPress = (content: ContentResponse) => {
    navigation.navigate('ContentViewer', { contentId: content.id });
  };

  // 액션 시트 옵션
  const actionSheetOptions: ActionSheetOption[] = useMemo(
    () => [
      {
        label: t('report.action'),
        icon: 'alert-circle-outline',
        onPress: () => setShowReportModal(true),
        destructive: true,
      },
      // 추후 추가 가능한 옵션들:
      // {
      //   label: '차단하기',
      //   icon: 'ban-outline',
      //   onPress: () => {},
      //   destructive: true,
      // },
    ],
    [t]
  );

  if (profileLoading || !profile) {
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
        {!isOwnProfile && (
          <TouchableOpacity
            style={styles.headerRight}
            onPress={() => setShowActionSheet(true)}
          >
            <Ionicons name="ellipsis-vertical" size={24} color={theme.colors.text.primary} />
          </TouchableOpacity>
        )}
        {isOwnProfile && <View style={styles.headerRight} />}
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
          contentCount={profile.contentCount}
          onFollowersPress={handleFollowersPress}
          onFollowingPress={handleFollowingPress}
        />

        {/* 액션 버튼들 */}
        {!isOwnProfile && (
          <View style={styles.actionsContainer}>
            <FollowButton
              isFollowing={isFollowing}
              onPress={handleFollowToggle}
              loading={followLoading || followStatusLoading}
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

        {/* 콘텐츠 그리드 */}
        <View style={styles.contentSection}>
          <View style={styles.contentHeader}>
            <Ionicons name="grid-outline" size={24} color={theme.colors.text.primary} />
          </View>
          <ContentGrid
            contents={userContents}
            loading={contentsLoading}
            onContentPress={handleContentPress}
            onEndReached={() => {
              if (hasNextPage && !isFetchingNextPage) {
                fetchNextPage();
              }
            }}
            isFetchingMore={isFetchingNextPage}
          />
        </View>
      </ScrollView>

      {/* 액션 시트 */}
      <ActionSheet
        visible={showActionSheet}
        onClose={() => setShowActionSheet(false)}
        options={actionSheetOptions}
      />

      {/* 신고 모달 */}
      <ReportModal
        visible={showReportModal}
        onClose={() => setShowReportModal(false)}
        targetType="user"
        targetId={userId}
        targetName={profile.nickname}
      />
    </SafeAreaView>
  );
}
