/**
 * 카테고리 피드 화면
 *
 * 선택된 카테고리의 콘텐츠를 표시하는 화면
 * Instagram Reels 스타일의 세로 스크롤 숏폼 비디오 피드
 * - FlatList로 무한 스크롤 구현
 * - 커서 기반 페이지네이션
 */

import React, { useRef, useCallback, useEffect } from 'react';
import {
  View,
  FlatList,
  Dimensions,
  TouchableOpacity,
  Text,
  ActivityIndicator,
  StatusBar,
} from 'react-native';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { ExploreStackParamList } from '@/types/navigation.types';
import { FeedItem } from '@/components/feed';
import { CommentModal } from '@/components/comment';
import { getCategoryFeed } from '@/api/feed.api';
import { createLike, deleteLike } from '@/api/like.api';
import { createSave, deleteSave } from '@/api/save.api';
import { shareContent } from '@/api/share.api';
import { followUser, unfollowUser } from '@/api/follow.api';
import type { FeedItem as FeedItemType } from '@/types/feed.types';
import { CATEGORIES, type Category } from '@/types/content.types';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

type CategoryFeedScreenRouteProp = RouteProp<ExploreStackParamList, 'CategoryFeed'>;

export default function CategoryFeedScreen() {
  const route = useRoute<CategoryFeedScreenRouteProp>();
  const { category } = route.params;

  const insets = useSafeAreaInsets();
  const [currentIndex, setCurrentIndex] = React.useState(0);
  const [commentModalVisible, setCommentModalVisible] = React.useState(false);
  const [selectedContentId, setSelectedContentId] = React.useState<string | null>(null);
  const flatListRef = useRef<FlatList>(null);

  // Video 로드 상태를 contentId별로 캐싱
  const videoLoadedCache = useRef<Map<string, boolean>>(new Map());

  const queryClient = useQueryClient();
  const navigation = useNavigation();

  // 선택된 카테고리 정보
  const categoryInfo = CATEGORIES.find(c => c.value === category);

  // 카테고리별 피드 데이터 fetching (무한 스크롤)
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
    isLoading,
  } = useInfiniteQuery({
    queryKey: ['categoryFeed', category],
    queryFn: ({ pageParam }) => {
      return getCategoryFeed(category, { cursor: pageParam, limit: 10 });
    },
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.nextCursor : undefined,
    initialPageParam: null as string | null,
    staleTime: 5 * 60 * 1000, // 5분
    gcTime: 10 * 60 * 1000, // 10분
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
      await queryClient.cancelQueries({ queryKey: ['categoryFeed', category] });
      const previousData = queryClient.getQueryData(['categoryFeed', category]);

      queryClient.setQueryData(['categoryFeed', category], (old: any) => {
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
      queryClient.setQueryData(['categoryFeed', category], (old: any) => {
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
      if (context?.previousData) {
        queryClient.setQueryData(['categoryFeed', category], context.previousData);
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
      await queryClient.cancelQueries({ queryKey: ['categoryFeed', category] });
      const previousData = queryClient.getQueryData(['categoryFeed', category]);

      queryClient.setQueryData(['categoryFeed', category], (old: any) => {
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
      queryClient.setQueryData(['categoryFeed', category], (old: any) => {
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
        queryClient.setQueryData(['categoryFeed', category], context.previousData);
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
      await queryClient.cancelQueries({ queryKey: ['categoryFeed', category] });
      const previousData = queryClient.getQueryData(['categoryFeed', category]);

      queryClient.setQueryData(['categoryFeed', category], (old: any) => {
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
        queryClient.setQueryData(['categoryFeed', category], context.previousData);
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
    category: category,
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
  // 로딩 중이면 스켈레톤, 로딩 끝났는데 데이터 없으면 빈 배열, 있으면 실제 데이터
  const displayItems = isLoading ? [loadingFeedItem] : feedItems;

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
    } catch (error) {
      console.error('Share failed:', error);
    }
  };

  const handleFollow = (userId: string, isFollowing: boolean = false) => {
    followMutation.mutate({ userId, isFollowing });
  };

  const handleCreatorPress = (userId: string) => {
    navigation.navigate('UserProfile', { userId });
  };

  // Video 로드 완료 콜백
  const handleVideoLoaded = useCallback((contentId: string) => {
    videoLoadedCache.current.set(contentId, true);
  }, []);

  // Video 로드 상태 확인
  const isVideoLoaded = useCallback((contentId: string) => {
    return videoLoadedCache.current.get(contentId) ?? false;
  }, []);

  // 무한 스크롤: 끝에 도달 시 다음 페이지 로드
  const handleEndReached = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  // 렌더링
  const renderItem = ({ item, index }: { item: FeedItemType; index: number }) => {
    const isLoadingItem = item.contentId === 'loading';
    const shouldPreload = Math.abs(index - currentIndex) <= 2;
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

      {/* 상단 헤더 - 카테고리 이름 */}
      <View style={{
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        zIndex: 10,
        paddingTop: insets.top + 10,
        paddingBottom: 12,
        paddingHorizontal: 16,
        flexDirection: 'row',
        alignItems: 'center',
        pointerEvents: 'box-none',
      }}>
        {/* 뒤로가기 버튼 */}
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          style={{
            marginRight: 12,
            padding: 4,
            pointerEvents: 'auto',
          }}
        >
          <Text style={{
            color: '#FFFFFF',
            fontSize: 28,
            textShadowColor: 'rgba(0, 0, 0, 0.8)',
            textShadowOffset: { width: 0, height: 1 },
            textShadowRadius: 4,
          }}>‹</Text>
        </TouchableOpacity>

        {/* 카테고리 이름 */}
        <Text style={{
          color: '#FFFFFF',
          fontSize: 20,
          fontWeight: '700',
          textShadowColor: 'rgba(0, 0, 0, 0.8)',
          textShadowOffset: { width: 0, height: 1 },
          textShadowRadius: 4,
        }}>
          {categoryInfo?.displayName || '카테고리'}
        </Text>
      </View>

      {/* 빈 콘텐츠 상태 */}
      {!isLoading && feedItems.length === 0 ? (
        <View style={{
          flex: 1,
          justifyContent: 'center',
          alignItems: 'center',
          paddingHorizontal: 32,
        }}>
          <Text style={{ fontSize: 48, marginBottom: 16 }}>
            📭
          </Text>
          <Text style={{
            color: '#FFFFFF',
            fontSize: 18,
            fontWeight: '600',
            textAlign: 'center',
            marginBottom: 8,
          }}>
            아직 콘텐츠가 없어요
          </Text>
          <Text style={{
            color: '#666666',
            fontSize: 14,
            textAlign: 'center',
            lineHeight: 20,
          }}>
            {categoryInfo?.displayName} 카테고리에{'\n'}
            곧 멋진 콘텐츠가 업로드될 거예요!
          </Text>
        </View>
      ) : (
        /* 피드 리스트 */
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
      )}

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
