package me.onetwo.upvy.domain.quiz.model

import java.time.Instant
import java.util.UUID

/**
 * 퀴즈 엔티티
 *
 * 교육용 객관식 퀴즈 문제를 나타냅니다.
 * 하나의 콘텐츠는 최대 하나의 퀴즈를 가질 수 있습니다 (1:1 관계).
 *
 * ### 기능
 * - 객관식 문제 생성
 * - 단일 정답 또는 복수 정답 지원
 * - Instagram Poll 스타일의 실시간 통계
 * - 무제한 재시도 지원 (교육 목적)
 *
 * @property id 퀴즈 고유 식별자
 * @property contentId 연관 콘텐츠 ID (1:1 관계)
 * @property question 퀴즈 질문 (최대 200자)
 * @property allowMultipleAnswers 복수 정답 허용 여부
 * @property createdAt 생성 시각 (UTC Instant)
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각 (UTC Instant)
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (UTC Instant, Soft Delete)
 */
data class Quiz(
    val id: UUID? = null,
    val contentId: UUID,
    val question: String,
    val allowMultipleAnswers: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
)
