/**
 * 퀴즈 API 클라이언트
 *
 * 백엔드 QuizController의 API를 호출합니다.
 * 참조: upvy-backend/.../quiz/controller/QuizController.kt
 */

import apiClient from '@/api/client';
import { API_ENDPOINTS } from '@/constants/api';
import type {
  QuizCreateRequest,
  QuizUpdateRequest,
  QuizResponse,
  QuizAttemptRequest,
  QuizAttemptResponse,
  UserQuizAttemptsResponse,
  QuizStatsResponse,
} from '@/types/quiz.types';

/**
 * 퀴즈를 생성합니다.
 *
 * 하나의 콘텐츠에는 하나의 퀴즈만 생성 가능합니다 (1:1 관계).
 *
 * 백엔드: POST /api/v1/contents/{contentId}/quiz
 *
 * @param contentId 콘텐츠 ID
 * @param request 퀴즈 생성 요청
 * @returns 생성된 퀴즈 응답
 */
export const createQuiz = async (
  contentId: string,
  request: QuizCreateRequest
): Promise<QuizResponse> => {
  const { data } = await apiClient.post<QuizResponse>(
    API_ENDPOINTS.QUIZ.CREATE(contentId),
    request
  );
  return data;
};

/**
 * 콘텐츠의 퀴즈를 조회합니다.
 *
 * 사용자가 이미 퀴즈를 풀었으면 정답이 공개되고, 풀지 않았으면 정답이 숨겨집니다.
 *
 * 백엔드: GET /api/v1/contents/{contentId}/quiz
 *
 * @param contentId 콘텐츠 ID
 * @returns 퀴즈 응답 (통계 포함)
 */
export const getQuiz = async (contentId: string): Promise<QuizResponse> => {
  const { data } = await apiClient.get<QuizResponse>(
    API_ENDPOINTS.QUIZ.GET(contentId)
  );
  return data;
};

/**
 * 콘텐츠의 퀴즈를 수정합니다.
 *
 * 기존 보기는 모두 삭제되고 새로운 보기로 대체됩니다.
 *
 * 백엔드: PUT /api/v1/contents/{contentId}/quiz
 *
 * @param contentId 콘텐츠 ID
 * @param request 퀴즈 수정 요청
 * @returns 수정된 퀴즈 응답
 */
export const updateQuiz = async (
  contentId: string,
  request: QuizUpdateRequest
): Promise<QuizResponse> => {
  const { data } = await apiClient.put<QuizResponse>(
    API_ENDPOINTS.QUIZ.UPDATE(contentId),
    request
  );
  return data;
};

/**
 * 콘텐츠의 퀴즈를 삭제합니다 (Soft Delete).
 *
 * 퀴즈와 함께 모든 보기도 삭제됩니다.
 *
 * 백엔드: DELETE /api/v1/contents/{contentId}/quiz
 *
 * @param contentId 콘텐츠 ID
 */
export const deleteQuiz = async (contentId: string): Promise<void> => {
  await apiClient.delete(API_ENDPOINTS.QUIZ.DELETE(contentId));
};

/**
 * 퀴즈에 대한 답변을 제출합니다.
 *
 * 사용자는 동일한 퀴즈를 여러 번 시도할 수 있습니다 (교육 목적).
 * 제출 즉시 정답 여부와 통계가 반환됩니다.
 *
 * 백엔드: POST /api/v1/quizzes/{quizId}/attempts
 *
 * @param quizId 퀴즈 ID
 * @param request 퀴즈 시도 요청 (선택한 보기 목록)
 * @returns 퀴즈 시도 응답 (정답 여부 + 통계)
 */
export const submitQuizAttempt = async (
  quizId: string,
  request: QuizAttemptRequest
): Promise<QuizAttemptResponse> => {
  const { data } = await apiClient.post<QuizAttemptResponse>(
    API_ENDPOINTS.QUIZ.SUBMIT_ATTEMPT(quizId),
    request
  );
  return data;
};

/**
 * 현재 사용자의 특정 퀴즈에 대한 모든 시도 기록을 조회합니다.
 *
 * 백엔드: GET /api/v1/quizzes/{quizId}/my-attempts
 *
 * @param quizId 퀴즈 ID
 * @returns 사용자 퀴즈 시도 기록 응답
 */
export const getMyQuizAttempts = async (
  quizId: string
): Promise<UserQuizAttemptsResponse> => {
  const { data } = await apiClient.get<UserQuizAttemptsResponse>(
    API_ENDPOINTS.QUIZ.MY_ATTEMPTS(quizId)
  );
  return data;
};

/**
 * 퀴즈의 전체 통계를 조회합니다.
 *
 * 모든 사용자의 시도 기록과 각 보기별 선택 통계를 포함합니다.
 *
 * 백엔드: GET /api/v1/quizzes/{quizId}/stats
 *
 * @param quizId 퀴즈 ID
 * @returns 퀴즈 통계 응답
 */
export const getQuizStats = async (
  quizId: string
): Promise<QuizStatsResponse> => {
  const { data } = await apiClient.get<QuizStatsResponse>(
    API_ENDPOINTS.QUIZ.STATS(quizId)
  );
  return data;
};
