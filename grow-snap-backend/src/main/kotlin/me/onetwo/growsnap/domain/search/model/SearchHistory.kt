package me.onetwo.growsnap.domain.search.model

import java.time.Instant
import java.util.UUID

/**
 * 검색 기록 엔티티
 *
 * 사용자의 검색 기록을 저장합니다.
 * 최근 검색어 조회 및 검색 통계 분석에 활용됩니다.
 *
 * @property id 검색 기록 ID
 * @property userId 검색한 사용자 ID
 * @property keyword 검색 키워드
 * @property searchType 검색 타입 (CONTENT, USER)
 * @property createdAt 생성 시각 (UTC Instant)
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각 (UTC Instant)
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (UTC Instant, Soft Delete)
 */
data class SearchHistory(
    val id: Long? = null,
    val userId: UUID,
    val keyword: String,
    val searchType: SearchType,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
)
