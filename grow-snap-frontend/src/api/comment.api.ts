/**
 * 댓글 API 클라이언트
 *
 * 백엔드: me.onetwo.growsnap.domain.interaction.controller.CommentController
 */

import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';
import type { CommentRequest, CommentResponse } from '@/types/interaction.types';

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
 * 댓글 목록 조회
 *
 * 백엔드: GET /api/v1/contents/{contentId}/comments
 * Response: CommentResponse[] (Flux로 반환되지만 배열로 받음)
 *
 * @param contentId 콘텐츠 ID
 * @returns 댓글 목록
 */
export const getComments = async (contentId: string): Promise<CommentResponse[]> => {
  const response = await apiClient.get<CommentResponse[]>(
    API_ENDPOINTS.COMMENT.LIST(contentId)
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
