/**
 * 알림 목록 화면
 *
 * 사용자의 알림 목록을 보여주는 화면입니다.
 * - 무한 스크롤 (커서 기반 페이징)
 * - 스와이프로 삭제
 * - 모두 읽음 버튼
 * - 알림 클릭 시 해당 화면으로 이동
 */

import React, { useCallback, useEffect, useState } from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  RefreshControl,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useTranslation } from 'react-i18next';
import { Image } from 'expo-image';
import { Swipeable } from 'react-native-gesture-handler';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

import { theme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';
import { withErrorHandling } from '@/utils/errorHandler';
import { RootStackParamList } from '@/types/navigation.types';
import {
  getNotifications,
  markNotificationAsRead,
  markAllNotificationsAsRead,
  deleteNotification,
} from '@/api/notification.api';
import { useNotificationStore } from '@/stores/notificationStore';
import type { NotificationResponse, NotificationType, NotificationTargetType } from '@/types/notification.types';

type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'NotificationList'>;

const useStyles = createStyleSheet({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  backButton: {
    width: 40,
    height: 40,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: theme.spacing[2],
  },
  headerTitle: {
    fontSize: theme.typography.fontSize.xl,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
  },
  markAllButton: {
    paddingHorizontal: theme.spacing[3],
    paddingVertical: theme.spacing[2],
  },
  markAllText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.primary[500],
    fontWeight: theme.typography.fontWeight.medium,
  },
  listContent: {
    flexGrow: 1,
  },
  notificationItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
    backgroundColor: theme.colors.background.primary,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  unreadItem: {
    backgroundColor: theme.colors.primary[50],
  },
  avatarContainer: {
    position: 'relative',
    marginRight: theme.spacing[3],
  },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: theme.colors.gray[200],
    borderWidth: 2,
    borderColor: theme.colors.primary[600],
  },
  iconBadge: {
    position: 'absolute',
    bottom: -2,
    right: -2,
    width: 20,
    height: 20,
    borderRadius: 10,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 2,
    borderColor: theme.colors.background.primary,
  },
  notificationContent: {
    flex: 1,
  },
  notificationTitle: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    fontWeight: theme.typography.fontWeight.medium,
    marginBottom: theme.spacing[1],
  },
  notificationBody: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    marginBottom: theme.spacing[1],
  },
  notificationTime: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
  },
  unreadDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: theme.colors.primary[500],
    marginLeft: theme.spacing[2],
  },
  deleteAction: {
    backgroundColor: theme.colors.error,
    justifyContent: 'center',
    alignItems: 'center',
    width: 80,
  },
  deleteText: {
    color: theme.colors.text.inverse,
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.medium,
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[4],
  },
  emptyIcon: {
    marginBottom: theme.spacing[4],
  },
  emptyTitle: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[2],
  },
  emptySubtitle: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    textAlign: 'center',
  },
  loadingFooter: {
    paddingVertical: theme.spacing[4],
    alignItems: 'center',
  },
  settingsButton: {
    width: 40,
    height: 40,
    justifyContent: 'center',
    alignItems: 'center',
  },
});

/**
 * 알림 타입에 따른 아이콘 정보 반환
 */
function getNotificationIcon(type: NotificationType): {
  name: keyof typeof Ionicons.glyphMap;
  color: string;
  backgroundColor: string;
} {
  switch (type) {
    case 'LIKE':
      return {
        name: 'heart',
        color: '#ef4444',
        backgroundColor: '#fee2e2',
      };
    case 'COMMENT':
      return {
        name: 'chatbubble',
        color: '#3b82f6',
        backgroundColor: '#dbeafe',
      };
    case 'REPLY':
      return {
        name: 'chatbubble-ellipses',
        color: '#8b5cf6',
        backgroundColor: '#ede9fe',
      };
    case 'FOLLOW':
      return {
        name: 'person-add',
        color: '#22c55e',
        backgroundColor: '#dcfce7',
      };
    default:
      return {
        name: 'notifications',
        color: theme.colors.gray[500],
        backgroundColor: theme.colors.gray[100],
      };
  }
}

/**
 * 상대 시간 포맷 (예: 5분 전, 1시간 전)
 */
function formatRelativeTime(dateString: string): string {
  const now = new Date();
  const date = new Date(dateString);
  const diffMs = now.getTime() - date.getTime();
  const diffSeconds = Math.floor(diffMs / 1000);
  const diffMinutes = Math.floor(diffSeconds / 60);
  const diffHours = Math.floor(diffMinutes / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffSeconds < 60) {
    return '방금 전';
  }
  if (diffMinutes < 60) {
    return `${diffMinutes}분 전`;
  }
  if (diffHours < 24) {
    return `${diffHours}시간 전`;
  }
  if (diffDays < 7) {
    return `${diffDays}일 전`;
  }
  return date.toLocaleDateString('ko-KR', {
    month: 'short',
    day: 'numeric',
  });
}

