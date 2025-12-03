/**
 * 피드 공통 로직 훅
 *
 * FeedScreen과 CategoryFeedScreen의 중복 코드를 제거하기 위한 커스텀 훅
 * - 무한 스크롤 피드 데이터 페칭
 * - 좋아요/저장/팔로우 mutation (낙관적 업데이트)
 * - Pull-to-Refresh
 * - Video 로드 상태 관리
 * - FlatList 핸들러
 */

import { useState, useRef, useCallback, useEffect } from 'react';
import {
  Dimensions,
  NativeScrollEvent,
  NativeSyntheticEvent,
  FlatList,
} from 'react-native';
import { useInfiniteQuery, useMutation, useQueryClient, InfiniteData } from '@tanstack/react-query';
import { useNavigation } from '@react-navigation/native';
import type { Category } from '@/types/content.types';
import type { FeedItem as FeedItemType, FeedResponse } from '@/types/feed.types';
import {
  getMainFeed,
  getFollowingFeed,
  getCategoryFeed,
  refreshFeed as refreshFeedApi,
} from '@/api/feed.api';
import { createLike, deleteLike } from '@/api/like.api';
import { createSave, deleteSave } from '@/api/save.api';
import { shareContent } from '@/api/share.api';
import { followUser, unfollowUser } from '@/api/follow.api';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

type FeedType = 'main' | 'following' | 'category';

interface UseFeedOptions {
  feedType: FeedType;
  category?: Category;
  enableAutoRefresh?: boolean;
  enableRefreshApi?: boolean;
}

