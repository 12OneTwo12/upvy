/**
 * 댓글 모달 컴포넌트
 *
 * Instagram 스타일의 댓글 Bottom Sheet
 * - 하단에서 올라오는 모달
 * - 댓글 목록 (FlatList)
 * - 댓글 작성/삭제
 * - 댓글 좋아요
 * - 답글 기능
 */

import React, { useState, useCallback, useEffect, useRef } from 'react';
import {
  View,
  Modal,
  Text,
  TouchableOpacity,
  FlatList,
  StyleSheet,
  ActivityIndicator,
  Dimensions,
  Animated,
  Alert,
  Easing,
  PanResponder,
  Keyboard,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery, useMutation, useQueryClient, useInfiniteQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { theme } from '@/theme';
import { getComments, getReplies, createComment as createCommentApi, deleteComment as deleteCommentApi } from '@/api/comment.api';
import { createCommentLike, deleteCommentLike } from '@/api/commentLike.api';
import { CommentItem } from './CommentItem';
import { CommentInput } from './CommentInput';
import { useAuthStore } from '@/stores/authStore';
import { Analytics } from '@/utils/analytics';
import type { CommentResponse } from '@/types/interaction.types';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

interface CommentModalProps {
  visible: boolean;
  contentId: string;
  onClose: () => void;
}

export const CommentModal: React.FC<CommentModalProps> = ({
  visible,
  contentId,
  onClose,
}) => {
  const { t } = useTranslation('interactions');
  const { t: tCommon } = useTranslation('common');
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const currentUser = useAuthStore((state) => state.user);
  const currentProfile = useAuthStore((state) => state.profile);
  const [replyTo, setReplyTo] = useState<{ commentId: string; nickname: string } | null>(null);

  // 댓글별 좋아요 개수/상태 저장
  const [commentLikes, setCommentLikes] = useState<Record<string, { count: number; isLiked: boolean }>>({});

  // 새로 작성된 최상위 댓글 저장 (Optimistic Update용)
  const [newComments, setNewComments] = useState<CommentResponse[]>([]);

  // 새로 작성된 답글 저장 (Optimistic Update용)
  const [newReplies, setNewReplies] = useState<Record<string, CommentResponse[]>>({});

  // 애니메이션
  const backdropOpacity = useRef(new Animated.Value(0)).current;
  const slideAnim = useRef(new Animated.Value(SCREEN_HEIGHT)).current;

  // FlatList ref (스크롤 제어용)
  const flatListRef = useRef<FlatList>(null);

  // 드래그 제스처 핸들러
  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: (_, gestureState) => {
        // 수직 드래그가 수평 드래그보다 클 때만 감지
        return Math.abs(gestureState.dy) > Math.abs(gestureState.dx) && Math.abs(gestureState.dy) > 5;
      },
      onPanResponderMove: (_, gestureState) => {
        // 아래로만 드래그 가능 (dy > 0)
        if (gestureState.dy > 0) {
          slideAnim.setValue(gestureState.dy);
        }
      },
      onPanResponderRelease: (_, gestureState) => {
        const threshold = SCREEN_HEIGHT * 0.3; // 30% 이상 내리면 닫기
        const velocity = gestureState.vy; // 속도 고려

        // 빠르게 아래로 스와이프하거나, 30% 이상 내리면 닫기
        if (gestureState.dy > threshold || (velocity > 0.5 && gestureState.dy > 100)) {
          // 현재 위치에서 화면 아래까지 애니메이션
          Animated.parallel([
            Animated.timing(backdropOpacity, {
              toValue: 0,
              duration: 250,
              useNativeDriver: true,
              easing: Easing.in(Easing.ease),
            }),
            Animated.timing(slideAnim, {
              toValue: SCREEN_HEIGHT,
              duration: 300,
              easing: Easing.in(Easing.cubic),
              useNativeDriver: true,
            }),
          ]).start(() => {
            // 애니메이션 완료 후 모달 닫기
            onClose();

            // 데이터 초기화
            setReplyTo(null);
            setCommentLikes({});
            setNewComments([]);
            setNewReplies({});
            loadedCommentIdsRef.current.clear();
            processedCommentIdsRef.current.clear();
            queryClient.removeQueries({ queryKey: ['comments', contentId] });
          });
        } else {
          // 원래 위치로 스냅백
          Animated.spring(slideAnim, {
            toValue: 0,
            useNativeDriver: true,
            tension: 65,
            friction: 11,
          }).start();
        }
      },
    })
  ).current;

  // 댓글 목록 조회 (무한 스크롤, 10개씩)
  const {
    data,
    isLoading,
    isFetchingNextPage,
    hasNextPage,
    fetchNextPage,
    refetch,
  } = useInfiniteQuery({
    queryKey: ['comments', contentId],
    queryFn: ({ pageParam }) => getComments(contentId, pageParam, 10),
    getNextPageParam: (lastPage) => {
      return lastPage.hasNext ? lastPage.nextCursor : undefined;
    },
    initialPageParam: undefined as string | undefined,
    enabled: visible, // 모달이 열릴 때만 데이터 fetch
  });

  // 모든 페이지의 댓글을 하나의 배열로 합침
  const loadedComments = data?.pages?.flatMap((page) => page.comments) ?? [];

  // 새 댓글 + 로드된 댓글 (중복 제거)
  const allComments = [
    ...newComments,
    ...loadedComments.filter((c) => !newComments.some((nc) => nc.id === c.id)),
  ];

  // 모달 열기 애니메이션
  useEffect(() => {
    if (visible) {
      // 애니메이션 값을 초기 위치로 리셋
      backdropOpacity.setValue(0);
      slideAnim.setValue(SCREEN_HEIGHT);

      // 모달 열기 애니메이션 시작
      Animated.parallel([
        Animated.timing(backdropOpacity, {
          toValue: 1,
          duration: 300,
          useNativeDriver: true,
          easing: Easing.out(Easing.ease),
        }),
        Animated.timing(slideAnim, {
          toValue: 0,
          duration: 400,
          easing: Easing.out(Easing.cubic),
          useNativeDriver: true,
        }),
      ]).start();
    }
  }, [visible, backdropOpacity, slideAnim]);

  // 로드된 댓글 ID 추적 (무한 루프 방지)
  const loadedCommentIdsRef = useRef<Set<string>>(new Set());

  // 댓글이 로드되면 각 댓글의 좋아요 개수/상태 조회 (새 댓글만)
  useEffect(() => {
    if (allComments.length > 0 && visible) {
      const loadCommentLikes = async () => {
        // 아직 로드하지 않은 새 댓글만 필터링 (Optimistic Update 보존)
        const commentsToLoad = allComments.filter(
          (comment) => !loadedCommentIdsRef.current.has(comment.id) && !comment.id.startsWith('temp-')
        );

        if (commentsToLoad.length === 0) return;

        // 백엔드에서 제공하는 likeCount, isLiked 사용 (N+1 API 호출 제거)
        const likesData: Record<string, { count: number; isLiked: boolean }> = {};
        commentsToLoad.forEach((comment) => {
          likesData[comment.id] = {
            count: comment.likeCount,
            isLiked: comment.isLiked,
          };
          // 로드 완료 표시
          loadedCommentIdsRef.current.add(comment.id);
        });

        // 기존 상태와 병합 (Optimistic Update 유지)
        setCommentLikes((prev) => ({ ...prev, ...likesData }));
      };

      loadCommentLikes();
    }
  }, [allComments, visible]);

  // 처리된 댓글 ID 추적 (중복 제거 방지)
  const processedCommentIdsRef = useRef<Set<string>>(new Set());

  // 실제 댓글이 로드되면 임시 댓글(optimistic update) 제거
  useEffect(() => {
    if (loadedComments.length > 0 && newComments.length > 0 && visible) {
      // 새로 로드된 댓글만 확인 (이미 처리된 댓글은 제외)
      const newlyLoadedComments = loadedComments.filter(
        (comment) => !processedCommentIdsRef.current.has(comment.id)
      );

      if (newlyLoadedComments.length === 0) return;

      // 새로 로드된 댓글의 ID 저장
      newlyLoadedComments.forEach((comment) => {
        processedCommentIdsRef.current.add(comment.id);
      });

      // 실제 댓글과 같은 content+userId를 가진 임시 댓글 제거
      setNewComments((prev) => {
        return prev.filter((tempComment) => {
          // 같은 내용의 실제 댓글이 로드되었는지 확인
          const hasRealComment = newlyLoadedComments.some(
            (realComment) =>
              realComment.content === tempComment.content &&
              realComment.userId === tempComment.userId
          );
          // 실제 댓글이 없으면 임시 댓글 유지, 있으면 제거
          return !hasRealComment;
        });
      });
    }
  }, [loadedComments.length, newComments.length, visible]);

  // 댓글 작성 mutation
  const createCommentMutation = useMutation({
    mutationFn: async ({ content, parentCommentId }: { content: string; parentCommentId?: string }) => {
      return await createCommentApi(contentId, { content, parentCommentId });
    },
    onMutate: async ({ content, parentCommentId }) => {
      // Optimistic Update: 즉시 화면에 표시
      const isReply = !!parentCommentId;
      const tempId = `temp-${Date.now()}`;

      // 임시 댓글 객체 생성
      const tempComment: CommentResponse = {
        id: tempId,
        contentId,
        userId: currentUser?.id || '',
        userNickname: currentProfile?.nickname || currentUser?.email || 'Unknown',
        userProfileImageUrl: currentProfile?.profileImageUrl || null,
        content,
        parentCommentId: parentCommentId || null,
        createdAt: new Date().toISOString(),
        replyCount: 0,
        likeCount: 0,
        isLiked: false,
      };

      if (isReply && parentCommentId) {
        // 대댓글: newReplies에 추가
        setNewReplies((prev) => ({
          ...prev,
          [parentCommentId]: [
            ...(prev[parentCommentId] || []),
            tempComment,
          ],
        }));
      } else {
        // 최상위 댓글: newComments 맨 위에 추가
        setNewComments((prev) => [tempComment, ...prev]);

        // 맨 위로 스크롤 (FlatList 업데이트 후)
        setTimeout(() => {
          flatListRef.current?.scrollToIndex({ index: 0, animated: true });
        }, 300);
      }

      // 이전 상태 저장 (롤백용)
      return { tempId, tempComment, isReply, parentCommentId };
    },
    onSuccess: (newComment, variables, context) => {
      const isReply = !!variables.parentCommentId;

      // Analytics 이벤트 (Fire-and-Forget - await 없음)
      const commentLength = variables.content?.length || 0;
      Analytics.logComment(contentId, commentLength);

      if (isReply && variables.parentCommentId) {
        // 백엔드 데이터와 동기화를 위해 답글 쿼리 invalidate
        queryClient.invalidateQueries({ queryKey: ['replies', variables.parentCommentId] });
      }

      // 댓글 목록 refetch (실제 댓글 로드)
      // 임시 댓글은 useEffect에서 자동으로 제거됨
      queryClient.invalidateQueries({ queryKey: ['comments', contentId] });
      // 피드 목록도 refetch (댓글 개수 업데이트)
      queryClient.invalidateQueries({ queryKey: ['feed'] });

      // 답글 모드 초기화
      setReplyTo(null);
    },
    onError: (error, variables, context) => {
      // 에러 발생 시 임시 댓글 제거 (롤백)
      if (context) {
        const isReply = !!variables.parentCommentId;

        if (isReply && variables.parentCommentId) {
          setNewReplies((prev) => {
            const updated = { ...prev };
            if (updated[variables.parentCommentId!]) {
              updated[variables.parentCommentId!] = updated[variables.parentCommentId!].filter(
                (c) => c.id !== context.tempId
              );
            }
            return updated;
          });
        } else {
          setNewComments((prev) => prev.filter((c) => c.id !== context.tempId));
        }
      }

      Alert.alert(t('comment.errorTitle', '오류'), t('comment.errorMessage', '댓글 작성에 실패했습니다.'));
    },
  });

  // 댓글 삭제 mutation
  const deleteCommentMutation = useMutation({
    mutationFn: (commentId: string) => deleteCommentApi(commentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['comments', contentId] });
      // 피드 목록도 refetch (댓글 개수 업데이트)
      queryClient.invalidateQueries({ queryKey: ['feed'] });
    },
  });

  // 댓글 좋아요 mutation (FeedScreen과 동일한 Optimistic update)
  const likeCommentMutation = useMutation({
    mutationFn: async ({ commentId, isLiked }: { commentId: string; isLiked: boolean }) => {
      if (isLiked) {
        return await deleteCommentLike(commentId);
      } else {
        return await createCommentLike(commentId);
      }
    },
    onMutate: async ({ commentId, isLiked }) => {
      // 진행 중인 refetch 취소 (중요!)
      await queryClient.cancelQueries({ queryKey: ['commentLikes', commentId] });

      // 이전 상태 저장 (rollback용)
      const previousLikes = { ...commentLikes };

      // Optimistic update: 즉시 UI 업데이트
      setCommentLikes((prev) => ({
        ...prev,
        [commentId]: {
          count: isLiked ? Math.max(0, (prev[commentId]?.count || 1) - 1) : (prev[commentId]?.count || 0) + 1,
          isLiked: !isLiked,
        },
      }));

      return { previousLikes };
    },
    onSuccess: (response, { commentId }) => {
      // 백엔드 응답으로 최종 동기화 (실제 DB 상태)
      setCommentLikes((prev) => ({
        ...prev,
        [commentId]: {
          count: response.likeCount,
          isLiked: response.isLiked,
        },
      }));
    },
    onError: (error, variables, context) => {
      // 에러 발생 시 전체 상태를 이전으로 rollback
      if (context?.previousLikes) {
        setCommentLikes(context.previousLikes);
      }
    },
  });

  // 댓글 작성 핸들러
  const handleCreateComment = useCallback(
    (content: string, parentCommentId?: string) => {
      createCommentMutation.mutate({ content, parentCommentId });
    },
    [createCommentMutation]
  );

  // 댓글 좋아요 핸들러
  const handleLikeComment = useCallback(
    (commentId: string, isLiked: boolean) => {
      likeCommentMutation.mutate({ commentId, isLiked });
    },
    [likeCommentMutation]
  );

  // 답글 작성 모드 시작
  const handleReply = useCallback((commentId: string, nickname: string) => {
    setReplyTo({ commentId, nickname });
  }, []);

  // 답글 모드 취소
  const handleCancelReply = useCallback(() => {
    setReplyTo(null);
  }, []);

  // 특정 댓글의 새 답글 초기화 (답글 더보기 클릭 시)
  const handleClearNewReplies = useCallback((commentId: string) => {
    setNewReplies((prev) => {
      const updated = { ...prev };
      delete updated[commentId];
      return updated;
    });
  }, []);

  // 댓글 삭제 핸들러 (확인 Alert 포함)
  const handleDeleteComment = useCallback(
    (commentId: string) => {
      Alert.alert(
        t('comment.delete'),
        t('comment.deleteConfirm'),
        [
          {
            text: tCommon('button.cancel'),
            style: 'cancel',
          },
          {
            text: tCommon('button.delete'),
            style: 'destructive',
            onPress: () => {
              deleteCommentMutation.mutate(commentId);
            },
          },
        ],
        { cancelable: true }
      );
    },
    [deleteCommentMutation, t, tCommon]
  );

  // 모달 닫기 핸들러
  const handleClose = useCallback(() => {
    // 키보드 닫기
    Keyboard.dismiss();

    // 닫기 애니메이션 실행
    Animated.parallel([
      Animated.timing(backdropOpacity, {
        toValue: 0,
        duration: 250,
        useNativeDriver: true,
        easing: Easing.in(Easing.ease),
      }),
      Animated.timing(slideAnim, {
        toValue: SCREEN_HEIGHT,
        duration: 350,
        easing: Easing.in(Easing.cubic),
        useNativeDriver: true,
      }),
    ]).start(() => {
      // 애니메이션 완료 후 모달 닫기
      onClose();

      // 데이터 초기화 (다음 열기를 위해)
      setReplyTo(null);
      setCommentLikes({});
      setNewComments([]);
      setNewReplies({});
      loadedCommentIdsRef.current.clear();
      processedCommentIdsRef.current.clear();

      // 댓글 쿼리 캐시 제거 (다음에 열 때 새로 로드)
      queryClient.removeQueries({ queryKey: ['comments', contentId] });
    });
  }, [onClose, backdropOpacity, slideAnim, contentId, queryClient]);


  // 무한 스크롤: 스크롤 끝에 도달하면 다음 페이지 로드
  const handleLoadMore = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);


  return (
    <Modal
      visible={visible}
      animationType="none"
      transparent={true}
      onRequestClose={handleClose}
    >
      <View style={styles.modalOverlay}>
        {/* 애니메이션 배경 (Fade) */}
        <Animated.View
          style={[
            styles.backdrop,
            {
              opacity: backdropOpacity,
            },
          ]}
        >
          <TouchableOpacity
            style={StyleSheet.absoluteFill}
            activeOpacity={1}
            onPress={handleClose}
          />
        </Animated.View>

        {/* 애니메이션 모달 콘텐츠 (Slide) */}
        <Animated.View
          style={[
            styles.modalContent,
            {
              transform: [{ translateY: slideAnim }],
            },
          ]}
        >
          {/* 드래그 핸들 */}
          <View {...panResponder.panHandlers} style={styles.dragHandleContainer}>
            <View style={styles.dragHandle} />
          </View>

          {/* 헤더 */}
          <TouchableOpacity activeOpacity={1} onPress={Keyboard.dismiss}>
            <View style={styles.header}>
              <Text style={styles.headerTitle}>{t('comment.title')}</Text>
              <TouchableOpacity onPress={handleClose} style={styles.closeButton}>
                <Ionicons name="close" size={28} color={theme.colors.text.primary} />
              </TouchableOpacity>
            </View>
          </TouchableOpacity>

          {/* 구분선 */}
          <View style={styles.divider} />

          {/* 댓글 목록 */}
          {isLoading ? (
            <View style={styles.loadingContainer}>
              <ActivityIndicator size="large" color={theme.colors.primary[500]} />
            </View>
          ) : !data || allComments.length === 0 ? (
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyEmoji}>🌱</Text>
              <Text style={styles.emptyText}>{t('comment.emptyTitle', '아직 댓글이 없습니다')}</Text>
              <Text style={styles.emptySubtext}>{t('comment.emptySubtitle', '첫 번째 댓글을 남겨보세요!')}</Text>
            </View>
          ) : (
            <FlatList
              ref={flatListRef}
              data={allComments}
              keyExtractor={(item) => item.id}
              renderItem={({ item }) => (
                <CommentItem
                  comment={item}
                  onLike={handleLikeComment}
                  onReply={handleReply}
                  onDelete={handleDeleteComment}
                  onClearNewReplies={handleClearNewReplies}
                  likeCount={commentLikes[item.id]?.count || 0}
                  isLiked={commentLikes[item.id]?.isLiked || false}
                  commentLikes={commentLikes}
                  contentId={contentId}
                  newReplies={newReplies[item.id] || []}
                />
              )}
              showsVerticalScrollIndicator={true}
              contentContainerStyle={{ paddingBottom: theme.spacing[4] }}
              onEndReached={handleLoadMore}
              onEndReachedThreshold={0.3}
              maintainVisibleContentPosition={{
                minIndexForVisible: 0,
              }}
            />
          )}

          {/* 무한 스크롤 로딩 인디케이터 (모달 하단 고정) */}
          {isFetchingNextPage && (
            <View style={styles.bottomLoader}>
              <ActivityIndicator size="small" color={theme.colors.primary[500]} />
            </View>
          )}

          {/* 댓글 입력 */}
          <CommentInput
            onSubmit={handleCreateComment}
            replyTo={replyTo}
            onCancelReply={handleCancelReply}
          />
        </Animated.View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  modalOverlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
  },
  modalContent: {
    backgroundColor: theme.colors.background.primary,
    borderTopLeftRadius: theme.borderRadius.lg,
    borderTopRightRadius: theme.borderRadius.lg,
    maxHeight: SCREEN_HEIGHT * 0.95,
  },
  dragHandleContainer: {
    alignItems: 'center',
    paddingVertical: theme.spacing[2],
  },
  dragHandle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: theme.colors.border.light,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[4],
  },
  headerTitle: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
  },
  closeButton: {
    padding: theme.spacing[1],
  },
  divider: {
    height: 1,
    backgroundColor: theme.colors.border.light,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: theme.spacing[20],
  },
  emptyContainer: {
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: theme.spacing[20],
    minHeight: 300,
  },
  emptyEmoji: {
    fontSize: 64,
    marginBottom: theme.spacing[2],
  },
  emptyText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.secondary,
    marginTop: theme.spacing[2],
  },
  emptySubtext: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.tertiary,
    marginTop: theme.spacing[2],
  },
  listContentContainer: {
    flexGrow: 1,
  },
  bottomLoader: {
    paddingVertical: theme.spacing[3],
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.colors.background.primary,
    borderTopWidth: 1,
    borderTopColor: theme.colors.border.light,
  },
});
