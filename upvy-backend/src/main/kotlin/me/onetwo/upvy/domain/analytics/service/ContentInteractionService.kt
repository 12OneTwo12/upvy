package me.onetwo.upvy.domain.analytics.service

import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 콘텐츠 인터랙션 서비스 인터페이스
 *
 * 콘텐츠 인터랙션 생성 및 관리를 담당합니다.
 */
interface ContentInteractionService {

    /**
     * 콘텐츠 인터랙션 생성
     *
     * 새로운 콘텐츠가 생성되면 인터랙션 레코드를 초기화합니다.
     *
     * @param contentId 콘텐츠 ID
     * @param creatorId 생성자 ID
     */
    fun createContentInteraction(contentId: UUID, creatorId: UUID): Mono<Void>

    /**
     * 좋아요 카운트 증가
     *
     * @param contentId 콘텐츠 ID
     * @return Void
     */
    fun incrementLikeCount(contentId: UUID): Mono<Void>

    /**
     * 좋아요 카운트 감소
     *
     * @param contentId 콘텐츠 ID
     * @return Void
     */
    fun decrementLikeCount(contentId: UUID): Mono<Void>

    /**
     * 저장 카운트 증가
     *
     * @param contentId 콘텐츠 ID
     * @return Void
     */
    fun incrementSaveCount(contentId: UUID): Mono<Void>

    /**
     * 저장 카운트 감소
     *
     * @param contentId 콘텐츠 ID
     * @return Void
     */
    fun decrementSaveCount(contentId: UUID): Mono<Void>

    /**
     * 댓글 카운트 증가
     *
     * @param contentId 콘텐츠 ID
     * @return Void
     */
    fun incrementCommentCount(contentId: UUID): Mono<Void>

    /**
     * 댓글 카운트 감소
     *
     * @param contentId 콘텐츠 ID
     * @return Void
     */
    fun decrementCommentCount(contentId: UUID): Mono<Void>

    /**
     * 공유 카운트 증가
     *
     * @param contentId 콘텐츠 ID
     * @return Void
     */
    fun incrementShareCount(contentId: UUID): Mono<Void>

    /**
     * 공유 카운트 감소
     *
     * @param contentId 콘텐츠 ID
     * @return Void
     */
    fun decrementShareCount(contentId: UUID): Mono<Void>
}
