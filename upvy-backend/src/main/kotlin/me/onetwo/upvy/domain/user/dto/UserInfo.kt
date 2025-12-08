package me.onetwo.upvy.domain.user.dto

/**
 * 사용자 기본 정보 DTO
 *
 * 댓글, 좋아요 등 다양한 컨텍스트에서 사용자 정보를 표현하기 위한 간단한 DTO입니다.
 * Pair<String, String?> 대신 명확한 필드명으로 가독성을 향상시킵니다.
 *
 * @property nickname 사용자 닉네임
 * @property profileImageUrl 사용자 프로필 이미지 URL (nullable)
 */
data class UserInfo(
    val nickname: String,
    val profileImageUrl: String?
)
