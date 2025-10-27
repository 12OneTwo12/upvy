package me.onetwo.growsnap.domain.content.repository

import me.onetwo.growsnap.domain.content.model.Content
import me.onetwo.growsnap.domain.content.model.ContentMetadata
import java.util.UUID

/**
 * 콘텐츠 레포지토리 인터페이스
 *
 * 콘텐츠 데이터베이스 CRUD를 담당합니다.
 */
interface ContentRepository {

    /**
     * 콘텐츠를 저장합니다.
     *
     * @param content 저장할 콘텐츠
     * @return 저장된 콘텐츠
     */
    fun save(content: Content): Content?

    /**
     * 콘텐츠를 ID로 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 조회된 콘텐츠 (없으면 null)
     */
    fun findById(contentId: UUID): Content?

    /**
     * 크리에이터의 콘텐츠 목록을 조회합니다.
     *
     * @param creatorId 크리에이터 ID
     * @return 콘텐츠 목록
     */
    fun findByCreatorId(creatorId: UUID): List<Content>

    /**
     * 크리에이터의 콘텐츠와 메타데이터를 한 번에 조회합니다.
     *
     * JOIN을 사용하여 N+1 쿼리 문제를 방지합니다.
     *
     * @param creatorId 크리에이터 ID
     * @return 콘텐츠와 메타데이터 쌍의 목록
     */
    fun findWithMetadataByCreatorId(creatorId: UUID): List<Pair<Content, ContentMetadata>>

    /**
     * 콘텐츠를 수정합니다.
     *
     * @param content 수정할 콘텐츠
     * @return 수정 성공 여부
     */
    fun update(content: Content): Boolean

    /**
     * 콘텐츠를 삭제합니다 (Soft Delete).
     *
     * @param contentId 삭제할 콘텐츠 ID
     * @param deletedBy 삭제한 사용자 ID
     * @return 삭제 성공 여부
     */
    fun delete(contentId: UUID, deletedBy: UUID): Boolean

    /**
     * 콘텐츠 메타데이터를 저장합니다.
     *
     * @param metadata 저장할 메타데이터
     * @return 저장된 메타데이터
     */
    fun saveMetadata(metadata: ContentMetadata): ContentMetadata?

    /**
     * 콘텐츠 메타데이터를 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 조회된 메타데이터 (없으면 null)
     */
    fun findMetadataByContentId(contentId: UUID): ContentMetadata?

    /**
     * 콘텐츠 메타데이터를 수정합니다.
     *
     * @param metadata 수정할 메타데이터
     * @return 수정 성공 여부
     */
    fun updateMetadata(metadata: ContentMetadata): Boolean
}
