package me.onetwo.growsnap.domain.content.repository

import me.onetwo.growsnap.domain.content.dto.ContentWithMetadata
import me.onetwo.growsnap.domain.content.model.Content
import me.onetwo.growsnap.domain.content.model.ContentMetadata
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 콘텐츠 레포지토리 인터페이스 (Reactive)
 *
 * 콘텐츠 데이터베이스 CRUD를 담당합니다.
 * R2DBC를 통해 완전한 Non-blocking 처리를 지원합니다.
 */
interface ContentRepository {

    /**
     * 콘텐츠를 저장합니다.
     *
     * @param content 저장할 콘텐츠
     * @return 저장된 콘텐츠 (Mono)
     */
    fun save(content: Content): Mono<Content>

    /**
     * 콘텐츠를 ID로 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 조회된 콘텐츠 (없으면 empty Mono)
     */
    fun findById(contentId: UUID): Mono<Content>

    /**
     * 크리에이터의 콘텐츠 목록을 조회합니다.
     *
     * @param creatorId 크리에이터 ID
     * @return 콘텐츠 목록 (Flux)
     */
    fun findByCreatorId(creatorId: UUID): Flux<Content>

    /**
     * 크리에이터의 콘텐츠와 메타데이터를 한 번에 조회합니다.
     *
     * JOIN을 사용하여 N+1 쿼리 문제를 방지합니다.
     *
     * @param creatorId 크리에이터 ID
     * @return 콘텐츠와 메타데이터의 목록 (Flux)
     */
    fun findWithMetadataByCreatorId(creatorId: UUID): Flux<ContentWithMetadata>

    /**
     * 콘텐츠를 수정합니다.
     *
     * @param content 수정할 콘텐츠
     * @return 수정 성공 여부 (Mono)
     */
    fun update(content: Content): Mono<Boolean>

    /**
     * 콘텐츠를 삭제합니다 (Soft Delete).
     *
     * @param contentId 삭제할 콘텐츠 ID
     * @param deletedBy 삭제한 사용자 ID
     * @return 삭제 성공 여부 (Mono)
     */
    fun delete(contentId: UUID, deletedBy: UUID): Mono<Boolean>

    /**
     * 콘텐츠 메타데이터를 저장합니다.
     *
     * @param metadata 저장할 메타데이터
     * @return 저장된 메타데이터 (Mono)
     */
    fun saveMetadata(metadata: ContentMetadata): Mono<ContentMetadata>

    /**
     * 콘텐츠 메타데이터를 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 조회된 메타데이터 (없으면 empty Mono)
     */
    fun findMetadataByContentId(contentId: UUID): Mono<ContentMetadata>

    /**
     * 콘텐츠 메타데이터를 수정합니다.
     *
     * @param metadata 수정할 메타데이터
     * @return 수정 성공 여부 (Mono)
     */
    fun updateMetadata(metadata: ContentMetadata): Mono<Boolean>
}
