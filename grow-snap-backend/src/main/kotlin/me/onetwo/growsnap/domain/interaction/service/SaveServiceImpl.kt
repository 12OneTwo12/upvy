package me.onetwo.growsnap.domain.interaction.service

import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.event.UserInteractionEvent
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.analytics.service.ContentInteractionService
import me.onetwo.growsnap.domain.content.repository.ContentMetadataRepository
import me.onetwo.growsnap.domain.interaction.dto.SaveResponse
import me.onetwo.growsnap.domain.interaction.dto.SaveStatusResponse
import me.onetwo.growsnap.domain.interaction.dto.SavedContentResponse
import me.onetwo.growsnap.domain.interaction.repository.UserSaveRepository
import me.onetwo.growsnap.infrastructure.event.ReactiveEventPublisher
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
 * @property eventPublisher Reactive 이벤트 발행자
 */
@Service
class SaveServiceImpl(
    private val userSaveRepository: UserSaveRepository,
    private val contentInteractionService: ContentInteractionService,
    private val contentInteractionRepository: ContentInteractionRepository,
    private val contentMetadataRepository: ContentMetadataRepository,
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

    companion object {
        private val logger = LoggerFactory.getLogger(SaveServiceImpl::class.java)
    }
}
