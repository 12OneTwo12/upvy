/**
 * 피드 오버레이 컴포넌트
 *
 * Instagram Reels 스타일의 정보 오버레이
 * - 하단 좌측: 크리에이터 정보, 제목, 설명
 * - 하단 우측: 인터랙션 버튼 (좋아요, 댓글, 저장, 공유, 신고)
 */

import React, { useState, useRef, useEffect, useMemo, useCallback } from 'react';
import { View, Text, TouchableOpacity, Image, StyleSheet, ScrollView, Dimensions, Animated, Easing } from 'react-native';
import { useTranslation } from 'react-i18next';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useAuthStore } from '@/stores/authStore';
import { useQuizStore } from '@/stores/quizStore';
import { ReportModal } from '@/components/report/ReportModal';
import { ActionSheet, ActionSheetOption } from '@/components/common/ActionSheet';
import { BlockConfirmModal } from '@/components/block/BlockConfirmModal';
import { DeleteConfirmModal, ContentEditModal } from '@/components/content';
import { QuizActionButton, QuizToggleButton } from '@/components/quiz';
import type { CreatorInfo, InteractionInfo, Category } from '@/types/feed.types';
import type { QuizMetadataResponse } from '@/types/quiz.types';
import type { BlockType } from '@/types/block.types';
import type { RootStackParamList } from '@/types/navigation.types';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');
const MAX_EXPAND_HEIGHT = 150; // 최대 확장 높이

type NavigationProp = NativeStackNavigationProp<RootStackParamList>;

interface FeedOverlayProps {
  creator: CreatorInfo;
  title: string;
  description: string | null;
  interactions: InteractionInfo;
  contentId: string; // 신고 기능을 위한 콘텐츠 ID
  category: Category; // 수정 기능을 위한 카테고리
  tags: string[]; // 수정 기능을 위한 태그
  language?: string; // 수정 기능을 위한 언어 (기본값: 'ko')
  quiz?: QuizMetadataResponse | null; // 퀴즈 메타데이터
  onLike?: () => void;
  onComment?: () => void;
  onSave?: () => void;
  onShare?: () => void;
  onFollow?: () => void;
  onCreatorPress?: () => void;
  onQuizPress?: () => void; // 퀴즈 보기 버튼 클릭 시 호출
  onBlockSuccess?: () => void; // 차단 성공 시 호출
  onDeleteSuccess?: () => void; // 삭제 성공 시 호출
  onEditSuccess?: () => void; // 수정 성공 시 호출
  isExpanded: boolean;
  setIsExpanded: (expanded: boolean) => void;
  tabBarHeight: number;
}

// 로딩 상태인지 확인
const isLoadingState = (creator: CreatorInfo) => {
  return creator.userId === 'loading';
};

// 재생바 영역 높이
const PROGRESS_BAR_AREA = 20;
const DEFAULT_BOTTOM_MARGIN = 24; // 탭바가 없을 때 하단 여백

/**
 * 태그 목록 컨테이너
 * Text 컴포넌트로 감싸서 본문과 동일한 lineHeight 적용
 */
interface ClickableTagsProps {
  tags: string[];
  onTagPress: (tag: string) => void;
  style?: any;
  numberOfLines?: number;
}

const ClickableTags: React.FC<ClickableTagsProps> = ({
  tags,
  onTagPress,
  style,
  numberOfLines
}) => {
  // Text 컴포넌트로 감싸서 본문과 동일한 자연스러운 줄 간격 적용
  // numberOfLines 지정 시 truncate, 미지정 시 자동 줄바꿈
  return (
    <Text style={style} numberOfLines={numberOfLines}>
      {tags.map((tag, index) => (
        <React.Fragment key={`${tag}-${index}`}>
          <Text onPress={() => onTagPress(tag)}>#{tag}</Text>
          {index < tags.length - 1 && ' '}
        </React.Fragment>
      ))}
    </Text>
  );
};

