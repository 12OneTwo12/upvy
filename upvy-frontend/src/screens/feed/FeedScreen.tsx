/**
 * 피드 화면
 *
 * Instagram Reels 스타일의 세로 스크롤 숏폼 비디오 피드
 * - FlatList로 무한 스크롤 구현
 * - 추천/팔로잉 탭 전환
 * - 커서 기반 페이지네이션
 */

import React, { useState, useEffect } from 'react';
import {
  View,
  FlatList,
  TouchableOpacity,
  Text,
  ActivityIndicator,
} from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigation, useIsFocused } from '@react-navigation/native';
import { useTranslation } from 'react-i18next';
import { FeedItem } from '@/components/feed';
import { CommentModal } from '@/components/comment';
import { useFeed } from '@/hooks/useFeed';
import type { FeedTab, FeedItem as FeedItemType } from '@/types/feed.types';

export default function FeedScreen() {
  const { t } = useTranslation('feed');
  const [currentTab, setCurrentTab] = useState<FeedTab>('recommended');
  const queryClient = useQueryClient();
  const navigation = useNavigation();
  const isScreenFocused = useIsFocused();

  // 피드 타입 결정
  const feedType = currentTab === 'recommended' ? 'main' : 'following';

  // 공통 피드 로직 훅
  const feed = useFeed({
    feedType,
    enableAutoRefresh: true,
    enableRefreshApi: currentTab === 'recommended',
  });

  const {
    feedItems,
    displayItems,
    isLoading,
    autoRefreshing,
    refreshing,
    isFetchingNextPage,
    currentIndex,
    setCurrentIndex,
    pullDistance,
    commentModalVisible,
    setCommentModalVisible,
    selectedContentId,
    handleLike,
    handleComment,
    handleSave,
    handleShare,
    handleFollow,
    handleCreatorPress,
    handleBlockSuccess,
    handleDeleteSuccess,
    handleVideoLoaded,
    isVideoLoaded,
    clearVideoCache,
    handleScroll,
    handleScrollEnd,
    handleEndReached,
    handleRefresh,
    onViewableItemsChanged,
    viewabilityConfig,
    flatListRef,
    loadingFeedItem,
    SCREEN_HEIGHT,
  } = feed;

  // 탭 전환 시 비디오 캐시 초기화
  useEffect(() => {
    clearVideoCache();
  }, [currentTab, clearVideoCache]);

  // 네비게이션 탭 재클릭 시 새로고침 (Instagram 스타일)
  useEffect(() => {
    const parent = navigation.getParent();
    if (!parent) return;

    const unsubscribe = parent.addListener('tabPress' as any, async (e: any) => {
      if (e.target?.split('-')[0] === 'Feed' && navigation.isFocused()) {
        await handleRefresh();
      }
    });

    return unsubscribe;
  }, [navigation, handleRefresh]);

  // 탭 전환 (Instagram 스타일: 같은 탭 재클릭 시 새로고침)
  const handleTabChange = async (tab: FeedTab) => {
    if (tab === currentTab) {
      await handleRefresh();
    } else {
      setCurrentTab(tab);
      setCurrentIndex(0);
      flatListRef.current?.scrollToOffset({ offset: 0, animated: false });
    }
  };

  // 렌더링
  const renderItem = ({ item, index }: { item: FeedItemType; index: number }) => {
    const isLoadingItem = item.contentId === 'loading';
    const shouldPreload = Math.abs(index - currentIndex) <= 2;
    const hasBeenLoaded = isVideoLoaded(item.contentId);

    return (
      <View
        style={{
          height: SCREEN_HEIGHT,
          backgroundColor: '#000000',
          overflow: 'hidden',
        }}
      >
        <FeedItem
          item={item}
          isFocused={index === currentIndex && isScreenFocused}
          shouldPreload={shouldPreload}
          hasBeenLoaded={hasBeenLoaded}
          onVideoLoaded={() => handleVideoLoaded(item.contentId)}
          onLike={() => handleLike(item.contentId, item.interactions.isLiked ?? false)}
          onComment={() => handleComment(item.contentId)}
          onSave={() => handleSave(item.contentId, item.interactions.isSaved ?? false)}
          onShare={() => handleShare(item.contentId)}
          onFollow={() => handleFollow(item.creator.userId, item.creator.isFollowing ?? false)}
          onCreatorPress={() => handleCreatorPress(item.creator.userId)}
          onBlockSuccess={handleBlockSuccess}
          onDeleteSuccess={handleDeleteSuccess}
          onEditSuccess={() => {}}
        />

        {isLoadingItem && (
          <View
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              justifyContent: 'center',
              alignItems: 'center',
              pointerEvents: 'none',
            }}
          >
            <ActivityIndicator size="large" color="#FFFFFF" />
          </View>
        )}
      </View>
    );
  };

  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      {/* 상단 그라데이션 오버레이 */}
      <LinearGradient
        colors={['rgba(0,0,0,0.8)', 'rgba(0,0,0,0.4)', 'transparent']}
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: 120,
          zIndex: 5,
          pointerEvents: 'none',
        }}
      />

      {/* Instagram Reels 스타일 탭 */}
      <View
        style={{
          position: 'absolute',
          top: 50,
          left: 0,
          right: 0,
          zIndex: 10,
          flexDirection: 'row',
          justifyContent: 'center',
          alignItems: 'center',
          pointerEvents: 'box-none',
        }}
      >
        <TouchableOpacity
          onPress={() => handleTabChange('following')}
          style={{ paddingHorizontal: 16, paddingVertical: 8 }}
        >
          <Text
            style={{
              fontSize: 17,
              fontWeight: currentTab === 'following' ? '700' : '400',
              color: currentTab === 'following' ? '#FFFFFF' : '#888888',
              textShadowColor: 'rgba(0, 0, 0, 0.75)',
              textShadowOffset: { width: 0, height: 1 },
              textShadowRadius: 3,
            }}
          >
            {t('tabs.following')}
          </Text>
        </TouchableOpacity>

        <View
          style={{
            width: 1,
            height: 12,
            backgroundColor: '#666666',
            marginHorizontal: 4,
          }}
        />

        <TouchableOpacity
          onPress={() => handleTabChange('recommended')}
          style={{ paddingHorizontal: 16, paddingVertical: 8 }}
        >
          <Text
            style={{
              fontSize: 17,
              fontWeight: currentTab === 'recommended' ? '700' : '400',
              color: currentTab === 'recommended' ? '#FFFFFF' : '#888888',
              textShadowColor: 'rgba(0, 0, 0, 0.75)',
              textShadowOffset: { width: 0, height: 1 },
              textShadowRadius: 3,
            }}
          >
            {t('tabs.forYou')}
          </Text>
        </TouchableOpacity>
      </View>

      {/* Pull-to-Refresh 인디케이터 */}
      {pullDistance > 30 && currentIndex === 0 && !refreshing && (
        <View
          style={{
            position: 'absolute',
            top: 60 + pullDistance * 0.5,
            left: 0,
            right: 0,
            zIndex: 100,
            alignItems: 'center',
          }}
        >
          <View
            style={{
              backgroundColor: 'rgba(0,0,0,0.7)',
              borderRadius: 20,
              padding: 10,
              paddingHorizontal: 20,
            }}
          >
            <Text style={{ color: 'white', fontSize: 14 }}>
              {pullDistance > 60 ? `🔄 ${t('refresh.releaseToRefresh')}` : `⬇️ ${t('refresh.pullToRefresh')}`}
            </Text>
          </View>
        </View>
      )}

      {/* 빈 콘텐츠 상태 */}
      {!isLoading && !autoRefreshing && !refreshing && feedItems.length === 0 ? (
        <View
          style={{
            flex: 1,
            justifyContent: 'center',
            alignItems: 'center',
            paddingHorizontal: 32,
          }}
        >
          <Text style={{ fontSize: 48, marginBottom: 16 }}>
            {currentTab === 'following' ? '👥' : '📭'}
          </Text>
          <Text
            style={{
              color: '#FFFFFF',
              fontSize: 18,
              fontWeight: '600',
              textAlign: 'center',
              marginBottom: 8,
            }}
          >
            {currentTab === 'following'
              ? t('empty.following.title')
              : t('empty.recommended.title')}
          </Text>
          <Text
            style={{
              color: '#666666',
              fontSize: 14,
              textAlign: 'center',
              lineHeight: 20,
            }}
          >
            {currentTab === 'following'
              ? t('empty.following.message')
              : t('empty.recommended.message')}
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
