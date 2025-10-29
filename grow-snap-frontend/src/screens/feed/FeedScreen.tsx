/**
 * 피드 화면
 *
 * Instagram Reels 스타일의 세로 스크롤 숏폼 비디오 피드
 * - FlatList로 무한 스크롤 구현
 * - 추천/팔로잉 탭 전환
 * - 커서 기반 페이지네이션
 */

import React, { useState, useRef, useCallback } from 'react';
import {
  View,
  FlatList,
  Dimensions,
  RefreshControl,
  TouchableOpacity,
  Text,
  ActivityIndicator,
  StatusBar,
  NativeScrollEvent,
  NativeSyntheticEvent,
} from 'react-native';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { FeedItem } from '@/components/feed';
import { getMainFeed, getFollowingFeed, refreshFeed as refreshFeedApi } from '@/api/feed.api';
import type { FeedTab, FeedItem as FeedItemType } from '@/types/feed.types';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

export default function FeedScreen() {
  const [currentTab, setCurrentTab] = useState<FeedTab>('recommended');
  const [currentIndex, setCurrentIndex] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  const [pullDistance, setPullDistance] = useState(0);
  const flatListRef = useRef<FlatList>(null);
  const scrollYRef = useRef(0);
  const queryClient = useQueryClient();

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

  // 모든 페이지의 콘텐츠를 평탄화
  const feedItems: FeedItemType[] = data?.pages.flatMap((page) => page.content) ?? [];

  // 로딩 중일 때 보여줄 스켈레톤 아이템
  const loadingFeedItem: FeedItemType = {
    contentId: 'loading',
    contentType: 'VIDEO',
    url: '',
    thumbnailUrl: '',
    duration: 0,
    width: 1080,
    height: 1920,
    title: '',
    description: '',
    category: 'GROWTH',
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
  };

  // 데이터 없거나 로딩중 -> 스켈레톤, 실제 데이터 있음 -> 실제 데이터 표시
  const displayItems = (isLoading || feedItems.length === 0) ? [loadingFeedItem] : feedItems;

  // 스크롤 이벤트: 현재 보이는 아이템 인덱스 추적
  const onViewableItemsChanged = useRef(({ viewableItems }: any) => {
    if (viewableItems.length > 0) {
      setCurrentIndex(viewableItems[0].index ?? 0);
    }
  }).current;

  const viewabilityConfig = useRef({
    itemVisiblePercentThreshold: 80,
  }).current;

  // Pull-to-Refresh - 현재 위치에서 데이터만 새로고침
  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    setPullDistance(0);
    try {
      await queryClient.invalidateQueries({ queryKey: ['feed'] });
      await refetch();
    } finally {
      setRefreshing(false);
    }
  }, [queryClient, refetch]);

  // 스크롤 이벤트 - Pull-to-Refresh 감지
  const handleScroll = useCallback((event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const offsetY = event.nativeEvent.contentOffset.y;
    scrollYRef.current = offsetY;

    // Pull-to-Refresh 거리 계산 (헤더 영역에서)
    if (offsetY < 0) {
      setPullDistance(Math.abs(offsetY));
    } else {
      setPullDistance(0);
    }
  }, []);

  // 스크롤 종료 시 - 페이지 스냅 및 새로고침 트리거
  const handleScrollEnd = useCallback(async (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const offsetY = event.nativeEvent.contentOffset.y;

    // Pull-to-Refresh 트리거
    if (pullDistance > 80 && offsetY <= 0) {
      await handleRefresh();
      return;
    }

    // 인덱스 계산
    const index = Math.max(0, Math.round(offsetY / SCREEN_HEIGHT));
    setCurrentIndex(index);
  }, [pullDistance, handleRefresh]);

  // 무한 스크롤: 끝에 도달 시 다음 페이지 로드
  const handleEndReached = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  // 탭 전환
  const handleTabChange = (tab: FeedTab) => {
    setCurrentTab(tab);
    setCurrentIndex(0);
    flatListRef.current?.scrollToOffset({ offset: 0, animated: false });
  };

  // 인터랙션 핸들러 (나중에 API 연동)
  const handleLike = (contentId: string) => {
    console.log('Like:', contentId);
    // TODO: API 연동
  };

  const handleComment = (contentId: string) => {
    console.log('Comment:', contentId);
    // TODO: 댓글 모달 열기
  };

  const handleSave = (contentId: string) => {
    console.log('Save:', contentId);
    // TODO: API 연동
  };

  const handleShare = (contentId: string) => {
    console.log('Share:', contentId);
    // TODO: 공유 기능
  };

  const handleCreatorPress = (userId: string) => {
    console.log('Creator:', userId);
    // TODO: 프로필 화면으로 이동
  };

  // 렌더링
  const renderItem = ({ item, index }: { item: FeedItemType; index: number }) => {
    const isLoadingItem = item.contentId === 'loading';

    return (
      <View style={{
        height: SCREEN_HEIGHT,
        backgroundColor: '#000000',
        overflow: 'hidden',
      }}>
        <FeedItem
          item={item}
          isFocused={index === currentIndex}
          onLike={() => handleLike(item.contentId)}
          onComment={() => handleComment(item.contentId)}
          onSave={() => handleSave(item.contentId)}
          onShare={() => handleShare(item.contentId)}
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
      {pullDistance > 50 && (
        <View style={{
          position: 'absolute',
          top: 80,
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
              {pullDistance > 80 ? '🔄 놓아서 새로고침' : '⬇️ 당겨서 새로고침'}
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
        keyExtractor={(item, index) => `${item.contentId}-${index}`}
        showsVerticalScrollIndicator={false}
        snapToInterval={SCREEN_HEIGHT}
        snapToAlignment="start"
        decelerationRate="fast"
        bounces={true}
        alwaysBounceVertical={true}
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
        removeClippedSubviews={true}
        maxToRenderPerBatch={2}
        windowSize={3}
        initialNumToRender={1}
        updateCellsBatchingPeriod={100}
      />
    </View>
  );
}
