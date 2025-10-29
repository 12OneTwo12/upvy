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
} from 'react-native';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { FeedItem } from '@/components/feed';
import { getMainFeed, getFollowingFeed, refreshFeed as refreshFeedApi } from '@/api/feed.api';
import type { FeedTab, FeedItem as FeedItemType } from '@/types/feed.types';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

export default function FeedScreen() {
  const [currentTab, setCurrentTab] = useState<FeedTab>('recommended');
  const [currentIndex, setCurrentIndex] = useState(0);
  const flatListRef = useRef<FlatList>(null);
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

  // 무한 스크롤: 끝에 도달 시 다음 페이지 로드
  const handleEndReached = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  // Pull-to-Refresh
  const handleRefresh = useCallback(() => {
    refreshMutation.mutate();
  }, [refreshMutation]);

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
      <View style={{ height: SCREEN_HEIGHT, backgroundColor: '#000000' }}>
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

      {/* 피드 리스트 */}
      <FlatList
        ref={flatListRef}
        data={displayItems}
        renderItem={renderItem}
        keyExtractor={(item, index) => `${item.contentId}-${index}`}
        pagingEnabled
        showsVerticalScrollIndicator={false}
        snapToInterval={SCREEN_HEIGHT}
        snapToAlignment="start"
        decelerationRate="normal"
        onViewableItemsChanged={onViewableItemsChanged}
        viewabilityConfig={viewabilityConfig}
        onEndReached={handleEndReached}
        onEndReachedThreshold={0.5}
        refreshControl={
          <RefreshControl
            refreshing={refreshMutation.isPending}
            onRefresh={handleRefresh}
            tintColor="white"
          />
        }
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
        removeClippedSubviews
        maxToRenderPerBatch={3}
        windowSize={5}
      />
    </View>
  );
}
