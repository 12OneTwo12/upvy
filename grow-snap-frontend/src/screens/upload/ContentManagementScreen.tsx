/**
 * 콘텐츠 관리 화면
 *
 * 인스타그램 스타일의 내 콘텐츠 관리
 * - 콘텐츠 목록 (그리드/리스트 뷰)
 * - 필터링 (전체, 비디오, 사진)
 * - 수정/삭제
 * - 간단 통계 (조회수, 좋아요)
 */

import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  FlatList,
  Image,
  Alert,
  RefreshControl,
  Dimensions,
} from 'react-native';
import { theme } from '@/theme';
import { getMyContents, deleteContent } from '@/api/content.api';
import type { ContentResponse, ContentType } from '@/types/content.types';
import { LoadingSpinner } from '@/components/common';

const SCREEN_WIDTH = Dimensions.get('window').width;
const GRID_COLUMNS = 3;
const IMAGE_SIZE = SCREEN_WIDTH / GRID_COLUMNS;

type ViewMode = 'grid' | 'list';
type FilterType = 'all' | 'VIDEO' | 'PHOTO';

export default function ContentManagementScreen({ navigation }: any) {
  const [contents, setContents] = useState<ContentResponse[]>([]);
  const [filteredContents, setFilteredContents] = useState<ContentResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [viewMode, setViewMode] = useState<ViewMode>('grid');
  const [filter, setFilter] = useState<FilterType>('all');

  useEffect(() => {
    loadContents();
  }, []);

  useEffect(() => {
    applyFilter();
  }, [filter, contents]);

  const loadContents = async () => {
    try {
      setIsLoading(true);
      const data = await getMyContents();
      setContents(data);
    } catch (error) {
      console.error('Failed to load contents:', error);
      Alert.alert('오류', '콘텐츠를 불러오는데 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await loadContents();
    setIsRefreshing(false);
  };

  const applyFilter = () => {
    if (filter === 'all') {
      setFilteredContents(contents);
    } else {
      setFilteredContents(contents.filter((c) => c.contentType === filter));
    }
  };

  const handleEdit = (content: ContentResponse) => {
    // TODO: 수정 화면으로 이동
    Alert.alert('준비 중', '콘텐츠 수정 기능은 곧 제공됩니다.');
  };

  const handleDelete = (content: ContentResponse) => {
    Alert.alert(
      '콘텐츠 삭제',
      '이 콘텐츠를 삭제하시겠습니까?\n삭제된 콘텐츠는 복구할 수 없습니다.',
      [
        { text: '취소', style: 'cancel' },
        {
          text: '삭제',
          style: 'destructive',
          onPress: async () => {
            try {
              await deleteContent(content.id);
              setContents(contents.filter((c) => c.id !== content.id));
              Alert.alert('완료', '콘텐츠가 삭제되었습니다.');
            } catch (error) {
              console.error('Failed to delete content:', error);
              Alert.alert('오류', '콘텐츠 삭제에 실패했습니다.');
            }
          },
        },
      ]
    );
  };

  const renderGridItem = ({ item }: { item: ContentResponse }) => (
    <TouchableOpacity
      style={styles.gridItem}
      onPress={() => {
        // TODO: 상세 화면으로 이동
        Alert.alert('콘텐츠', item.title, [
          { text: '수정', onPress: () => handleEdit(item) },
          { text: '삭제', onPress: () => handleDelete(item), style: 'destructive' },
          { text: '취소', style: 'cancel' },
        ]);
      }}
      activeOpacity={0.7}
    >
      <Image source={{ uri: item.thumbnailUrl }} style={styles.gridImage} />

      {/* 비디오 표시 */}
      {item.contentType === 'VIDEO' && (
        <View style={styles.videoIndicator}>
          <Text style={styles.videoIcon}>▶️</Text>
        </View>
      )}

      {/* 사진 개수 표시 */}
      {item.contentType === 'PHOTO' && item.photoUrls && item.photoUrls.length > 1 && (
        <View style={styles.photoCountBadge}>
          <Text style={styles.photoCountText}>📷 {item.photoUrls.length}</Text>
        </View>
      )}
    </TouchableOpacity>
  );

  const renderListItem = ({ item }: { item: ContentResponse }) => (
    <TouchableOpacity
      style={styles.listItem}
      onPress={() => {
        Alert.alert('콘텐츠', item.title, [
          { text: '수정', onPress: () => handleEdit(item) },
          { text: '삭제', onPress: () => handleDelete(item), style: 'destructive' },
          { text: '취소', style: 'cancel' },
        ]);
      }}
      activeOpacity={0.7}
    >
      <Image source={{ uri: item.thumbnailUrl }} style={styles.listThumbnail} />

      <View style={styles.listContent}>
        <Text style={styles.listTitle} numberOfLines={2}>
          {item.title}
        </Text>
        <Text style={styles.listDescription} numberOfLines={1}>
          {item.description || '설명 없음'}
        </Text>

        <View style={styles.listStats}>
          <Text style={styles.listStatText}>👁 {0}</Text>
          <Text style={styles.listStatText}>❤️ {0}</Text>
          <Text style={styles.listStatText}>
            {item.contentType === 'VIDEO' ? '🎥 비디오' : '📷 사진'}
          </Text>
        </View>

        <Text style={styles.listDate}>
          {new Date(item.createdAt).toLocaleDateString('ko-KR')}
        </Text>
      </View>
    </TouchableOpacity>
  );

  if (isLoading) {
    return (
      <View style={styles.loadingContainer}>
        <LoadingSpinner size="large" />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Text style={styles.headerButton}>뒤로</Text>
        </TouchableOpacity>

        <Text style={styles.headerTitle}>내 콘텐츠</Text>

        <TouchableOpacity onPress={() => setViewMode(viewMode === 'grid' ? 'list' : 'grid')}>
          <Text style={styles.headerButton}>{viewMode === 'grid' ? '리스트' : '그리드'}</Text>
        </TouchableOpacity>
      </View>

      {/* 통계 */}
      <View style={styles.statsContainer}>
        <View style={styles.statItem}>
          <Text style={styles.statValue}>{contents.length}</Text>
          <Text style={styles.statLabel}>전체</Text>
        </View>
        <View style={styles.statItem}>
          <Text style={styles.statValue}>
            {contents.filter((c) => c.contentType === 'VIDEO').length}
          </Text>
          <Text style={styles.statLabel}>비디오</Text>
        </View>
        <View style={styles.statItem}>
          <Text style={styles.statValue}>
            {contents.filter((c) => c.contentType === 'PHOTO').length}
          </Text>
          <Text style={styles.statLabel}>사진</Text>
        </View>
      </View>

      {/* 필터 */}
      <View style={styles.filterContainer}>
        <TouchableOpacity
          style={[styles.filterButton, filter === 'all' && styles.filterButtonActive]}
          onPress={() => setFilter('all')}
        >
          <Text
            style={[
              styles.filterButtonText,
              filter === 'all' && styles.filterButtonTextActive,
            ]}
          >
            전체
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.filterButton, filter === 'VIDEO' && styles.filterButtonActive]}
          onPress={() => setFilter('VIDEO')}
        >
          <Text
            style={[
              styles.filterButtonText,
              filter === 'VIDEO' && styles.filterButtonTextActive,
            ]}
          >
            비디오
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.filterButton, filter === 'PHOTO' && styles.filterButtonActive]}
          onPress={() => setFilter('PHOTO')}
        >
          <Text
            style={[
              styles.filterButtonText,
              filter === 'PHOTO' && styles.filterButtonTextActive,
            ]}
          >
            사진
          </Text>
        </TouchableOpacity>
      </View>

      {/* 콘텐츠 목록 */}
      {filteredContents.length === 0 ? (
        <View style={styles.emptyContainer}>
          <Text style={styles.emptyIcon}>📦</Text>
          <Text style={styles.emptyTitle}>콘텐츠가 없습니다</Text>
          <Text style={styles.emptyDescription}>
            첫 번째 콘텐츠를 업로드해보세요!
          </Text>
        </View>
      ) : (
        <FlatList
          data={filteredContents}
          renderItem={viewMode === 'grid' ? renderGridItem : renderListItem}
          keyExtractor={(item) => item.id}
          numColumns={viewMode === 'grid' ? GRID_COLUMNS : 1}
          key={viewMode} // 뷰 모드 변경 시 리렌더링
          contentContainerStyle={styles.listContainer}
          refreshControl={
            <RefreshControl
              refreshing={isRefreshing}
              onRefresh={handleRefresh}
              tintColor={theme.colors.primary[500]}
            />
          }
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },
  loadingContainer: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
    justifyContent: 'center',
    alignItems: 'center',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  headerButton: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
  },
  headerTitle: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
  },
  statsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingVertical: theme.spacing[4],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  statItem: {
    alignItems: 'center',
  },
  statValue: {
    fontSize: theme.typography.fontSize['2xl'],
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[1],
  },
  statLabel: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
  },
  filterContainer: {
    flexDirection: 'row',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
    gap: theme.spacing[2],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  filterButton: {
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[2],
    borderRadius: theme.borderRadius.full,
    backgroundColor: theme.colors.gray[100],
  },
  filterButtonActive: {
    backgroundColor: theme.colors.primary[500],
  },
  filterButtonText: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.medium,
    color: theme.colors.text.secondary,
  },
  filterButtonTextActive: {
    color: theme.colors.text.inverse,
  },
  listContainer: {
    paddingBottom: theme.spacing[20],
  },
  // 그리드 뷰
  gridItem: {
    width: IMAGE_SIZE,
    height: IMAGE_SIZE,
    padding: 1,
    position: 'relative',
  },
  gridImage: {
    width: '100%',
    height: '100%',
    backgroundColor: theme.colors.gray[200],
  },
  videoIndicator: {
    position: 'absolute',
    bottom: 8,
    right: 8,
  },
  videoIcon: {
    fontSize: 16,
  },
  photoCountBadge: {
    position: 'absolute',
    top: 8,
    right: 8,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: theme.borderRadius.sm,
  },
  photoCountText: {
    color: theme.colors.text.inverse,
    fontSize: theme.typography.fontSize.xs,
    fontWeight: theme.typography.fontWeight.medium,
  },
  // 리스트 뷰
  listItem: {
    flexDirection: 'row',
    padding: theme.spacing[4],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  listThumbnail: {
    width: 100,
    height: 100,
    borderRadius: theme.borderRadius.base,
    backgroundColor: theme.colors.gray[200],
  },
  listContent: {
    flex: 1,
    marginLeft: theme.spacing[3],
    justifyContent: 'space-between',
  },
  listTitle: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[1],
  },
  listDescription: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    marginBottom: theme.spacing[2],
  },
  listStats: {
    flexDirection: 'row',
    gap: theme.spacing[3],
  },
  listStatText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.tertiary,
  },
  listDate: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
    marginTop: theme.spacing[1],
  },
  // 빈 상태
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[6],
  },
  emptyIcon: {
    fontSize: 64,
    marginBottom: theme.spacing[4],
  },
  emptyTitle: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[2],
  },
  emptyDescription: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.secondary,
    textAlign: 'center',
  },
});
