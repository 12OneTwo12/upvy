import React from 'react';
import { View, Text, Image, TouchableOpacity, Dimensions, FlatList, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { theme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';
import type { ContentResponse } from '@/types/content.types';

interface ContentGridProps {
  contents: ContentResponse[];
  loading?: boolean;
  onContentPress?: (content: ContentResponse) => void;
  numColumns?: number;
  onEndReached?: () => void;
  onEndReachedThreshold?: number;
  isFetchingMore?: boolean;
}

const { width: SCREEN_WIDTH } = Dimensions.get('window');

/**
 * 그리드 아이템 컴포넌트
 */
interface GridItemProps {
  item: ContentResponse;
  width: number;
  onPress: () => void;
}

const GridItem: React.FC<GridItemProps> = ({ item, width, onPress }) => {
  const styles = useStyles();
  const [imageLoading, setImageLoading] = React.useState(true);
  const [retryCount, setRetryCount] = React.useState(0);
  const [showRetryButton, setShowRetryButton] = React.useState(false);
  const [imageKey, setImageKey] = React.useState(0);
  const loadingTimeoutRef = React.useRef<NodeJS.Timeout | null>(null);
  const retryTimeoutRef = React.useRef<NodeJS.Timeout | null>(null);

  const MAX_RETRIES = 3;
  const LOADING_TIMEOUT = 5000; // 5초 (썸네일이므로 짧게)

  // 타이머 정리
  const clearAllTimers = React.useCallback(() => {
    if (loadingTimeoutRef.current) {
      clearTimeout(loadingTimeoutRef.current);
      loadingTimeoutRef.current = null;
    }
    if (retryTimeoutRef.current) {
      clearTimeout(retryTimeoutRef.current);
      retryTimeoutRef.current = null;
    }
  }, []);

  // 이미지 로드 에러 핸들러 (재시도 로직 포함)
  const handleImageError = React.useCallback(() => {
    clearAllTimers();

    setRetryCount(prevRetryCount => {
      if (prevRetryCount < MAX_RETRIES) {
        const newRetryCount = prevRetryCount + 1;
        const delay = Math.min(1000 * 2 ** prevRetryCount, 30000);

        retryTimeoutRef.current = setTimeout(() => {
          setImageKey(key => key + 1);
        }, delay);

        return newRetryCount;
      }

      // 모든 재시도 실패 시에만 에러 로그
      console.error(`[ContentGrid] Thumbnail load failed after ${MAX_RETRIES} retries:`, item.id);
      setShowRetryButton(true);
      setImageLoading(false);
      return prevRetryCount;
    });
  }, [item.id, clearAllTimers]);

  // item이 바뀌거나 재시도(imageKey 변경) 시 로딩 시작
  React.useEffect(() => {
    setImageLoading(true);
    setShowRetryButton(false);

    clearAllTimers();

    loadingTimeoutRef.current = setTimeout(handleImageError, LOADING_TIMEOUT);

    return clearAllTimers;
  }, [item.id, item.thumbnailUrl, imageKey, handleImageError, clearAllTimers]);

  // item.id가 변경될 때만 재시도 카운트 초기화
  React.useEffect(() => {
    setRetryCount(0);
    setImageKey(0);
  }, [item.id, item.thumbnailUrl]);

  // 수동 재시도 핸들러
  const handleManualRetry = React.useCallback(() => {
    setRetryCount(0);
    setImageKey(prev => prev + 1);
  }, []);

  // 이미지 로드 성공 핸들러
  const handleImageLoaded = React.useCallback(() => {
    clearAllTimers();
    setImageLoading(false);
    setRetryCount(0);
  }, [clearAllTimers]);

  return (
    <TouchableOpacity
      style={[styles.gridItem, { width }]}
      onPress={onPress}
      activeOpacity={0.7}
    >
      {/* 썸네일 이미지 */}
      {!showRetryButton && (
        <Image
          key={imageKey}
          source={{ uri: item.thumbnailUrl }}
          style={styles.thumbnail}
          resizeMode="cover"
          onLoad={handleImageLoaded}
          onError={handleImageError}
        />
      )}

      {/* 로딩 인디케이터 */}
      {imageLoading && !showRetryButton && (
        <View style={styles.overlay}>
          <ActivityIndicator size="small" color={theme.colors.primary[500]} />
        </View>
      )}

      {/* 재시도 버튼 - 모든 재시도 실패 시 표시 */}
      {showRetryButton && (
        <TouchableOpacity
          style={styles.retryOverlay}
          onPress={handleManualRetry}
          activeOpacity={0.7}
        >
          <Ionicons name="refresh" size={24} color={theme.colors.gray[400]} />
        </TouchableOpacity>
      )}

      {/* VIDEO 타입 표시 */}
      {!imageLoading && !showRetryButton && item.contentType === 'VIDEO' && (
        <View style={styles.videoIndicator}>
          <Ionicons name="play" size={16} color={theme.colors.text.inverse} />
        </View>
      )}

      {/* PHOTO 타입이면서 여러 사진인 경우 표시 */}
      {!imageLoading && !showRetryButton && item.contentType === 'PHOTO' && item.photoUrls && item.photoUrls.length > 1 && (
        <View style={styles.photoIndicator}>
          <Ionicons name="images" size={16} color={theme.colors.text.inverse} />
        </View>
      )}
    </TouchableOpacity>
  );
};

/**
 * 인스타그램 스타일 콘텐츠 그리드
 * 정사각형 썸네일을 3열 그리드로 표시
 */
const useStyles = createStyleSheet({
  container: {
    flex: 1,
  },
  gridItem: {
    aspectRatio: 1,
    margin: 1,
    position: 'relative',
    backgroundColor: theme.colors.gray[100],
  },
  thumbnail: {
    width: '100%',
    height: '100%',
  },
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.3)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  retryOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  videoIndicator: {
    position: 'absolute',
    top: theme.spacing[2],
    right: theme.spacing[2],
  },
  photoIndicator: {
    position: 'absolute',
    top: theme.spacing[2],
    right: theme.spacing[2],
  },
  emptyContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: theme.spacing[12],
    paddingHorizontal: theme.spacing[6],
  },
  emptyIcon: {
    marginBottom: theme.spacing[4],
  },
  emptyText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.medium,
    color: theme.colors.text.secondary,
    textAlign: 'center',
    marginBottom: theme.spacing[1],
  },
  emptySubtext: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.normal,
    color: theme.colors.text.tertiary,
    textAlign: 'center',
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: theme.spacing[12],
  },
  footerLoader: {
    paddingVertical: theme.spacing[4],
    alignItems: 'center',
  },
});

