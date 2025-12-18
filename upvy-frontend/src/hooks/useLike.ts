/**
 * useLike - 좋아요/좋아요 취소 Custom Hook
 *
 * React Query + Analytics 통합
 * - API 호출과 Analytics 로깅을 분리
 * - onSuccess 콜백에서 Analytics 호출 (Fire-and-Forget)
 * - 캐시 무효화를 통한 UI 자동 업데이트
 */

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createLike, deleteLike } from '@/api/like.api';
import { Analytics } from '@/utils/analytics';

interface UseLikeOptions {
  onSuccess?: () => void;
  onError?: (error: Error) => void;
}

export const useLike = (contentId: string, options?: UseLikeOptions) => {
  const queryClient = useQueryClient();

  // 좋아요 mutation
  const likeMutation = useMutation({
    mutationFn: () => createLike(contentId),
    onSuccess: () => {
      // Analytics 이벤트 (Fire-and-Forget - await 없음)
      Analytics.logLike(contentId, 'video');

      // 캐시 무효화
      queryClient.invalidateQueries({ queryKey: ['content', contentId] });
      queryClient.invalidateQueries({ queryKey: ['feed'] });
      queryClient.invalidateQueries({ queryKey: ['likes'] });

      // 사용자 콜백
      options?.onSuccess?.();
    },
    onError: (error: Error) => {
      options?.onError?.(error);
    },
  });

  // 좋아요 취소 mutation
  const unlikeMutation = useMutation({
    mutationFn: () => deleteLike(contentId),
    onSuccess: () => {
      // Analytics 이벤트 (Fire-and-Forget - await 없음)
      Analytics.logUnlike(contentId, 'video');

      // 캐시 무효화
      queryClient.invalidateQueries({ queryKey: ['content', contentId] });
      queryClient.invalidateQueries({ queryKey: ['feed'] });
      queryClient.invalidateQueries({ queryKey: ['likes'] });

      // 사용자 콜백
      options?.onSuccess?.();
    },
    onError: (error: Error) => {
      options?.onError?.(error);
    },
  });

  return {
    /** 좋아요 함수 */
    like: likeMutation.mutate,
    /** 좋아요 취소 함수 */
    unlike: unlikeMutation.mutate,
    /** 로딩 상태 (좋아요 or 좋아요 취소) */
    isLiking: likeMutation.isPending || unlikeMutation.isPending,
  };
};
