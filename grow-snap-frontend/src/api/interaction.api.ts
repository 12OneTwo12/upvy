/**
 * 인터랙션 API 클라이언트
 *
 * 백엔드 Interaction 관련 Controller의 API를 호출합니다.
 * 참조:
 * - grow-snap-backend/.../interaction/controller/LikeController.kt
 * - grow-snap-backend/.../interaction/controller/SaveController.kt
 * - grow-snap-backend/.../interaction/controller/ShareController.kt
 * - grow-snap-backend/.../interaction/controller/CommentController.kt
 */

import apiClient from '@/api/client';
import { API_ENDPOINTS } from '@/constants/api';
import type {
  LikeResponse,
  LikeStatusResponse,
  LikeCountResponse,
  SaveResponse,
  SaveStatusResponse,
  SavedContentResponse,
  ShareResponse,
  ShareLinkResponse,
  CommentRequest,
  CommentResponse,
  CommentListResponse,
  CommentLikeResponse,
  CommentLikeCountResponse,
  CommentLikeStatusResponse,
} from '@/types/interaction.types';

// ==================== Like API ====================

/**
 * 콘텐츠에 좋아요를 추가합니다.
 *
 * 백엔드: POST /api/v1/contents/{contentId}/like
 *
 * @param contentId 콘텐츠 ID
 * @returns 좋아요 응답
 */
export const likeContent = async (contentId: string): Promise<LikeResponse> => {
  const { data } = await apiClient.post<LikeResponse>(
    API_ENDPOINTS.LIKE.CREATE(contentId)
  );
  return data;
};

/**
 * 콘텐츠 좋아요를 취소합니다.
 *
 * 백엔드: DELETE /api/v1/contents/{contentId}/like
 *
 * @param contentId 콘텐츠 ID
 * @returns 좋아요 응답
 */
export const unlikeContent = async (contentId: string): Promise<LikeResponse> => {
  const { data } = await apiClient.delete<LikeResponse>(
    API_ENDPOINTS.LIKE.DELETE(contentId)
  );
  return data;
};

/**
 * 콘텐츠의 좋아요 상태를 조회합니다.
 *
 * 백엔드: GET /api/v1/contents/{contentId}/like/status
 *
 * @param contentId 콘텐츠 ID
 * @returns 좋아요 상태 응답
 */
export const getLikeStatus = async (contentId: string): Promise<LikeStatusResponse> => {
  const { data } = await apiClient.get<LikeStatusResponse>(
    API_ENDPOINTS.LIKE.STATUS(contentId)
  );
  return data;
};

/**
 * 콘텐츠의 좋아요 개수를 조회합니다.
 *
 * 백엔드: GET /api/v1/contents/{contentId}/likes
 *
 * @param contentId 콘텐츠 ID
 * @returns 좋아요 개수 응답
 */
export const getLikeCount = async (contentId: string): Promise<LikeCountResponse> => {
  const { data } = await apiClient.get<LikeCountResponse>(
    API_ENDPOINTS.LIKE.COUNT(contentId)
  );
  return data;
};

// ==================== Save API ====================

/**
 * 콘텐츠를 저장합니다.
 *
 * 백엔드: POST /api/v1/contents/{contentId}/save
 *
 * @param contentId 콘텐츠 ID
 * @returns 저장 응답
 */
export const saveContent = async (contentId: string): Promise<SaveResponse> => {
  const { data } = await apiClient.post<SaveResponse>(
    API_ENDPOINTS.SAVE.CREATE(contentId)
  );
  return data;
};

/**
 * 콘텐츠 저장을 취소합니다.
 *
 * 백엔드: DELETE /api/v1/contents/{contentId}/save
 *
 * @param contentId 콘텐츠 ID
 * @returns 저장 응답
 */
export const unsaveContent = async (contentId: string): Promise<SaveResponse> => {
  const { data } = await apiClient.delete<SaveResponse>(
    API_ENDPOINTS.SAVE.DELETE(contentId)
  );
  return data;
};

/**
 * 콘텐츠의 저장 상태를 조회합니다.
 *
 * 백엔드: GET /api/v1/contents/{contentId}/save/status
 *
 * @param contentId 콘텐츠 ID
 * @returns 저장 상태 응답
 */
export const getSaveStatus = async (contentId: string): Promise<SaveStatusResponse> => {
  const { data } = await apiClient.get<SaveStatusResponse>(
    API_ENDPOINTS.SAVE.STATUS(contentId)
  );
  return data;
};

/**
 * 저장한 콘텐츠 목록을 조회합니다.
 *
 * 백엔드: GET /api/v1/users/me/saved-contents
 *
 * @returns 저장한 콘텐츠 목록
 */
export const getSavedContents = async (): Promise<SavedContentResponse[]> => {
  const { data } = await apiClient.get<SavedContentResponse[]>(
    API_ENDPOINTS.SAVE.LIST
  );
  return data;
};

// ==================== Share API ====================

