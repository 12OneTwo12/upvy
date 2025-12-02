/**
 * 차단 API 클라이언트
 *
 * 백엔드 Controller:
 * - UserBlockController: grow-snap-backend/src/main/kotlin/me/onetwo/growsnap/domain/block/controller/UserBlockController.kt
 * - ContentBlockController: grow-snap-backend/src/main/kotlin/me/onetwo/growsnap/domain/block/controller/ContentBlockController.kt
 */

import apiClient from './client';
import type {
  UserBlockResponse,
  ContentBlockResponse,
  BlockedUsersResponse,
  BlockedContentsResponse,
} from '@/types/block.types';

/**
 * 사용자 차단
 *
 * POST /api/v1/users/{userId}/block
 *
 * @param userId 차단할 사용자 ID
 * @returns 사용자 차단 응답
 */
export const blockUser = async (userId: string): Promise<UserBlockResponse> => {
  const response = await apiClient.post<UserBlockResponse>(
    `/users/${userId}/block`
  );
  return response.data;
};

/**
 * 사용자 차단 해제
 *
 * DELETE /api/v1/users/{userId}/block
 *
 * @param userId 차단 해제할 사용자 ID
 * @returns 204 No Content
 */
export const unblockUser = async (userId: string): Promise<void> => {
  await apiClient.delete(`/users/${userId}/block`);
};

/**
 * 차단한 사용자 목록 조회
 *
 * GET /api/v1/users/blocks?cursor=&limit=20
 *
 * @param cursor 커서 (차단 ID)
 * @param limit 조회 개수 (기본값: 20)
 * @returns 차단한 사용자 목록
 */
export const getBlockedUsers = async (
  cursor?: string,
  limit: number = 20
): Promise<BlockedUsersResponse> => {
  const params = new URLSearchParams();
  if (cursor) {
    params.append('cursor', cursor);
  }
  params.append('limit', limit.toString());

  const response = await apiClient.get<BlockedUsersResponse>(
    `/users/blocks?${params.toString()}`
  );
  return response.data;
};

/**
 * 콘텐츠 차단
 *
 * POST /api/v1/contents/{contentId}/block
 *
 * @param contentId 차단할 콘텐츠 ID
 * @returns 콘텐츠 차단 응답
 */
export const blockContent = async (
  contentId: string
): Promise<ContentBlockResponse> => {
  const response = await apiClient.post<ContentBlockResponse>(
    `/contents/${contentId}/block`
  );
  return response.data;
};

/**
 * 콘텐츠 차단 해제
 *
 * DELETE /api/v1/contents/{contentId}/block
 *
 * @param contentId 차단 해제할 콘텐츠 ID
 * @returns 204 No Content
 */
export const unblockContent = async (contentId: string): Promise<void> => {
  await apiClient.delete(`/contents/${contentId}/block`);
};

/**
 * 차단한 콘텐츠 목록 조회
 *
 * GET /api/v1/contents/blocks?cursor=&limit=20
 *
 * @param cursor 커서 (차단 ID)
 * @param limit 조회 개수 (기본값: 20)
 * @returns 차단한 콘텐츠 목록
 */
export const getBlockedContents = async (
  cursor?: string,
  limit: number = 20
): Promise<BlockedContentsResponse> => {
  const params = new URLSearchParams();
  if (cursor) {
    params.append('cursor', cursor);
  }
  params.append('limit', limit.toString());

  const response = await apiClient.get<BlockedContentsResponse>(
    `/contents/blocks?${params.toString()}`
  );
  return response.data;
};
