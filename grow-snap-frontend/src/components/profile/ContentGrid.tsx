import React from 'react';
import { View, Text, Image, TouchableOpacity, Dimensions, FlatList, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { theme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';
import type { ContentResponse } from '@/types/content.types';

interface ContentGridProps {
  contents: ContentResponse[];
  loading?: boolean;
  onContentPress?: (content: ContentResponse) => void;
  numColumns?: number;
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
  const [imageError, setImageError] = React.useState(false);

  // item이 바뀔 때마다 에러 상태 초기화 (FlatList 재사용 대응)
  React.useEffect(() => {
    setImageError(false);
  }, [item.id, item.thumbnailUrl]);

  return (
    <TouchableOpacity
      style={[styles.gridItem, { width }]}
      onPress={onPress}
      activeOpacity={0.7}
    >
      {/* 썸네일 이미지 */}
      {!imageError && (
        <Image
          source={{ uri: item.thumbnailUrl }}
          style={styles.thumbnail}
          resizeMode="cover"
          onError={() => setImageError(true)}
        />
      )}

      {/* 에러 상태 */}
      {imageError && (
        <View style={styles.overlay}>
          <Ionicons name="alert-circle" size={24} color={theme.colors.gray[400]} />
        </View>
      )}

      {/* VIDEO 타입 표시 */}
      {!imageError && item.contentType === 'VIDEO' && (
        <View style={styles.videoIndicator}>
          <Ionicons name="play" size={16} color={theme.colors.text.inverse} />
        </View>
      )}

      {/* PHOTO 타입이면서 여러 사진인 경우 표시 */}
      {!imageError && item.contentType === 'PHOTO' && item.photoUrls && item.photoUrls.length > 1 && (
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
});

export const ContentGrid: React.FC<ContentGridProps> = ({
  contents,
  loading = false,
  onContentPress,
  numColumns = 3,
}) => {
  const styles = useStyles();

  // 그리드 아이템 너비 계산 (간격 고려)
  const itemWidth = (SCREEN_WIDTH - (numColumns + 1) * 2) / numColumns;

  const renderItem = ({ item }: { item: ContentResponse }) => (
    <GridItem
      item={item}
      width={itemWidth}
      onPress={() => onContentPress?.(item)}
    />
  );

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
        <Text style={styles.emptyText}>아직 업로드한 콘텐츠가 없습니다</Text>
        <Text style={styles.emptySubtext}>첫 콘텐츠를 업로드해보세요!</Text>
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
    />
  );
};
