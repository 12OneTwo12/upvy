package me.onetwo.upvy.domain.block.service

import me.onetwo.upvy.domain.block.dto.BlockedContentsResponse
import me.onetwo.upvy.domain.block.dto.ContentBlockResponse
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 콘텐츠 차단 서비스 인터페이스
 *
 * 콘텐츠 차단 관련 비즈니스 로직을 처리합니다.
 * Reactive Mono/Flux를 반환하여 Non-blocking 처리를 지원합니다.
 */
interface ContentBlockService {

    /**
     * 콘텐츠 차단
     *
     * ### 비즈니스 규칙
     * - 로그인한 사용자만 차단 가능 (Controller에서 Principal 체크)
     * - 자신의 콘텐츠도 차단 가능
     * - 중복 차단 방지 (DuplicateContentBlockException)
     *
     * ### 예외
     * - DuplicateContentBlockException: 이미 차단한 콘텐츠인 경우
     *
     * @param userId 차단한 사용자 ID
     * @param contentId 차단된 콘텐츠 ID
     * @return 콘텐츠 차단 응답
     */
    fun blockContent(
        userId: UUID,
        contentId: UUID
    ): Mono<ContentBlockResponse>

    /**
     * 콘텐츠 차단 해제
     *
     * ### 비즈니스 규칙
     * - 로그인한 사용자만 차단 해제 가능 (Controller에서 Principal 체크)
     * - 차단하지 않은 콘텐츠는 해제할 수 없음 (ContentBlockNotFoundException)
     *
     * ### 예외
     * - ContentBlockNotFoundException: 차단하지 않은 콘텐츠인 경우
     *
     * @param userId 차단한 사용자 ID
     * @param contentId 차단된 콘텐츠 ID
     * @return 빈 Mono
     */
    fun unblockContent(
        userId: UUID,
        contentId: UUID
    ): Mono<Void>

    /**
     * 차단한 콘텐츠 목록 조회
     *
     * ### 비즈니스 규칙
     * - 로그인한 사용자만 조회 가능 (Controller에서 Principal 체크)
     * - 커서 기반 페이지네이션 지원 (block_id 기준)
     * - 삭제되지 않은 차단만 조회 (deleted_at IS NULL)
     *
     * @param userId 차단한 사용자 ID
     * @param cursor 커서 (차단 ID)
     * @param limit 조회 개수
     * @return 차단한 콘텐츠 목록
     */
    fun getBlockedContents(
        userId: UUID,
        cursor: String?,
        limit: Int
    ): Mono<BlockedContentsResponse>
}
