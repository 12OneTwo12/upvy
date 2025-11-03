package me.onetwo.growsnap.domain.interaction.service

import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.content.repository.ContentMetadataRepository
import me.onetwo.growsnap.domain.interaction.dto.SaveResponse
import me.onetwo.growsnap.domain.interaction.dto.SaveStatusResponse
import me.onetwo.growsnap.domain.interaction.dto.SavedContentResponse
import me.onetwo.growsnap.domain.interaction.event.SaveCreatedEvent
import me.onetwo.growsnap.domain.interaction.event.SaveDeletedEvent
import me.onetwo.growsnap.domain.interaction.repository.UserSaveRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

/**
 * 저장 서비스 구현체
 *
 * 콘텐츠 저장 비즈니스 로직을 처리합니다.
 *
 * ### 처리 흐름 (이벤트 기반)
 * 1. 저장 상태 변경 (user_saves 테이블)
 * 2. Spring Event 발행 (SaveCreatedEvent / SaveDeletedEvent)
 * 3. 응답 반환 (사용자는 즉시 성공 확인)
 * 4. [비동기] InteractionEventListener가 처리:
 *    - content_interactions의 save_count 증가/감소
 *    - user_content_interactions 저장 (협업 필터링용)
 *
 * ### 장점
 * - 카운트 증가 실패해도 사용자 경험에 영향 없음
 * - 빠른 응답 시간
 * - 높은 가용성
 *
 * @property userSaveRepository 사용자 저장 레포지토리
 * @property applicationEventPublisher Spring 이벤트 발행자
 * @property contentInteractionRepository 콘텐츠 인터랙션 레포지토리
 * @property contentMetadataRepository 콘텐츠 메타데이터 레포지토리
 */
@Service
@Transactional(readOnly = true)
class SaveServiceImpl(
    private val userSaveRepository: UserSaveRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val contentInteractionRepository: ContentInteractionRepository,
    private val contentMetadataRepository: ContentMetadataRepository
) : SaveService {

    @Transactional
    override fun saveContent(userId: UUID, contentId: UUID): Mono<SaveResponse> {
        logger.debug("Saving content: userId={}, contentId={}", userId, contentId)

        return Mono.fromCallable { userSaveRepository.exists(userId, contentId) }
            .flatMap { exists ->
                if (exists) {
                    logger.debug("Content already saved: userId={}, contentId={}", userId, contentId)
                    getSaveResponse(contentId, true)
                } else {
                    Mono.fromCallable { userSaveRepository.save(userId, contentId) }
                        .doOnSuccess {
                            logger.debug("Publishing SaveCreatedEvent: userId={}, contentId={}", userId, contentId)
                            applicationEventPublisher.publishEvent(
                                SaveCreatedEvent(userId, contentId)
                            )
                        }
                        .then(getSaveResponse(contentId, true))
                }
            }
            .doOnSuccess { logger.debug("Content saved successfully: userId={}, contentId={}", userId, contentId) }
    }

    @Transactional
    override fun unsaveContent(userId: UUID, contentId: UUID): Mono<SaveResponse> {
        logger.debug("Unsaving content: userId={}, contentId={}", userId, contentId)

        return Mono.fromCallable { userSaveRepository.exists(userId, contentId) }
            .flatMap { exists ->
                if (!exists) {
                    logger.debug("Content not saved: userId={}, contentId={}", userId, contentId)
                    getSaveResponse(contentId, false)
                } else {
                    Mono.fromCallable { userSaveRepository.delete(userId, contentId) }
                        .doOnSuccess {
                            logger.debug("Publishing SaveDeletedEvent: userId={}, contentId={}", userId, contentId)
                            applicationEventPublisher.publishEvent(
                                SaveDeletedEvent(userId, contentId)
                            )
                        }
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    override fun getSaveStatus(userId: UUID, contentId: UUID): Mono<SaveStatusResponse> {
        logger.debug("Getting save status: userId={}, contentId={}", userId, contentId)

        return Mono.fromCallable { userSaveRepository.exists(userId, contentId) }
            .subscribeOn(Schedulers.boundedElastic())
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
