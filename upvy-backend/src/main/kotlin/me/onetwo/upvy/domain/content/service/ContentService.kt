package me.onetwo.upvy.domain.content.service

import me.onetwo.upvy.domain.content.dto.ContentCreateRequest
import me.onetwo.upvy.domain.content.dto.ContentPageResponse
import me.onetwo.upvy.domain.content.dto.ContentResponse
import me.onetwo.upvy.domain.content.dto.ContentUpdateRequest
import me.onetwo.upvy.domain.content.dto.ContentUploadUrlRequest
import me.onetwo.upvy.domain.content.dto.ContentUploadUrlResponse
import me.onetwo.upvy.infrastructure.common.dto.CursorPageRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 콘텐츠 서비스 인터페이스
 *
 * 콘텐츠 생성, 조회, 수정, 삭제 등 비즈니스 로직을 담당합니다.
 */
interface ContentService {

    /**
     * S3 Presigned URL을 생성합니다.
     *
     * 클라이언트가 S3에 직접 파일을 업로드할 수 있도록 Presigned URL을 발급합니다.
     *
     * @param userId 사용자 ID
     * @param request Presigned URL 요청 정보
     * @return Presigned URL 정보를 담은 Mono
     */
    fun generateUploadUrl(
        userId: UUID,
        request: ContentUploadUrlRequest
    ): Mono<ContentUploadUrlResponse>

    /**
     * 콘텐츠를 생성합니다.
     *
     * S3 업로드 완료 후 콘텐츠 메타데이터를 등록합니다.
     *
     * @param userId 사용자 ID
     * @param request 콘텐츠 생성 요청
     * @return 생성된 콘텐츠 정보를 담은 Mono
     */
    fun createContent(
        userId: UUID,
        request: ContentCreateRequest
    ): Mono<ContentResponse>

    /**
     * 콘텐츠를 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @param userId 사용자 ID (선택, 인터랙션 정보에 사용자별 상태를 포함하려면 제공)
     * @return 콘텐츠 정보를 담은 Mono
     */
    fun getContent(contentId: UUID, userId: UUID? = null): Mono<ContentResponse>

    /**
     * 크리에이터의 콘텐츠 목록을 조회합니다.
     *
     * @param creatorId 크리에이터 ID
     * @param userId 사용자 ID (선택, 인터랙션 정보에 사용자별 상태를 포함하려면 제공)
     * @return 콘텐츠 목록을 담은 Flux
     */
    fun getContentsByCreator(creatorId: UUID, userId: UUID? = null): Flux<ContentResponse>

    /**
     * 크리에이터의 콘텐츠 목록을 커서 기반 페이징으로 조회합니다.
     *
     * @param creatorId 크리에이터 ID
     * @param userId 사용자 ID (선택, 인터랙션 정보에 사용자별 상태를 포함하려면 제공)
     * @param pageRequest 커서 페이지 요청
     * @return 콘텐츠 페이지 응답을 담은 Mono
     */
    fun getContentsByCreatorWithCursor(
        creatorId: UUID,
        userId: UUID?,
        pageRequest: CursorPageRequest
    ): Mono<ContentPageResponse>

    /**
     * 여러 콘텐츠 ID로 콘텐츠 목록을 배치 조회합니다.
     *
     * 저장한 콘텐츠 목록 등에서 N+1 문제 없이 조회하기 위해 사용됩니다.
     *
     * @param contentIds 조회할 콘텐츠 ID 목록
     * @param userId 사용자 ID (선택, 인터랙션 정보에 사용자별 상태를 포함하려면 제공)
     * @return 콘텐츠 목록을 담은 Flux (순서는 DB 순서대로)
     */
    fun getContentsByIds(contentIds: List<UUID>, userId: UUID? = null): Flux<ContentResponse>

    /**
     * 콘텐츠를 수정합니다.
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @param request 수정 요청
     * @return 수정된 콘텐츠 정보를 담은 Mono
     */
    fun updateContent(
        userId: UUID,
        contentId: UUID,
        request: ContentUpdateRequest
    ): Mono<ContentResponse>

    /**
     * 콘텐츠를 삭제합니다.
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 삭제 완료를 알리는 Mono<Void>
     */
    fun deleteContent(
        userId: UUID,
        contentId: UUID
    ): Mono<Void>

    /**
     * 크리에이터의 총 콘텐츠 개수를 조회합니다.
     *
     * 프로필 화면에서 사용자의 총 콘텐츠 개수를 표시하기 위해 사용됩니다.
     * Soft Delete되지 않은 콘텐츠만 카운트합니다.
     *
     * @param creatorId 크리에이터 ID
     * @return 콘텐츠 개수를 담은 Mono
     */
    fun countContentsByCreatorId(creatorId: UUID): Mono<Long>
}
