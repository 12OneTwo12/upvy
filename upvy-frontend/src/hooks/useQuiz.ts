/**
 * useQuiz - 퀴즈 관련 Custom Hook
 *
 * React Query + TanStack Query 통합
 * - 퀴즈 조회, 시도 제출, 통계 조회
 * - 캐시 무효화를 통한 UI 자동 업데이트
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getQuiz,
  submitQuizAttempt,
  getMyQuizAttempts,
  getQuizStats,
} from '@/api/quiz.api';
import type {
  QuizResponse,
  QuizAttemptRequest,
  QuizAttemptResponse,
} from '@/types/quiz.types';

/**
 * 퀴즈 조회 Hook
 *
 * 콘텐츠의 퀴즈를 조회합니다.
 */
export const useQuizQuery = (contentId: string, enabled: boolean = true) => {
  return useQuery({
    queryKey: ['quiz', contentId],
    queryFn: () => getQuiz(contentId),
    enabled,
    staleTime: 1000 * 60 * 5, // 5분간 캐시 유지
  });
};

/**
 * 퀴즈 시도 제출 Hook
 *
 * 퀴즈에 대한 답변을 제출합니다.
 */
interface UseQuizAttemptOptions {
  onSuccess?: (data: QuizAttemptResponse) => void;
  onError?: (error: Error) => void;
}

export const useQuizAttempt = (
  quizId: string,
  contentId: string,
  options?: UseQuizAttemptOptions
) => {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: (request: QuizAttemptRequest) => submitQuizAttempt(quizId, request),
    onSuccess: (data) => {
      // 캐시 무효화 - 퀴즈 데이터 갱신 (통계 업데이트)
      queryClient.invalidateQueries({ queryKey: ['quiz', contentId] });
      queryClient.invalidateQueries({ queryKey: ['quiz-attempts', quizId] });
      queryClient.invalidateQueries({ queryKey: ['quiz-stats', quizId] });

      // 피드 캐시도 무효화 (quiz metadata 업데이트)
      queryClient.invalidateQueries({ queryKey: ['feed'] });
      queryClient.invalidateQueries({ queryKey: ['content', contentId] });

      // 사용자 콜백
      options?.onSuccess?.(data);
    },
    onError: (error: Error) => {
      options?.onError?.(error);
    },
  });

  return {
    /** 퀴즈 시도 제출 함수 (void 반환) */
    submit: mutation.mutate,
    /** 퀴즈 시도 제출 함수 (Promise 반환) */
    submitAsync: mutation.mutateAsync,
    /** 제출 결과 데이터 */
    data: mutation.data,
    /** 로딩 상태 */
    isSubmitting: mutation.isPending,
    /** 성공 여부 */
    isSuccess: mutation.isSuccess,
    /** 에러 */
    error: mutation.error,
  };
};

/**
 * 퀴즈 시도 기록 조회 Hook
 *
 * 현재 사용자의 특정 퀴즈에 대한 모든 시도 기록을 조회합니다.
 */
export const useQuizAttemptsQuery = (quizId: string, enabled: boolean = true) => {
  return useQuery({
    queryKey: ['quiz-attempts', quizId],
    queryFn: () => getMyQuizAttempts(quizId),
    enabled,
    staleTime: 1000 * 60 * 5, // 5분간 캐시 유지
  });
};

/**
 * 퀴즈 통계 조회 Hook
 *
 * 퀴즈의 전체 통계를 조회합니다.
 */
export const useQuizStatsQuery = (quizId: string, enabled: boolean = true) => {
  return useQuery({
    queryKey: ['quiz-stats', quizId],
    queryFn: () => getQuizStats(quizId),
    enabled,
    staleTime: 1000 * 60 * 1, // 1분간 캐시 유지 (통계는 자주 변경됨)
  });
};

/**
 * 통합 퀴즈 Hook
 *
 * 퀴즈 조회와 시도 제출을 한 번에 사용할 수 있는 통합 Hook
 */
export const useQuiz = (contentId: string, options?: UseQuizAttemptOptions) => {
  const quizQuery = useQuizQuery(contentId, !!contentId);
  const quizId = quizQuery.data?.id;

  // 이미 시도한 퀴즈인지 확인
  const hasAttempted = quizQuery.data?.options?.some(opt => opt.isCorrect !== null) ?? false;

  // 이미 시도한 퀴즈라면 시도 기록 조회
  const attemptsQuery = useQuizAttemptsQuery(quizId ?? '', hasAttempted && !!quizId);

  const attemptMutation = useQuizAttempt(quizId ?? '', contentId, options);

  // 마지막 시도 결과 (이미 시도한 경우 or 새로 제출한 결과)
  let lastAttemptResult = attemptMutation.data;

  // 이미 시도한 퀴즈의 경우, UserQuizAttemptDetail을 QuizAttemptResponse로 변환
  if (!lastAttemptResult && attemptsQuery.data?.attempts && attemptsQuery.data.attempts.length > 0 && quizQuery.data) {
    // 백엔드가 이미 최신순(createdAt DESC)으로 정렬해서 반환하므로 첫 번째 시도가 최신
    const latestAttempt = attemptsQuery.data.attempts[0];
    const selectedOptionIds = latestAttempt.selectedOptions;

    // quiz.options에 isSelected 정보를 추가
    const optionsWithSelection = quizQuery.data.options.map(opt => ({
      ...opt,
      isSelected: selectedOptionIds.includes(opt.id),
    }));

    lastAttemptResult = {
      attemptId: latestAttempt.attemptId,
      quizId: quizId ?? '',
      isCorrect: latestAttempt.isCorrect,
      attemptNumber: latestAttempt.attemptNumber,
      options: optionsWithSelection,
    } as any;
  }

  return {
    /** 퀴즈 데이터 */
    quiz: quizQuery.data,
    /** 퀴즈 로딩 상태 */
    isLoadingQuiz: quizQuery.isLoading || (hasAttempted && attemptsQuery.isLoading),
    /** 퀴즈 조회 에러 */
    quizError: quizQuery.error,
    /** 퀴즈 시도 제출 함수 (void 반환) */
    submitAttempt: attemptMutation.submit,
    /** 퀴즈 시도 제출 함수 (Promise 반환) */
    submitAttemptAsync: attemptMutation.submitAsync,
    /** 시도 제출 결과 (마지막 시도 또는 새로 제출한 결과) */
    attemptResult: lastAttemptResult,
    /** 시도 제출 중 상태 */
    isSubmitting: attemptMutation.isSubmitting,
    /** 시도 제출 성공 여부 */
    isSubmitSuccess: attemptMutation.isSuccess,
    /** 시도 제출 에러 */
    submitError: attemptMutation.error,
  };
};
