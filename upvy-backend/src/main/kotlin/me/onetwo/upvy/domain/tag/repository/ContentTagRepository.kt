package me.onetwo.upvy.domain.tag.repository

import me.onetwo.upvy.domain.tag.model.ContentTag
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 콘텐츠-태그 관계 레포지토리 인터페이스 (Reactive)
 *
 * Content와 Tag 간의 M:N 관계 데이터베이스 CRUD를 담당합니다.
 * R2DBC를 통해 완전한 Non-blocking 처리를 지원합니다.
 */
interface ContentTagRepository {

    /**
     * 콘텐츠-태그 관계를 저장합니다.
     *
     * @param contentTag 저장할 관계
     * @return 저장된 관계 (Auto Increment ID 포함, Mono)
     */
    fun save(contentTag: ContentTag): Mono<ContentTag>

    /**
     * 콘텐츠-태그 관계 여러 개를 배치 저장합니다.
     *
     * 하나의 콘텐츠에 여러 태그를 한 번에 추가할 때 사용됩니다.
     *
     * @param contentTags 저장할 관계 목록
     * @return 저장된 관계 목록 (Flux)
     */
    fun saveAll(contentTags: List<ContentTag>): Flux<ContentTag>

    /**
     * 콘텐츠에 연결된 모든 태그 ID를 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 태그 ID 목록 (Flux)
     */
    fun findTagIdsByContentId(contentId: UUID): Flux<Long>

    /**
     * 콘텐츠에 연결된 모든 콘텐츠-태그 관계를 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 콘텐츠-태그 관계 목록 (Flux)
     */
    fun findByContentId(contentId: UUID): Flux<ContentTag>

    /**
     * 태그가 사용된 모든 콘텐츠 ID를 조회합니다.
     *
     * 태그별 콘텐츠 목록 조회 시 사용됩니다.
     *
     * @param tagId 태그 ID
     * @return 콘텐츠 ID 목록 (Flux)
     */
    fun findContentIdsByTagId(tagId: Long): Flux<UUID>

    /**
     * 특정 콘텐츠와 태그의 관계가 존재하는지 확인합니다.
     *
     * @param contentId 콘텐츠 ID
     * @param tagId 태그 ID
     * @return 존재 여부 (Mono)
     */
    fun existsByContentIdAndTagId(contentId: UUID, tagId: Long): Mono<Boolean>

    /**
     * 특정 콘텐츠와 여러 태그의 관계 중 이미 존재하는 태그 ID 목록을 반환합니다.
     *
     * N+1 문제를 방지하기 위해 여러 태그의 존재 여부를 한 번의 쿼리로 확인합니다.
     *
     * @param contentId 콘텐츠 ID
     * @param tagIds 확인할 태그 ID 목록
     * @return 이미 존재하는 태그 ID 목록 (Flux)
     */
    fun findExistingTagIds(contentId: UUID, tagIds: List<Long>): Flux<Long>

    /**
     * 콘텐츠의 모든 태그를 삭제합니다 (Soft Delete).
     *
     * 콘텐츠 삭제 시 연결된 모든 태그 관계도 함께 삭제됩니다.
     *
     * @param contentId 콘텐츠 ID
     * @param deletedBy 삭제한 사용자 ID
     * @return 삭제된 관계 개수 (Mono)
     */
    fun deleteByContentId(contentId: UUID, deletedBy: String): Mono<Int>

    /**
     * 특정 콘텐츠와 태그의 관계를 삭제합니다 (Soft Delete).
     *
     * @param contentId 콘텐츠 ID
     * @param tagId 태그 ID
     * @param deletedBy 삭제한 사용자 ID
     * @return 삭제 성공 여부 (Mono)
     */
    fun deleteByContentIdAndTagId(contentId: UUID, tagId: Long, deletedBy: String): Mono<Boolean>
}
