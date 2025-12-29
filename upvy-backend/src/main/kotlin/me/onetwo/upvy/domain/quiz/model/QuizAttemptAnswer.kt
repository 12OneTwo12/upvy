package me.onetwo.upvy.domain.quiz.model

import java.time.Instant
import java.util.UUID

/**
 * 퀴즈 시도 답변 엔티티
 *
 * 특정 시도 시 사용자가 선택한 보기를 나타냅니다.
 * 하나의 시도는 여러 보기를 선택할 수 있습니다 (1:N 관계).
 * (복수 정답 퀴즈인 경우)
 *
 * ### 제약 조건
 * - 하나의 시도에서 동일한 보기를 중복 선택 불가
 * - UNIQUE (attempt_id, option_id)
 *
 * @property id 답변 고유 식별자
 * @property attemptId 연관 시도 ID
 * @property optionId 선택한 보기 ID
 * @property createdAt 선택 시각 (UTC Instant)
 */
data class QuizAttemptAnswer(
    val id: UUID? = null,
    val attemptId: UUID,
    val optionId: UUID,
    val createdAt: Instant = Instant.now()
)
