package me.onetwo.growsnap.domain.user.model

import java.time.Instant
import java.util.UUID

/**
 * 사용자 상태 변경 이력
 *
 * 탈퇴, 재가입, 정지 등 모든 상태 변경을 추적합니다.
 *
 * @property id 이력 ID
 * @property userId 사용자 ID
 * @property previousStatus 이전 상태 (최초 가입 시 null)
 * @property newStatus 새로운 상태
 * @property reason 변경 사유
 * @property metadata 추가 메타데이터 (JSON)
 * @property changedAt 변경 시각 (UTC Instant)
 * @property changedBy 변경한 사용자 ID (본인 또는 관리자)
 */
data class UserStatusHistory(
    val id: Long? = null,
    val userId: UUID,
    val previousStatus: UserStatus?,
    val newStatus: UserStatus,
    val reason: String? = null,
    val metadata: String? = null,  // JSON string
    val changedAt: Instant = Instant.now(),
    val changedBy: String? = null
)
