package me.onetwo.upvy.crawler.domain.content

import jakarta.persistence.*
import java.time.Instant

/**
 * 퀴즈 엔티티 (백엔드 quizzes 테이블)
 *
 * AI 크롤러에서 생성한 퀴즈를 백엔드 quizzes 테이블에 저장하기 위한 Entity입니다.
 * 하나의 콘텐츠는 최대 하나의 퀴즈를 가질 수 있습니다 (1:1 관계).
 *
 * @property id 퀴즈 고유 식별자
 * @property contentId 연관 콘텐츠 ID (1:1 관계)
 * @property question 퀴즈 질문 (최대 200자)
 * @property allowMultipleAnswers 복수 정답 허용 여부
 * @property createdAt 생성 시각
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
@Entity
@Table(name = "quizzes")
data class Quiz(
    @Id
    @Column(length = 36)
    val id: String,

    @Column(name = "content_id", nullable = false, length = 36, unique = true)
    val contentId: String,

    @Column(nullable = false, length = 200)
    val question: String,

    @Column(name = "allow_multiple_answers", nullable = false)
    val allowMultipleAnswers: Boolean = false,

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
