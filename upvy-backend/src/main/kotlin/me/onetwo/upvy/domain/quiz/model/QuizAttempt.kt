package me.onetwo.upvy.domain.quiz.model

import java.time.Instant
import java.util.UUID

/**
 * 퀴즈 시도 기록 엔티티
 *
 * 사용자의 퀴즈 풀이 시도를 나타냅니다.
 * 한 사용자는 동일한 퀴즈를 여러 번 시도할 수 있습니다 (교육 목적).
 *
 * ### 시도 횟수 관리
 * - 무제한 재시도 가능
 * - attemptNumber로 시도 순서 추적 (1, 2, 3, ...)
 * - 각 시도마다 정답 여부 기록
 *
 * ### 연관 관계
 * - QuizAttemptAnswer: 각 시도 시 선택한 보기들 (1:N 관계)
 *
 * @property id 시도 고유 식별자
 * @property quizId 연관 퀴즈 ID
 * @property userId 시도한 사용자 ID
 * @property attemptNumber 시도 번호 (1, 2, 3, ...)
 * @property isCorrect 정답 여부
 * @property createdAt 시도 시각 (UTC Instant)
 */
data class QuizAttempt(
    val id: UUID? = null,
    val quizId: UUID,
    val userId: UUID,
    val attemptNumber: Int,
    val isCorrect: Boolean,
    val createdAt: Instant = Instant.now()
)
