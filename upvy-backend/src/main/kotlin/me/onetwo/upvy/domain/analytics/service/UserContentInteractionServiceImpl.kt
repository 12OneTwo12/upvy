package me.onetwo.upvy.domain.analytics.service

import me.onetwo.upvy.domain.analytics.dto.InteractionType
import me.onetwo.upvy.domain.analytics.repository.UserContentInteractionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 사용자별 콘텐츠 인터랙션 서비스 구현체
 *
 * 협업 필터링을 위한 사용자별 콘텐츠 인터랙션 데이터를 관리합니다.
 *
 * @property userContentInteractionRepository 사용자별 콘텐츠 인터랙션 레포지토리
 */
@Service
class UserContentInteractionServiceImpl(
    private val userContentInteractionRepository: UserContentInteractionRepository
) : UserContentInteractionService {

    private val logger = LoggerFactory.getLogger(UserContentInteractionServiceImpl::class.java)

    /**
     * 사용자 인터랙션을 저장합니다 (Reactive).
     *
     * 완전한 Reactive 방식으로 저장합니다.
     * 에러가 발생해도 로그만 남기고 예외를 전파하지 않습니다.
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @param interactionType 인터랙션 타입
     * @return Mono<Void> 저장 완료 신호
     */
    override fun saveUserInteraction(userId: UUID, contentId: UUID, interactionType: InteractionType): Mono<Void> {
        logger.debug(
            "Saving user interaction: userId={}, contentId={}, type={}",
            userId,
            contentId,
            interactionType
        )

        return userContentInteractionRepository.save(userId, contentId, interactionType)
            .doOnSuccess {
                logger.debug(
                    "User interaction saved successfully: userId={}, contentId={}, type={}",
                    userId,
                    contentId,
                    interactionType
                )
            }
            .doOnError { error ->
                logger.error(
                    "Failed to save user interaction: userId={}, contentId={}, type={}",
                    userId,
                    contentId,
                    interactionType,
                    error
                )
            }
            .onErrorResume { Mono.empty() }
    }
}
