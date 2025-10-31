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
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery, useMutation, useQueryClient, useInfiniteQuery } from '@tanstack/react-query';
import { theme } from '@/theme';
import { getComments, getReplies, createComment as createCommentApi, deleteComment as deleteCommentApi } from '@/api/comment.api';
import { createCommentLike, deleteCommentLike, getCommentLikeCount, getCommentLikeStatus } from '@/api/commentLike.api';
import { CommentItem } from './CommentItem';
import { CommentInput } from './CommentInput';
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
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const [replyTo, setReplyTo] = useState<{ commentId: string; nickname: string } | null>(null);

  // 댓글별 좋아요 개수/상태 저장
  const [commentLikes, setCommentLikes] = useState<Record<string, { count: number; isLiked: boolean }>>({});

  // 애니메이션
  const backdropOpacity = useRef(new Animated.Value(0)).current;
  const slideAnim = useRef(new Animated.Value(SCREEN_HEIGHT)).current;

  // FlatList ref (스크롤 제어용)
  const flatListRef = useRef<FlatList>(null);

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
  const comments = data?.pages?.flatMap((page) => page.comments) ?? [];

  // 모달 열기/닫기 애니메이션
  useEffect(() => {
    if (visible) {
      // 모달 열기
      Animated.parallel([
        Animated.timing(backdropOpacity, {
          toValue: 1,
          duration: 350,
          useNativeDriver: true,
        }),
        Animated.timing(slideAnim, {
          toValue: 0,
          duration: 550,
          easing: Easing.out(Easing.cubic),
          useNativeDriver: true,
        }),
      ]).start();
    } else {
      // 모달 닫기 시 데이터 초기화
      setReplyTo(null);
      setCommentLikes({});
      loadedCommentIdsRef.current.clear();

      // 댓글 쿼리 캐시 제거 (다음에 열 때 새로 로드)
      queryClient.removeQueries({ queryKey: ['comments', contentId] });

      // 애니메이션
      Animated.parallel([
        Animated.timing(backdropOpacity, {
          toValue: 0,
          duration: 250,
          useNativeDriver: true,
        }),
        Animated.timing(slideAnim, {
          toValue: SCREEN_HEIGHT,
          duration: 400,
          easing: Easing.in(Easing.ease),
          useNativeDriver: true,
        }),
      ]).start();
    }
  }, [visible, backdropOpacity, slideAnim, contentId, queryClient]);

  // 로드된 댓글 ID 추적 (무한 루프 방지)
  const loadedCommentIdsRef = useRef<Set<string>>(new Set());

  // 댓글이 로드되면 각 댓글의 좋아요 개수/상태 조회 (새 댓글만)
  useEffect(() => {
    if (comments.length > 0 && visible) {
      const loadCommentLikes = async () => {
        // 아직 로드하지 않은 새 댓글만 필터링 (Optimistic Update 보존)
        const newComments = comments.filter(
          (comment) => !loadedCommentIdsRef.current.has(comment.id)
        );

        if (newComments.length === 0) return;

        const likesData: Record<string, { count: number; isLiked: boolean }> = {};

        // 새 댓글의 좋아요 정보만 조회
        await Promise.all(
          newComments.map(async (comment) => {
            try {
              const [countRes, statusRes] = await Promise.all([
                getCommentLikeCount(comment.id),
                getCommentLikeStatus(comment.id),
              ]);
              likesData[comment.id] = {
                count: countRes.likeCount,
                isLiked: statusRes.isLiked,
              };
              // 로드 완료 표시
              loadedCommentIdsRef.current.add(comment.id);
            } catch (error) {
              // 에러 발생 시 기본값
              likesData[comment.id] = { count: 0, isLiked: false };
              loadedCommentIdsRef.current.add(comment.id);
            }
          })
        );

        // 기존 상태와 병합 (Optimistic Update 유지)
        setCommentLikes((prev) => ({ ...prev, ...likesData }));
      };

      loadCommentLikes();
    }
  }, [comments, visible]);

  // 댓글 작성 mutation
  const createCommentMutation = useMutation({
    mutationFn: async ({ content, parentCommentId }: { content: string; parentCommentId?: string }) => {
      return await createCommentApi(contentId, { content, parentCommentId });
    },
    onSuccess: (data, variables) => {
      const isReply = !!variables.parentCommentId;

      // 댓글 목록 refetch
      queryClient.invalidateQueries({ queryKey: ['comments', contentId] });
      // 피드 목록도 refetch (댓글 개수 업데이트)
      queryClient.invalidateQueries({ queryKey: ['feed'] });

      // 대댓글인 경우 해당 댓글의 답글 목록도 refetch (화면에 즉시 표시)
      if (isReply && variables.parentCommentId) {
        queryClient.invalidateQueries({ queryKey: ['replies', variables.parentCommentId] });
      }

      // 답글 모드 초기화
      setReplyTo(null);

      // 새 최상위 댓글인 경우에만 스크롤 (대댓글은 스크롤 안 함)
      if (!isReply) {
        setTimeout(() => {
          flatListRef.current?.scrollToEnd({ animated: true });
        }, 300);
      }
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

  // 댓글 삭제 핸들러 (확인 Alert 포함)
  const handleDeleteComment = useCallback(
    (commentId: string) => {
      Alert.alert(
        '댓글 삭제',
        '정말로 이 댓글을 삭제하시겠습니까?',
        [
          {
            text: '취소',
            style: 'cancel',
          },
          {
            text: '삭제',
            style: 'destructive',
            onPress: () => {
              deleteCommentMutation.mutate(commentId);
            },
          },
        ],
        { cancelable: true }
      );
    },
    [deleteCommentMutation]
  );

  // 모달 닫기 핸들러
  const handleClose = useCallback(() => {
    onClose();
  }, [onClose]);


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
          {/* 헤더 */}
          <View style={styles.header}>
            <Text style={styles.headerTitle}>댓글</Text>
            <TouchableOpacity onPress={handleClose} style={styles.closeButton}>
              <Ionicons name="close" size={28} color={theme.colors.text.primary} />
            </TouchableOpacity>
          </View>

          {/* 구분선 */}
          <View style={styles.divider} />

          {/* 댓글 목록 */}
          {isLoading ? (
            <View style={styles.loadingContainer}>
              <ActivityIndicator size="large" color={theme.colors.primary[500]} />
            </View>
          ) : comments.length === 0 ? (
            <View style={styles.emptyContainer}>
              <Ionicons name="chatbubble-outline" size={48} color={theme.colors.text.tertiary} />
              <Text style={styles.emptyText}>아직 댓글이 없습니다.</Text>
              <Text style={styles.emptySubtext}>첫 번째 댓글을 남겨보세요!</Text>
            </View>
          ) : (
            <FlatList
              ref={flatListRef}
              data={comments}
              keyExtractor={(item) => item.id}
              renderItem={({ item }) => (
                <CommentItem
                  comment={item}
                  onLike={handleLikeComment}
                  onReply={handleReply}
                  onDelete={handleDeleteComment}
                  likeCount={commentLikes[item.id]?.count || 0}
                  isLiked={commentLikes[item.id]?.isLiked || false}
                  commentLikes={commentLikes}
                  contentId={contentId}
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
    maxHeight: SCREEN_HEIGHT * 0.85,
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
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: theme.spacing[20],
  },
  emptyText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.secondary,
    marginTop: theme.spacing[4],
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
