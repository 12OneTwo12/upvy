package me.onetwo.upvy.domain.search.dto

import me.onetwo.upvy.domain.feed.dto.FeedItemResponse
import me.onetwo.upvy.infrastructure.common.dto.CursorPageResponse

/**
 * 콘텐츠 검색 응답 DTO
 *
 * 콘텐츠 검색 결과를 FeedItemResponse 형식으로 반환합니다.
 * 기존 Feed API와 동일한 형식을 사용하여 프론트엔드에서
 * FeedItem 컴포넌트를 재사용할 수 있습니다.
 *
 * ## 응답 구조
 * - content: 콘텐츠 검색 결과 목록 (FeedItemResponse[])
 * - nextCursor: 다음 페이지 커서 (없으면 null)
 * - hasNext: 다음 페이지 존재 여부
 * - count: 현재 페이지 항목 수
 *
 * ## 커서 기반 페이지네이션
 * - 무한 스크롤 지원
 * - 커서는 마지막 항목의 contentId 사용
 * - hasNext가 true인 경우 nextCursor로 다음 페이지 요청
 */
typealias ContentSearchResponse = CursorPageResponse<FeedItemResponse>