/**
 * 콘텐츠 공유를 기록합니다.
 *
 * 백엔드: POST /api/v1/contents/{contentId}/share
 *
 * @param contentId 콘텐츠 ID
 * @returns 공유 응답
 */
export const shareContent = async (contentId: string): Promise<ShareResponse> => {
  const { data } = await apiClient.post<ShareResponse>(
    API_ENDPOINTS.SHARE.CREATE(contentId)
  );
  return data;
};

/**
 * 콘텐츠의 공유 링크를 생성합니다.
 *
 * 백엔드: GET /api/v1/contents/{contentId}/share-link
 *
 * @param contentId 콘텐츠 ID
 * @returns 공유 링크 응답
 */
export const getShareLink = async (contentId: string): Promise<ShareLinkResponse> => {
  const { data } = await apiClient.get<ShareLinkResponse>(
    API_ENDPOINTS.SHARE.GET_LINK(contentId)
  );
  return data;
};

// ==================== Comment API ====================

/**
 * 댓글을 작성합니다.
 *
 * 백엔드: POST /api/v1/contents/{contentId}/comments
 *
 * @param contentId 콘텐츠 ID
 * @param request 댓글 작성 요청
 * @returns 댓글 응답
 */
export const createComment = async (
  contentId: string,
  request: CommentRequest
): Promise<CommentResponse> => {
  const { data } = await apiClient.post<CommentResponse>(
    API_ENDPOINTS.COMMENT.CREATE(contentId),
    request
  );
  return data;
};

/**
 * 콘텐츠의 댓글 목록을 조회합니다.
 *
 * 백엔드: GET /api/v1/contents/{contentId}/comments
 *
 * @param contentId 콘텐츠 ID
 * @param cursor 페이지 커서 (선택)
 * @returns 댓글 목록 응답
 */
export const getComments = async (
  contentId: string,
  cursor?: string
): Promise<CommentListResponse> => {
  const { data } = await apiClient.get<CommentListResponse>(
    API_ENDPOINTS.COMMENT.LIST(contentId),
    {
      params: cursor ? { cursor } : undefined,
    }
  );
  return data;
};

/**
 * 댓글의 답글 목록을 조회합니다.
 *
 * 백엔드: GET /api/v1/comments/{commentId}/replies
 *
 * @param commentId 댓글 ID
 * @param cursor 페이지 커서 (선택)
 * @returns 답글 목록 응답
 */
export const getReplies = async (
  commentId: string,
  cursor?: string
): Promise<CommentListResponse> => {
  const { data } = await apiClient.get<CommentListResponse>(
    API_ENDPOINTS.COMMENT.REPLIES(commentId),
    {
      params: cursor ? { cursor } : undefined,
    }
  );
  return data;
};

/**
 * 댓글을 삭제합니다.
 *
 * 백엔드: DELETE /api/v1/comments/{commentId}
 *
 * @param commentId 댓글 ID
 */
export const deleteComment = async (commentId: string): Promise<void> => {
  await apiClient.delete(API_ENDPOINTS.COMMENT.DELETE(commentId));
};

// ==================== Comment Like API ====================

/**
 * 댓글에 좋아요를 추가합니다.
 *
 * 백엔드: POST /api/v1/comments/{commentId}/likes
 *
 * @param commentId 댓글 ID
 * @returns 댓글 좋아요 응답
 */
export const likeComment = async (commentId: string): Promise<CommentLikeResponse> => {
  const { data } = await apiClient.post<CommentLikeResponse>(
    API_ENDPOINTS.COMMENT_LIKE.CREATE(commentId)
  );
  return data;
};

/**
 * 댓글 좋아요를 취소합니다.
 *
 * 백엔드: DELETE /api/v1/comments/{commentId}/likes
 *
 * @param commentId 댓글 ID
 * @returns 댓글 좋아요 응답
 */
export const unlikeComment = async (commentId: string): Promise<CommentLikeResponse> => {
  const { data } = await apiClient.delete<CommentLikeResponse>(
    API_ENDPOINTS.COMMENT_LIKE.DELETE(commentId)
  );
  return data;
};

/**
 * 댓글의 좋아요 개수를 조회합니다.
 *
 * 백엔드: GET /api/v1/comments/{commentId}/likes/count
 *
 * @param commentId 댓글 ID
 * @returns 좋아요 개수 응답
 */
export const getCommentLikeCount = async (
  commentId: string
): Promise<CommentLikeCountResponse> => {
  const { data } = await apiClient.get<CommentLikeCountResponse>(
    API_ENDPOINTS.COMMENT_LIKE.COUNT(commentId)
  );
  return data;
};

/**
 * 댓글의 좋아요 상태를 조회합니다.
 *
 * 백엔드: GET /api/v1/comments/{commentId}/likes/check
 *
 * @param commentId 댓글 ID
 * @returns 좋아요 상태 응답
 */
export const getCommentLikeStatus = async (
  commentId: string
): Promise<CommentLikeStatusResponse> => {
  const { data} = await apiClient.get<CommentLikeStatusResponse>(
    API_ENDPOINTS.COMMENT_LIKE.STATUS(commentId)
  );
  return data;
};
