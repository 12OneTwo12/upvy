package me.onetwo.upvy.domain.analytics.dto

import java.time.Instant
import java.util.UUID

/**
 * 시청 기록 상세 정보
 *
 * 사용자 선호도 점수 계산에 필요한 시청 기록 데이터를 담는 DTO입니다.
 *
 * @property contentId 콘텐츠 ID
 * @property watchedDuration 시청한 시간 (초)
 * @property completionRate 완료율 (0-100)
 * @property watchedAt 시청 시각 (시간 감쇠 계산용)
 */
data class ViewHistoryDetail(
    val contentId: UUID,
    val watchedDuration: Int,
    val completionRate: Int,
    val watchedAt: Instant
)
