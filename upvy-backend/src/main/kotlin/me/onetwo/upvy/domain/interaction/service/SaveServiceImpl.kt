package me.onetwo.upvy.domain.interaction.service

import me.onetwo.upvy.domain.analytics.dto.InteractionType
import me.onetwo.upvy.domain.analytics.event.UserInteractionEvent
import me.onetwo.upvy.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.upvy.domain.analytics.service.ContentInteractionService
import me.onetwo.upvy.domain.content.dto.ContentResponse
import me.onetwo.upvy.domain.content.repository.ContentMetadataRepository
import me.onetwo.upvy.domain.content.service.ContentService
import me.onetwo.upvy.domain.interaction.dto.SaveResponse
import me.onetwo.upvy.domain.interaction.dto.SaveStatusResponse
import me.onetwo.upvy.domain.interaction.dto.SavedContentPageResponse
import me.onetwo.upvy.domain.interaction.dto.SavedContentResponse
import me.onetwo.upvy.domain.interaction.model.UserSave
import me.onetwo.upvy.domain.interaction.repository.UserSaveRepository
import me.onetwo.upvy.infrastructure.common.dto.CursorPageRequest
import me.onetwo.upvy.infrastructure.common.dto.CursorPageResponse
import me.onetwo.upvy.infrastructure.event.ReactiveEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 저장 서비스 구현체
 *
 * ## 처리 흐름
 * 1. user_saves 저장 (트랜잭션)
 * 2. content_interactions.save_count 증가 (메인 체인, 즉시 반영)
 * 3. UserInteractionEvent 발행 (협업 필터링용, 비동기)
 * 4. 응답 반환
 *
 * @property userSaveRepository 사용자 저장 레포지토리
 * @property contentInteractionService 콘텐츠 인터랙션 서비스
 * @property contentInteractionRepository 콘텐츠 인터랙션 레포지토리
 * @property contentMetadataRepository 콘텐츠 메타데이터 레포지토리
 * @property contentService 콘텐츠 서비스
 * @property eventPublisher Reactive 이벤트 발행자
 */