export function useFeed(options: UseFeedOptions) {
  const { feedType, category, enableAutoRefresh = false, enableRefreshApi = false } = options;

  const [currentIndex, setCurrentIndex] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  const [autoRefreshing, setAutoRefreshing] = useState(false);
  const [pullDistance, setPullDistance] = useState(0);
  const [commentModalVisible, setCommentModalVisible] = useState(false);
  const [selectedContentId, setSelectedContentId] = useState<string | null>(null);

  const flatListRef = useRef<FlatList>(null);
  const scrollYRef = useRef(0);
  const hasAutoRefreshed = useRef(false);
  const videoLoadedCache = useRef<Map<string, boolean>>(new Map());

  const queryClient = useQueryClient();
  const navigation = useNavigation();

  // Query key 생성
  const getQueryKey = () => {
    if (feedType === 'category' && category) {
      return ['categoryFeed', category];
    }
    return ['feed', feedType];
  };

  const queryKey = getQueryKey();

  // 피드 데이터 fetching (무한 스크롤)
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
    isLoading,
  } = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) => {
      if (feedType === 'category' && category) {
        return getCategoryFeed(category, { cursor: pageParam, limit: 10 });
      } else if (feedType === 'main') {
        return getMainFeed({ cursor: pageParam, limit: 10 });
      } else {
        return getFollowingFeed({ cursor: pageParam, limit: 10 });
      }
    },
    getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.nextCursor : undefined),
    initialPageParam: null as string | null,
    staleTime: feedType === 'category' ? 5 * 60 * 1000 : undefined,
    gcTime: feedType === 'category' ? 10 * 60 * 1000 : undefined,
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
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData(queryKey);

      queryClient.setQueryData<InfiniteData<FeedResponse>>(queryKey, (old) => {
        if (!old) return old;
        return {
          ...old,
          pages: old.pages.map((page) => ({
            ...page,
            content: page.content.map((item) =>
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
      queryClient.setQueryData<InfiniteData<FeedResponse>>(queryKey, (old) => {
        if (!old) return old;
        return {
          ...old,
          pages: old.pages.map((page) => ({
            ...page,
            content: page.content.map((item) =>
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
      if (context?.previousData) {
        queryClient.setQueryData(queryKey, context.previousData);
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
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData(queryKey);

      queryClient.setQueryData<InfiniteData<FeedResponse>>(queryKey, (old) => {
        if (!old) return old;
        return {
          ...old,
          pages: old.pages.map((page) => ({
            ...page,
            content: page.content.map((item) =>
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
      queryClient.setQueryData<InfiniteData<FeedResponse>>(queryKey, (old) => {
        if (!old) return old;
        return {
          ...old,
          pages: old.pages.map((page) => ({
            ...page,
            content: page.content.map((item) =>
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

      // Profile 화면의 저장된 콘텐츠 목록 자동 새로고침
      queryClient.invalidateQueries({ queryKey: ['savedContents'] });
    },
    onError: (err, variables, context) => {
      if (context?.previousData) {
        queryClient.setQueryData(queryKey, context.previousData);
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
      await queryClient.cancelQueries({ queryKey });
      const previousData = queryClient.getQueryData(queryKey);

      queryClient.setQueryData<InfiniteData<FeedResponse>>(queryKey, (old) => {
        if (!old) return old;
        return {
          ...old,
          pages: old.pages.map((page) => ({
            ...page,
            content: page.content.map((item) =>
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
        queryClient.setQueryData(queryKey, context.previousData);
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
    category: category || 'OTHER',
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

  // 데이터 상태 처리
  const displayItems =
    isLoading || autoRefreshing || refreshing ? [loadingFeedItem] : feedItems;

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
    if (!enableAutoRefresh) return;

    const performAutoRefresh = async () => {
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

        await new Promise((resolve) => setTimeout(resolve, 1000));

        try {
          await queryClient.invalidateQueries({ queryKey: [queryKey[0]] });
          await refetch();

          flatListRef.current?.scrollToOffset({ offset: 0, animated: true });
          setCurrentIndex(0);
        } finally {
          setAutoRefreshing(false);
        }
      }
    };

    performAutoRefresh();
  }, [
    enableAutoRefresh,
    currentIndex,
    displayItems.length,
    hasNextPage,
    isFetchingNextPage,
    isLoading,
    autoRefreshing,
    queryClient,
    refetch,
  ]);

  // 인터랙션 핸들러
  const handleLike = useCallback(
    (contentId: string, isLiked: boolean = false) => {
      likeMutation.mutate({ contentId, isLiked });
    },
    [likeMutation]
  );

  const handleComment = useCallback((contentId: string) => {
    setSelectedContentId(contentId);
    setCommentModalVisible(true);
  }, []);

  const handleSave = useCallback(
    (contentId: string, isSaved: boolean = false) => {
      saveMutation.mutate({ contentId, isSaved });
    },
    [saveMutation]
  );

  const handleShare = useCallback(async (contentId: string) => {
    try {
      await shareContent(contentId);
    } catch (error) {
      console.error('Share failed:', error);
    }
  }, []);

  const handleFollow = useCallback(
    (userId: string, isFollowing: boolean = false) => {
      followMutation.mutate({ userId, isFollowing });
    },
    [followMutation]
  );

  const handleCreatorPress = useCallback(
    (userId: string) => {
      (navigation as any).navigate('UserProfile', { userId });
    },
    [navigation]
  );

  // Video 로드 완료 콜백
  const handleVideoLoaded = useCallback((contentId: string) => {
    videoLoadedCache.current.set(contentId, true);
  }, []);

  // Video 로드 상태 확인
  const isVideoLoaded = useCallback((contentId: string) => {
    return videoLoadedCache.current.get(contentId) ?? false;
  }, []);

  // Pull-to-Refresh
  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    setPullDistance(0);
    try {
      if (enableRefreshApi) {
        await refreshFeedApi();
      }

      await queryClient.resetQueries({ queryKey });

      setCurrentIndex(0);
      flatListRef.current?.scrollToOffset({ offset: 0, animated: false });
    } finally {
      setRefreshing(false);
    }
  }, [queryClient, queryKey, enableRefreshApi]);

  // 스크롤 이벤트 - Pull-to-Refresh 감지
  const handleScroll = useCallback(
    (event: NativeSyntheticEvent<NativeScrollEvent>) => {
      const offsetY = event.nativeEvent.contentOffset.y;
      scrollYRef.current = offsetY;

      if (currentIndex === 0 && offsetY < 0) {
        setPullDistance(Math.abs(offsetY));
      } else {
        setPullDistance(0);
      }
    },
    [currentIndex]
  );

  // 스크롤 종료 시 - 페이지 스냅 및 새로고침 트리거
  const handleScrollEnd = useCallback(
    async (event: NativeSyntheticEvent<NativeScrollEvent>) => {
      const offsetY = event.nativeEvent.contentOffset.y;

      if (pullDistance > 60 && offsetY <= 0 && currentIndex === 0) {
        await handleRefresh();
        return;
      }

      const index = Math.max(0, Math.round(offsetY / SCREEN_HEIGHT));
      setCurrentIndex(index);
    },
    [pullDistance, currentIndex, handleRefresh]
  );

  // 무한 스크롤: 끝에 도달 시 다음 페이지 로드
  const handleEndReached = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  // Video 캐시 초기화 (탭 변경 시 외부에서 호출)
  const clearVideoCache = useCallback(() => {
    videoLoadedCache.current.clear();
    hasAutoRefreshed.current = false;
  }, []);

  return {
    // 데이터
    data,
    feedItems,
    displayItems,
    isLoading,
    refreshing,
    autoRefreshing,

    // 페이지네이션
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,

    // 인덱스 & 상태
    currentIndex,
    setCurrentIndex,
    pullDistance,

    // 댓글 모달
    commentModalVisible,
    setCommentModalVisible,
    selectedContentId,
    setSelectedContentId,

    // 인터랙션 핸들러
    handleLike,
    handleComment,
    handleSave,
    handleShare,
    handleFollow,
    handleCreatorPress,

    // Video 관리
    handleVideoLoaded,
    isVideoLoaded,
    clearVideoCache,

    // 스크롤 핸들러
    handleRefresh,
    handleScroll,
    handleScrollEnd,
    handleEndReached,
    onViewableItemsChanged,
    viewabilityConfig,

    // Refs
    flatListRef,
    scrollYRef,

    // 기타
    loadingFeedItem,
    SCREEN_HEIGHT,
  };
}
