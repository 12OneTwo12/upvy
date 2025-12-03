import React, { useState, useCallback, useEffect, useRef } from 'react';
import {
  View,
  Text,
  TextInput,
  FlatList,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  Keyboard,
  StatusBar,
  Image,
  Dimensions,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Video, ResizeMode } from 'expo-av';
import { theme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';
import {
  autocomplete,
  getTrendingKeywords,
  getSearchHistory,
  deleteSearchHistory,
  deleteAllSearchHistory,
  searchContents,
  searchUsers,
} from '@/api/search.api';
import type {
  AutocompleteSuggestion,
  TrendingKeyword,
  SearchHistoryItem,
  UserSearchResult,
} from '@/types/search.types';
import type { FeedItem } from '@/types/feed.types';
import type { RootStackParamList } from '@/types/navigation.types';
import { withErrorHandling } from '@/utils/errorHandler';

type TabType = 'creators' | 'shorts';
type NavigationProp = NativeStackNavigationProp<RootStackParamList>;

/**
 * 그리드 아이템 컴포넌트 (Instagram Explore 스타일)
 */
interface ExploreGridItemProps {
  item: FeedItem;
  index: number;
  totalItems: number;
  videoIndex?: number; // 비디오 순서 (사진은 undefined)
  onPress: (contentId: string) => void;
}

const ExploreGridItem: React.FC<ExploreGridItemProps> = ({ item, index, totalItems, videoIndex, onPress }) => {
  const [mediaLoading, setMediaLoading] = React.useState(true);
  const [retryCount, setRetryCount] = React.useState(0);
  const [showRetryButton, setShowRetryButton] = React.useState(false);
  const [mediaKey, setMediaKey] = React.useState(0);
  const loadingTimeoutRef = React.useRef<NodeJS.Timeout | null>(null);
  const retryTimeoutRef = React.useRef<NodeJS.Timeout | null>(null);
  const screenWidth = Dimensions.get('window').width;
  const gap = 2;

  const MAX_RETRIES = 3;
  const LOADING_TIMEOUT = 5000; // 5초 (썸네일이므로 짧게)

  // 기본 아이템 크기 (1/3 폭)
  const baseItemWidth = (screenWidth - gap * 2) / 3;

  // 사진은 무조건 짧게, 비디오만 다이나믹하게
  const isNotNearEnd = index < totalItems - 6;
  let isLarge = false;

  if (item.contentType === 'VIDEO' && videoIndex !== undefined && isNotNearEnd) {
    if (videoIndex === 0) {
      // 첫 번째 비디오: 무조건 길게
      isLarge = true;
    } else if (videoIndex === 1) {
      // 두 번째 비디오: 무조건 짧게
      isLarge = false;
    } else {
      // 세 번째부터: 매 3개마다 랜덤하게 하나씩 길게
      const positionInGroup = (videoIndex - 2) % 3;
      // contentId 기반 deterministic random (같은 비디오는 항상 같은 결과)
      const hash = item.contentId.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
      const selectedPosition = hash % 3; // 0, 1, 2 중 하나
      isLarge = positionInGroup === selectedPosition;
    }
  }

  // 너비와 높이 계산 (모두 1/3 폭)
  // isLarge: 큰 아이템 (1/3 폭, 2배 높이)
  // 일반: 1/3 폭, 1배 높이
  const itemWidth = baseItemWidth;
  const itemHeight = isLarge ? baseItemWidth * 2 + gap : baseItemWidth;

  // 로딩 타임아웃 및 재시도 타이머 정리
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

  // 미디어 로드 에러 핸들러 (재시도 로직 포함)
  const handleMediaError = React.useCallback(() => {
    clearAllTimers();

    setRetryCount(prevRetryCount => {
      if (prevRetryCount < MAX_RETRIES) {
        const newRetryCount = prevRetryCount + 1;
        const delay = Math.min(1000 * 2 ** prevRetryCount, 30000);
        console.log(`[Retry ${newRetryCount}/${MAX_RETRIES}] Retrying thumbnail load after ${delay}ms...`);

        retryTimeoutRef.current = setTimeout(() => {
          setMediaKey(key => key + 1); // useEffect를 트리거하여 재시도
        }, delay);

        return newRetryCount;
      }

      // 모든 재시도 실패 시에만 에러 로그
      console.error(`[Search] Thumbnail load failed after ${MAX_RETRIES} retries:`, item.contentId);
      setShowRetryButton(true);
      setMediaLoading(false);
      return prevRetryCount;
    });
  }, [item.contentId, clearAllTimers]);

  // item이 바뀌거나 재시도(mediaKey 변경) 시 로딩 시작
  React.useEffect(() => {
    setMediaLoading(true);
    setShowRetryButton(false);

    clearAllTimers();

    loadingTimeoutRef.current = setTimeout(handleMediaError, LOADING_TIMEOUT);

    return clearAllTimers;
  }, [item.contentId, mediaKey, handleMediaError, clearAllTimers]);

  // item.contentId가 변경될 때만 재시도 카운트 초기화
  React.useEffect(() => {
    setRetryCount(0);
    setMediaKey(0);
  }, [item.contentId]);

  // 수동 재시도 핸들러
  const handleManualRetry = React.useCallback(() => {
    setRetryCount(0);
    setMediaKey(prev => prev + 1);
  }, []);

  // 미디어 로드 성공 핸들러
  const handleMediaLoaded = React.useCallback(() => {
    clearAllTimers();
    setMediaLoading(false);
    setRetryCount(0);
  }, [clearAllTimers]);

  const styles = useStyles();

  return (
    <TouchableOpacity
      style={[styles.exploreGridItem, { width: itemWidth, height: itemHeight }]}
      onPress={() => onPress(item.contentId)}
      activeOpacity={0.95}
    >
      {/* 비디오 또는 이미지 */}
      {!showRetryButton && (
        <>
          {item.contentType === 'VIDEO' ? (
            <Video
              key={mediaKey}
              source={{ uri: item.url }}
              style={styles.exploreGridThumbnail}
              resizeMode={ResizeMode.COVER}
              shouldPlay={isLarge}
              isLooping
              isMuted
              useNativeControls={false}
              onLoad={handleMediaLoaded}
              onReadyForDisplay={handleMediaLoaded}
              onError={handleMediaError}
            />
          ) : (
            <Image
              key={mediaKey}
              source={{ uri: item.photoUrls?.[0] || item.thumbnailUrl }}
              style={styles.exploreGridThumbnail}
              resizeMode="cover"
              onLoad={handleMediaLoaded}
              onError={handleMediaError}
            />
          )}
        </>
      )}

      {/* 로딩 스피너 */}
      {mediaLoading && !showRetryButton && (
        <View style={styles.exploreLoadingOverlay}>
          <ActivityIndicator size="small" color={theme.colors.primary[500]} />
        </View>
      )}

      {/* 재시도 버튼 - 모든 재시도 실패 시 표시 */}
      {showRetryButton && (
        <TouchableOpacity
          onPress={handleManualRetry}
          style={[styles.exploreLoadingOverlay, { backgroundColor: 'rgba(0, 0, 0, 0.7)' }]}
          activeOpacity={0.8}
        >
          <Ionicons name="refresh" size={24} color="#FFFFFF" />
        </TouchableOpacity>
      )}

      {/* 비디오 타입 인디케이터 */}
      {!mediaLoading && !showRetryButton && item.contentType === 'VIDEO' && (
        <View style={styles.exploreVideoIndicator}>
          <Ionicons name="play" size={16} color={theme.colors.text.inverse} />
        </View>
      )}

      {/* 멀티 포토 인디케이터 */}
      {!mediaLoading && !showRetryButton && item.contentType === 'PHOTO' && item.photoUrls && item.photoUrls.length > 1 && (
        <View style={styles.explorePhotoIndicator}>
          <Ionicons name="copy-outline" size={16} color={theme.colors.text.inverse} />
        </View>
      )}
    </TouchableOpacity>
  );
};

export default function SearchScreen() {
  const styles = useStyles();
  const navigation = useNavigation<NavigationProp>();
  const insets = useSafeAreaInsets();

  // 검색 상태
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const [activeTab, setActiveTab] = useState<TabType>('creators');

  // 자동완성
  const [suggestions, setSuggestions] = useState<AutocompleteSuggestion[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [loadingSuggestions, setLoadingSuggestions] = useState(false);

  // 인기 검색어
  const [trendingKeywords, setTrendingKeywords] = useState<TrendingKeyword[]>([]);
  const [loadingTrending, setLoadingTrending] = useState(false);

  // 검색 기록
  const [searchHistory, setSearchHistory] = useState<SearchHistoryItem[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  // 검색 결과
  const [contentResults, setContentResults] = useState<FeedItem[]>([]);
  const [userResults, setUserResults] = useState<UserSearchResult[]>([]);
  const [loadingResults, setLoadingResults] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);

  // Debounce를 위한 ref
  const debounceTimer = useRef<NodeJS.Timeout | null>(null);

  // 인기 검색어 및 검색 기록 로드
  useEffect(() => {
    loadTrendingKeywords();
    loadSearchHistory();
  }, []);

  // 자동완성 debounce
  useEffect(() => {
    if (searchQuery.length > 0) {
      setShowSuggestions(true);

      if (debounceTimer.current) {
        clearTimeout(debounceTimer.current);
      }

      debounceTimer.current = setTimeout(() => {
        loadAutocomplete(searchQuery);
      }, 300);
    } else {
      setShowSuggestions(false);
      setSuggestions([]);
    }

    return () => {
      if (debounceTimer.current) {
        clearTimeout(debounceTimer.current);
      }
    };
  }, [searchQuery]);

  // 인기 검색어 로드
  const loadTrendingKeywords = useCallback(async () => {
    setLoadingTrending(true);
    const result = await withErrorHandling(
      () => getTrendingKeywords(10),
      { showAlert: false }
    );
    if (result) {
      setTrendingKeywords(result.keywords);
    }
    setLoadingTrending(false);
  }, []);

  // 검색 기록 로드
  const loadSearchHistory = useCallback(async () => {
    setLoadingHistory(true);
    const result = await withErrorHandling(
      () => getSearchHistory(10),
      { showAlert: false }
    );
    if (result) {
      setSearchHistory(result.keywords);
    }
    setLoadingHistory(false);
  }, []);

  // 자동완성 로드
  const loadAutocomplete = useCallback(async (query: string) => {
    setLoadingSuggestions(true);
    const result = await withErrorHandling(
      () => autocomplete({ q: query, limit: 10 }),
      { showAlert: false }
    );
    if (result) {
      setSuggestions(result.suggestions);
    }
    setLoadingSuggestions(false);
  }, []);

  // 검색 실행
  const handleSearch = useCallback(async (query: string) => {
    if (!query.trim()) return;

    setIsSearching(true);
    setLoadingResults(true);
    setHasSearched(true);
    setShowSuggestions(false);
    Keyboard.dismiss();

    // 콘텐츠 검색
    const contentResult = await withErrorHandling(
      () => searchContents({ q: query, limit: 20 }),
      { showAlert: false }
    );

    // 사용자 검색
    const userResult = await withErrorHandling(
      () => searchUsers({ q: query, limit: 20 }),
      { showAlert: false }
    );

    if (contentResult) {
      setContentResults(contentResult.content);
    }
    if (userResult) {
      setUserResults(userResult.content);
    }

    setLoadingResults(false);
    setIsSearching(false);

    // 검색 기록 새로고침
    loadSearchHistory();
  }, []);

  // 검색어 클릭
  const handleKeywordPress = useCallback((keyword: string) => {
    setSearchQuery(keyword);
    handleSearch(keyword);
  }, [handleSearch]);

  // 검색 기록 삭제
  const handleDeleteHistory = useCallback(async (keyword: string) => {
    await withErrorHandling(
      () => deleteSearchHistory(keyword),
      { showAlert: false }
    );
    loadSearchHistory();
  }, []);

  // 전체 검색 기록 삭제
  const handleClearAllHistory = useCallback(async () => {
    await withErrorHandling(
      () => deleteAllSearchHistory(),
      { showAlert: false }
    );
    loadSearchHistory();
  }, []);

  // 검색창 초기화
  const handleClearSearch = useCallback(() => {
    setSearchQuery('');
    setShowSuggestions(false);
    setHasSearched(false);
    setContentResults([]);
    setUserResults([]);
  }, []);

  // 자동완성 아이템 렌더
  const renderSuggestion = ({ item }: { item: AutocompleteSuggestion }) => (
    <TouchableOpacity
      style={styles.suggestionItem}
      onPress={() => handleKeywordPress(item.text)}
    >
      <Ionicons
        name={
          item.type === 'USER'
            ? 'person-outline'
            : item.type === 'TAG'
            ? 'pricetag-outline'
            : 'search-outline'
        }
        size={20}
        color={theme.colors.text.tertiary}
      />
      <Text style={styles.suggestionText}>{item.text}</Text>
      <Ionicons name="arrow-up-outline" size={20} color={theme.colors.text.tertiary} />
    </TouchableOpacity>
  );

  // 인기 검색어 아이템 렌더
  const renderTrendingKeyword = ({ item }: { item: TrendingKeyword }) => (
    <TouchableOpacity
      style={styles.trendingItem}
      onPress={() => handleKeywordPress(item.keyword)}
    >
      <View style={styles.trendingRank}>
        <Text style={styles.trendingRankText}>{item.rank}</Text>
      </View>
      <Text style={styles.trendingKeyword}>{item.keyword}</Text>
    </TouchableOpacity>
  );

  // 검색 기록 아이템 렌더
  const renderHistoryItem = ({ item }: { item: SearchHistoryItem }) => (
    <View style={styles.historyItem}>
      <TouchableOpacity
        style={styles.historyItemLeft}
        onPress={() => handleKeywordPress(item.keyword)}
      >
        <Ionicons name="time-outline" size={20} color={theme.colors.text.tertiary} />
        <Text style={styles.historyKeyword}>{item.keyword}</Text>
      </TouchableOpacity>
      <TouchableOpacity onPress={() => handleDeleteHistory(item.keyword)}>
        <Ionicons name="close-outline" size={20} color={theme.colors.text.tertiary} />
      </TouchableOpacity>
    </View>
  );

  // 콘텐츠 클릭 핸들러
  const handleContentPress = (contentId: string) => {
    navigation.navigate('ContentViewer', { contentId });
  };

  // 사용자 결과 아이템 렌더 (Instagram 스타일 - 컴팩트)
  const renderUserResult = ({ item }: { item: UserSearchResult }) => {
    // 팔로워 수 포맷팅 (Instagram 스타일)
    const formatFollowerCount = (count: number) => {
      if (count >= 1000000) {
        return `${(count / 1000000).toFixed(1)}M`;
      }
      if (count >= 10000) {
        return `${(count / 10000).toFixed(1)}만`;
      }
      if (count >= 1000) {
        return `${(count / 1000).toFixed(1)}K`;
      }
      return count.toString();
    };

    return (
      <TouchableOpacity
        style={styles.userResultItem}
        onPress={() => navigation.navigate('UserProfile', { userId: item.userId })}
        activeOpacity={0.9}
      >
        {/* 프로필 이미지 */}
        {item.profileImageUrl ? (
          <Image
            source={{ uri: item.profileImageUrl }}
            style={styles.userAvatar}
          />
        ) : (
          <View style={[styles.userAvatar, styles.userAvatarPlaceholder]}>
            <Text style={styles.userAvatarText}>
              {item.nickname.charAt(0).toUpperCase()}
            </Text>
          </View>
        )}

        {/* 사용자 정보 */}
        <View style={styles.userInfo}>
          <Text style={styles.userNickname} numberOfLines={1}>
            {item.nickname}
          </Text>
          {item.bio ? (
            <Text style={styles.userBio} numberOfLines={1}>
              {item.bio}
            </Text>
          ) : (
            <Text style={styles.userFollowers}>
              팔로워 {formatFollowerCount(item.followerCount)}
            </Text>
          )}
        </View>
      </TouchableOpacity>
    );
  };

  // 탭 렌더
  const renderTabs = () => {
    if (!hasSearched) return null;

    return (
      <View style={styles.tabContainer}>
        <TouchableOpacity
          style={[styles.tab, activeTab === 'creators' && styles.tabActive]}
          onPress={() => setActiveTab('creators')}
        >
          <Text style={[styles.tabText, activeTab === 'creators' && styles.tabTextActive]}>
            크리에이터
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tab, activeTab === 'shorts' && styles.tabActive]}
          onPress={() => setActiveTab('shorts')}
        >
          <Text style={[styles.tabText, activeTab === 'shorts' && styles.tabTextActive]}>
            쇼츠
          </Text>
        </TouchableOpacity>
      </View>
    );
  };

  // 검색 결과 렌더
  const renderResults = () => {
    if (loadingResults) {
      return (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={theme.colors.primary[500]} />
        </View>
      );
    }

    if (activeTab === 'creators') {
      return (
        <FlatList
          key="creators-list"
          data={userResults}
          renderItem={renderUserResult}
          keyExtractor={(item) => item.userId}
          ListEmptyComponent={
            <Text style={styles.emptyText}>검색 결과가 없습니다.</Text>
          }
          contentContainerStyle={styles.creatorListContainer}
        />
      );
    }

    if (activeTab === 'shorts') {
      // 비디오 인덱스 매핑 (사진 제외, 비디오만 카운트)
      const videoIndexMap = new Map<string, number>();
      let videoCount = 0;
      contentResults.forEach(item => {
        if (item.contentType === 'VIDEO') {
          videoIndexMap.set(item.contentId, videoCount);
          videoCount++;
        }
      });

      if (contentResults.length === 0) {
        return (
          <View style={styles.loadingContainer}>
            <Text style={styles.emptyText}>검색 결과가 없습니다.</Text>
          </View>
        );
      }

      // 3개 컬럼에 아이템 분배 (Masonry Layout - 가장 짧은 컬럼에 추가)
      const columns: FeedItem[][] = [[], [], []];
      const columnHeights = [0, 0, 0]; // 각 컬럼의 현재 높이 추적

      const screenWidth = Dimensions.get('window').width;
      const gap = 2;
      const baseItemWidth = (screenWidth - gap * 2) / 3;
      const baseItemHeight = baseItemWidth;

      contentResults.forEach((item, idx) => {
        // 이 아이템의 높이를 미리 계산
        let itemHeight = baseItemHeight;

        const videoIndex = item.contentType === 'VIDEO' ? videoIndexMap.get(item.contentId) : undefined;
        const isNotNearEnd = idx < contentResults.length - 6;

        if (item.contentType === 'VIDEO' && videoIndex !== undefined && isNotNearEnd) {
          if (videoIndex === 0) {
            itemHeight = baseItemHeight * 2 + gap;
          } else if (videoIndex === 1) {
            itemHeight = baseItemHeight;
          } else {
            const positionInGroup = (videoIndex - 2) % 3;
            const hash = item.contentId.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
            const selectedPosition = hash % 3;
            if (positionInGroup === selectedPosition) {
              itemHeight = baseItemHeight * 2 + gap;
            }
          }
        }

        // 가장 짧은 컬럼 찾기
        const shortestColumnIndex = columnHeights.indexOf(Math.min(...columnHeights));

        // 해당 컬럼에 아이템 추가
        columns[shortestColumnIndex].push(item);
        columnHeights[shortestColumnIndex] += itemHeight + gap; // gap 포함
      });

      return (
        <ScrollView
          showsVerticalScrollIndicator={false}
          contentContainerStyle={styles.exploreGridContainer}
        >
          <View style={styles.exploreMasonryContainer}>
            {columns.map((columnItems, colIndex) => (
              <View key={`col-${colIndex}`} style={styles.exploreMasonryColumn}>
                {columnItems.map((item) => (
                  <ExploreGridItem
                    key={item.contentId}
                    item={item}
                    index={contentResults.indexOf(item)}
                    totalItems={contentResults.length}
                    videoIndex={item.contentType === 'VIDEO' ? videoIndexMap.get(item.contentId) : undefined}
                    onPress={handleContentPress}
                  />
                ))}
              </View>
            ))}
          </View>
        </ScrollView>
      );
    }

    return null;
  };

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      <StatusBar barStyle="dark-content" />

      {/* 헤더 (Instagram 스타일) */}
      <View style={styles.header}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => navigation.goBack()}
          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
        >
          <Ionicons name="arrow-back" size={24} color={theme.colors.text.primary} />
        </TouchableOpacity>

        <View style={styles.searchInputContainer}>
          <Ionicons name="search-outline" size={18} color={theme.colors.text.tertiary} />
          <TextInput
            style={styles.searchInput}
            placeholder="검색"
            placeholderTextColor={theme.colors.text.tertiary}
            value={searchQuery}
            onChangeText={setSearchQuery}
            onSubmitEditing={() => handleSearch(searchQuery)}
            returnKeyType="search"
            autoCapitalize="none"
            autoCorrect={false}
            autoFocus
          />
          {searchQuery.length > 0 && (
            <TouchableOpacity onPress={handleClearSearch}>
              <Ionicons name="close-circle" size={18} color={theme.colors.text.tertiary} />
            </TouchableOpacity>
          )}
        </View>
      </View>

      {/* 검색 결과 탭 */}
      {renderTabs()}

      {/* 콘텐츠 영역 */}
      {hasSearched ? (
        renderResults()
      ) : showSuggestions && suggestions.length > 0 ? (
        /* 자동완성 */
        <FlatList
          data={suggestions}
          renderItem={renderSuggestion}
          keyExtractor={(item, index) => `${item.text}-${index}`}
          style={styles.suggestionsContainer}
        />
      ) : (
        /* 검색 전 화면 */
        <FlatList
          data={[]}
          renderItem={() => null}
          ListHeaderComponent={
            <>
              {/* 검색 기록 */}
              {searchHistory.length > 0 && (
                <View style={styles.section}>
                  <View style={styles.sectionHeader}>
                    <Text style={styles.sectionTitle}>최근 검색</Text>
                    <TouchableOpacity onPress={handleClearAllHistory}>
                      <Text style={styles.clearAllText}>모두 지우기</Text>
                    </TouchableOpacity>
                  </View>
                  {searchHistory.map((item, index) => (
                    <View key={`${item.keyword}-${index}`}>
                      {renderHistoryItem({ item } as any)}
                    </View>
                  ))}
                </View>
              )}

              {/* 인기 검색어 */}
              {(loadingTrending || trendingKeywords.length > 0) && (
                <View style={styles.section}>
                  <View style={styles.sectionHeader}>
                    <Text style={styles.sectionTitle}>인기 검색어</Text>
                  </View>
                  {loadingTrending ? (
                    <ActivityIndicator
                      size="small"
                      color={theme.colors.primary[500]}
                      style={styles.loadingIndicator}
                    />
                  ) : (
                    trendingKeywords.map((item, index) => (
                      <View key={`${item.keyword}-${index}`}>
                        {renderTrendingKeyword({ item } as any)}
                      </View>
                    ))
                  )}
                </View>
              )}
            </>
          }
        />
      )}
    </View>
  );
}

