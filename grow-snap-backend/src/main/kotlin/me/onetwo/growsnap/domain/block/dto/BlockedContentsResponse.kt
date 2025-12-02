package me.onetwo.growsnap.domain.block.dto

import me.onetwo.growsnap.infrastructure.common.dto.CursorPageResponse

/**
 * 차단한 콘텐츠 목록 응답 DTO
 *
 * 차단한 콘텐츠 목록 API의 응답 형식입니다.
 * 커서 기반 페이지네이션을 사용하여 무한 스크롤을 지원합니다.
 */
typealias BlockedContentsResponse = CursorPageResponse<BlockedContentItemResponse>
