import React, { useEffect, useState, useCallback } from 'react';
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
import { useNavigation, CompositeNavigationProp, useFocusEffect } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import { useQuery } from '@tanstack/react-query';
import { RootStackParamList, MainTabParamList } from '@/types/navigation.types';
import { ProfileHeader, ContentGrid } from '@/components/profile';
import { Button, LoadingSpinner } from '@/components/common';
import { useAuthStore } from '@/stores/authStore';
import { getMyProfile } from '@/api/auth.api';
import { getMyContents } from '@/api/content.api';
import { theme } from '@/theme';
import { withErrorHandling } from '@/utils/errorHandler';
import { createStyleSheet } from '@/utils/styles';
import type { ContentResponse } from '@/types/content.types';

type NavigationProp = CompositeNavigationProp<
  BottomTabNavigationProp<MainTabParamList, 'Profile'>,
  NativeStackNavigationProp<RootStackParamList>
>;

/**
 * 내 프로필 화면
 * 인스타그램 스타일의 프로필 관리 화면
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
  headerTitle: {
    fontSize: theme.typography.fontSize.xl,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
  },
  headerIcons: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  iconButton: {
    padding: theme.spacing[2],
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
  editButton: {
    flex: 1,
  },
  editButtonText: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.semibold,
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
    marginBottom: theme.spacing[1],
  },
  emptySubtext: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.normal,
    color: theme.colors.text.tertiary,
    textAlign: 'center',
  },
});

export default function ProfileScreen() {
  const styles = useStyles();
  const navigation = useNavigation<NavigationProp>();
  const { profile: storeProfile, user, updateProfile } = useAuthStore();

  // 프로필 데이터 로드 (React Query)
  const { data: profile, isLoading: profileLoading, refetch: refetchProfile } = useQuery({
    queryKey: ['myProfile'],
    queryFn: async () => {
      const data = await getMyProfile();
      updateProfile(data);
      return data;
    },
    initialData: storeProfile || undefined,
    staleTime: 1000 * 60 * 5, // 5분간 신선한 상태 유지
  });

  // 콘텐츠 목록 로드 (React Query - 캐싱 활성화)
  const { data: contents = [], isLoading: contentsLoading, refetch: refetchContents } = useQuery({
    queryKey: ['myContents'],
    queryFn: getMyContents,
    staleTime: 1000 * 60 * 5, // 5분간 신선한 상태 유지 (다시 로드하지 않음)
    gcTime: 1000 * 60 * 30, // 30분간 캐시 유지
  });

  // 새로고침
  const [refreshing, setRefreshing] = useState(false);
  const handleRefresh = async () => {
    setRefreshing(true);
    await Promise.all([refetchProfile(), refetchContents()]);
    setRefreshing(false);
  };

  // 프로필 수정 화면으로 이동
  const handleEditProfile = () => {
    navigation.navigate('EditProfile');
  };

  // 설정 화면으로 이동
  const handleSettings = () => {
    // TODO: 설정 화면 구현 후 연결
    Alert.alert('설정', '설정 화면은 추후 구현 예정입니다.');
  };

  // 팔로워 목록으로 이동
  const handleFollowersPress = () => {
    if (!user?.id) return;
    navigation.navigate('FollowList', {
      userId: user.id,
      initialTab: 'followers',
    });
  };

  // 팔로잉 목록으로 이동
  const handleFollowingPress = () => {
    if (!user?.id) return;
    navigation.navigate('FollowList', {
      userId: user.id,
      initialTab: 'following',
    });
  };

  // 콘텐츠 클릭 핸들러
  const handleContentPress = (content: ContentResponse) => {
    navigation.navigate('ContentViewer', { contentId: content.id });
  };

  if (profileLoading || !profile) {
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
        <Text style={styles.headerTitle}>{profile.nickname}</Text>
        <View style={styles.headerIcons}>
          <TouchableOpacity onPress={handleSettings} style={styles.iconButton}>
            <Ionicons name="settings-outline" size={24} color={theme.colors.text.primary} />
          </TouchableOpacity>
        </View>
      </View>

      <ScrollView
        style={styles.scrollView}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />}
        showsVerticalScrollIndicator={false}
      >
        {/* 프로필 헤더 */}
        <ProfileHeader
          profile={profile}
          isOwnProfile={true}
          onFollowersPress={handleFollowersPress}
          onFollowingPress={handleFollowingPress}
        />

        {/* 액션 버튼들 */}
        <View style={styles.actionsContainer}>
          <Button
            variant="outline"
            onPress={handleEditProfile}
            style={styles.editButton}
            textStyle={styles.editButtonText}
          >
            프로필 수정
          </Button>

        </View>

        {/* 콘텐츠 그리드 */}
        <View style={styles.contentSection}>
          <View style={styles.contentHeader}>
            <Ionicons name="grid-outline" size={24} color={theme.colors.text.primary} />
          </View>
          {contentsLoading ? (
            <View style={styles.emptyContent}>
              <LoadingSpinner />
            </View>
          ) : (
            <ContentGrid contents={contents} onContentPress={handleContentPress} />
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
