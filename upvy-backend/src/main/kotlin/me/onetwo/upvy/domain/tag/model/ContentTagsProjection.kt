package me.onetwo.upvy.domain.tag.model

import java.util.UUID

/**
 * 콘텐츠별 태그 목록 프로젝션
 *
 * 여러 콘텐츠의 태그를 배치 조회한 결과를 표현합니다.
 * N+1 문제를 방지하기 위한 bulk 조회 결과입니다.
 *
 * @property contentId 콘텐츠 ID
 * @property tags 태그 이름 목록
 */
data class ContentTagsProjection(
    val contentId: UUID,
    val tags: List<String>
)