const useStyles = createStyleSheet({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },

  // 헤더 (Instagram 스타일)
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[3],
    paddingVertical: theme.spacing[2],
    backgroundColor: theme.colors.background.primary,
    borderBottomWidth: 0.5,
    borderBottomColor: theme.colors.border.light,
    gap: theme.spacing[2],
  },

  backButton: {
    padding: theme.spacing[1],
  },

  searchInputContainer: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: theme.colors.gray[100],
    borderRadius: theme.borderRadius.xl,
    paddingHorizontal: theme.spacing[3],
    paddingVertical: theme.spacing[2],
    gap: theme.spacing[2],
    height: 36,
  },

  searchInput: {
    flex: 1,
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    padding: 0,
  },

  // 탭
  tabContainer: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
    backgroundColor: theme.colors.background.primary,
  },

  tab: {
    flex: 1,
    paddingVertical: theme.spacing[3],
    alignItems: 'center',
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },

  tabActive: {
    borderBottomColor: theme.colors.primary[500],
  },

  tabText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.medium,
    color: theme.colors.text.tertiary,
  },

  tabTextActive: {
    color: theme.colors.primary[500],
    fontWeight: theme.typography.fontWeight.bold,
  },

  // 자동완성
  suggestionsContainer: {
    backgroundColor: theme.colors.background.primary,
  },

  suggestionItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
    gap: theme.spacing[3],
  },

  suggestionText: {
    flex: 1,
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
  },

  // 섹션
  section: {
    paddingVertical: theme.spacing[4],
  },

  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[4],
    marginBottom: theme.spacing[3],
  },

  sectionTitle: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
  },

  clearAllText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.primary[500],
    fontWeight: theme.typography.fontWeight.semibold,
  },

  // 검색 기록
  historyItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
  },

  historyItemLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: theme.spacing[3],
    flex: 1,
  },

  historyKeyword: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
  },

  // 인기 검색어
  trendingItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
    gap: theme.spacing[3],
  },

  trendingRank: {
    width: 24,
    height: 24,
    borderRadius: theme.borderRadius.sm,
    backgroundColor: theme.colors.gray[100],
    alignItems: 'center',
    justifyContent: 'center',
  },

  trendingRankText: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.secondary,
  },

  trendingKeyword: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
  },

  // 콘텐츠 결과
  contentResultItem: {
    flexDirection: 'row',
    padding: theme.spacing[4],
    gap: theme.spacing[3],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },

  contentThumbnail: {
    width: 120,
    height: 90,
    borderRadius: theme.borderRadius.base,
    backgroundColor: theme.colors.gray[200],
    alignItems: 'center',
    justifyContent: 'center',
  },

  contentThumbnailText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.tertiary,
  },

  contentInfo: {
    flex: 1,
    justifyContent: 'space-between',
  },

  contentTitle: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
  },

  contentCreator: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
  },

  contentStats: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.tertiary,
  },

  // 크리에이터 목록 컨테이너 (탭과 간격)
  creatorListContainer: {
    paddingTop: theme.spacing[3],
  },

  // 사용자 결과 (Instagram 스타일 - 컴팩트)
  userResultItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[2.5],
    gap: theme.spacing[3],
    backgroundColor: theme.colors.background.primary,
  },

  userAvatar: {
    width: 54,
    height: 54,
    borderRadius: theme.borderRadius.full,
    backgroundColor: theme.colors.gray[100],
  },

  userAvatarPlaceholder: {
    backgroundColor: theme.colors.primary[100],
    alignItems: 'center',
    justifyContent: 'center',
  },

  userAvatarText: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.primary[500],
  },

  userInfo: {
    flex: 1,
    justifyContent: 'center',
    gap: theme.spacing[0.5],
  },

  userNickname: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
  },

  userFollowers: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.normal,
    color: theme.colors.text.secondary,
  },

  userBio: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.normal,
    color: theme.colors.text.secondary,
  },


  // 로딩 및 빈 상태
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: theme.spacing[8],
  },

  loadingIndicator: {
    marginVertical: theme.spacing[4],
  },

  emptyText: {
    textAlign: 'center',
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.tertiary,
    paddingVertical: theme.spacing[8],
  },

  // Instagram Explore 그리드 스타일 (Masonry Layout)
  exploreGridContainer: {
    paddingTop: theme.spacing[2],
  },

  exploreMasonryContainer: {
    flexDirection: 'row',
    gap: 2,
  },

  exploreMasonryColumn: {
    flex: 1,
    gap: 2,
  },

  exploreGridItem: {
    position: 'relative',
    backgroundColor: theme.colors.gray[200],
  },

  exploreGridThumbnail: {
    width: '100%',
    height: '100%',
  },

  exploreLoadingOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: theme.colors.gray[100],
    justifyContent: 'center',
    alignItems: 'center',
  },

  exploreVideoIndicator: {
    position: 'absolute',
    top: theme.spacing[2],
    right: theme.spacing[2],
  },

  explorePhotoIndicator: {
    position: 'absolute',
    top: theme.spacing[2],
    right: theme.spacing[2],
  },
});
