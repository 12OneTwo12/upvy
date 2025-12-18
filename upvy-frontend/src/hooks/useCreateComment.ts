/**
 * useCreateComment - 댓글 작성 Custom Hook
 *
 * React Query + Analytics 통합
 * - API 호출과 Analytics 로깅을 분리
 * - onSuccess 콜백에서 Analytics 호출 (Fire-and-Forget)
 * - 캐시 무효화를 통한 UI 자동 업데이트
 */

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createComment, type CreateCommentRequest } from '@/api/comment.api';
import { Analytics } from '@/utils/analytics';

interface UseCreateCommentOptions {
  onSuccess?: () => void;
  onError?: (error: Error) => void;
}

export const useCreateComment = (contentId: string, options?: UseCreateCommentOptions) => {
  const queryClient = useQueryClient();

  const commentMutation = useMutation({
    mutationFn: (request: CreateCommentRequest) => createComment(contentId, request),
    onSuccess: (data, variables) => {
      // Analytics 이벤트 (Fire-and-Forget - await 없음)
      const commentLength = variables.content?.length || 0;
      Analytics.logComment(contentId, commentLength);

      // 캐시 무효화
      queryClient.invalidateQueries({ queryKey: ['comments', contentId] });
      queryClient.invalidateQueries({ queryKey: ['content', contentId] });

      // 사용자 콜백
      options?.onSuccess?.();
    },
    onError: (error: Error) => {
      options?.onError?.(error);
    },
  });

  return {
    /** 댓글 작성 함수 */
    comment: commentMutation.mutate,
    /** 로딩 상태 */
    isCommenting: commentMutation.isPending,
  };
};
