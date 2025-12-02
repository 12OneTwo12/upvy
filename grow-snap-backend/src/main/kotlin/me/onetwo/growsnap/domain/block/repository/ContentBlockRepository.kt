package me.onetwo.growsnap.domain.block.repository

import me.onetwo.growsnap.domain.block.dto.BlockedContentItemResponse
import me.onetwo.growsnap.domain.block.model.ContentBlock
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 콘텐츠 차단 레포지토리 인터페이스 (Reactive)
 *
 * 사용자가 차단한 콘텐츠 정보를 관리합니다.
 * JOOQ를 사용하여 SQL을 생성하고, R2DBC를 통해 실행합니다.
 * 모든 메서드는 Mono/Flux를 반환하여 완전한 Non-blocking 처리를 지원합니다.
 */
interface ContentBlockRepository {

    /**
     * 콘텐츠 차단 생성
     *
     * @param userId 차단한 사용자 ID
     * @param contentId 차단된 콘텐츠 ID
     * @return 생성된 콘텐츠 차단 (Mono)
     */
    fun save(
        userId: UUID,
        contentId: UUID
    ): Mono<ContentBlock>

    /**
     * 콘텐츠 차단 존재 여부 확인
     *
     * 동일한 사용자가 동일한 콘텐츠를 이미 차단했는지 확인합니다.
     * 삭제되지 않은 차단만 확인합니다 (deleted_at IS NULL).
     *
     * @param userId 차단한 사용자 ID
     * @param contentId 차단된 콘텐츠 ID
     * @return 차단 여부 (true: 이미 차단함, false: 차단하지 않음)
     */
    fun exists(
        userId: UUID,
        contentId: UUID
    ): Mono<Boolean>

    /**
     * 콘텐츠 차단 조회
     *
     * @param userId 차단한 사용자 ID
     * @param contentId 차단된 콘텐츠 ID
     * @return 콘텐츠 차단 (없으면 empty Mono)
     */
    fun findByUserIdAndContentId(
        userId: UUID,
        contentId: UUID
    ): Mono<ContentBlock>

    /**
     * 콘텐츠 차단 삭제 (Soft Delete)
     *
     * @param userId 차단한 사용자 ID
     * @param contentId 차단된 콘텐츠 ID
     * @return 삭제 성공 여부 (Mono)
     */
    fun delete(
        userId: UUID,
        contentId: UUID
    ): Mono<Void>

    /**
     * 차단한 콘텐츠 목록 조회 (커서 기반 페이지네이션)
     *
     * @param userId 차단한 사용자 ID
     * @param cursor 커서 (차단 ID)
     * @param limit 조회 개수
     * @return 차단한 콘텐츠 목록 (Flux)
     */
    fun findBlockedContentsByUserId(
        userId: UUID,
        cursor: Long?,
        limit: Int
    ): Flux<BlockedContentItemResponse>
}
