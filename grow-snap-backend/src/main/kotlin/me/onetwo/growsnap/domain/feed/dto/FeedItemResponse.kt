package me.onetwo.growsnap.domain.feed.dto

import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.ContentType
import java.util.UUID

/**
 * 피드 아이템 응답 DTO
 *
 * 피드에 표시되는 개별 콘텐츠 정보입니다.
 * 요구사항 명세서 섹션 2.2.1의 응답 데이터를 포함합니다.
 *
 * @property contentId 콘텐츠 ID
 * @property contentType 콘텐츠 타입 (VIDEO, PHOTO)
 * @property url 콘텐츠 URL
 * @property photoUrls 사진 URL 목록 (PHOTO 타입인 경우)
 * @property thumbnailUrl 썸네일 이미지 URL
 * @property duration 비디오 길이 (초, 사진인 경우 null)
 * @property width 콘텐츠 가로 크기 (픽셀)
 * @property height 콘텐츠 세로 크기 (픽셀)
 * @property title 제목
 * @property description 설명
 * @property category 카테고리
 * @property tags 태그 목록
 * @property creator 크리에이터 정보
 * @property interactions 인터랙션 정보
 * @property subtitles 자막 정보 목록
 */
data class FeedItemResponse(
    val contentId: UUID,
    val contentType: ContentType,
    val url: String,
    val photoUrls: List<String>?,
    val thumbnailUrl: String,
    val duration: Int?,
    val width: Int,
    val height: Int,
    val title: String,
    val description: String?,
    val category: Category,
    val tags: List<String>,
    val creator: CreatorInfoResponse,
    val interactions: InteractionInfoResponse,
    val subtitles: List<SubtitleInfoResponse>
)