/**
 * 알림 목록 화면
 */
export default function NotificationListScreen() {
  const styles = useStyles();
  const navigation = useNavigation<NavigationProp>();
  const { t } = useTranslation('notification');

  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [hasNext, setHasNext] = useState(true);

  const { resetUnreadCount, decrementUnreadCount } = useNotificationStore();

  /**
   * 알림 목록 불러오기
   */
  const loadNotifications = useCallback(
    async (cursor?: number) => {
      const result = await withErrorHandling(
        () => getNotifications(cursor, 20),
        {
          showAlert: true,
          alertTitle: t('error.loadFailed'),
          logContext: 'NotificationListScreen.loadNotifications',
        }
      );

      if (result) {
        if (cursor) {
          setNotifications((prev) => [...prev, ...result.notifications]);
        } else {
          setNotifications(result.notifications);
        }
        setNextCursor(result.nextCursor);
        setHasNext(result.hasNext);
      }
    },
    [t]
  );

  /**
   * 초기 로딩
   */
  useEffect(() => {
    const init = async () => {
      setIsLoading(true);
      await loadNotifications();
      setIsLoading(false);
    };
    init();
  }, [loadNotifications]);

  /**
   * 새로고침
   */
  const handleRefresh = useCallback(async () => {
    setIsRefreshing(true);
    await loadNotifications();
    setIsRefreshing(false);
  }, [loadNotifications]);

  /**
   * 무한 스크롤 - 더 불러오기
   */
  const handleLoadMore = useCallback(async () => {
    if (!hasNext || isLoadingMore || nextCursor === null) return;

    setIsLoadingMore(true);
    await loadNotifications(nextCursor);
    setIsLoadingMore(false);
  }, [hasNext, isLoadingMore, nextCursor, loadNotifications]);

  /**
   * 알림 클릭 처리
   */
  const handleNotificationPress = useCallback(
    async (notification: NotificationResponse) => {
      // 읽음 처리
      if (!notification.isRead) {
        await markNotificationAsRead(notification.id);
        setNotifications((prev) =>
          prev.map((n) =>
            n.id === notification.id ? { ...n, isRead: true } : n
          )
        );
        decrementUnreadCount();
      }

      // 화면 이동
      if (notification.targetType && notification.targetId) {
        switch (notification.targetType) {
          case 'CONTENT':
            navigation.navigate('Main', {
              screen: 'Feed',
              params: {
                screen: 'ContentViewer',
                params: { contentId: notification.targetId },
              },
            });
            break;
          case 'USER':
            navigation.navigate('Main', {
              screen: 'Profile',
              params: {
                screen: 'UserProfile',
                params: { userId: notification.targetId },
              },
            });
            break;
          case 'COMMENT':
            // 댓글의 경우 일단 알림 목록에서 처리
            break;
        }
      }
    },
    [navigation, decrementUnreadCount]
  );

  /**
   * 알림 삭제
   */
  const handleDelete = useCallback(
    async (notificationId: number) => {
      const result = await withErrorHandling(
        () => deleteNotification(notificationId),
        {
          showAlert: true,
          alertTitle: t('error.deleteFailed'),
          logContext: 'NotificationListScreen.handleDelete',
        }
      );

      if (result !== null) {
        setNotifications((prev) =>
          prev.filter((n) => n.id !== notificationId)
        );
      }
    },
    [t]
  );

  /**
   * 모두 읽음 처리
   */
  const handleMarkAllAsRead = useCallback(async () => {
    const result = await withErrorHandling(
      () => markAllNotificationsAsRead(),
      {
        showAlert: true,
        alertTitle: t('error.markAllFailed'),
        logContext: 'NotificationListScreen.handleMarkAllAsRead',
      }
    );

    if (result !== null) {
      setNotifications((prev) =>
        prev.map((n) => ({ ...n, isRead: true }))
      );
      resetUnreadCount();
    }
  }, [t, resetUnreadCount]);

  /**
   * 스와이프 삭제 액션 렌더링
   */
  const renderRightActions = useCallback(
    (notificationId: number) => (
      <TouchableOpacity
        style={styles.deleteAction}
        onPress={() => handleDelete(notificationId)}
      >
        <Ionicons name="trash-outline" size={24} color="white" />
        <Text style={styles.deleteText}>{t('button.delete')}</Text>
      </TouchableOpacity>
    ),
    [styles, handleDelete, t]
  );

  /**
   * 알림 아이템 렌더링
   */
  const renderNotificationItem = useCallback(
    ({ item }: { item: NotificationResponse }) => {
      const iconInfo = getNotificationIcon(item.type);

      return (
        <Swipeable
          renderRightActions={() => renderRightActions(item.id)}
          overshootRight={false}
        >
          <TouchableOpacity
            style={[
              styles.notificationItem,
              !item.isRead && styles.unreadItem,
            ]}
            onPress={() => handleNotificationPress(item)}
            activeOpacity={0.7}
          >
            {/* 프로필 이미지 및 아이콘 */}
            <View style={styles.avatarContainer}>
              {item.actorProfileImageUrl ? (
                <Image
                  source={{ uri: item.actorProfileImageUrl }}
                  style={styles.avatar}
                  contentFit="cover"
                />
              ) : (
                <View style={[styles.avatar, { backgroundColor: theme.colors.gray[300] }]}>
                  <Ionicons
                    name="person"
                    size={24}
                    color={theme.colors.gray[500]}
                  />
                </View>
              )}
              <View
                style={[
                  styles.iconBadge,
                  { backgroundColor: iconInfo.backgroundColor },
                ]}
              >
                <Ionicons
                  name={iconInfo.name}
                  size={12}
                  color={iconInfo.color}
                />
              </View>
            </View>

            {/* 알림 내용 */}
            <View style={styles.notificationContent}>
              <Text style={styles.notificationTitle} numberOfLines={1}>
                {item.title}
              </Text>
              <Text style={styles.notificationBody} numberOfLines={2}>
                {item.body}
              </Text>
              <Text style={styles.notificationTime}>
                {formatRelativeTime(item.createdAt)}
              </Text>
            </View>

            {/* 읽지 않음 표시 */}
            {!item.isRead && <View style={styles.unreadDot} />}
          </TouchableOpacity>
        </Swipeable>
      );
    },
    [styles, handleNotificationPress, renderRightActions]
  );

  /**
   * 빈 목록 렌더링
   */
  const renderEmptyComponent = useCallback(
    () => (
      <View style={styles.emptyContainer}>
        <Ionicons
          name="notifications-off-outline"
          size={64}
          color={theme.colors.gray[300]}
          style={styles.emptyIcon}
        />
        <Text style={styles.emptyTitle}>{t('empty.title')}</Text>
        <Text style={styles.emptySubtitle}>{t('empty.subtitle')}</Text>
      </View>
    ),
    [styles, t]
  );

  /**
   * 로딩 푸터 렌더링
   */
  const renderFooter = useCallback(() => {
    if (!isLoadingMore) return null;
    return (
      <View style={styles.loadingFooter}>
        <ActivityIndicator color={theme.colors.primary[500]} />
      </View>
    );
  }, [isLoadingMore, styles]);

  if (isLoading) {
    return (
      <SafeAreaView style={styles.container} edges={['top']}>
        <View style={styles.header}>
          <View style={styles.headerLeft}>
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
        </View>
        <View style={styles.emptyContainer}>
          <ActivityIndicator size="large" color={theme.colors.primary[500]} />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaView style={styles.container} edges={['top']}>
        {/* 헤더 */}
        <View style={styles.header}>
          <View style={styles.headerLeft}>
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
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            {notifications.some((n) => !n.isRead) && (
              <TouchableOpacity
                onPress={handleMarkAllAsRead}
                style={styles.markAllButton}
              >
                <Text style={styles.markAllText}>{t('button.markAllRead')}</Text>
              </TouchableOpacity>
            )}
            <TouchableOpacity
              onPress={() => navigation.navigate('NotificationSettings')}
              style={styles.settingsButton}
            >
              <Ionicons
                name="settings-outline"
                size={22}
                color={theme.colors.text.secondary}
              />
            </TouchableOpacity>
          </View>
        </View>

        {/* 알림 목록 */}
        <FlatList
          data={notifications}
          keyExtractor={(item) => item.id.toString()}
          renderItem={renderNotificationItem}
          contentContainerStyle={styles.listContent}
          refreshControl={
            <RefreshControl
              refreshing={isRefreshing}
              onRefresh={handleRefresh}
              tintColor={theme.colors.primary[500]}
            />
          }
          onEndReached={handleLoadMore}
          onEndReachedThreshold={0.5}
          ListEmptyComponent={renderEmptyComponent}
          ListFooterComponent={renderFooter}
        />
      </SafeAreaView>
    </GestureHandlerRootView>
  );
}
