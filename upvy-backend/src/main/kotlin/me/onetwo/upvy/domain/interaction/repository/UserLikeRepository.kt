package me.onetwo.upvy.domain.interaction.repository

import me.onetwo.upvy.domain.interaction.model.UserLike
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 사용자 좋아요 레포지토리 인터페이스 (Reactive)
 *
 * 사용자의 콘텐츠 좋아요 상태를 관리합니다.
 * JOOQ를 사용하여 SQL을 생성하고, R2DBC를 통해 실행합니다.
 * 모든 메서드는 Mono를 반환하여 완전한 Non-blocking 처리를 지원합니다.
 */
interface UserLikeRepository {

    /**
     * 좋아요 생성
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 생성된 좋아요 (Mono)
     */
    fun save(userId: UUID, contentId: UUID): Mono<UserLike>

    /**
     * 좋아요 삭제 (Soft Delete)
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 삭제 완료 시그널 (Mono<Void>)
     */
    fun delete(userId: UUID, contentId: UUID): Mono<Void>

    /**
     * 좋아요 존재 여부 확인
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 좋아요 여부 (true: 좋아요, false: 좋아요 안 함)
     */
    fun exists(userId: UUID, contentId: UUID): Mono<Boolean>

    /**
     * 사용자의 좋아요 조회
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 좋아요 (없으면 empty Mono)
     */
    fun findByUserIdAndContentId(userId: UUID, contentId: UUID): Mono<UserLike>
}
