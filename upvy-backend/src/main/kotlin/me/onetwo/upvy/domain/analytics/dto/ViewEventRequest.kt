package me.onetwo.upvy.domain.analytics.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.util.UUID

/**
 * 시청 이벤트 요청 DTO
 *
 * 사용자의 콘텐츠 시청 기록을 추적하기 위한 요청입니다.
 * 추천 시스템의 개인화를 위해 사용됩니다.
 *
 * @property contentId 시청한 콘텐츠 ID
 * @property watchedDuration 시청 시간 (초 단위)
 * @property completionRate 시청 완료율 (0-100)
 * @property skipped 스킵 여부 (3초 이내 시청 후 스킵)
 */
data class ViewEventRequest(
    @field:NotNull(message = "콘텐츠 ID는 필수입니다")
    val contentId: UUID?,

    @field:NotNull(message = "시청 시간은 필수입니다")
    @field:Min(value = 0, message = "시청 시간은 0 이상이어야 합니다")
    val watchedDuration: Int?,

    @field:NotNull(message = "완료율은 필수입니다")
    @field:Min(value = 0, message = "완료율은 0 이상이어야 합니다")
    @field:Max(value = 100, message = "완료율은 100 이하여야 합니다")
    val completionRate: Int?,

    @field:NotNull(message = "스킵 여부는 필수입니다")
    val skipped: Boolean?
)