export const FeedOverlay: React.FC<FeedOverlayProps> = ({
  creator,
  title,
  description,
  interactions,
  contentId,
  category,
  tags,
  language = 'ko',
  quiz,
  onLike,
  onComment,
  onSave,
  onShare,
  onFollow,
  onCreatorPress,
  onQuizPress,
  onBlockSuccess,
  onDeleteSuccess,
  onEditSuccess,
  isExpanded,
  setIsExpanded,
  tabBarHeight,
}) => {
  const { t } = useTranslation('feed');
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const navigation = useNavigation<NavigationProp>();

  // Quiz store
  const { isQuizAutoDisplayEnabled, toggleQuizAutoDisplay } = useQuizStore();
  const currentUser = useAuthStore((state) => state.user);
  const isLoading = isLoadingState(creator);
  const isOwnPost = !isLoading && currentUser && currentUser.id === creator.userId;
  const shouldShowFollowButton = !isLoading && currentUser && currentUser.id !== creator.userId;
  const [showActionSheet, setShowActionSheet] = useState(false);
  const [showReportModal, setShowReportModal] = useState(false);
  const [showBlockModal, setShowBlockModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [blockType, setBlockType] = useState<BlockType>('content');

  // 확장/축소 애니메이션 값 (0 = 축소, 1 = 확장)
  const expandAnim = useRef(new Animated.Value(0)).current;

  // 하단 패딩 = 탭바 높이 + 재생바 영역 + 여유 공간 (탭바가 없으면 기본 여백 사용)
  const effectiveTabBarHeight = tabBarHeight > 0 ? tabBarHeight : DEFAULT_BOTTOM_MARGIN;
  const bottomPadding = effectiveTabBarHeight + PROGRESS_BAR_AREA + 16;

  // 높이 애니메이션 상수
  const COLLAPSED_HEIGHT = 44; // 약 2줄 (13px font * 18px line-height * 2 + margin)
  const EXPANDED_MAX_HEIGHT = MAX_EXPAND_HEIGHT + 50; // 전체 콘텐츠 + 태그 + 여유
  const ANIMATION_DURATION = 280; // 자연스러운 속도

  // 축소 콘텐츠 높이 애니메이션 (COLLAPSED_HEIGHT → 0)
  const collapsedHeight = expandAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [COLLAPSED_HEIGHT, 0],
  });

  // 확장 콘텐츠 높이 애니메이션 (0 → EXPANDED_MAX_HEIGHT)
  const expandedMaxHeight = expandAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [0, EXPANDED_MAX_HEIGHT],
  });

  // 축소 상태 opacity (0~30% 구간에서 빠르게 사라짐)
  const collapsedOpacity = expandAnim.interpolate({
    inputRange: [0, 0.3],
    outputRange: [1, 0],
    extrapolate: 'clamp',
  });

  // 축소 상태 태그 높이 (0 = 정상, 1 = 높이 0)
  const collapsedTagHeight = expandAnim.interpolate({
    inputRange: [0, 0.3],
    outputRange: [1, 0],
    extrapolate: 'clamp',
  });

  // 확장 상태 opacity (70~100% 구간에서 빠르게 나타남)
  const expandedOpacity = expandAnim.interpolate({
    inputRange: [0.7, 1],
    outputRange: [0, 1],
    extrapolate: 'clamp',
  });

  // 확장 배경 overlay opacity (댓글 모달처럼 fade in)
  const expandedBackgroundOpacity = expandAnim.interpolate({
    inputRange: [0, 1],
    outputRange: [0, 1],
    extrapolate: 'clamp',
  });

  // 확장/축소 애니메이션 - isExpanded 변경 시 Animated.timing 실행
  useEffect(() => {
    Animated.timing(expandAnim, {
      toValue: isExpanded ? 1 : 0,
      duration: ANIMATION_DURATION,
      easing: Easing.out(Easing.cubic), // 부드러운 감속
      useNativeDriver: false, // height 애니메이션은 native driver 불가
    }).start();
  }, [isExpanded, expandAnim]);

  // 제목 + 설명 조합 (설명이 없으면 제목만 표시)
  const fullText = description ? `${title}\n${description}` : title;

  // 설명이 길면 더보기 표시 (60자 이상)
  const shouldShowMore = fullText.length > 60;

  // 액션 시트 옵션 - 내 콘텐츠 vs 타인 콘텐츠 분기
  const actionSheetOptions: ActionSheetOption[] = useMemo(() => {
    if (isOwnPost) {
      // 내 콘텐츠: 수정, 삭제 옵션
      return [
        {
          label: t('menu.edit'),
          icon: 'create-outline',
          onPress: () => setShowEditModal(true),
        },
        {
          label: t('menu.delete'),
          icon: 'trash-outline',
          onPress: () => setShowDeleteModal(true),
          destructive: true,
        },
      ];
    }

    // 타인 콘텐츠: 관심없음, 차단, 신고 옵션
    return [
      {
        label: t('menu.notInterested'),
        icon: 'eye-off-outline',
        onPress: () => {
          setBlockType('content');
          setShowBlockModal(true);
        },
      },
      {
        label: t('menu.blockUser'),
        icon: 'person-remove-outline',
        onPress: () => {
          setBlockType('user');
          setShowBlockModal(true);
        },
        destructive: true,
      },
      {
        label: t('menu.report'),
        icon: 'alert-circle-outline',
        onPress: () => setShowReportModal(true),
        destructive: true,
      },
    ];
  }, [t, isOwnPost]);

  /**
   * 태그 클릭 - Search 탭으로 네비게이션
   */
  const handleTagPress = useCallback((tag: string) => {
    navigation.navigate('Main', {
      screen: 'Search',
      params: {
        screen: 'SearchMain',
        params: {
          initialQuery: tag,      // # 없이 태그 텍스트만 전달
          initialTab: 'shorts',   // 콘텐츠 탭으로 자동 전환
        },
      },
    });
  }, [navigation]);

  const formatCount = (count: number): string => {
    if (count >= 1000000) {
      return `${(count / 1000000).toFixed(1)}M`;
    }
    if (count >= 1000) {
      return `${(count / 1000).toFixed(1)}K`;
    }
    return count.toString();
  };

  return (
    <>
      {/* 확장 시 전체 화면 어두운 overlay - 댓글 모달처럼 */}
      <Animated.View
        style={[
          styles.fullScreenOverlay,
          {
            opacity: expandedBackgroundOpacity,
          }
        ]}
        pointerEvents="none"
      />

      {/* 하단 콘텐츠 - Animated */}
      <View
        style={[
          styles.container,
          {
            paddingBottom: bottomPadding,
          }
        ]}
      >
        {/* 오른쪽 상단: 퀴즈 버튼들 */}
        {/* QuizToggleButton: 항상 표시 (전역 설정) */}
        {/* QuizActionButton: 퀴즈가 있을 때만 표시 */}
        <View style={styles.quizButtonsContainer}>
          {quiz && onQuizPress && (
            <>
              <QuizActionButton
                hasAttempted={quiz.hasAttempted}
                onPress={onQuizPress}
              />
              <View style={{ width: 8 }} />
            </>
          )}
          <QuizToggleButton
            isEnabled={isQuizAutoDisplayEnabled}
            onToggle={toggleQuizAutoDisplay}
          />
        </View>

        <View style={styles.content}>
          {/* 좌측: 크리에이터 정보 + 콘텐츠 정보 */}
          <View style={styles.leftSection}>
            {/* 크리에이터 프로필 */}
            {isLoading ? (
              // 스켈레톤 UI
              <View style={styles.creatorContainer}>
                <View style={[styles.profilePlaceholder, styles.skeleton]} />
                <View style={[styles.skeletonText, { width: 100, marginLeft: 8 }]} />
              </View>
            ) : (
              <TouchableOpacity
                onPress={onCreatorPress}
                style={styles.creatorContainer}
              >
                {creator.profileImageUrl ? (
                  <Image
                    source={{ uri: creator.profileImageUrl }}
                    style={styles.profileImage}
                  />
                ) : (
                  <View style={styles.profilePlaceholder}>
                    <Text style={styles.profilePlaceholderText}>
                      {creator.nickname.charAt(0).toUpperCase()}
                    </Text>
                  </View>
                )}
                <Text style={styles.creatorName}>{creator.nickname}</Text>
                {/* 자기 게시물이 아닐 때만 팔로우 버튼 표시 */}
                {shouldShowFollowButton && (
                  <TouchableOpacity style={styles.followButton} onPress={onFollow}>
                    <Text style={styles.followButtonText}>
                      {creator.isFollowing ? t('actions.following') : t('actions.follow')}
                    </Text>
                  </TouchableOpacity>
                )}
              </TouchableOpacity>
            )}

            {/* 콘텐츠 설명 - 높이 애니메이션 + opacity 크로스페이드 */}
            <View style={styles.descriptionContainer}>
              {isLoading ? (
                // 스켈레톤 UI
                <>
                  <View style={[styles.skeletonText, { width: '90%', marginBottom: 6 }]} />
                  <View style={[styles.skeletonText, { width: '70%' }]} />
                </>
              ) : (
                <>
                  {/* 축소 콘텐츠 - 높이가 줄어들면서 위로 밀림 */}
                  <Animated.View
                    style={{
                      height: collapsedHeight,
                      overflow: 'hidden',
                      opacity: collapsedOpacity,
                    }}
                    pointerEvents={isExpanded ? 'none' : 'auto'}
                  >
                    <TouchableOpacity
                      onPress={() => shouldShowMore && setIsExpanded(true)}
                      activeOpacity={shouldShowMore ? 0.7 : 1}
                      disabled={!shouldShowMore}
                    >
                      <Text style={styles.description} numberOfLines={2}>
                        <Text style={styles.titleText}>{title}</Text>
                        {description && `\n${description}`}
                      </Text>
                    </TouchableOpacity>
                  </Animated.View>

                  {/* 확장 콘텐츠 - 높이가 늘어나면서 위로 확장 */}
                  <Animated.View
                    style={{
                      maxHeight: expandedMaxHeight,
                      overflow: 'hidden',
                      opacity: expandedOpacity,
                    }}
                    pointerEvents={isExpanded ? 'auto' : 'none'}
                  >
                    <ScrollView
                      style={styles.expandedInlineScrollView}
                      contentContainerStyle={styles.expandedScrollContent}
                      showsVerticalScrollIndicator={true}
                      nestedScrollEnabled
                      scrollEnabled={isExpanded}
                    >
                      <Text style={styles.description}>
                        <Text style={styles.titleText}>{title}</Text>
                        {description && `\n${description}`}
                      </Text>
                      {tags && tags.length > 0 && (
                        <View style={{ marginTop: 8 }}>
                          <ClickableTags
                            tags={tags}
                            onTagPress={handleTagPress}
                            style={styles.expandedInlineTagsText}
                          />
                        </View>
                      )}
                    </ScrollView>
                  </Animated.View>

                  {/* 태그 - 축소 상태에서 별도로 표시 (확장 시 fade out & 높이 축소) */}
                  {tags && tags.length > 0 && (
                    <Animated.View
                      style={{
                        opacity: collapsedOpacity,
                        transform: [{ scaleY: collapsedTagHeight }],
                        height: collapsedTagHeight.interpolate({
                          inputRange: [0, 1],
                          outputRange: [0, 24], // 태그 높이 (marginTop 6 + lineHeight 18)
                        }),
                      }}
                      pointerEvents={isExpanded ? 'none' : 'auto'}  // 확장 시 터치 비활성화
                    >
                      <View style={styles.tagsContainer}>
                        <ClickableTags
                          tags={tags}
                          onTagPress={handleTagPress}
                          style={styles.tagsText}
                          numberOfLines={1}
                        />
                      </View>
                    </Animated.View>
                  )}
                </>
              )}
            </View>
          </View>

          {/* 우측: 인터랙션 버튼 */}
          <View style={[styles.rightSection, { bottom: bottomPadding - 30 }]}>
            {/* 좋아요 */}
            <TouchableOpacity onPress={onLike} style={styles.actionButton}>
              <Ionicons
                name={interactions.isLiked ? "heart" : "heart-outline"}
                size={32}
                color={interactions.isLiked ? "#FF0000" : "#FFFFFF"}
                style={styles.iconShadow}
              />
              <Text style={[styles.actionCount, isLoading && { opacity: 0 }]}>
                {isLoading ? '0' : formatCount(interactions.likeCount)}
              </Text>
            </TouchableOpacity>

            {/* 댓글 */}
            <TouchableOpacity onPress={onComment} style={styles.actionButton}>
              <Ionicons name="chatbubble-outline" size={30} color="#FFFFFF" style={styles.iconShadow} />
              <Text style={[styles.actionCount, isLoading && { opacity: 0 }]}>
                {isLoading ? '0' : formatCount(interactions.commentCount)}
              </Text>
            </TouchableOpacity>

            {/* 저장 */}
            <TouchableOpacity onPress={onSave} style={styles.actionButton}>
              <Ionicons
                name={interactions.isSaved ? "bookmark" : "bookmark-outline"}
                size={30}
                color="#FFFFFF"
                style={styles.iconShadow}
              />
              <Text style={[styles.actionCount, isLoading && { opacity: 0 }]}>
                {isLoading ? '0' : formatCount(interactions.saveCount)}
              </Text>
            </TouchableOpacity>

            {/* 공유 */}
            <TouchableOpacity onPress={onShare} style={styles.actionButton}>
              <Ionicons name="paper-plane-outline" size={30} color="#FFFFFF" style={styles.iconShadow} />
              <Text style={[styles.actionCount, isLoading && { opacity: 0 }]}>
                {isLoading ? '0' : formatCount(interactions.shareCount)}
              </Text>
            </TouchableOpacity>

            {/* 더보기 (점 3개) */}
            <TouchableOpacity
              style={styles.actionButton}
              onPress={() => setShowActionSheet(true)}
            >
              <Ionicons name="ellipsis-vertical" size={24} color="#FFFFFF" style={styles.iconShadow} />
            </TouchableOpacity>
          </View>
        </View>

        {/* 액션 시트 */}
        <ActionSheet
          visible={showActionSheet}
          onClose={() => setShowActionSheet(false)}
          options={actionSheetOptions}
        />

        {/* 신고 모달 */}
        <ReportModal
          visible={showReportModal}
          onClose={() => setShowReportModal(false)}
          targetType="content"
          targetId={contentId}
          targetName={title}
          contentCategory={category}
        />

        {/* 차단 확인 모달 */}
        <BlockConfirmModal
          visible={showBlockModal}
          onClose={() => setShowBlockModal(false)}
          blockType={blockType}
          targetId={blockType === 'user' ? creator.userId : contentId}
          targetName={blockType === 'user' ? creator.nickname : title}
          onSuccess={() => {
            // 차단 성공 시 다음 콘텐츠로 자동 스크롤 (Instagram/TikTok 스타일)
            onBlockSuccess?.();
          }}
        />

        {/* 삭제 확인 모달 */}
        <DeleteConfirmModal
          visible={showDeleteModal}
          onClose={() => setShowDeleteModal(false)}
          contentId={contentId}
          contentTitle={title}
          onSuccess={() => {
            // 피드/콘텐츠 쿼리 무효화
            queryClient.invalidateQueries({ queryKey: ['feed'] });
            queryClient.invalidateQueries({ queryKey: ['myContents'] });
            queryClient.invalidateQueries({ queryKey: ['content', contentId] });
            onDeleteSuccess?.();
          }}
        />

        {/* 수정 모달 */}
        <ContentEditModal
          visible={showEditModal}
          onClose={() => setShowEditModal(false)}
          contentId={contentId}
          initialTitle={title}
          initialDescription={description}
          initialCategory={category}
          initialTags={tags}
          initialLanguage={language}
          onSuccess={() => {
            // 피드/콘텐츠 쿼리 무효화
            queryClient.invalidateQueries({ queryKey: ['feed'] });
            queryClient.invalidateQueries({ queryKey: ['myContents'] });
            queryClient.invalidateQueries({ queryKey: ['content', contentId] });
            onEditSuccess?.();
          }}
        />

      </View>
    </>
  );
};

