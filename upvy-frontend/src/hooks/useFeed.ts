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
  Share,
  Alert,
} from 'react-native';
import { useInfiniteQuery, useMutation, useQueryClient, InfiniteData } from '@tanstack/react-query';
import { useNavigation } from '@react-navigation/native';
import * as Haptics from 'expo-haptics';
import type { Category } from '@/types/content.types';
import type { FeedItem as FeedItemType, FeedResponse } from '@/types/feed.types';
import {
  getMainFeed,
  getFollowingFeed,
  getCategoryFeed,
  refreshFeed as refreshFeedApi,
  refreshCategoryFeed,
} from '@/api/feed.api';
import { createLike, deleteLike } from '@/api/like.api';
import { createSave, deleteSave } from '@/api/save.api';
import { shareContent, getShareLink } from '@/api/share.api';
import { followUser, unfollowUser } from '@/api/follow.api';
import { trackView } from '@/api/analytics.api';
import { useLanguageStore } from '@/stores/languageStore';
import { Analytics } from '@/utils/analytics';

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

  // 시청 기록 추적
  const viewStartTimeRef = useRef<number>(0); // 현재 콘텐츠 포커스 시작 시간
  const trackedViewsRef = useRef<Set<string>>(new Set()); // 이미 기록한 콘텐츠 ID (중복 방지)
  const previousIndexRef = useRef<number>(-1); // 이전 currentIndex

  const queryClient = useQueryClient();
  const navigation = useNavigation();
  const currentLanguage = useLanguageStore((state) => state.currentLanguage);

  // Query key 생성
  const getQueryKey = () => {
    if (feedType === 'category' && category) {
      return ['categoryFeed', category, currentLanguage];
    }
    return ['feed', feedType, currentLanguage];
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
        return getCategoryFeed(category, { cursor: pageParam, limit: 10, language: currentLanguage });
      } else if (feedType === 'main') {
        return getMainFeed({ cursor: pageParam, limit: 10, language: currentLanguage });
      } else {
        return getFollowingFeed({ cursor: pageParam, limit: 10 });
      }
    },
    getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.nextCursor : undefined),
    initialPageParam: null as string | null,
    staleTime: feedType === 'category' ? 5 * 60 * 1000 : undefined,
    gcTime: feedType === 'category' ? 10 * 60 * 1000 : undefined,
    retry: 3,
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 30000),
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
      // Analytics 이벤트 (Fire-and-Forget - await 없음)
      if (response.isLiked) {
        Analytics.logLike(response.contentId, 'video');
      } else {
        Analytics.logUnlike(response.contentId, 'video');
      }

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
      // Analytics 이벤트 (Fire-and-Forget - await 없음)
      if (response.isSaved) {
        Analytics.logSave(response.contentId, 'video');
      } else {
        Analytics.logUnsave(response.contentId, 'video');
      }

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
      return { userId, isFollowing: !isFollowing };
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
    onSuccess: (result) => {
      // Analytics 이벤트 (Fire-and-Forget - await 없음)
      if (result.isFollowing) {
        Analytics.logFollow(result.userId);
      } else {
        Analytics.logUnfollow(result.userId);
      }
    },
    onError: (err, variables, context) => {
      if (context?.previousData) {
        queryClient.setQueryData(queryKey, context.previousData);
      }
    },
  });

  // 공유 mutation (Optimistic update)
  const shareMutation = useMutation({
    mutationFn: async (contentId: string) => {
      return await shareContent(contentId);
    },
    onMutate: async (contentId) => {
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
                      shareCount: item.interactions.shareCount + 1,
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
      // Analytics 이벤트 (Fire-and-Forget - await 없음)
      Analytics.logShare(response.contentId, 'video', 'link');

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
                      shareCount: response.shareCount,
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

  const handleShare = useCallback(
    async (contentId: string) => {
      try {
        // 1. 공유 링크 가져오기
        const { shareUrl } = await getShareLink(contentId);

        // 2. 네이티브 공유 시트 열기
        const result = await Share.share({
          message: `Upvy에서 흥미로운 콘텐츠를 발견했어요! 같이 봐요 😊\n\n${shareUrl}`,
          title: 'Upvy 콘텐츠 공유',
        });

        // 3. 공유 성공 시 카운터 증가 (낙관적 업데이트)
        if (result.action === Share.sharedAction) {
          shareMutation.mutate(contentId);
        }
      } catch (error: unknown) {
        // 사용자가 공유를 취소한 경우는 에러로 처리하지 않음 (Android)
        // 참고: 이 에러 메시지는 React Native 버전에 따라 변경될 수 있습니다.
        if (error instanceof Error && error.message.includes('User did not share')) {
          return;
        }
        console.error('Share failed:', error);
        Alert.alert('공유 실패', '잠시 후 다시 시도해주세요.');
      }
    },
    [shareMutation]
  );

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

  // 차단 성공 후 다음 콘텐츠로 자동 스크롤 (Instagram/TikTok 스타일)
  const handleBlockSuccess = useCallback(() => {
    const nextIndex = currentIndex + 1;

    // 다음 콘텐츠가 있으면 스크롤
    if (nextIndex < displayItems.length) {
      flatListRef.current?.scrollToIndex({
        index: nextIndex,
        animated: true, // 부드러운 전환 애니메이션
      });
    }
    // 마지막 콘텐츠를 차단한 경우는 그대로 유지 (사용자가 스와이프하여 이동)
  }, [currentIndex, displayItems.length]);

  // 삭제 성공 후 다음 콘텐츠로 스크롤 및 피드 새로고침
  const handleDeleteSuccess = useCallback(async () => {
    const nextIndex = currentIndex + 1;

    // 다음 콘텐츠가 있으면 스크롤
    if (nextIndex < displayItems.length) {
      flatListRef.current?.scrollToIndex({
        index: nextIndex,
        animated: true,
      });
    }

    // 피드 데이터 새로고침 (삭제된 콘텐츠 제거)
    await queryClient.invalidateQueries({ queryKey });
  }, [currentIndex, displayItems.length, queryClient, queryKey]);

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
    // 햅틱 피드백
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);

    setRefreshing(true);
    setPullDistance(0);
    try {
      if (enableRefreshApi) {
        if (feedType === 'category' && category) {
          await refreshCategoryFeed(category);
        } else {
          await refreshFeedApi();
        }
      }

      await queryClient.resetQueries({ queryKey });

      setCurrentIndex(0);
      flatListRef.current?.scrollToOffset({ offset: 0, animated: false });
    } finally {
      setRefreshing(false);
    }
  }, [queryClient, queryKey, enableRefreshApi, feedType, category]);

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
    trackedViewsRef.current.clear(); // 시청 기록도 초기화
  }, []);

  // 시청 기록 전송 함수
  const sendViewEvent = useCallback(async (contentId: string, watchedDuration: number) => {
    // contentId 유효성 검증
    if (!contentId || contentId === 'loading' || contentId.trim() === '') {
      return;
    }

    // 중복 전송 방지
    if (trackedViewsRef.current.has(contentId)) {
      return;
    }

    // 최소 2초 이상 시청한 경우에만 전송 (우발적 터치 방지)
    if (watchedDuration < 2) {
      return;
    }

    try {
      // 3초 이상 시청: 정상 시청 (view_count 증가)
      // 3초 미만: 스킵 (시청 기록만 저장)
      const skipped = watchedDuration < 3;

      await trackView({
        contentId,
        watchedDuration: Math.floor(watchedDuration),
        completionRate: 0, // 현재는 0으로 전송 (향후 VideoPlayer에서 실제 값 전달 가능)
        skipped,
      });

      // 기록 완료 표시
      trackedViewsRef.current.add(contentId);
    } catch (error) {
      // 시청 기록 전송 실패는 사용자 경험에 영향 없으므로 조용히 무시
      console.error('Failed to track view:', error);
    }
  }, []);

  // currentIndex 변경 감지하여 시청 기록 전송
  useEffect(() => {
    const currentItem = displayItems[currentIndex];

    // 로딩 아이템이거나 데이터가 없으면 무시
    if (!currentItem || currentItem.contentId === 'loading') {
      return;
    }

    // 이전 콘텐츠의 시청 기록 전송 (포커스 해제)
    if (previousIndexRef.current >= 0 && previousIndexRef.current !== currentIndex) {
      const prevItem = displayItems[previousIndexRef.current];
      if (prevItem && prevItem.contentId !== 'loading' && viewStartTimeRef.current > 0) {
        const watchedDuration = (Date.now() - viewStartTimeRef.current) / 1000;
        sendViewEvent(prevItem.contentId, watchedDuration);
      }
    }

    // 새 콘텐츠 포커스 시작 시간 기록
    viewStartTimeRef.current = Date.now();
    previousIndexRef.current = currentIndex;
  }, [currentIndex, displayItems, sendViewEvent]);

  // 컴포넌트 언마운트 시 마지막 콘텐츠 시청 기록 전송
  useEffect(() => {
    return () => {
      const currentItem = displayItems[currentIndex];
      if (currentItem && currentItem.contentId !== 'loading' && viewStartTimeRef.current > 0) {
        const watchedDuration = (Date.now() - viewStartTimeRef.current) / 1000;
        sendViewEvent(currentItem.contentId, watchedDuration);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // 언마운트 시에만 실행

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
    handleBlockSuccess,
    handleDeleteSuccess,

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
