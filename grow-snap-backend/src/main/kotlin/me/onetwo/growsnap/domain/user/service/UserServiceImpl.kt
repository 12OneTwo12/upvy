package me.onetwo.growsnap.domain.user.service

import me.onetwo.growsnap.domain.user.event.UserCreatedEvent
import me.onetwo.growsnap.domain.user.exception.UserNotFoundException
import me.onetwo.growsnap.infrastructure.event.ReactiveEventPublisher
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserProfile
import me.onetwo.growsnap.domain.user.model.UserStatus
import me.onetwo.growsnap.domain.user.model.UserStatusHistory
import me.onetwo.growsnap.domain.user.repository.FollowRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.domain.user.repository.UserStatusHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 사용자 관리 서비스 구현체
 *
 * 사용자 인증, 등록, 조회 등의 비즈니스 로직을 처리합니다.
 *
 * @property userRepository 사용자 Repository
 * @property userProfileRepository 사용자 프로필 Repository
 * @property followRepository 팔로우 Repository
 * @property userStatusHistoryRepository 사용자 상태 변경 이력 Repository
 * @property eventPublisher 이벤트 Publisher
 */
@Service
@Transactional(readOnly = true)
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
    private val followRepository: FollowRepository,
    private val userStatusHistoryRepository: UserStatusHistoryRepository,
    private val eventPublisher: ReactiveEventPublisher
) : UserService {

    private val logger = LoggerFactory.getLogger(UserServiceImpl::class.java)

    companion object {
        /** 닉네임 최대 길이 */
        private const val MAX_NICKNAME_LENGTH = 20

        /** 닉네임 suffix 길이 (UUID 기반) */
        private const val NICKNAME_SUFFIX_LENGTH = 6

        /** 닉네임 생성 최대 재시도 횟수 */
        private const val MAX_NICKNAME_GENERATION_ATTEMPTS = 10

        /** 닉네임 base 부분 최대 길이 (suffix와 언더스코어를 위한 공간 확보) */
        private const val MAX_NICKNAME_BASE_LENGTH = 14

        /** Fallback 닉네임 생성 시 UUID 길이 */
        private const val FALLBACK_NICKNAME_UUID_LENGTH = 10
    }

    /**
     * OAuth 제공자와 Provider ID로 사용자 조회 또는 생성
     *
     * 오케스트레이션만 담당하며, 실제 로직은 private 메소드에 위임합니다.
     *
     * @param email 사용자 이메일
     * @param provider OAuth 제공자 (GOOGLE, NAVER, KAKAO 등)
     * @param providerId OAuth 제공자에서 제공한 사용자 고유 ID
     * @param name OAuth 제공자에서 제공한 사용자 이름 (닉네임으로 사용)
     * @param profileImageUrl OAuth 제공자에서 제공한 프로필 이미지 URL
     * @return 조회되거나 생성된 사용자
     */
    @Transactional
    override fun findOrCreateOAuthUser(
        email: String,
        provider: OAuthProvider,
        providerId: String,
        name: String?,
        profileImageUrl: String?
    ): Mono<User> {
        return userRepository.findByEmailIncludingDeleted(email)
            .flatMap { user -> handleExistingUser(user, provider, providerId, name, profileImageUrl) }
            .switchIfEmpty(Mono.defer { createNewUser(email, provider, providerId, name, profileImageUrl) })
    }

    /**
     * 기존 사용자 처리
     *
     * 사용자 상태에 따라 적절한 처리를 수행합니다.
     */
    private fun handleExistingUser(
        user: User,
        provider: OAuthProvider,
        providerId: String,
        name: String?,
        profileImageUrl: String?
    ): Mono<User> {
        return when {
            user.isDeleted() -> restoreDeletedAccount(user, provider, providerId, name, profileImageUrl)
            user.hasProviderChanged(provider, providerId) -> updateProviderIfNeeded(user, provider, providerId)
            else -> {
                logger.info("Existing active user found: userId=${user.id}, email=${user.email}, provider=$provider")
                Mono.just(user)
            }
        }
    }

    /**
     * 탈퇴한 계정 복원
     *
     * 1. Provider 정보 업데이트 (다른 OAuth로 재가입 가능)
     * 2. 사용자 상태를 ACTIVE로 변경 (이력 기록)
     * 3. 새로운 프로필 생성 (이전 프로필은 audit trail로만 보관)
     *
     * 참고: 탈퇴한 사용자가 재가입하면 완전히 새로 시작합니다.
     * 이전 프로필은 soft delete 상태로 audit trail에만 남기고 복원하지 않습니다.
     */
    private fun restoreDeletedAccount(
        user: User,
        provider: OAuthProvider,
        providerId: String,
        name: String?,
        profileImageUrl: String?
    ): Mono<User> {
        logger.info("Re-registering deleted account: userId=${user.id}, email=${user.email}, provider=$provider")

        return updateProviderIfNeeded(user, provider, providerId)
            .flatMap { updatedUser ->
                changeUserStatus(
                    userId = updatedUser.id!!,
                    newStatus = UserStatus.ACTIVE,
                    reason = "User re-registration via OAuth",
                    changedBy = updatedUser.id
                )
            }
            .flatMap { restoredUser ->
                // 탈퇴 후 재가입은 항상 새 프로필 생성 (이전 프로필은 복원하지 않음)
                logger.info("Creating fresh profile for re-registered user: userId=${restoredUser.id}")
                createProfileForNewUser(restoredUser, name ?: restoredUser.email.substringBefore("@"), profileImageUrl)
                    .doOnNext { logger.info("New profile created for re-registered user: userId=${restoredUser.id}") }
                    .thenReturn(restoredUser)
            }
            .doOnNext { logger.info("Account restored with fresh profile: userId=${it.id}, email=${it.email}, provider=$provider") }
    }

    /**
     * Provider 정보 업데이트 (필요한 경우만)
     *
     * 다른 OAuth 제공자로 로그인 시 Provider 정보를 업데이트합니다.
     */
    private fun updateProviderIfNeeded(
        user: User,
        provider: OAuthProvider,
        providerId: String
    ): Mono<User> {
        if (!user.hasProviderChanged(provider, providerId)) {
            return Mono.just(user)
        }

        logger.info("Updating provider info: userId=${user.id}, oldProvider=${user.provider}, newProvider=$provider")
        return userRepository.updateProvider(user.id!!, provider, providerId)
    }

    /**
     * 사용자 상태 변경 + 이력 기록
     *
     * 상태 변경과 동시에 user_status_history에 이력을 기록합니다.
     */
    private fun changeUserStatus(
        userId: UUID,
        newStatus: UserStatus,
        reason: String,
        changedBy: UUID
    ): Mono<User> {
        return userRepository.findByIdIncludingDeleted(userId)
            .flatMap { user ->
                val history = UserStatusHistory(
                    userId = userId,
                    previousStatus = user.status,
                    newStatus = newStatus,
                    reason = reason,
                    changedBy = changedBy.toString()
                )

                Mono.zip(
                    userRepository.updateStatus(userId, newStatus, changedBy),
                    userStatusHistoryRepository.save(history)
                ).map { tuple ->
                    logger.info("User status changed: userId=$userId, ${user.status} → $newStatus")
                    tuple.t1
                }
            }
    }

    /**
     * 신규 사용자 생성
     *
     * 1. 사용자 생성
     * 2. 상태 이력 기록 (최초 가입)
     * 3. 프로필 자동 생성
     * 4. UserCreatedEvent 발행 (알림 설정 등 비동기 처리)
     */
    private fun createNewUser(
        email: String,
        provider: OAuthProvider,
        providerId: String,
        name: String?,
        profileImageUrl: String?
    ): Mono<User> {
        logger.info("Creating new user: email=$email, provider=$provider")

        val newUser = User(
            email = email,
            provider = provider,
            providerId = providerId,
            status = UserStatus.ACTIVE
        )

        return userRepository.save(newUser)
            .doOnNext { logger.info("New user created: userId=${it.id}, email=$email") }
            .flatMap { savedUser ->
                // 최초 가입 이력 기록
                val history = UserStatusHistory(
                    userId = savedUser.id!!,
                    previousStatus = null,
                    newStatus = UserStatus.ACTIVE,
                    reason = "Initial signup via $provider OAuth",
                    changedBy = savedUser.id.toString()
                )

                Mono.zip(
                    createProfileForNewUser(savedUser, name ?: email.substringBefore("@"), profileImageUrl),
                    userStatusHistoryRepository.save(history)
                ).map { tuple -> tuple.t1 }
            }
            .doOnSuccess { savedUser ->
                savedUser.id?.let { userId ->
                    // Non-Critical Path: 알림 설정 등 후속 처리를 위해 이벤트 발행
                    eventPublisher.publish(UserCreatedEvent(userId = userId))
                    logger.debug("UserCreatedEvent published: userId={}", userId)
                } ?: logger.error("Cannot publish UserCreatedEvent: user ID is null after saving")
            }
    }

    /**
     * 신규 사용자 프로필 자동 생성
     *
     * OAuth 이름을 기반으로 고유한 닉네임을 생성하고 프로필을 생성합니다.
     */
    private fun createProfileForNewUser(
        user: User,
        baseName: String,
        profileImageUrl: String?
    ): Mono<User> {
        return generateUniqueNickname(baseName)
            .flatMap { nickname ->
                val profile = UserProfile(
                    userId = user.id!!,
                    nickname = nickname,
                    profileImageUrl = profileImageUrl,
                    bio = null
                )
                userProfileRepository.save(profile)
                    .doOnNext { logger.info("Auto-created profile: userId=${user.id}, nickname=$nickname") }
                    .thenReturn(user)
            }
    }

    /**
     * User 확장 - Provider 변경 여부 확인
     */
    private fun User.hasProviderChanged(provider: OAuthProvider, providerId: String): Boolean {
        return this.provider != provider || this.providerId != providerId
    }

    /**
     * 고유한 닉네임 생성
     *
     * 닉네임이 중복되면 6자리 랜덤 suffix를 추가합니다.
     * 예: "John" -> "John_a1b2c3"
     *
     * @param baseName 기본 닉네임
     * @return 고유한 닉네임
     */
    private fun generateUniqueNickname(baseName: String): Mono<String> {
        val initialNickname = baseName.take(MAX_NICKNAME_LENGTH)

        fun tryNickname(nickname: String, attempts: Int): Mono<String> {
            if (attempts >= MAX_NICKNAME_GENERATION_ATTEMPTS) {
                // 만약 MAX_NICKNAME_GENERATION_ATTEMPTS번 시도해도 중복이면 UUID 사용
                val fallbackNickname = "user_${UUID.randomUUID().toString().replace("-", "").take(FALLBACK_NICKNAME_UUID_LENGTH)}"
                logger.warn("Failed to generate unique nickname after $MAX_NICKNAME_GENERATION_ATTEMPTS attempts, using UUID: $fallbackNickname")
                return Mono.just(fallbackNickname)
            }

            return userProfileRepository.existsByNickname(nickname)
                .flatMap { exists ->
                    if (exists) {
                        val suffix = UUID.randomUUID().toString().replace("-", "").take(NICKNAME_SUFFIX_LENGTH)
                        val newNickname = "${baseName.take(MAX_NICKNAME_BASE_LENGTH)}_$suffix"
                        tryNickname(newNickname, attempts + 1)
                    } else {
                        Mono.just(nickname)
                    }
                }
        }

        return tryNickname(initialNickname, 0)
    }

    /**
     * 사용자 ID로 조회
     *
     * @param userId 사용자 ID
     * @return 사용자 정보
     * @throws UserNotFoundException 사용자를 찾을 수 없는 경우
     */
    override fun getUserById(userId: UUID): Mono<User> {
        return userRepository.findById(userId)
            .switchIfEmpty(Mono.error(UserNotFoundException("사용자를 찾을 수 없습니다. ID: $userId")))
    }

    /**
     * 이메일로 사용자 조회
     *
     * @param email 이메일
     * @return 사용자 정보
     * @throws UserNotFoundException 사용자를 찾을 수 없는 경우
     */
    override fun getUserByEmail(email: String): Mono<User> {
        return userRepository.findByEmail(email)
            .switchIfEmpty(Mono.error(UserNotFoundException("사용자를 찾을 수 없습니다. Email: $email")))
    }

    /**
     * 사용자 이메일 중복 확인
     *
     * @param email 확인할 이메일
     * @return 중복 여부 (true: 중복, false: 사용 가능)
     */
    override fun isEmailDuplicated(email: String): Mono<Boolean> {
        return userRepository.findByEmail(email)
            .map { true }
            .defaultIfEmpty(false)
    }

    /**
     * 사용자 회원 탈퇴 (Soft Delete)
     *
     * 1. 사용자 상태를 DELETED로 변경 (이력 기록)
     * 2. 프로필, 팔로우 관계를 soft delete 처리
     *
     * @param userId 탈퇴할 사용자 ID
     * @throws UserNotFoundException 사용자를 찾을 수 없는 경우
     */
    @Transactional
    override fun withdrawUser(userId: UUID): Mono<Void> {
        return getUserById(userId)
            .doOnNext { user -> logger.info("Starting user withdrawal: userId=$userId, email=${user.email}") }
            .flatMap { user ->
                // 사용자 상태를 DELETED로 변경 + 이력 기록
                changeUserStatus(
                    userId = userId,
                    newStatus = UserStatus.DELETED,
                    reason = "User requested account deletion",
                    changedBy = userId
                )
            }
            .flatMap {
                // 프로필, 팔로우 관계를 병렬로 soft delete
                Mono.zip(
                    userProfileRepository.softDelete(userId, userId)
                        .doOnSuccess { logger.info("User profile soft deleted: userId=$userId") },
                    followRepository.softDeleteAllByUserId(userId, userId)
                        .doOnSuccess { logger.info("All follow relationships soft deleted: userId=$userId") }
                )
            }
            .doOnSuccess { logger.info("User withdrawal completed: userId=$userId") }
            .then()
    }
}
