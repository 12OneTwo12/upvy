/**
 * 댓글 API 클라이언트
 *
 * 백엔드: me.onetwo.upvy.domain.interaction.controller.CommentController
 */

import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';
import type {
  CommentRequest,
  CommentResponse,
  CommentListResponse,
} from '@/types/interaction.types';

/**
 * 댓글 작성
 *
 * 백엔드: POST /api/v1/contents/{contentId}/comments
 * Response: CommentResponse (201 Created)
 *
 * @param contentId 콘텐츠 ID
 * @param request 댓글 작성 요청
 * @returns 작성된 댓글 응답
 */
export const createComment = async (
  contentId: string,
  request: CommentRequest
): Promise<CommentResponse> => {
  const response = await apiClient.post<CommentResponse>(
    API_ENDPOINTS.COMMENT.CREATE(contentId),
    request
  );
  return response.data;
};

/**
 * 댓글 목록 조회 (페이징)
 *
 * 백엔드: GET /api/v1/contents/{contentId}/comments?cursor={cursor}&limit={limit}
 * Response: CommentListResponse
 *
 * @param contentId 콘텐츠 ID
 * @param cursor 페이징 커서 (이전 페이지의 마지막 댓글 ID)
 * @param limit 페이지 크기 (기본 20)
 * @returns 댓글 목록 응답 (페이징 정보 포함)
 */
export const getComments = async (
  contentId: string,
  cursor?: string | null,
  limit: number = 20
): Promise<CommentListResponse> => {
  const params = new URLSearchParams();
  if (cursor) {
    params.append('cursor', cursor);
  }
  params.append('limit', limit.toString());

  const response = await apiClient.get<CommentListResponse>(
    `${API_ENDPOINTS.COMMENT.LIST(contentId)}?${params.toString()}`
  );
  return response.data;
};

/**
 * 대댓글 목록 조회 (페이징)
 *
 * 백엔드: GET /api/v1/comments/{commentId}/replies?cursor={cursor}&limit={limit}
 * Response: CommentListResponse
 *
 * @param commentId 부모 댓글 ID
 * @param cursor 페이징 커서 (이전 페이지의 마지막 대댓글 ID)
 * @param limit 페이지 크기 (기본 20)
 * @returns 대댓글 목록 응답 (페이징 정보 포함)
 */
export const getReplies = async (
  commentId: string,
  cursor?: string | null,
  limit: number = 20
): Promise<CommentListResponse> => {
  const params = new URLSearchParams();
  if (cursor) {
    params.append('cursor', cursor);
  }
  params.append('limit', limit.toString());

  const response = await apiClient.get<CommentListResponse>(
    `${API_ENDPOINTS.COMMENT.REPLIES(commentId)}?${params.toString()}`
  );
  return response.data;
};

/**
 * 댓글 삭제
 *
 * 백엔드: DELETE /api/v1/comments/{commentId}
 * Response: 204 No Content
 *
 * @param commentId 댓글 ID
 */
export const deleteComment = async (commentId: string): Promise<void> => {
  await apiClient.delete(API_ENDPOINTS.COMMENT.DELETE(commentId));
};