const styles = StyleSheet.create({
  fullScreenOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
  },
  container: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    paddingHorizontal: 12,
    // paddingBottom은 동적으로 설정됨
  },
  quizButtonsContainer: {
    position: 'absolute',
    top: 60,
    right: 16,
    flexDirection: 'row',
    alignItems: 'center',
    zIndex: 10,
  },
  content: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
  },
  leftSection: {
    flex: 1,
    paddingRight: 60,
  },
  creatorContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 10,
  },
  profileImage: {
    width: 38,
    height: 38,
    borderRadius: 19,
    borderWidth: 1.5,
    borderColor: '#FFFFFF',
  },
  profilePlaceholder: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: '#dcfce7',
    borderWidth: 1.5,
    borderColor: '#FFFFFF',
    justifyContent: 'center',
    alignItems: 'center',
  },
  profilePlaceholderText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#22c55e',
  },
  skeleton: {
    backgroundColor: 'rgba(255, 255, 255, 0.15)',
    borderWidth: 0,
  },
  skeletonText: {
    height: 14,
    backgroundColor: 'rgba(255, 255, 255, 0.15)',
    borderRadius: 4,
  },
  creatorName: {
    fontSize: 14,
    fontWeight: '700',
    color: '#FFFFFF',
    marginLeft: 8,
    textShadowColor: 'rgba(0, 0, 0, 0.75)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 3,
  },
  followButton: {
    marginLeft: 10,
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 4,
    borderWidth: 1,
    borderColor: '#FFFFFF',
  },
  followButtonText: {
    fontSize: 13,
    fontWeight: '700',
    color: '#FFFFFF',
    textShadowColor: 'rgba(0, 0, 0, 0.75)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 3,
  },
  descriptionContainer: {
    marginBottom: 0,
  },
  description: {
    fontSize: 13,
    color: '#FFFFFF',
    lineHeight: 18,
    textShadowColor: 'rgba(0, 0, 0, 0.75)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 3,
  },
  rightSection: {
    position: 'absolute',
    right: 0,
    // bottom은 동적으로 설정됨
    alignItems: 'center',
    gap: 12,
  },
  actionButton: {
    alignItems: 'center',
    justifyContent: 'center',
    width: 48,
  },
  iconShadow: {
    textShadowColor: 'rgba(0, 0, 0, 0.75)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 3,
  },
  actionCount: {
    fontSize: 11,
    fontWeight: '600',
    color: '#FFFFFF',
    marginTop: 2,
    textAlign: 'center',
    textShadowColor: 'rgba(0, 0, 0, 0.75)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 3,
  },
  moreButton: {
    paddingVertical: 4,
  },
  moreText: {
    fontSize: 13,
    color: '#888888',
  },
  lessText: {
    fontSize: 13,
    color: '#888888',
  },
  lessButton: {
    paddingVertical: 8,
  },
  expandedWrapper: {
    maxHeight: MAX_EXPAND_HEIGHT + 40,
  },
  expandedInlineScrollView: {
    maxHeight: MAX_EXPAND_HEIGHT,
  },
  expandedScrollContent: {
    paddingRight: 8,
  },
  expandedInlineDescription: {
    fontSize: 14,
    color: '#FFFFFF',
    lineHeight: 20,
  },
  expandedInlineTagsText: {
    fontSize: 14,
    color: '#9BD4FF',
    lineHeight: 20,
    marginTop: 8,
    textShadowColor: 'rgba(0, 0, 0, 0.75)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 3,
  },
  modalOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
  },
  modalContent: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: '#1C1C1E',
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    maxHeight: SCREEN_HEIGHT * 0.7,
    paddingHorizontal: 16,
    paddingTop: 8,
    paddingBottom: 24,
  },
  modalHandle: {
    width: 40,
    height: 4,
    backgroundColor: '#48484A',
    borderRadius: 2,
    alignSelf: 'center',
    marginBottom: 16,
  },
  modalHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 16,
  },
  modalProfileImage: {
    width: 32,
    height: 32,
    borderRadius: 16,
  },
  modalProfilePlaceholder: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#444444',
    justifyContent: 'center',
    alignItems: 'center',
  },
  modalCreatorName: {
    fontSize: 15,
    fontWeight: '700',
    color: '#FFFFFF',
    marginLeft: 10,
  },
  modalScrollView: {
    maxHeight: SCREEN_HEIGHT * 0.5,
  },
  modalDescription: {
    fontSize: 14,
    color: '#FFFFFF',
    lineHeight: 22,
  },
  expandedDescriptionContainer: {
    paddingTop: 8,
    paddingHorizontal: 12,
  },
  expandedScrollView: {
    flex: 1,
  },
  expandedDescription: {
    fontSize: 14,
    color: '#FFFFFF',
    lineHeight: 20,
    paddingBottom: 16,
  },
  titleText: {
    fontWeight: '700',
  },
  tagsContainer: {
    marginTop: 6,
  },
  tagsText: {
    fontSize: 13,
    color: '#9BD4FF',
    lineHeight: 18,
    textShadowColor: 'rgba(0, 0, 0, 0.75)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 3,
  },
  expandedTagsText: {
    fontSize: 14,
    color: '#9BD4FF',
    lineHeight: 20,
    marginTop: 8,
    textShadowColor: 'rgba(0, 0, 0, 0.75)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 3,
  },
});
