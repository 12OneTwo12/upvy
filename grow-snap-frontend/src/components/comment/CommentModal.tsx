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

import React, { useState, useCallback } from 'react';
import {
  View,
  Modal,
  Text,
  TouchableOpacity,
  FlatList,
  StyleSheet,
  ActivityIndicator,
  Dimensions,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { theme } from '@/theme';
import { getComments, createComment as createCommentApi, deleteComment as deleteCommentApi } from '@/api/comment.api';
import { createCommentLike, deleteCommentLike } from '@/api/commentLike.api';
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

  // 댓글 목록 조회
  const {
    data: comments = [],
    isLoading,
    refetch,
  } = useQuery({
    queryKey: ['comments', contentId],
    queryFn: () => getComments(contentId),
    enabled: visible, // 모달이 열릴 때만 데이터 fetch
  });

  // 댓글 작성 mutation
  const createCommentMutation = useMutation({
    mutationFn: async ({ content, parentCommentId }: { content: string; parentCommentId?: string }) => {
      return await createCommentApi(contentId, { content, parentCommentId });
    },
    onSuccess: () => {
      // 댓글 목록 refetch
      queryClient.invalidateQueries({ queryKey: ['comments', contentId] });
      // 답글 모드 초기화
      setReplyTo(null);
    },
  });

  // 댓글 삭제 mutation
  const deleteCommentMutation = useMutation({
    mutationFn: (commentId: string) => deleteCommentApi(commentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['comments', contentId] });
    },
  });

  // 댓글 좋아요 mutation (Optimistic update)
  const likeCommentMutation = useMutation({
    mutationFn: async ({ commentId, isLiked }: { commentId: string; isLiked: boolean }) => {
      if (isLiked) {
        return await deleteCommentLike(commentId);
      } else {
        return await createCommentLike(commentId);
      }
    },
    // TODO: Optimistic update 구현 (현재는 단순 refetch)
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['comments', contentId] });
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

  // 댓글 삭제 핸들러
  const handleDeleteComment = useCallback(
    (commentId: string) => {
      deleteCommentMutation.mutate(commentId);
    },
    [deleteCommentMutation]
  );

  // 모달 닫기 핸들러
  const handleClose = useCallback(() => {
    setReplyTo(null);
    onClose();
  }, [onClose]);

  // 댓글 개수 계산 (답글 포함)
  const getTotalCommentCount = (comments: CommentResponse[]): number => {
    return comments.reduce((total, comment) => {
      return total + 1 + (comment.replies?.length || 0);
    }, 0);
  };

  const totalCommentCount = getTotalCommentCount(comments);

  return (
    <Modal
      visible={visible}
      animationType="slide"
      transparent={true}
      onRequestClose={handleClose}
    >
      <View style={styles.modalOverlay}>
        <TouchableOpacity
          style={styles.backdrop}
          activeOpacity={1}
          onPress={handleClose}
        />

        <View style={[styles.modalContent, { paddingTop: insets.top || theme.spacing[4] }]}>
          {/* 헤더 */}
          <View style={styles.header}>
            <Text style={styles.headerTitle}>댓글 {totalCommentCount}개</Text>
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
              data={comments}
              keyExtractor={(item) => item.id}
              renderItem={({ item }) => (
                <CommentItem
                  comment={item}
                  onLike={handleLikeComment}
                  onReply={handleReply}
                  onDelete={handleDeleteComment}
                  // TODO: 댓글 좋아요 개수/상태는 별도 API로 조회 필요
                  likeCount={0}
                  isLiked={false}
                />
              )}
              showsVerticalScrollIndicator={true}
              contentContainerStyle={styles.listContentContainer}
            />
          )}

          {/* 댓글 입력 */}
          <CommentInput
            onSubmit={handleCreateComment}
            replyTo={replyTo}
            onCancelReply={handleCancelReply}
          />
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'flex-end',
  },
  backdrop: {
    flex: 1,
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
});
