package me.onetwo.upvy.domain.quiz.model

import java.time.Instant
import java.util.UUID

/**
 * 퀴즈 보기 엔티티
 *
 * 퀴즈 문제의 선택지를 나타냅니다.
 * 하나의 퀴즈는 여러 보기를 가질 수 있으며 (1:N 관계),
 * 각 보기는 정답 여부를 가집니다 (복수 정답 가능).
 *
 * ### 제약 조건
 * - 최소 2개 이상의 보기 필요
 * - 최소 1개 이상의 정답 필요
 * - 정답은 복수 개 가능 (allowMultipleAnswers = true 인 경우)
 *
 * @property id 보기 고유 식별자
 * @property quizId 연관 퀴즈 ID
 * @property optionText 보기 텍스트 (최대 100자)
 * @property isCorrect 정답 여부 (복수 정답 가능)
 * @property displayOrder 표시 순서 (1, 2, 3, ...)
 * @property createdAt 생성 시각 (UTC Instant)
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각 (UTC Instant)
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (UTC Instant, Soft Delete)
 */
data class QuizOption(
    val id: UUID? = null,
    val quizId: UUID,
    val optionText: String,
    val isCorrect: Boolean,
    val displayOrder: Int,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
)
