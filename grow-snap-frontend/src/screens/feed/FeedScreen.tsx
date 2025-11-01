/**
 * 피드 화면
 *
 * Instagram Reels 스타일의 세로 스크롤 숏폼 비디오 피드
 * - FlatList로 무한 스크롤 구현
 * - 추천/팔로잉 탭 전환
 * - 커서 기반 페이지네이션
 */

import React, { useState, useRef, useCallback, useEffect } from 'react';
import {
  View,
  FlatList,
  Dimensions,
  TouchableOpacity,
  Text,
  ActivityIndicator,
  StatusBar,
  NativeScrollEvent,
  NativeSyntheticEvent,
} from 'react-native';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigation } from '@react-navigation/native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { FeedItem } from '@/components/feed';
import { CommentModal } from '@/components/comment';
import { getMainFeed, getFollowingFeed, refreshFeed as refreshFeedApi } from '@/api/feed.api';
import { createLike, deleteLike } from '@/api/like.api';
import { createSave, deleteSave } from '@/api/save.api';
import { shareContent } from '@/api/share.api';
import { followUser, unfollowUser } from '@/api/follow.api';
import type { FeedTab, FeedItem as FeedItemType } from '@/types/feed.types';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

export default function FeedScreen() {
  const insets = useSafeAreaInsets();
  const [currentTab, setCurrentTab] = useState<FeedTab>('recommended');
  const [currentIndex, setCurrentIndex] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  const [autoRefreshing, setAutoRefreshing] = useState(false);
  const [pullDistance, setPullDistance] = useState(0);
  const [commentModalVisible, setCommentModalVisible] = useState(false);
  const [selectedContentId, setSelectedContentId] = useState<string | null>(null);
  const flatListRef = useRef<FlatList>(null);
  const scrollYRef = useRef(0);
  const hasAutoRefreshed = useRef(false);

  // Video 로드 상태를 contentId별로 캐싱 (FlatList 재활용과 무관하게 유지)
  const videoLoadedCache = useRef<Map<string, boolean>>(new Map());

  const queryClient = useQueryClient();
  const navigation = useNavigation();

  // 피드 데이터 fetching (무한 스크롤)
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
    isLoading,
  } = useInfiniteQuery({
    queryKey: ['feed', currentTab],
    queryFn: ({ pageParam }) => {
      const fetchFn = currentTab === 'recommended' ? getMainFeed : getFollowingFeed;
      return fetchFn({ cursor: pageParam, limit: 10 });
    },
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.nextCursor : undefined,
    initialPageParam: null as string | null,
  });

  // 피드 새로고침 mutation
  const refreshMutation = useMutation({
    mutationFn: refreshFeedApi,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['feed'] });
      refetch();
    },
  });

  // 좋아요 mutation (Optimistic update)
  const likeMutation = useMutation({
    mutationFn: async ({ contentId, isLiked }: { contentId: string; isLiked: boolean }) => {
      if (isLiked) {
        return await deleteLike(contentId);
      } else {
        return await createLike(contentId);
      }
    },
    onMutate: async ({ contentId }) => {
      // Optimistic update
      await queryClient.cancelQueries({ queryKey: ['feed', currentTab] });

      const previousData = queryClient.getQueryData(['feed', currentTab]);

      queryClient.setQueryData(['feed', currentTab], (old: any) => {
        if (!old) return old;
        return {
          ...old,
          pages: old.pages.map((page: any) => ({
            ...page,
            content: page.content.map((item: FeedItemType) =>
              item.contentId === contentId
                ? {
                    ...item,
                    interactions: {
                      ...item.interactions,
                      isLiked: !item.interactions.isLiked,
                      likeCount: item.interactions.isLiked
                        ? item.interactions.likeCount - 1
                        : item.interactions.likeCount + 1,
                    },
                  }
                : item
            ),
          })),
        };
      });

      return { previousData };
    },
    onSuccess: (response) => {
      // 백엔드 응답으로 정확한 값 업데이트
      queryClient.setQueryData(['feed', currentTab], (old: any) => {
        if (!old) return old;
        return {
          ...old,
          pages: old.pages.map((page: any) => ({
            ...page,
            content: page.content.map((item: FeedItemType) =>
              item.contentId === response.contentId
                ? {
                    ...item,
                    interactions: {
                      ...item.interactions,
                      isLiked: response.isLiked,
                      likeCount: response.likeCount,
                    },
                  }
                : item
            ),
          })),
        };
      });
    },
    onError: (err, variables, context) => {
      // Rollback on error
      if (context?.previousData) {
        queryClient.setQueryData(['feed', currentTab], context.previousData);
      }
    },
  });

  // 저장 mutation (Optimistic update)
  const saveMutation = useMutation({
    mutationFn: async ({ contentId, isSaved }: { contentId: string; isSaved: boolean }) => {
      if (isSaved) {
        return await deleteSave(contentId);
      } else {
        return await createSave(contentId);
      }
    },
    onMutate: async ({ contentId }) => {
      await queryClient.cancelQueries({ queryKey: ['feed', currentTab] });

      const previousData = queryClient.getQueryData(['feed', currentTab]);

      queryClient.setQueryData(['feed', currentTab], (old: any) => {
        if (!old) return old;
        return {
          ...old,
          pages: old.pages.map((page: any) => ({
            ...page,
            content: page.content.map((item: FeedItemType) =>
              item.contentId === contentId
                ? {
                    ...item,
                    interactions: {
                      ...item.interactions,
                      isSaved: !item.interactions.isSaved,
                      saveCount: item.interactions.isSaved
                        ? item.interactions.saveCount - 1
                        : item.interactions.saveCount + 1,
                    },
                  }
                : item
            ),
          })),
        };
      });

      return { previousData };
    },
    onSuccess: (response) => {
      // 백엔드 응답으로 정확한 값 업데이트
      queryClient.setQueryData(['feed', currentTab], (old: any) => {
        if (!old) return old;
        return {
          ...old,
          pages: old.pages.map((page: any) => ({
            ...page,
            content: page.content.map((item: FeedItemType) =>
              item.contentId === response.contentId
                ? {
                    ...item,
                    interactions: {
                      ...item.interactions,
                      isSaved: response.isSaved,
                      saveCount: response.saveCount,
                    },
                  }
                : item
            ),
          })),
        };
      });
    },
    onError: (err, variables, context) => {
      if (context?.previousData) {
        queryClient.setQueryData(['feed', currentTab], context.previousData);
      }
    },
  });

  // 팔로우 mutation (Optimistic update)
  const followMutation = useMutation({
    mutationFn: async ({ userId, isFollowing }: { userId: string; isFollowing: boolean }) => {
      if (isFollowing) {
        await unfollowUser(userId);
      } else {
        await followUser(userId);
      }
    },
    onMutate: async ({ userId }) => {
      await queryClient.cancelQueries({ queryKey: ['feed', currentTab] });

      const previousData = queryClient.getQueryData(['feed', currentTab]);

      queryClient.setQueryData(['feed', currentTab], (old: any) => {
        if (!old) return old;
        return {
          ...old,
          pages: old.pages.map((page: any) => ({
            ...page,
            content: page.content.map((item: FeedItemType) =>
              item.creator.userId === userId
                ? {
                    ...item,
                    creator: {
                      ...item.creator,
                      isFollowing: !item.creator.isFollowing,
                    },
                  }
                : item
            ),
          })),
        };
      });

      return { previousData };
    },
    onError: (err, variables, context) => {
      if (context?.previousData) {
        queryClient.setQueryData(['feed', currentTab], context.previousData);
      }
    },
  });

  // 모든 페이지의 콘텐츠를 평탄화 + uniqueKey 추가
  const feedItems: (FeedItemType & { uniqueKey: string })[] =
    data?.pages.flatMap((page, pageIndex) =>
      page.content.map((item, itemIndex) => ({
        ...item,
        uniqueKey: `${pageIndex}-${itemIndex}-${item.contentId}`,
      }))
    ) ?? [];

  // 로딩 중일 때 보여줄 스켈레톤 아이템
  const loadingFeedItem: FeedItemType & { uniqueKey: string } = {
    contentId: 'loading',
    contentType: 'VIDEO',
    url: '',
    photoUrls: null,
    thumbnailUrl: '',
    duration: 0,
    width: 1080,
    height: 1920,
    title: '',
    description: '',
    category: 'TECH',
    tags: [],
    creator: {
      userId: 'loading',
      nickname: '',
      profileImageUrl: null,
    },
    interactions: {
      likeCount: 0,
      commentCount: 0,
      saveCount: 0,
      shareCount: 0,
    },
    subtitles: [],
    uniqueKey: 'loading-0-loading',
  };

  // 데이터 없거나 로딩중 -> 스켈레톤, 실제 데이터 있음 -> 실제 데이터 표시
  // 자동 새로고침 중일 때도 스켈레톤 표시
  const displayItems = (isLoading || feedItems.length === 0 || autoRefreshing) ? [loadingFeedItem] : feedItems;

  // 스크롤 이벤트: 현재 보이는 아이템 인덱스 추적
  const onViewableItemsChanged = useRef(({ viewableItems }: any) => {
    if (viewableItems.length > 0) {
      const currentIdx = viewableItems[0].index ?? 0;
      setCurrentIndex(currentIdx);
    }
  }).current;

  const viewabilityConfig = useRef({
    itemVisiblePercentThreshold: 80,
  }).current;

  // 무한 스크롤 최적화: 끝에서 2개 전에 다음 페이지 미리 로드
  useEffect(() => {
    if (hasNextPage && !isFetchingNextPage && displayItems.length > 0) {
      const distanceFromEnd = displayItems.length - currentIndex;
      if (distanceFromEnd <= 2) {
        fetchNextPage();
      }
    }
  }, [currentIndex, hasNextPage, isFetchingNextPage, displayItems.length, fetchNextPage]);

  // 콘텐츠 끝 도달 시 자동 새로고침
  useEffect(() => {
    const performAutoRefresh = async () => {
      // 마지막 아이템에 도달했고, 다음 페이지가 없고, 아직 자동 새로고침 안했을 때
      if (
        !hasNextPage &&
        !isFetchingNextPage &&
        !isLoading &&
        displayItems.length > 0 &&
        currentIndex >= displayItems.length - 1 &&
        !hasAutoRefreshed.current &&
        !autoRefreshing
      ) {
        hasAutoRefreshed.current = true;
        setAutoRefreshing(true);

        // 스켈레톤 화면 잠깐 표시 (1초)
        await new Promise((resolve) => setTimeout(resolve, 1000));

        try {
          // 새로고침 실행
          await queryClient.invalidateQueries({ queryKey: ['feed'] });
          await refetch();

          // 맨 위로 스크롤
          flatListRef.current?.scrollToOffset({ offset: 0, animated: true });
          setCurrentIndex(0);
        } finally {
          setAutoRefreshing(false);
        }
      }
    };

    performAutoRefresh();
  }, [
    currentIndex,
    displayItems.length,
    hasNextPage,
    isFetchingNextPage,
    isLoading,
    autoRefreshing,
    queryClient,
    refetch,
  ]);

  // 탭 전환 시 자동 새로고침 플래그 리셋 및 비디오 캐시 초기화
  useEffect(() => {
    hasAutoRefreshed.current = false;
    videoLoadedCache.current.clear();
  }, [currentTab]);

  // 네비게이션 탭 재클릭 시 새로고침 (Instagram 스타일)
  useEffect(() => {
    const unsubscribe = navigation.addListener('tabPress' as any, async () => {
      // 이미 피드 화면에 있을 때 탭을 다시 누르면
      if (navigation.isFocused()) {
        // 첫 번째부터 새로고침
        setRefreshing(true);
        try {
          // 기존 데이터 완전히 리셋하고 첫 페이지부터 다시 로드
          await queryClient.resetQueries({ queryKey: ['feed', currentTab] });
          // 첫 번째 아이템으로 이동
          setCurrentIndex(0);
          flatListRef.current?.scrollToOffset({ offset: 0, animated: false });
        } finally {
          setRefreshing(false);
        }
      }
    });

    return unsubscribe;
  }, [navigation, queryClient, currentTab]);

  // Pull-to-Refresh - 첫 번째부터 새로고침
  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    setPullDistance(0);
    try {
      // 기존 데이터 완전히 리셋하고 첫 페이지부터 다시 로드
      await queryClient.resetQueries({ queryKey: ['feed', currentTab] });
      // 첫 번째 아이템으로 이동
      setCurrentIndex(0);
      flatListRef.current?.scrollToOffset({ offset: 0, animated: false });
    } finally {
      setRefreshing(false);
    }
  }, [queryClient, currentTab]);

  // 스크롤 이벤트 - Pull-to-Refresh 감지 (Instagram 스타일: 첫 번째 아이템에서만)
  const handleScroll = useCallback((event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const offsetY = event.nativeEvent.contentOffset.y;
    scrollYRef.current = offsetY;

    // Pull-to-Refresh 거리 계산 (첫 번째 아이템에서만)
    if (currentIndex === 0 && offsetY < 0) {
      setPullDistance(Math.abs(offsetY));
    } else {
      setPullDistance(0);
    }
  }, [currentIndex]);

  // 스크롤 종료 시 - 페이지 스냅 및 새로고침 트리거 (Instagram 스타일: 첫 번째에서만)
  const handleScrollEnd = useCallback(async (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const offsetY = event.nativeEvent.contentOffset.y;

    // Pull-to-Refresh 트리거 (첫 번째 아이템에서만)
    if (pullDistance > 60 && offsetY <= 0 && currentIndex === 0) {
      await handleRefresh();
      return;
    }

    // 인덱스 계산
    const index = Math.max(0, Math.round(offsetY / SCREEN_HEIGHT));
    setCurrentIndex(index);
  }, [pullDistance, currentIndex, handleRefresh]);

  // 무한 스크롤: 끝에 도달 시 다음 페이지 로드
  const handleEndReached = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  // 탭 전환 (Instagram 스타일: 같은 탭 재클릭 시 새로고침)
  const handleTabChange = async (tab: FeedTab) => {
    if (tab === currentTab) {
      // 같은 탭을 다시 클릭하면 첫 번째부터 새로고침
      setRefreshing(true);
      try {
        await queryClient.resetQueries({ queryKey: ['feed', currentTab] });
        // 첫 번째 아이템으로 이동
        setCurrentIndex(0);
        flatListRef.current?.scrollToOffset({ offset: 0, animated: false });
      } finally {
        setRefreshing(false);
      }
    } else {
      // 다른 탭으로 전환
      setCurrentTab(tab);
      setCurrentIndex(0);
      flatListRef.current?.scrollToOffset({ offset: 0, animated: false });
    }
  };

  // 인터랙션 핸들러
  const handleLike = (contentId: string, isLiked: boolean = false) => {
    likeMutation.mutate({ contentId, isLiked });
  };

  const handleComment = (contentId: string) => {
    setSelectedContentId(contentId);
    setCommentModalVisible(true);
  };

  const handleSave = (contentId: string, isSaved: boolean = false) => {
    saveMutation.mutate({ contentId, isSaved });
  };

  const handleShare = async (contentId: string) => {
    try {
      await shareContent(contentId);
      // TODO: 공유 UI (공유 링크 가져오기, 클립보드 복사 등)
    } catch (error) {
      console.error('Share failed:', error);
    }
  };

  const handleFollow = (userId: string, isFollowing: boolean = false) => {
    followMutation.mutate({ userId, isFollowing });
  };

  const handleCreatorPress = (userId: string) => {
    console.log('Creator:', userId);
    // TODO: 프로필 화면으로 이동
  };

  // Video 로드 완료 콜백
  const handleVideoLoaded = useCallback((contentId: string) => {
    videoLoadedCache.current.set(contentId, true);
  }, []);

  // Video 로드 상태 확인
  const isVideoLoaded = useCallback((contentId: string) => {
    return videoLoadedCache.current.get(contentId) ?? false;
  }, []);

  // 렌더링
  const renderItem = ({ item, index }: { item: FeedItemType; index: number }) => {
    const isLoadingItem = item.contentId === 'loading';
    // Pre-loading: 현재 인덱스 기준 ±2 범위의 비디오 미리 로드
    const shouldPreload = Math.abs(index - currentIndex) <= 2;
    // 이미 로드된 비디오인지 확인
    const hasBeenLoaded = isVideoLoaded(item.contentId);

    return (
      <View style={{
        height: SCREEN_HEIGHT,
        backgroundColor: '#000000',
        overflow: 'hidden',
      }}>
        <FeedItem
          item={item}
          isFocused={index === currentIndex}
          shouldPreload={shouldPreload}
          hasBeenLoaded={hasBeenLoaded}
          onVideoLoaded={() => handleVideoLoaded(item.contentId)}
          onLike={() => handleLike(item.contentId, item.interactions.isLiked ?? false)}
          onComment={() => handleComment(item.contentId)}
          onSave={() => handleSave(item.contentId, item.interactions.isSaved ?? false)}
          onShare={() => handleShare(item.contentId)}
          onFollow={() => handleFollow(item.creator.userId, item.creator.isFollowing ?? false)}
          onCreatorPress={() => handleCreatorPress(item.creator.userId)}
        />

        {/* 로딩 중일 때 이 아이템 중앙에 인디케이터 표시 */}
        {isLoadingItem && (
          <View style={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            justifyContent: 'center',
            alignItems: 'center',
            pointerEvents: 'none',
          }}>
            <ActivityIndicator size="large" color="#FFFFFF" />
          </View>
        )}
      </View>
    );
  };

  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <StatusBar barStyle="light-content" backgroundColor="#000000" translucent />

      {/* Instagram Reels 스타일 탭 */}
      <View style={{
        position: 'absolute',
        top: 50,
        left: 0,
        right: 0,
        zIndex: 10,
        flexDirection: 'row',
        justifyContent: 'center',
        alignItems: 'center',
        pointerEvents: 'box-none',
      }}>
        <TouchableOpacity
          onPress={() => handleTabChange('following')}
          style={{ paddingHorizontal: 16, paddingVertical: 8 }}
        >
          <Text style={{
            fontSize: 17,
            fontWeight: currentTab === 'following' ? '700' : '400',
            color: currentTab === 'following' ? '#FFFFFF' : '#888888',
          }}>
            팔로잉
          </Text>
        </TouchableOpacity>

        <View style={{
          width: 1,
          height: 12,
          backgroundColor: '#666666',
          marginHorizontal: 4,
        }} />

        <TouchableOpacity
          onPress={() => handleTabChange('recommended')}
          style={{ paddingHorizontal: 16, paddingVertical: 8 }}
        >
          <Text style={{
            fontSize: 17,
            fontWeight: currentTab === 'recommended' ? '700' : '400',
            color: currentTab === 'recommended' ? '#FFFFFF' : '#888888',
          }}>
            추천
          </Text>
        </TouchableOpacity>
      </View>

      {/* Pull-to-Refresh 인디케이터 */}
      {pullDistance > 30 && currentIndex === 0 && (
        <View style={{
          position: 'absolute',
          top: 60 + pullDistance * 0.5,
          left: 0,
          right: 0,
          zIndex: 100,
          alignItems: 'center',
        }}>
          <View style={{
            backgroundColor: 'rgba(0,0,0,0.7)',
            borderRadius: 20,
            padding: 10,
            paddingHorizontal: 20,
          }}>
            <Text style={{ color: 'white', fontSize: 14 }}>
              {pullDistance > 60 ? '🔄 놓아서 새로고침' : '⬇️ 당겨서 새로고침'}
            </Text>
          </View>
        </View>
      )}

      {/* 새로고침 중 인디케이터 */}
      {refreshing && (
        <View style={{
          position: 'absolute',
          top: 80,
          left: 0,
          right: 0,
          zIndex: 100,
          alignItems: 'center',
        }}>
          <ActivityIndicator size="large" color="#FFFFFF" />
        </View>
      )}

      {/* 피드 리스트 */}
      <FlatList
        ref={flatListRef}
        data={displayItems}
        renderItem={renderItem}
        keyExtractor={(item) => (item as FeedItemType & { uniqueKey: string }).uniqueKey}
        extraData={currentIndex}
        showsVerticalScrollIndicator={false}
        snapToInterval={SCREEN_HEIGHT}
        snapToAlignment="start"
        decelerationRate="fast"
        bounces={true}
        scrollEventThrottle={16}
        onScroll={handleScroll}
        onScrollBeginDrag={handleScroll}
        onScrollEndDrag={handleScrollEnd}
        onMomentumScrollEnd={handleScrollEnd}
        onViewableItemsChanged={onViewableItemsChanged}
        viewabilityConfig={viewabilityConfig}
        onEndReached={handleEndReached}
        onEndReachedThreshold={0.5}
        ListFooterComponent={
          isFetchingNextPage && feedItems.length > 0 ? (
            <View style={{ paddingVertical: 16, backgroundColor: '#000000' }}>
              <ActivityIndicator size="large" color="#FFFFFF" />
            </View>
          ) : null
        }
        getItemLayout={(_, index) => ({
          length: SCREEN_HEIGHT,
          offset: SCREEN_HEIGHT * index,
          index,
        })}
        removeClippedSubviews={false}
        maxToRenderPerBatch={5}
        windowSize={7}
        initialNumToRender={3}
        updateCellsBatchingPeriod={50}
        persistentScrollbar={false}
      />

      {/* 댓글 모달 */}
      {selectedContentId && (
        <CommentModal
          visible={commentModalVisible}
          contentId={selectedContentId}
          onClose={() => setCommentModalVisible(false)}
        />
      )}
    </View>
  );
}
