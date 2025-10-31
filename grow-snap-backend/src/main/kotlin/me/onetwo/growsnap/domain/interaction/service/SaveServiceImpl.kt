package me.onetwo.growsnap.domain.interaction.service

import me.onetwo.growsnap.domain.analytics.dto.InteractionEventRequest
import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.analytics.service.AnalyticsService
import me.onetwo.growsnap.domain.content.repository.ContentMetadataRepository
import me.onetwo.growsnap.domain.interaction.dto.SaveResponse
import me.onetwo.growsnap.domain.interaction.dto.SaveStatusResponse
import me.onetwo.growsnap.domain.interaction.dto.SavedContentResponse
import me.onetwo.growsnap.domain.interaction.repository.UserSaveRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 저장 서비스 구현체
 *
 * 콘텐츠 저장 비즈니스 로직을 처리합니다.
 *
 * ### 처리 흐름
 * 1. 저장 상태 변경 (user_saves 테이블)
 * 2. AnalyticsService를 통한 이벤트 발행
 *    - 카운터 증가 (content_interactions 테이블)
 *    - Spring Event 발행 (UserInteractionEvent)
 *    - user_content_interactions 테이블 저장 (협업 필터링용)
 *
 * @property userSaveRepository 사용자 저장 레포지토리
 * @property analyticsService Analytics 서비스 (이벤트 발행)
 * @property contentInteractionRepository 콘텐츠 인터랙션 레포지토리
 * @property contentMetadataRepository 콘텐츠 메타데이터 레포지토리
 */
@Service
class SaveServiceImpl(
    private val userSaveRepository: UserSaveRepository,
    private val analyticsService: AnalyticsService,
    private val contentInteractionRepository: ContentInteractionRepository,
    private val contentMetadataRepository: ContentMetadataRepository
) : SaveService {

    override fun saveContent(userId: UUID, contentId: UUID): Mono<SaveResponse> {
        logger.debug("Saving content: userId={}, contentId={}", userId, contentId)

        return Mono.fromCallable { userSaveRepository.exists(userId, contentId) }
            .flatMap { exists ->
                if (exists) {
                    logger.debug("Content already saved: userId={}, contentId={}", userId, contentId)
                    getSaveResponse(contentId, true)
                } else {
                    Mono.fromCallable { userSaveRepository.save(userId, contentId) }
                        .then(
                            analyticsService.trackInteractionEvent(
                                userId,
                                InteractionEventRequest(
                                    contentId = contentId,
                                    interactionType = InteractionType.SAVE
                                )
                            )
                        )
                        .then(getSaveResponse(contentId, true))
                }
            }
            .doOnSuccess { logger.debug("Content saved successfully: userId={}, contentId={}", userId, contentId) }
    }

    override fun unsaveContent(userId: UUID, contentId: UUID): Mono<SaveResponse> {
        logger.debug("Unsaving content: userId={}, contentId={}", userId, contentId)

        return Mono.fromCallable { userSaveRepository.exists(userId, contentId) }
            .flatMap { exists ->
                if (!exists) {
                    logger.debug("Content not saved: userId={}, contentId={}", userId, contentId)
                    getSaveResponse(contentId, false)
                } else {
                    Mono.fromCallable { userSaveRepository.delete(userId, contentId) }
                        .then(contentInteractionRepository.decrementSaveCount(contentId))
                        .then(getSaveResponse(contentId, false))
                }
            }
            .doOnSuccess { logger.debug("Content unsaved successfully: userId={}, contentId={}", userId, contentId) }
    }

    /**
     * 사용자의 저장된 콘텐츠 목록 조회
     *
     * N+1 쿼리 문제를 방지하기 위해 모든 콘텐츠 정보를 한 번에 조회합니다.
     *
     * ### 처리 흐름
     * 1. user_saves 테이블에서 사용자의 저장 목록 조회
     * 2. 모든 contentId를 수집
     * 3. ContentMetadataRepository로 콘텐츠 정보 일괄 조회 (N+1 문제 해결)
     * 4. 메모리에서 데이터 조합
     *
     * @param userId 사용자 ID
     * @return 저장된 콘텐츠 목록
     */
    override fun getSavedContents(userId: UUID): Flux<SavedContentResponse> {
        logger.debug("Getting saved contents: userId={}", userId)

        return Mono.fromCallable { userSaveRepository.findByUserId(userId) }
            .flatMapMany { userSaves ->
                if (userSaves.isEmpty()) {
                    return@flatMapMany Flux.empty()
                }

                val contentIds = userSaves.map { it.contentId }.toSet()

                Mono.fromCallable {
                    contentMetadataRepository.findContentInfosByContentIds(contentIds)
                }.flatMapMany { contentInfoMap ->
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
     * 특정 콘텐츠에 대한 사용자의 저장 상태를 확인합니다.
     *
     * ### 처리 흐름
     * 1. UserSaveRepository.exists()로 저장 여부 확인
     * 2. SaveStatusResponse 반환
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 저장 상태 응답
     */
    override fun getSaveStatus(userId: UUID, contentId: UUID): Mono<SaveStatusResponse> {
        logger.debug("Getting save status: userId={}, contentId={}", userId, contentId)

        return Mono.fromCallable { userSaveRepository.exists(userId, contentId) }
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
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
