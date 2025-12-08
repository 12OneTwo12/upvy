package me.onetwo.upvy.domain.interaction.service

import me.onetwo.upvy.domain.interaction.dto.SaveResponse
import me.onetwo.upvy.domain.interaction.dto.SaveStatusResponse
import me.onetwo.upvy.domain.interaction.dto.SavedContentPageResponse
import me.onetwo.upvy.domain.interaction.dto.SavedContentResponse
import me.onetwo.upvy.infrastructure.common.dto.CursorPageRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 저장 서비스 인터페이스
 *
 * 콘텐츠 저장 기능을 제공합니다.
 */
interface SaveService {

    /**
     * 콘텐츠 저장
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 저장 응답
     */
    fun saveContent(userId: UUID, contentId: UUID): Mono<SaveResponse>

    /**
     * 콘텐츠 저장 취소
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 저장 응답
     */
    fun unsaveContent(userId: UUID, contentId: UUID): Mono<SaveResponse>

    /**
     * 저장한 콘텐츠 목록 조회
     *
     * @param userId 사용자 ID
     * @return 저장된 콘텐츠 목록
     */
    fun getSavedContents(userId: UUID): Flux<SavedContentResponse>

    /**
     * 저장한 콘텐츠 목록을 커서 기반 페이징으로 조회
     *
     * @param userId 사용자 ID
     * @param pageRequest 커서 페이지 요청
     * @return 저장된 콘텐츠 페이지 응답
     */
    fun getSavedContentsWithCursor(
        userId: UUID,
        pageRequest: CursorPageRequest
    ): Mono<SavedContentPageResponse>

    /**
     * 저장 상태 조회
     *
     * 특정 콘텐츠에 대한 사용자의 저장 상태를 확인합니다.
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 저장 상태 응답
     */
    fun getSaveStatus(userId: UUID, contentId: UUID): Mono<SaveStatusResponse>
}
