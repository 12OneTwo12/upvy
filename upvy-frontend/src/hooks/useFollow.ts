/**
 * useFollow - 팔로우/언팔로우 Custom Hook
 *
 * React Query + Analytics 통합
 * - API 호출과 Analytics 로깅을 분리
 * - onSuccess 콜백에서 Analytics 호출 (Fire-and-Forget)
 * - 캐시 무효화를 통한 UI 자동 업데이트
 */

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { followUser, unfollowUser } from '@/api/follow.api';
import { Analytics } from '@/utils/analytics';

interface UseFollowOptions {
  onSuccess?: () => void;
  onError?: (error: Error) => void;
}

export const useFollow = (userId: string, options?: UseFollowOptions) => {
  const queryClient = useQueryClient();

  // 팔로우 mutation
  const followMutation = useMutation({
    mutationFn: () => followUser(userId),
    onSuccess: () => {
      // Analytics 이벤트 (Fire-and-Forget - await 없음)
      Analytics.logFollow(userId);

      // 캐시 무효화
      queryClient.invalidateQueries({ queryKey: ['profile', userId] });
      queryClient.invalidateQueries({ queryKey: ['followers'] });
      queryClient.invalidateQueries({ queryKey: ['following'] });

      // 사용자 콜백
      options?.onSuccess?.();
    },
    onError: (error: Error) => {
      options?.onError?.(error);
    },
  });

  // 언팔로우 mutation
  const unfollowMutation = useMutation({
    mutationFn: () => unfollowUser(userId),
    onSuccess: () => {
      // Analytics 이벤트 (Fire-and-Forget - await 없음)
      Analytics.logUnfollow(userId);

      // 캐시 무효화
      queryClient.invalidateQueries({ queryKey: ['profile', userId] });
      queryClient.invalidateQueries({ queryKey: ['followers'] });
      queryClient.invalidateQueries({ queryKey: ['following'] });

      // 사용자 콜백
      options?.onSuccess?.();
    },
    onError: (error: Error) => {
      options?.onError?.(error);
    },
  });

  return {
    /** 팔로우 함수 */
    follow: followMutation.mutate,
    /** 언팔로우 함수 */
    unfollow: unfollowMutation.mutate,
    /** 로딩 상태 (팔로우 or 언팔로우) */
    isFollowing: followMutation.isPending || unfollowMutation.isPending,
  };
};
