import React, { useState, useCallback, useEffect, useRef } from 'react';
import {
  View,
  Text,
  TextInput,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  Keyboard,
  StatusBar,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
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
import { withErrorHandling } from '@/utils/errorHandler';

type TabType = 'all' | 'videos' | 'creators';

export default function SearchScreen() {
  const styles = useStyles();
  const navigation = useNavigation();
  const insets = useSafeAreaInsets();

  // 검색 상태
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const [activeTab, setActiveTab] = useState<TabType>('all');

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

  // 콘텐츠 결과 아이템 렌더 (간단히)
  const renderContentResult = ({ item }: { item: FeedItem }) => (
    <TouchableOpacity style={styles.contentResultItem}>
      <View style={styles.contentThumbnail}>
        <Text style={styles.contentThumbnailText}>썸네일</Text>
      </View>
      <View style={styles.contentInfo}>
        <Text style={styles.contentTitle} numberOfLines={2}>
          {item.title}
        </Text>
        <Text style={styles.contentCreator}>{item.creator.nickname}</Text>
        <Text style={styles.contentStats}>
          조회수 {item.interactions.viewCount || 0}회
        </Text>
      </View>
    </TouchableOpacity>
  );

  // 사용자 결과 아이템 렌더
  const renderUserResult = ({ item }: { item: UserSearchResult }) => (
    <TouchableOpacity style={styles.userResultItem}>
      <View style={styles.userAvatar}>
        <Text style={styles.userAvatarText}>
          {item.nickname.charAt(0).toUpperCase()}
        </Text>
      </View>
      <View style={styles.userInfo}>
        <Text style={styles.userNickname}>{item.nickname}</Text>
        {item.bio && (
          <Text style={styles.userBio} numberOfLines={1}>
            {item.bio}
          </Text>
        )}
        <Text style={styles.userFollowers}>팔로워 {item.followerCount}명</Text>
      </View>
    </TouchableOpacity>
  );

  // 탭 렌더
  const renderTabs = () => {
    if (!hasSearched) return null;

    return (
      <View style={styles.tabContainer}>
        <TouchableOpacity
          style={[styles.tab, activeTab === 'all' && styles.tabActive]}
          onPress={() => setActiveTab('all')}
        >
          <Text style={[styles.tabText, activeTab === 'all' && styles.tabTextActive]}>
            전체
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tab, activeTab === 'videos' && styles.tabActive]}
          onPress={() => setActiveTab('videos')}
        >
          <Text style={[styles.tabText, activeTab === 'videos' && styles.tabTextActive]}>
            비디오
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tab, activeTab === 'creators' && styles.tabActive]}
          onPress={() => setActiveTab('creators')}
        >
          <Text style={[styles.tabText, activeTab === 'creators' && styles.tabTextActive]}>
            크리에이터
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

    if (activeTab === 'all') {
      return (
        <FlatList
          data={contentResults}
          renderItem={renderContentResult}
          keyExtractor={(item) => item.contentId}
          ListEmptyComponent={
            <Text style={styles.emptyText}>검색 결과가 없습니다.</Text>
          }
        />
      );
    }

    if (activeTab === 'videos') {
      return (
        <FlatList
          data={contentResults}
          renderItem={renderContentResult}
          keyExtractor={(item) => item.contentId}
          ListEmptyComponent={
            <Text style={styles.emptyText}>검색 결과가 없습니다.</Text>
          }
        />
      );
    }

    if (activeTab === 'creators') {
      return (
        <FlatList
          data={userResults}
          renderItem={renderUserResult}
          keyExtractor={(item) => item.userId}
          ListEmptyComponent={
            <Text style={styles.emptyText}>검색 결과가 없습니다.</Text>
          }
        />
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

  // 사용자 결과
  userResultItem: {
    flexDirection: 'row',
    padding: theme.spacing[4],
    gap: theme.spacing[3],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },

  userAvatar: {
    width: 56,
    height: 56,
    borderRadius: theme.borderRadius.full,
    backgroundColor: theme.colors.primary[100],
    alignItems: 'center',
    justifyContent: 'center',
  },

  userAvatarText: {
    fontSize: theme.typography.fontSize.xl,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.primary[500],
  },

  userInfo: {
    flex: 1,
    justifyContent: 'center',
  },

  userNickname: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
  },

  userBio: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    marginTop: theme.spacing[1],
  },

  userFollowers: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.tertiary,
    marginTop: theme.spacing[1],
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
});
