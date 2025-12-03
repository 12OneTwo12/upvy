package me.onetwo.growsnap.domain.interaction.repository

import me.onetwo.growsnap.domain.interaction.model.UserSave
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 사용자 저장 레포지토리 인터페이스 (Reactive)
 *
 * 사용자의 콘텐츠 저장 상태를 관리합니다.
 * JOOQ를 사용하여 SQL을 생성하고, R2DBC를 통해 실행합니다.
 * 모든 메서드는 Mono를 반환하여 완전한 Non-blocking 처리를 지원합니다.
 */
interface UserSaveRepository {

    /**
     * 콘텐츠 저장
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 생성된 저장 (Mono)
     */
    fun save(userId: UUID, contentId: UUID): Mono<UserSave>

    /**
     * 콘텐츠 저장 취소 (Soft Delete)
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 삭제 완료 시그널 (Mono<Void>)
     */
    fun delete(userId: UUID, contentId: UUID): Mono<Void>

    /**
     * 저장 여부 확인
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 저장 여부 (true: 저장됨, false: 저장 안 됨)
     */
    fun exists(userId: UUID, contentId: UUID): Mono<Boolean>

    /**
     * 사용자의 저장 목록 조회
     *
     * @param userId 사용자 ID
     * @return 저장 목록
     */
    fun findByUserId(userId: UUID): Flux<UserSave>

    /**
     * 사용자의 저장 목록을 커서 기반 페이징으로 조회
     *
     * created_at 기준 내림차순 정렬로 최신 저장 순서대로 조회합니다.
     *
     * @param userId 사용자 ID
     * @param cursor 이전 페이지의 마지막 저장 ID (null이면 첫 페이지)
     * @param limit 페이지당 항목 수
     * @return 저장 목록
     */
    fun findByUserIdWithCursor(
        userId: UUID,
        cursor: Long?,
        limit: Int
    ): Flux<UserSave>
}
