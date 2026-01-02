package me.onetwo.upvy.crawler.domain.content

import jakarta.persistence.*
import java.time.Instant

/**
 * 퀴즈 보기 엔티티 (백엔드 quiz_options 테이블)
 *
 * AI 크롤러에서 생성한 퀴즈 보기를 백엔드 quiz_options 테이블에 저장하기 위한 Entity입니다.
 * 하나의 퀴즈는 여러 보기를 가질 수 있습니다 (1:N 관계).
 *
 * @property id 보기 고유 식별자
 * @property quizId 연관 퀴즈 ID
 * @property optionText 보기 텍스트 (최대 100자)
 * @property isCorrect 정답 여부
 * @property displayOrder 표시 순서 (1, 2, 3, ...)
 * @property createdAt 생성 시각
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
@Entity
@Table(name = "quiz_options")
data class QuizOption(
    @Id
    @Column(length = 36)
    val id: String,

    @Column(name = "quiz_id", nullable = false, length = 36)
    val quizId: String,

    @Column(name = "option_text", nullable = false, length = 100)
    val optionText: String,

    @Column(name = "is_correct", nullable = false)
    val isCorrect: Boolean,

    @Column(name = "display_order", nullable = false)
    val displayOrder: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "created_by", length = 36)
    val createdBy: String? = null,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),

    @Column(name = "updated_by", length = 36)
    val updatedBy: String? = null,

    @Column(name = "deleted_at")
    val deletedAt: Instant? = null
)
