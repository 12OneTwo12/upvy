package me.onetwo.growsnap.domain.notification.service

import me.onetwo.growsnap.domain.notification.dto.PushTokenResponse
import me.onetwo.growsnap.domain.notification.dto.RegisterPushTokenRequest
import me.onetwo.growsnap.domain.notification.model.PushToken
import me.onetwo.growsnap.domain.notification.repository.PushTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 푸시 토큰 서비스 구현체
 *
 * 다양한 푸시 제공자(Expo, FCM, APNs)의 토큰 등록, 삭제 관련 비즈니스 로직을 처리합니다.
 *
 * @property pushTokenRepository 푸시 토큰 Repository
 */
@Service
@Transactional(readOnly = true)
class PushTokenServiceImpl(
    private val pushTokenRepository: PushTokenRepository
) : PushTokenService {

    private val logger = LoggerFactory.getLogger(PushTokenServiceImpl::class.java)

    /**
     * 푸시 토큰 등록/갱신
     */
    @Transactional
    override fun registerToken(userId: UUID, request: RegisterPushTokenRequest): Mono<PushTokenResponse> {
        logger.info(
            "Registering push token: userId={}, deviceId={}, deviceType={}, provider={}",
            userId,
            request.deviceId,
            request.deviceType,
            request.provider
        )

        val token = PushToken(
            userId = userId,
            token = request.token,
            deviceId = request.deviceId,
            deviceType = request.deviceType,
            provider = request.provider
        )

        return pushTokenRepository.saveOrUpdate(token)
            .map { savedToken -> PushTokenResponse.from(savedToken) }
            .doOnSuccess {
                logger.info(
                    "Push token registered successfully: userId={}, deviceId={}, provider={}",
                    userId,
                    request.deviceId,
                    request.provider
                )
            }
    }

    /**
     * 특정 디바이스의 푸시 토큰 삭제
     */
    @Transactional
    override fun deleteToken(userId: UUID, deviceId: String): Mono<Void> {
        logger.info("Deleting push token: userId={}, deviceId={}", userId, deviceId)

        return pushTokenRepository.deleteByUserIdAndDeviceId(userId, deviceId)
            .doOnSuccess {
                logger.info("Push token deleted: userId={}, deviceId={}", userId, deviceId)
            }
    }

    /**
     * 모든 디바이스의 푸시 토큰 삭제
     */
    @Transactional
    override fun deleteAllTokens(userId: UUID): Mono<Void> {
        logger.info("Deleting all push tokens: userId={}", userId)

        return pushTokenRepository.deleteAllByUserId(userId)
            .doOnSuccess {
                logger.info("All push tokens deleted: userId={}", userId)
            }
    }
}
