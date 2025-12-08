/**
 * 댓글 좋아요 API 클라이언트
 *
 * 백엔드: me.onetwo.upvy.domain.interaction.controller.CommentLikeController
 */

import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';
import type {
  CommentLikeResponse,
  CommentLikeCountResponse,
  CommentLikeStatusResponse,
} from '@/types/interaction.types';

/**
 * 댓글 좋아요 추가
 *
 * 백엔드: POST /api/v1/comments/{commentId}/likes
 * Response: CommentLikeResponse { commentId, likeCount, isLiked }
 *
 * @param commentId 댓글 ID
 * @returns 댓글 좋아요 응답
 */
export const createCommentLike = async (commentId: string): Promise<CommentLikeResponse> => {
  const response = await apiClient.post<CommentLikeResponse>(
    API_ENDPOINTS.COMMENT_LIKE.CREATE(commentId)
  );
  return response.data;
};

/**
 * 댓글 좋아요 취소
 *
 * 백엔드: DELETE /api/v1/comments/{commentId}/likes
 * Response: CommentLikeResponse { commentId, likeCount, isLiked }
 *
 * @param commentId 댓글 ID
 * @returns 댓글 좋아요 응답
 */
export const deleteCommentLike = async (commentId: string): Promise<CommentLikeResponse> => {
  const response = await apiClient.delete<CommentLikeResponse>(
    API_ENDPOINTS.COMMENT_LIKE.DELETE(commentId)
  );
  return response.data;
};

/**
 * 댓글 좋아요 수 조회
 *
 * 백엔드: GET /api/v1/comments/{commentId}/likes/count
 * Response: CommentLikeCountResponse { commentId, likeCount }
 *
 * @param commentId 댓글 ID
 * @returns 댓글 좋아요 개수 응답
 */
export const getCommentLikeCount = async (commentId: string): Promise<CommentLikeCountResponse> => {
  const response = await apiClient.get<CommentLikeCountResponse>(
    API_ENDPOINTS.COMMENT_LIKE.COUNT(commentId)
  );
  return response.data;
};

/**
 * 댓글 좋아요 상태 조회
 *
 * 백엔드: GET /api/v1/comments/{commentId}/likes/check
 * Response: CommentLikeStatusResponse { commentId, isLiked }
 *
 * @param commentId 댓글 ID
 * @returns 댓글 좋아요 상태
 */
export const getCommentLikeStatus = async (commentId: string): Promise<CommentLikeStatusResponse> => {
  const response = await apiClient.get<CommentLikeStatusResponse>(
    API_ENDPOINTS.COMMENT_LIKE.STATUS(commentId)
  );
  return response.data;
};
