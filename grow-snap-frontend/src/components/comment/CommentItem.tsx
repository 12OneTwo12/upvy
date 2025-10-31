/**
 * 댓글 아이템 컴포넌트
 *
 * Instagram 스타일의 댓글 UI
 * - 프로필 이미지, 닉네임, 댓글 내용
 * - 작성 시간 (상대 시간)
 * - 좋아요, 답글, 삭제 버튼
 * - 답글은 왼쪽 라인 + 들여쓰기
 */

import React from 'react';
import { View, Text, TouchableOpacity, Image, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { theme } from '@/theme';
import { useAuthStore } from '@/stores/authStore';
import type { CommentResponse } from '@/types/interaction.types';

/**
 * 상대 시간 계산 함수
 * 예: "방금", "2분 전", "1시간 전", "3일 전"
 */
const getRelativeTime = (dateString: string): string => {
  const now = new Date();
  const date = new Date(dateString);
  const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

  if (diffInSeconds < 60) return '방금';
  if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)}분 전`;
  if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)}시간 전`;
  if (diffInSeconds < 604800) return `${Math.floor(diffInSeconds / 86400)}일 전`;
  if (diffInSeconds < 2592000) return `${Math.floor(diffInSeconds / 604800)}주 전`;
  if (diffInSeconds < 31536000) return `${Math.floor(diffInSeconds / 2592000)}개월 전`;
  return `${Math.floor(diffInSeconds / 31536000)}년 전`;
};

interface CommentItemProps {
  comment: CommentResponse;
  onLike: (commentId: string, isLiked: boolean) => void;
  onReply: (commentId: string, nickname: string) => void;
  onDelete: (commentId: string) => void;
  isReply?: boolean; // 답글인 경우 true
  likeCount?: number; // 좋아요 개수
  isLiked?: boolean; // 좋아요 상태
  commentLikes?: Record<string, { count: number; isLiked: boolean }>; // 답글의 좋아요 데이터
}

export const CommentItem: React.FC<CommentItemProps> = ({
  comment,
  onLike,
  onReply,
  onDelete,
  isReply = false,
  likeCount = 0,
  isLiked = false,
  commentLikes,
}) => {
  const currentUser = useAuthStore((state) => state.user);
  const isOwnComment = currentUser && currentUser.id === comment.userId;

  return (
    <View style={styles.container}>
      {/* 답글인 경우 왼쪽 라인 표시 */}
      {isReply && <View style={styles.replyLine} />}

      <View style={[styles.commentWrapper, isReply && styles.replyWrapper]}>
        {/* 프로필 이미지 */}
        <TouchableOpacity>
          {comment.userProfileImageUrl ? (
            <Image
              source={{ uri: comment.userProfileImageUrl }}
              style={styles.profileImage}
            />
          ) : (
            <View style={[styles.profileImage, styles.profileImagePlaceholder]}>
              <Ionicons name="person" size={16} color={theme.colors.text.tertiary} />
            </View>
          )}
        </TouchableOpacity>

        {/* 댓글 내용 */}
        <View style={styles.contentWrapper}>
          <View style={styles.textWrapper}>
            {/* 닉네임 */}
            <Text style={styles.nickname}>{comment.userNickname}</Text>

            {/* 댓글 내용 */}
            <Text style={styles.content}>{comment.content}</Text>
          </View>

          {/* 액션 버튼: 시간, 답글 개수, 답글 달기, 삭제 */}
          <View style={styles.actionsRow}>
            <Text style={styles.timeText}>{getRelativeTime(comment.createdAt)}</Text>

            {!isReply && comment.replies && comment.replies.length > 0 && (
              <>
                <Text style={styles.actionDivider}>·</Text>
                <Text style={styles.actionText}>답글 {comment.replies.length}개</Text>
              </>
            )}

            {!isReply && (
              <>
                <Text style={styles.actionDivider}>·</Text>
                <TouchableOpacity onPress={() => onReply(comment.id, comment.userNickname)}>
                  <Text style={styles.actionButton}>답글 달기</Text>
                </TouchableOpacity>
              </>
            )}

            {isOwnComment && (
              <>
                <Text style={styles.actionDivider}>·</Text>
                <TouchableOpacity onPress={() => onDelete(comment.id)}>
                  <Text style={styles.actionButton}>삭제</Text>
                </TouchableOpacity>
              </>
            )}
          </View>
        </View>

        {/* 좋아요 버튼 + 개수 */}
        <View style={styles.likeContainer}>
          <TouchableOpacity
            onPress={() => onLike(comment.id, isLiked)}
            style={styles.likeButton}
          >
            <Ionicons
              name={isLiked ? 'heart' : 'heart-outline'}
              size={14}
              color={isLiked ? theme.colors.error : theme.colors.text.tertiary}
            />
          </TouchableOpacity>
          {likeCount > 0 && (
            <Text style={styles.likeCountText}>{likeCount}</Text>
          )}
        </View>
      </View>

      {/* 답글 렌더링 (재귀) */}
      {comment.replies && comment.replies.length > 0 && (
        <View style={styles.repliesContainer}>
          {comment.replies.map((reply) => (
            <CommentItem
              key={reply.id}
              comment={reply}
              onLike={onLike}
              onReply={onReply}
              onDelete={onDelete}
              isReply={true}
              likeCount={commentLikes?.[reply.id]?.count || 0}
              isLiked={commentLikes?.[reply.id]?.isLiked || false}
              commentLikes={commentLikes}
            />
          ))}
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: theme.colors.background.primary,
  },
  replyLine: {
    position: 'absolute',
    left: theme.spacing[4] + 16, // 프로필 이미지 중앙
    top: 0,
    bottom: 0,
    width: 2,
    backgroundColor: theme.colors.border.light,
  },
  commentWrapper: {
    flexDirection: 'row',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
  },
  replyWrapper: {
    paddingLeft: theme.spacing[4] + 32 + theme.spacing[3], // 답글 들여쓰기
  },
  profileImage: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: theme.colors.background.tertiary,
  },
  profileImagePlaceholder: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  contentWrapper: {
    flex: 1,
    marginLeft: theme.spacing[3],
  },
  textWrapper: {
    flex: 1,
  },
  nickname: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: 2,
  },
  content: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.primary,
    lineHeight: theme.typography.lineHeight.normal * theme.typography.fontSize.sm,
  },
  actionsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: theme.spacing[2],
  },
  timeText: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
  },
  actionDivider: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
    marginHorizontal: theme.spacing[1],
  },
  actionText: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
    fontWeight: theme.typography.fontWeight.medium,
  },
  actionButton: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
    fontWeight: theme.typography.fontWeight.medium,
  },
  likeContainer: {
    alignItems: 'center',
    justifyContent: 'flex-start',
    marginLeft: theme.spacing[2],
  },
  likeButton: {
    padding: theme.spacing[1],
  },
  likeCountText: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
    marginTop: 2,
  },
  repliesContainer: {
    // 답글 컨테이너는 스타일 없음 (재귀적으로 CommentItem 렌더링)
  },
});
