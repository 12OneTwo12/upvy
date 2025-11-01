package me.onetwo.growsnap.domain.analytics.service

import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.repository.UserContentInteractionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
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
     * 사용자 인터랙션을 저장합니다.
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @param interactionType 인터랙션 타입
     */
    override fun saveUserInteraction(userId: UUID, contentId: UUID, interactionType: InteractionType) {
        logger.debug(
            "Saving user interaction: userId={}, contentId={}, type={}",
            userId,
            contentId,
            interactionType
        )

        userContentInteractionRepository.save(userId, contentId, interactionType)
            .subscribe(
                {
                    logger.debug(
                        "User interaction saved successfully: userId={}, contentId={}, type={}",
                        userId,
                        contentId,
                        interactionType
                    )
                },
                { error ->
                    logger.error(
                        "Failed to save user interaction: userId={}, contentId={}, type={}",
                        userId,
                        contentId,
                        interactionType,
                        error
                    )
                }
            )
    }
}
