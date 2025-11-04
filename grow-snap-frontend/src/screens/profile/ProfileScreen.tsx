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
  const { profile: storeProfile, user, updateProfile, logout } = useAuthStore();
  const [profile, setProfile] = useState(storeProfile);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [contents, setContents] = useState<ContentResponse[]>([]);
  const [contentsLoading, setContentsLoading] = useState(false);

  // 프로필 데이터 로드
  const loadProfile = async (showLoading = true) => {
    if (showLoading) setLoading(true);

    const result = await withErrorHandling(
      async () => {
        const data = await getMyProfile();
        setProfile(data);
        updateProfile(data);
        return data;
      },
      {
        showAlert: true,
        alertTitle: '프로필 조회 실패',
        logContext: 'ProfileScreen.loadProfile',
      }
    );

    if (showLoading) setLoading(false);
    return result;
  };

  // 콘텐츠 목록 로드
  const loadContents = async (showLoading = true) => {
    if (showLoading) setContentsLoading(true);

    const result = await withErrorHandling(
      async () => {
        const data = await getMyContents();
        setContents(data);
        return data;
      },
      {
        showAlert: true,
        alertTitle: '콘텐츠 조회 실패',
        logContext: 'ProfileScreen.loadContents',
      }
    );

    if (showLoading) setContentsLoading(false);
    return result;
  };

  // 새로고침
  const handleRefresh = async () => {
    setRefreshing(true);
    await Promise.all([loadProfile(false), loadContents(false)]);
    setRefreshing(false);
  };

  // 화면이 포커스될 때마다 프로필 및 콘텐츠 리로드
  useFocusEffect(
    useCallback(() => {
      // 화면에 진입할 때 프로필 및 콘텐츠 새로고침
      loadProfile(false);
      loadContents(false);
    }, [])
  );

  // 초기 로드
  useEffect(() => {
    if (!profile) {
      loadProfile();
    }
    // 콘텐츠는 항상 로드
    loadContents();
  }, []);

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


  if (loading || !profile) {
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
