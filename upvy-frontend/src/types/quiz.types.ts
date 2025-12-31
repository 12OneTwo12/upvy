/**
 * Quiz Type Definitions
 *
 * 모든 타입은 백엔드 QuizDto.kt와 100% 일치합니다.
 * Backend: src/main/kotlin/me/onetwo/upvy/domain/quiz/dto/QuizDto.kt
 */

/**
 * 퀴즈 메타데이터 응답
 *
 * 피드 및 콘텐츠 조회 시 퀴즈 존재 여부와 사용자의 시도 정보
 */
export interface QuizMetadataResponse {
  quizId: string;
  hasAttempted: boolean;
  attemptCount: number;
}

/**
 * 퀴즈 보기 생성 요청
 */
export interface QuizOptionCreateRequest {
  optionText: string;
  isCorrect: boolean;
}

/**
 * 퀴즈 생성 요청
 */
export interface QuizCreateRequest {
  question: string;
  allowMultipleAnswers: boolean;
  options: QuizOptionCreateRequest[];
}

/**
 * 퀴즈 수정 요청
 */
export interface QuizUpdateRequest {
  question: string;
  allowMultipleAnswers: boolean;
  options: QuizOptionCreateRequest[];
}

/**
 * 퀴즈 보기 응답
 *
 * @property isCorrect 정답 여부 (사용자가 이미 풀었을 경우에만 노출)
 */
export interface QuizOptionResponse {
  id: string;
  optionText: string;
  displayOrder: number;
  selectionCount: number;
  selectionPercentage: number;
  isCorrect: boolean | null;
}

/**
 * 퀴즈 조회 응답
 */
export interface QuizResponse {
  id: string;
  contentId: string;
  question: string;
  allowMultipleAnswers: boolean;
  options: QuizOptionResponse[];
  userAttemptCount: number | null;
  totalAttempts: number;
}

/**
 * 퀴즈 시도 요청
 */
export interface QuizAttemptRequest {
  selectedOptionIds: string[];
}

/**
 * 퀴즈 시도 응답
 *
 * 퀴즈 제출 후 즉시 정답 여부와 통계를 반환
 */
export interface QuizAttemptResponse {
  attemptId: string;
  quizId: string;
  isCorrect: boolean;
  attemptNumber: number;
  options: QuizOptionResponse[];
}

/**
 * 사용자 퀴즈 시도 상세 정보
 */
export interface UserQuizAttemptDetail {
  attemptId: string;
  attemptNumber: number;
  isCorrect: boolean;
  selectedOptions: string[];
  attemptedAt: string;
}

/**
 * 사용자 퀴즈 시도 기록 응답
 */
export interface UserQuizAttemptsResponse {
  attempts: UserQuizAttemptDetail[];
}

/**
 * 퀴즈 보기 통계 응답
 */
export interface QuizOptionStatsResponse {
  optionId: string;
  optionText: string;
  selectionCount: number;
  selectionPercentage: number;
  isCorrect: boolean;
}

/**
 * 퀴즈 통계 응답
 */
export interface QuizStatsResponse {
  quizId: string;
  totalAttempts: number;
  uniqueUsers: number;
  options: QuizOptionStatsResponse[];
}
