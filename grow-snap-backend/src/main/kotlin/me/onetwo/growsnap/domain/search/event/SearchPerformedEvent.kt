package me.onetwo.growsnap.domain.search.event

import me.onetwo.growsnap.domain.search.model.SearchType
import java.util.UUID

/**
 * 검색 수행 이벤트
 *
 * 사용자가 검색을 수행했을 때 발행되는 이벤트입니다.
 *
 * ### 이벤트 처리 흐름
 * 1. SearchService가 검색 수행
 * 2. SearchPerformedEvent 발행
 * 3. SearchEventListener가 비동기로 처리:
 *    - search_history 테이블에 검색 기록 저장
 *    - Redis trending keywords 카운트 증가 (TODO)
 *
 * @property userId 검색한 사용자 ID (인증되지 않은 경우 null)
 * @property keyword 검색 키워드
 * @property searchType 검색 타입 (CONTENT, USER)
 */
data class SearchPerformedEvent(
    val userId: UUID?,
    val keyword: String,
    val searchType: SearchType
)
