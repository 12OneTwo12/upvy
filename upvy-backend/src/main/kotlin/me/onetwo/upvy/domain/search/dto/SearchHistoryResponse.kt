package me.onetwo.upvy.domain.search.dto

import me.onetwo.upvy.domain.search.model.SearchType

/**
 * 검색 기록 응답 DTO
 *
 * 사용자의 최근 검색어를 반환합니다.
 *
 * @property keywords 최근 검색어 목록
 */
data class SearchHistoryResponse(
    val keywords: List<SearchHistoryItem>
)

/**
 * 검색 기록 항목
 *
 * @property keyword 검색 키워드
 * @property searchType 검색 타입 (CONTENT, USER)
 */
data class SearchHistoryItem(
    val keyword: String,
    val searchType: SearchType
)
