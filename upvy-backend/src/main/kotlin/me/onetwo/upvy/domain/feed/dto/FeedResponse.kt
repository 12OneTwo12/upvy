package me.onetwo.upvy.domain.feed.dto

import me.onetwo.upvy.infrastructure.common.dto.CursorPageResponse

/**
 * 피드 응답 DTO
 *
 * 피드 API의 응답 형식입니다.
 * 커서 기반 페이지네이션을 사용하여 무한 스크롤을 지원합니다.
 */
typealias FeedResponse = CursorPageResponse<FeedItemResponse>
