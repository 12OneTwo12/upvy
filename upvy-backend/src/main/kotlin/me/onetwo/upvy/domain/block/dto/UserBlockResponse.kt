package me.onetwo.upvy.domain.block.dto

import me.onetwo.upvy.domain.block.model.UserBlock
import java.time.Instant

/**
 * 사용자 차단 응답 DTO
 *
 * 사용자 차단 완료 후 반환되는 응답 데이터입니다.
 *
 * @property id 차단 ID
 * @property blockerId 차단한 사용자 ID
 * @property blockedId 차단된 사용자 ID
 * @property createdAt 차단 시각
 */
data class UserBlockResponse(
    val id: Long,
    val blockerId: String,
    val blockedId: String,
    val createdAt: Instant
) {
    companion object {
        /**
         * UserBlock 모델을 UserBlockResponse DTO로 변환합니다.
         *
         * @param userBlock 사용자 차단 모델
         * @return 사용자 차단 응답 DTO
         * @throws IllegalArgumentException id 또는 createdAt이 null인 경우
         */
        fun from(userBlock: UserBlock): UserBlockResponse {
            return UserBlockResponse(
                id = requireNotNull(userBlock.id) { "차단 ID는 null일 수 없습니다" },
                blockerId = userBlock.blockerId.toString(),
                blockedId = userBlock.blockedId.toString(),
                createdAt = requireNotNull(userBlock.createdAt) { "차단 생성 시각은 null일 수 없습니다" }
            )
        }
    }
}