@Service
class SaveServiceImpl(
    private val userSaveRepository: UserSaveRepository,
    private val contentInteractionService: ContentInteractionService,
    private val contentInteractionRepository: ContentInteractionRepository,
    private val contentMetadataRepository: ContentMetadataRepository,
    private val contentService: ContentService,
    private val eventPublisher: ReactiveEventPublisher
) : SaveService {

    @Transactional
    override fun saveContent(userId: UUID, contentId: UUID): Mono<SaveResponse> {
        logger.debug("Saving content: userId={}, contentId={}", userId, contentId)

        return userSaveRepository.exists(userId, contentId)
            .flatMap { exists ->
                if (exists) {
                    logger.debug("Content already saved: userId={}, contentId={}", userId, contentId)
                    getSaveResponse(contentId, true)
                } else {
                    userSaveRepository.save(userId, contentId)
                        .flatMap {
                            // 카운트 증가를 메인 체인에 포함 ← 즉시 반영
                            logger.debug("Incrementing save count for contentId={}", contentId)
                            contentInteractionService.incrementSaveCount(contentId)
                        }
                        .doOnSuccess {
                            logger.debug("Publishing UserInteractionEvent: userId={}, contentId={}", userId, contentId)
                            // 협업 필터링만 이벤트로 처리 (실패해도 OK)
                            eventPublisher.publish(
                                UserInteractionEvent(
                                    userId = userId,
                                    contentId = contentId,
                                    interactionType = InteractionType.SAVE
                                )
                            )
                        }
                        .then(getSaveResponse(contentId, true))  // ← 카운트 항상 정확!
                }
            }
            .doOnSuccess { logger.debug("Content saved successfully: userId={}, contentId={}", userId, contentId) }
    }

    @Transactional
    override fun unsaveContent(userId: UUID, contentId: UUID): Mono<SaveResponse> {
        logger.debug("Unsaving content: userId={}, contentId={}", userId, contentId)

        return userSaveRepository.exists(userId, contentId)
            .flatMap { exists ->
                if (!exists) {
                    logger.debug("Content not saved: userId={}, contentId={}", userId, contentId)
                    getSaveResponse(contentId, false)
                } else {
                    userSaveRepository.delete(userId, contentId)
                        .doOnSuccess { logger.debug("Decrementing save count for contentId={}", contentId) }
                        .then(contentInteractionService.decrementSaveCount(contentId))
                        .then(getSaveResponse(contentId, false))
                }
            }
            .doOnSuccess { logger.debug("Content unsaved successfully: userId={}, contentId={}", userId, contentId) }
    }

    /**
     * 사용자의 저장된 콘텐츠 목록 조회
     *
     * @param userId 사용자 ID
     * @return 저장된 콘텐츠 목록
     */
    override fun getSavedContents(userId: UUID): Flux<SavedContentResponse> {
        logger.debug("Getting saved contents: userId={}", userId)

        return userSaveRepository.findByUserId(userId)
            .collectList()
            .flatMapMany { userSaves ->
                if (userSaves.isEmpty()) {
                    return@flatMapMany Flux.empty()
                }

                val contentIds = userSaves.map { it.contentId }.toSet()

                contentMetadataRepository.findContentInfosByContentIds(contentIds)
                    .flatMapMany { contentInfoMap ->
                        Flux.fromIterable(userSaves).mapNotNull { userSave ->
                            contentInfoMap[userSave.contentId]?.let { (title, thumbnailUrl) ->
                                SavedContentResponse(
                                    contentId = userSave.contentId.toString(),
                                    title = title,
                                    thumbnailUrl = thumbnailUrl,
                                    savedAt = userSave.createdAt.toString()
                                )
                            }
                        }
                    }
            }
    }

    /**
     * 사용자의 저장된 콘텐츠 목록을 커서 기반 페이징으로 조회
     *
     * ContentResponse 형식으로 반환하여 다른 콘텐츠 조회 API와 일관성을 유지합니다.
     *
     * @param userId 사용자 ID
     * @param pageRequest 커서 페이지 요청
     * @return 저장된 콘텐츠 페이지 응답
     */
    override fun getSavedContentsWithCursor(
        userId: UUID,
        pageRequest: CursorPageRequest
    ): Mono<SavedContentPageResponse> {
        logger.debug("Getting saved contents with cursor: userId={}, cursor={}, limit={}", userId, pageRequest.cursor, pageRequest.limit)

        val cursor = pageRequest.cursor?.toLongOrNull()
        val limit = pageRequest.limit

        return userSaveRepository.findByUserIdWithCursor(userId, cursor, limit + 1)
            .collectList()
            .flatMap { userSaves ->
                if (userSaves.isEmpty()) {
                    return@flatMap Mono.just(CursorPageResponse.empty())
                }

                processSavedContents(userSaves, userId)
                    .map { savedContentData ->
                        buildPageResponse(savedContentData, limit)
                    }
            }
    }

    /**
     * 저장 상태 조회
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 저장 상태 응답
     */
    override fun getSaveStatus(userId: UUID, contentId: UUID): Mono<SaveStatusResponse> {
        logger.debug("Getting save status: userId={}, contentId={}", userId, contentId)

        return userSaveRepository.exists(userId, contentId)
            .map { isSaved ->
                SaveStatusResponse(
                    contentId = contentId.toString(),
                    isSaved = isSaved
                )
            }
            .doOnSuccess { response ->
                logger.debug("Save status retrieved: userId={}, contentId={}, isSaved={}", userId, contentId, response.isSaved)
            }
    }

    /**
     * 저장된 콘텐츠 데이터 처리
     *
     * @param userSaves 사용자 저장 목록
     * @param userId 사용자 ID
     * @return 저장된 콘텐츠 데이터
     */
    private fun processSavedContents(
        userSaves: List<UserSave>,
        userId: UUID
    ): Mono<SavedContentData> {
        val contentIdToSaveMap = userSaves.associateBy { it.contentId }
        val contentIds = userSaves.map { it.contentId }

        return contentService.getContentsByIds(contentIds, userId)
            .collectList()
            .map { contentResponses ->
                val contentResponseMap = contentResponses.associateBy { UUID.fromString(it.id) }
                val orderedResponses = contentIds.mapNotNull { contentId ->
                    contentResponseMap[contentId]
                }

                SavedContentData(
                    contentIdToSaveMap = contentIdToSaveMap,
                    orderedResponses = orderedResponses
                )
            }
    }

    /**
     * 페이지 응답 생성
     *
     * @param savedContentData 저장된 콘텐츠 데이터
     * @param limit 페이지 크기
     * @return 저장된 콘텐츠 페이지 응답
     */
    private fun buildPageResponse(
        savedContentData: SavedContentData,
        limit: Int
    ): SavedContentPageResponse {
        return CursorPageResponse.of(
            content = savedContentData.orderedResponses,
            limit = limit,
            getCursor = { response ->
                savedContentData.contentIdToSaveMap[UUID.fromString(response.id)]?.id.toString()
            }
        )
    }

    private fun getSaveResponse(contentId: UUID, isSaved: Boolean): Mono<SaveResponse> {
        return contentInteractionRepository.getSaveCount(contentId)
            .map { saveCount ->
                SaveResponse(
                    contentId = contentId.toString(),
                    saveCount = saveCount,
                    isSaved = isSaved
                )
            }
    }

    /**
     * 저장된 콘텐츠 처리를 위한 데이터 클래스
     *
     * @property contentIdToSaveMap 콘텐츠 ID를 키로 하는 UserSave Map (커서 추출용)
     * @property orderedResponses 저장 순서대로 정렬된 ContentResponse 목록
     */
    private data class SavedContentData(
        val contentIdToSaveMap: Map<UUID, UserSave>,
        val orderedResponses: List<ContentResponse>
    )

    companion object {
        private val logger = LoggerFactory.getLogger(SaveServiceImpl::class.java)
    }
}
