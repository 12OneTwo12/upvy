package me.onetwo.upvy.domain.analytics.dto

import jakarta.validation.constraints.NotNull
import java.util.UUID

/**
 * 인터랙션 이벤트 요청 DTO
 *
 * 사용자의 콘텐츠 인터랙션 (좋아요, 저장, 공유, 댓글)을 추적하기 위한 요청입니다.
 * 추천 시스템의 개인화를 위해 사용됩니다.
 *
 * @property contentId 인터랙션한 콘텐츠 ID
 * @property interactionType 인터랙션 타입
 */
data class InteractionEventRequest(
    @field:NotNull(message = "콘텐츠 ID는 필수입니다")
    val contentId: UUID?,

    @field:NotNull(message = "인터랙션 타입은 필수입니다")
    val interactionType: InteractionType?
)

/**
 * 인터랙션 타입
 *
 * @property weight 추천 시스템에서의 가중치 (높을수록 선호도 높음)
 */
enum class InteractionType(val weight: Int) {
    /**
     * 좋아요 (가중치: 5)
     */
    LIKE(5),

    /**
     * 저장 (가중치: 7) - 좋아요보다 더 강한 선호도 표현
     */
    SAVE(7),

    /**
     * 공유 (가중치: 10) - 가장 강한 선호도 표현
     */
    SHARE(10),

    /**
     * 댓글 (가중치: 3) - 참여도 표현
     */
    COMMENT(3)
}
