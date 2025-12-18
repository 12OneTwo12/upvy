/**
 * 차단 관리 화면
 *
 * 차단한 사용자 및 콘텐츠 목록을 관리하는 화면
 * - 탭으로 사용자/콘텐츠 구분
 * - 무한 스크롤 (커서 기반 페이지네이션)
 * - 차단 해제 기능
 */

import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  FlatList,
  Image,
  ActivityIndicator,
  Alert,
  RefreshControl,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';
import { getBlockedUsers, getBlockedContents, unblockUser, unblockContent } from '@/api/block.api';
import type { BlockedUser, BlockedContent } from '@/types/block.types';

type TabType = 'users' | 'contents';

export const BlockManagementScreen: React.FC = () => {
  const styles = useStyles();
  const dynamicTheme = useTheme();
  const { t } = useTranslation('interactions');
  const { t: tCommon } = useTranslation('common');
  const navigation = useNavigation();
  const [selectedTab, setSelectedTab] = useState<TabType>('users');
  const [blockedUsers, setBlockedUsers] = useState<BlockedUser[]>([]);
  const [blockedContents, setBlockedContents] = useState<BlockedContent[]>([]);
  const [usersCursor, setUsersCursor] = useState<string | undefined>();
  const [contentsCursor, setContentsCursor] = useState<string | undefined>();
  const [usersHasNext, setUsersHasNext] = useState(false);
  const [contentsHasNext, setContentsHasNext] = useState(false);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  // 차단한 사용자 목록 로드
  const loadBlockedUsers = useCallback(async (cursor?: string, isRefresh = false) => {
    if (loading) return;

    setLoading(true);
    try {
      const response = await getBlockedUsers(cursor);

      if (isRefresh) {
        setBlockedUsers(response.content);
      } else {
        setBlockedUsers((prev) => [...prev, ...response.content]);
      }

      setUsersCursor(response.nextCursor || undefined);
      setUsersHasNext(response.hasNext);
    } catch (error) {
      Alert.alert(t('blockManagement.error.loadTitle'), t('blockManagement.error.loadMessage'));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [t]);

  // 차단한 콘텐츠 목록 로드
  const loadBlockedContents = useCallback(async (cursor?: string, isRefresh = false) => {
    if (loading) return;

    setLoading(true);
    try {
      const response = await getBlockedContents(cursor);

      if (isRefresh) {
        setBlockedContents(response.content);
      } else {
        setBlockedContents((prev) => [...prev, ...response.content]);
      }

      setContentsCursor(response.nextCursor || undefined);
      setContentsHasNext(response.hasNext);
    } catch (error) {
      Alert.alert(t('blockManagement.error.loadTitle'), t('blockManagement.error.loadMessage'));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [t]);

  // 초기 로드
  React.useEffect(() => {
    if (selectedTab === 'users' && blockedUsers.length === 0) {
      loadBlockedUsers();
    } else if (selectedTab === 'contents' && blockedContents.length === 0) {
      loadBlockedContents();
    }
  }, [selectedTab]);

  // 새로고침
  const handleRefresh = () => {
    setRefreshing(true);
    if (selectedTab === 'users') {
      loadBlockedUsers(undefined, true);
    } else {
      loadBlockedContents(undefined, true);
    }
  };

  // 더 불러오기
  const handleLoadMore = () => {
    if (selectedTab === 'users' && usersHasNext && !loading) {
      loadBlockedUsers(usersCursor);
    } else if (selectedTab === 'contents' && contentsHasNext && !loading) {
      loadBlockedContents(contentsCursor);
    }
  };

  // 사용자 차단 해제
  const handleUnblockUser = (userId: string, nickname: string) => {
    Alert.alert(
      t('blockManagement.unblock.userTitle'),
      t('blockManagement.unblock.userConfirm', { name: `@${nickname}` }),
      [
        { text: tCommon('button.cancel'), style: 'cancel' },
        {
          text: t('block.unblock'),
          onPress: async () => {
            try {
              await unblockUser(userId);
              setBlockedUsers((prev) => prev.filter((user) => user.userId !== userId));
              Alert.alert(tCommon('button.done'), t('blockManagement.unblock.userSuccess'));
            } catch (error) {
              Alert.alert(t('blockManagement.error.loadTitle'), t('blockManagement.unblock.error'));
            }
          },
        },
      ]
    );
  };

  // 콘텐츠 차단 해제
  const handleUnblockContent = (contentId: string, title: string) => {
    Alert.alert(
      t('blockManagement.unblock.contentTitle'),
      t('blockManagement.unblock.contentConfirm', { title }),
      [
        { text: tCommon('button.cancel'), style: 'cancel' },
        {
          text: t('block.unblock'),
          onPress: async () => {
            try {
              await unblockContent(contentId);
              setBlockedContents((prev) => prev.filter((content) => content.contentId !== contentId));
              Alert.alert(tCommon('button.done'), t('blockManagement.unblock.contentSuccess'));
            } catch (error) {
              Alert.alert(t('blockManagement.error.loadTitle'), t('blockManagement.unblock.error'));
            }
          },
        },
      ]
    );
  };

  // 사용자 아이템 렌더링
  const renderUserItem = ({ item }: { item: BlockedUser }) => (
    <View style={styles.item}>
      <View style={styles.itemLeft}>
        {item.profileImageUrl ? (
          <Image source={{ uri: item.profileImageUrl }} style={styles.profileImage} />
        ) : (
          <View style={styles.profilePlaceholder}>
            <Ionicons name="person" size={24} color={dynamicTheme.colors.text.tertiary} />
          </View>
        )}
        <View style={styles.itemInfo}>
          <Text style={styles.itemTitle}>{item.nickname}</Text>
          <Text style={styles.itemSubtitle}>
            {new Date(item.blockedAt).toLocaleDateString('ko-KR')}
          </Text>
        </View>
      </View>
      <TouchableOpacity
        style={styles.unblockButton}
        onPress={() => handleUnblockUser(item.userId, item.nickname)}
      >
        <Text style={styles.unblockButtonText}>{t('block.unblock')}</Text>
      </TouchableOpacity>
    </View>
  );

  // 콘텐츠 아이템 렌더링
  const renderContentItem = ({ item }: { item: BlockedContent }) => (
    <View style={styles.item}>
      <View style={styles.itemLeft}>
        <Image source={{ uri: item.thumbnailUrl }} style={styles.thumbnail} />
        <View style={styles.itemInfo}>
          <Text style={styles.itemTitle} numberOfLines={1}>
            {item.title}
          </Text>
          <Text style={styles.itemSubtitle}>
            {item.creatorNickname} • {new Date(item.blockedAt).toLocaleDateString('ko-KR')}
          </Text>
        </View>
      </View>
      <TouchableOpacity
        style={styles.unblockButton}
        onPress={() => handleUnblockContent(item.contentId, item.title)}
      >
        <Text style={styles.unblockButtonText}>{t('block.unblock')}</Text>
      </TouchableOpacity>
    </View>
  );

  const renderEmpty = () => (
    <View style={styles.emptyContainer}>
      <Ionicons
        name={selectedTab === 'users' ? 'people-outline' : 'grid-outline'}
        size={64}
        color={dynamicTheme.colors.text.tertiary}
      />
      <Text style={styles.emptyText}>
        {t(`blockManagement.empty.${selectedTab}`)}
      </Text>
    </View>
  );

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
            color={dynamicTheme.colors.text.primary}
          />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{t('blockManagement.title')}</Text>
      </View>

      {/* 탭 */}
      <View style={styles.tabContainer}>
        <TouchableOpacity
          style={[styles.tab, selectedTab === 'users' && styles.tabActive]}
          onPress={() => setSelectedTab('users')}
        >
          <Text style={[styles.tabText, selectedTab === 'users' && styles.tabTextActive]}>
            {t('blockManagement.tabs.users')}
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tab, selectedTab === 'contents' && styles.tabActive]}
          onPress={() => setSelectedTab('contents')}
        >
          <Text style={[styles.tabText, selectedTab === 'contents' && styles.tabTextActive]}>
            {t('blockManagement.tabs.contents')}
          </Text>
        </TouchableOpacity>
      </View>

      {/* 목록 */}
      {selectedTab === 'users' ? (
        <FlatList
          data={blockedUsers}
          renderItem={renderUserItem}
          keyExtractor={(item) => item.userId}
          ListEmptyComponent={!loading ? renderEmpty : null}
          onEndReached={handleLoadMore}
          onEndReachedThreshold={0.5}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
          }
          ListFooterComponent={
            loading && blockedUsers.length > 0 ? (
              <ActivityIndicator style={styles.loader} />
            ) : null
          }
        />
      ) : (
        <FlatList
          data={blockedContents}
          renderItem={renderContentItem}
          keyExtractor={(item) => item.contentId}
          ListEmptyComponent={!loading ? renderEmpty : null}
          onEndReached={handleLoadMore}
          onEndReachedThreshold={0.5}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
          }
          ListFooterComponent={
            loading && blockedContents.length > 0 ? (
              <ActivityIndicator style={styles.loader} />
            ) : null
          }
        />
      )}
    </SafeAreaView>
  );
};

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
  tabContainer: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  tab: {
    flex: 1,
    paddingVertical: theme.spacing[4],
    alignItems: 'center',
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },
  tabActive: {
    borderBottomColor: theme.colors.primary[500],
  },
  tabText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.medium,
    color: theme.colors.text.secondary,
  },
  tabTextActive: {
    color: theme.colors.primary[500],
    fontWeight: theme.typography.fontWeight.bold,
  },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: theme.spacing[4],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  itemLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  profileImage: {
    width: 48,
    height: 48,
    borderRadius: 24,
  },
  profilePlaceholder: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: theme.colors.background.secondary,
    justifyContent: 'center',
    alignItems: 'center',
  },
  thumbnail: {
    width: 48,
    height: 48,
    borderRadius: theme.borderRadius.sm,
  },
  itemInfo: {
    marginLeft: theme.spacing[3],
    flex: 1,
  },
  itemTitle: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[1],
  },
  itemSubtitle: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
  },
  unblockButton: {
    paddingHorizontal: theme.spacing[3],
    paddingVertical: theme.spacing[2],
    borderRadius: theme.borderRadius.md,
    borderWidth: 1,
    borderColor: theme.colors.border.light,
  },
  unblockButtonText: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.medium,
    color: theme.colors.text.primary,
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: theme.spacing[10],
  },
  emptyText: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.secondary,
    marginTop: theme.spacing[3],
  },
  loader: {
    paddingVertical: theme.spacing[4],
  },
}));