export const ContentGrid: React.FC<ContentGridProps> = ({
  contents,
  loading = false,
  onContentPress,
  numColumns = 3,
  onEndReached,
  onEndReachedThreshold = 0.5,
  isFetchingMore = false,
}) => {
  const styles = useStyles();
  const { t } = useTranslation('profile');

  // 그리드 아이템 너비 계산 (간격 고려)
  const itemWidth = (SCREEN_WIDTH - (numColumns + 1) * 2) / numColumns;

  const renderItem = ({ item }: { item: ContentResponse }) => (
    <GridItem
      item={item}
      width={itemWidth}
      onPress={() => onContentPress?.(item)}
    />
  );

  const renderFooter = () => {
    if (!isFetchingMore) return null;
    return (
      <View style={styles.footerLoader}>
        <ActivityIndicator size="small" color={theme.colors.primary[500]} />
      </View>
    );
  };

  // Loading state
  if (loading && contents.length === 0) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color={theme.colors.primary[500]} />
      </View>
    );
  }

  // Empty state
  if (!loading && contents.length === 0) {
    return (
      <View style={styles.emptyContainer}>
        <Ionicons
          name="images-outline"
          size={64}
          color={theme.colors.gray[300]}
          style={styles.emptyIcon}
        />
        <Text style={styles.emptyText}>{t('content.noPosts')}</Text>
        <Text style={styles.emptySubtext}>{t('content.noPostsSubtitle')}</Text>
      </View>
    );
  }

  return (
    <FlatList
      data={contents}
      renderItem={renderItem}
      keyExtractor={(item) => item.id}
      numColumns={numColumns}
      scrollEnabled={false}
      showsVerticalScrollIndicator={false}
      contentContainerStyle={styles.container}
      onEndReached={onEndReached}
      onEndReachedThreshold={onEndReachedThreshold}
      ListFooterComponent={renderFooter}
    />
  );
};
