package me.onetwo.growsnap.domain.search.dto

import me.onetwo.growsnap.infrastructure.common.dto.CursorPageResponse
import java.util.UUID

/**
 * 사용자 검색 응답 DTO
 *
 * 사용자 검색 결과를 커서 기반 페이지네이션으로 반환합니다.
 *
 * ## 응답 구조
 * - content: 사용자 검색 결과 목록
 * - nextCursor: 다음 페이지 커서 (없으면 null)
 * - hasNext: 다음 페이지 존재 여부
 * - count: 현재 페이지 항목 수
 */
typealias UserSearchResponse = CursorPageResponse<UserSearchResult>

/**
 * 사용자 검색 결과 항목
 *
 * 인스타그램 스타일의 사용자 검색 결과입니다.
 *
 * @property userId 사용자 ID
 * @property nickname 닉네임
 * @property profileImageUrl 프로필 이미지 URL (없으면 null)
 * @property bio 프로필 설명 (없으면 null)
 * @property followerCount 팔로워 수
 * @property isFollowing 현재 사용자가 팔로우 중인지 여부
 */
data class UserSearchResult(
    val userId: UUID,
    val nickname: String,
    val profileImageUrl: String?,
    val bio: String?,
    val followerCount: Int,
    val isFollowing: Boolean
)
