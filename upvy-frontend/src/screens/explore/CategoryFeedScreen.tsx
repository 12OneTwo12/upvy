/**
 * 카테고리 피드 화면
 *
 * 선택된 카테고리의 콘텐츠를 표시하는 화면
 * Instagram Reels 스타일의 세로 스크롤 숏폼 비디오 피드
 * - FlatList로 무한 스크롤 구현
 * - 커서 기반 페이지네이션
 */

import React, { useEffect } from 'react';
import {
  View,
  FlatList,
  TouchableOpacity,
  Text,
  ActivityIndicator,
  StatusBar,
} from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { useNavigation, useRoute, useIsFocused, RouteProp } from '@react-navigation/native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';
import type { ExploreStackParamList } from '@/types/navigation.types';
import { FeedItem } from '@/components/feed';
import { CommentModal } from '@/components/comment';
import { useFeed } from '@/hooks/useFeed';
import type { FeedItem as FeedItemType } from '@/types/feed.types';
import { CATEGORIES } from '@/types/content.types';

type CategoryFeedScreenRouteProp = RouteProp<ExploreStackParamList, 'CategoryFeed'>;

export default function CategoryFeedScreen() {
  const route = useRoute<CategoryFeedScreenRouteProp>();
  const { category } = route.params;

  const insets = useSafeAreaInsets();
  const navigation = useNavigation();
  const isScreenFocused = useIsFocused();
  const { t } = useTranslation('search');

  // 화면 포커스 시 StatusBar 업데이트
  useEffect(() => {
    if (isScreenFocused) {
      StatusBar.setBarStyle('light-content');
    }
  }, [isScreenFocused]);

  // 선택된 카테고리 정보
  const categoryInfo = CATEGORIES.find((c) => c.value === category);

  // 공통 피드 로직 훅
  const feed = useFeed({
    feedType: 'category',
    category,
    enableAutoRefresh: false,
    enableRefreshApi: true,
  });

  const {
    feedItems,
    displayItems,
    isLoading,
    refreshing,
    isFetchingNextPage,
    currentIndex,
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
    handleScroll,
    handleScrollEnd,
    handleEndReached,
    onViewableItemsChanged,
    viewabilityConfig,
    flatListRef,
    SCREEN_HEIGHT,
  } = feed;

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
      <StatusBar barStyle="light-content" backgroundColor="#000000" translucent />

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

      {/* 상단 헤더 - 카테고리 이름 */}
      <View
        style={{
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
        }}
      >
        {/* 뒤로가기 버튼 */}
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          style={{
            marginRight: 12,
            padding: 4,
            pointerEvents: 'auto',
          }}
        >
          <Text
            style={{
              color: '#FFFFFF',
              fontSize: 28,
              textShadowColor: 'rgba(0, 0, 0, 0.8)',
              textShadowOffset: { width: 0, height: 1 },
              textShadowRadius: 4,
            }}
          >
            ‹
          </Text>
        </TouchableOpacity>

        {/* 카테고리 이름 */}
        <Text
          style={{
            color: '#FFFFFF',
            fontSize: 20,
            fontWeight: '700',
            textShadowColor: 'rgba(0, 0, 0, 0.8)',
            textShadowOffset: { width: 0, height: 1 },
            textShadowRadius: 4,
          }}
        >
          {categoryInfo ? t(`category.${category}.name`, categoryInfo.displayName) : t('explore.categories')}
        </Text>
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
              {pullDistance > 60 ? `🔄 ${t('explore.releaseToRefresh')}` : `⬇️ ${t('explore.pullToRefresh')}`}
            </Text>
          </View>
        </View>
      )}

      {/* 빈 콘텐츠 상태 */}
      {!isLoading && !refreshing && feedItems.length === 0 ? (
        <View
          style={{
            flex: 1,
            justifyContent: 'center',
            alignItems: 'center',
            paddingHorizontal: 32,
          }}
        >
          <Text style={{ fontSize: 48, marginBottom: 16 }}>📭</Text>
          <Text
            style={{
              color: '#FFFFFF',
              fontSize: 18,
              fontWeight: '600',
              textAlign: 'center',
              marginBottom: 8,
            }}
          >
            {t('explore.noContent')}
          </Text>
          <Text
            style={{
              color: '#666666',
              fontSize: 14,
              textAlign: 'center',
              lineHeight: 20,
            }}
          >
            {categoryInfo ? t(`category.${category}.name`, categoryInfo.displayName) : ''} {t('explore.noContentDescription')}
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
