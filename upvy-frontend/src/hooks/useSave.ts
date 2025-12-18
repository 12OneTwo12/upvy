/**
 * useSave - 저장/저장 취소 Custom Hook
 *
 * React Query + Analytics 통합
 * - API 호출과 Analytics 로깅을 분리
 * - onSuccess 콜백에서 Analytics 호출 (Fire-and-Forget)
 * - 캐시 무효화를 통한 UI 자동 업데이트
 */

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createSave, deleteSave } from '@/api/save.api';
import { Analytics } from '@/utils/analytics';

interface UseSaveOptions {
  onSuccess?: () => void;
  onError?: (error: Error) => void;
}

export const useSave = (contentId: string, options?: UseSaveOptions) => {
  const queryClient = useQueryClient();

  // 저장 mutation
  const saveMutation = useMutation({
    mutationFn: () => createSave(contentId),
    onSuccess: () => {
      // Analytics 이벤트 (Fire-and-Forget - await 없음)
      Analytics.logSave(contentId, 'video');

      // 캐시 무효화
      queryClient.invalidateQueries({ queryKey: ['content', contentId] });
      queryClient.invalidateQueries({ queryKey: ['saves'] });

      // 사용자 콜백
      options?.onSuccess?.();
    },
    onError: (error: Error) => {
      options?.onError?.(error);
    },
  });

  // 저장 취소 mutation
  const unsaveMutation = useMutation({
    mutationFn: () => deleteSave(contentId),
    onSuccess: () => {
      // Analytics 이벤트 (Fire-and-Forget - await 없음)
      Analytics.logUnsave(contentId, 'video');

      // 캐시 무효화
      queryClient.invalidateQueries({ queryKey: ['content', contentId] });
      queryClient.invalidateQueries({ queryKey: ['saves'] });

      // 사용자 콜백
      options?.onSuccess?.();
    },
    onError: (error: Error) => {
      options?.onError?.(error);
    },
  });

  return {
    /** 저장 함수 */
    save: saveMutation.mutate,
    /** 저장 취소 함수 */
    unsave: unsaveMutation.mutate,
    /** 로딩 상태 (저장 or 저장 취소) */
    isSaving: saveMutation.isPending || unsaveMutation.isPending,
  };
};
