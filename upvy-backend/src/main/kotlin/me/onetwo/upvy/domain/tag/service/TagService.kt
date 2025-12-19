package me.onetwo.upvy.domain.tag.service

import me.onetwo.upvy.domain.tag.model.Tag
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 태그 서비스 인터페이스
 *
 * 태그 생성, 콘텐츠-태그 연결, 태그 인기도 관리 등 비즈니스 로직을 담당합니다.
 */
interface TagService {

    /**
     * 콘텐츠에 태그를 연결합니다.
     *
     * 1. 새로운 태그는 tags 테이블에 INSERT
     * 2. content_tags에 관계 저장
     * 3. tags.usage_count 증가
     *
     * @param contentId 콘텐츠 ID
     * @param tagNames 태그 이름 목록
     * @param userId 사용자 ID
     * @return 연결된 태그 목록 (Flux)
     */
    fun attachTagsToContent(
        contentId: UUID,
        tagNames: List<String>,
        userId: String
    ): Flux<Tag>

    /**
     * 콘텐츠에서 태그를 제거합니다.
     *
     * 1. content_tags soft delete
     * 2. tags.usage_count 감소
     *
     * @param contentId 콘텐츠 ID
     * @param userId 사용자 ID
     * @return 제거된 관계 개수 (Mono)
     */
    fun detachTagsFromContent(contentId: UUID, userId: String): Mono<Int>

    /**
     * 콘텐츠의 태그 목록을 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 태그 목록 (Flux)
     */
    fun getTagsByContentId(contentId: UUID): Flux<Tag>

    /**
     * 인기 태그를 조회합니다 (usage_count DESC).
     *
     * @param limit 최대 조회 개수
     * @return 인기 태그 목록 (Flux)
     */
    fun getPopularTags(limit: Int = 20): Flux<Tag>

    /**
     * 태그 이름을 정규화합니다.
     *
     * 검색 및 중복 체크를 위해 태그 이름을 정규화합니다.
     * - 소문자 변환
     * - 앞뒤 공백 제거
     * - # 제거 (예: "#Java" → "java")
     *
     * @param tagName 원본 태그 이름
     * @return 정규화된 태그 이름
     */
    fun normalizeTagName(tagName: String): String

    /**
     * 태그를 찾거나 생성합니다.
     *
     * 1. normalized_name으로 기존 태그 검색
     * 2. 없으면 새로 생성
     *
     * @param tagName 태그 이름
     * @param userId 사용자 ID
     * @return 태그 (Mono)
     */
    fun findOrCreateTag(tagName: String, userId: String): Mono<Tag>
}
