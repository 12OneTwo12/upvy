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
} from 'react-native';
import { useQuery, useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
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
  const renderItem = ({ item, index }: { item: FeedItemType; index: number }) => (
    <FeedItem
      item={item}
      isFocused={index === currentIndex}
      onLike={() => handleLike(item.contentId)}
      onComment={() => handleComment(item.contentId)}
      onSave={() => handleSave(item.contentId)}
      onShare={() => handleShare(item.contentId)}
      onCreatorPress={() => handleCreatorPress(item.creator.userId)}
    />
  );

  return (
    <View className="flex-1 bg-black">
      {/* 탭 전환 버튼 */}
      <View className="absolute top-12 left-0 right-0 z-10 flex-row justify-center">
        <View className="flex-row bg-black/40 rounded-full p-1">
          <TouchableOpacity
            onPress={() => handleTabChange('recommended')}
            className={`px-6 py-2 rounded-full ${
              currentTab === 'recommended' ? 'bg-white' : 'bg-transparent'
            }`}
          >
            <Text
              className={`font-semibold ${
                currentTab === 'recommended' ? 'text-black' : 'text-white'
              }`}
            >
              추천
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            onPress={() => handleTabChange('following')}
            className={`px-6 py-2 rounded-full ${
              currentTab === 'following' ? 'bg-white' : 'bg-transparent'
            }`}
          >
            <Text
              className={`font-semibold ${
                currentTab === 'following' ? 'text-black' : 'text-white'
              }`}
            >
              팔로잉
            </Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* 피드 리스트 */}
      <FlatList
        ref={flatListRef}
        data={feedItems}
        renderItem={renderItem}
        keyExtractor={(item) => item.contentId}
        pagingEnabled
        showsVerticalScrollIndicator={false}
        snapToInterval={SCREEN_HEIGHT}
        snapToAlignment="start"
        decelerationRate="fast"
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
        ListEmptyComponent={
          isLoading ? (
            <View style={{ height: SCREEN_HEIGHT }} className="items-center justify-center">
              <ActivityIndicator size="large" color="#22c55e" />
            </View>
          ) : (
            <View style={{ height: SCREEN_HEIGHT }} className="items-center justify-center px-8">
              <Text className="text-white text-2xl font-bold mb-4">
                {currentTab === 'recommended' ? '콘텐츠가 없어요' : '팔로잉 피드가 비어있어요'}
              </Text>
              <Text className="text-white/70 text-center mb-6">
                {currentTab === 'recommended'
                  ? '아직 추천할 콘텐츠가 없습니다.\n잠시 후 다시 확인해주세요.'
                  : '팔로우한 크리에이터의\n새로운 콘텐츠가 아직 없습니다.'}
              </Text>
              <TouchableOpacity
                onPress={() => refetch()}
                className="bg-green-500 px-6 py-3 rounded-full"
              >
                <Text className="text-white font-semibold">새로고침</Text>
              </TouchableOpacity>
            </View>
          )
        }
        ListFooterComponent={
          isFetchingNextPage ? (
            <View className="py-4">
              <ActivityIndicator size="large" color="#22c55e" />
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
