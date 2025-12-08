/**
 * 팔로우 API 클라이언트
 *
 * 백엔드 스펙: POST/DELETE 방식
 */

import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';

/**
 * 사용자 팔로우
 *
 * 백엔드: POST /api/v1/follows/{userId}
 * Response: 201 Created
 *
 * @param userId 팔로우할 사용자 ID
 */
export const followUser = async (userId: string): Promise<void> => {
  await apiClient.post(API_ENDPOINTS.FOLLOW.FOLLOW(userId));
};

/**
 * 사용자 언팔로우
 *
 * 백엔드: DELETE /api/v1/follows/{userId}
 * Response: 204 No Content
 *
 * @param userId 언팔로우할 사용자 ID
 */
export const unfollowUser = async (userId: string): Promise<void> => {
  await apiClient.delete(API_ENDPOINTS.FOLLOW.UNFOLLOW(userId));
};

/**
 * 팔로우 상태 확인
 *
 * 백엔드: GET /api/v1/follows/check/{userId}
 * Response: { isFollowing: boolean }
 *
 * @param userId 확인할 사용자 ID
 * @returns 팔로우 상태
 */
export const checkFollow = async (userId: string): Promise<boolean> => {
  const response = await apiClient.get<{ isFollowing: boolean }>(
    API_ENDPOINTS.FOLLOW.CHECK(userId)
  );
  return response.data.isFollowing;
};

/**
 * 팔로우 통계 조회
 *
 * 백엔드: GET /api/v1/follows/stats/{userId}
 * Response: { followerCount: number, followingCount: number }
 *
 * @param userId 사용자 ID
 * @returns 팔로워/팔로잉 수
 */
export const getFollowStats = async (
  userId: string
): Promise<{ followerCount: number; followingCount: number }> => {
  const response = await apiClient.get(API_ENDPOINTS.FOLLOW.STATS(userId));
  return response.data;
};

/**
 * 내 팔로우 통계 조회
 *
 * 백엔드: GET /api/v1/follows/stats/me
 * Response: { followerCount: number, followingCount: number }
 *
 * @returns 내 팔로워/팔로잉 수
 */
export const getMyFollowStats = async (): Promise<{
  followerCount: number;
  followingCount: number;
}> => {
  const response = await apiClient.get(API_ENDPOINTS.FOLLOW.STATS_ME);
  return response.data;
};

/**
 * 팔로워 목록 조회
 *
 * 백엔드: GET /api/v1/follows/followers/{userId}
 *
 * @param userId 사용자 ID
 * @returns 팔로워 목록
 */
export const getFollowers = async (userId: string): Promise<any[]> => {
  const response = await apiClient.get(API_ENDPOINTS.FOLLOW.FOLLOWERS(userId));
  return response.data;
};

/**
 * 팔로잉 목록 조회
 *
 * 백엔드: GET /api/v1/follows/following/{userId}
 *
 * @param userId 사용자 ID
 * @returns 팔로잉 목록
 */
export const getFollowing = async (userId: string): Promise<any[]> => {
  const response = await apiClient.get(API_ENDPOINTS.FOLLOW.FOLLOWING(userId));
  return response.data;
};
