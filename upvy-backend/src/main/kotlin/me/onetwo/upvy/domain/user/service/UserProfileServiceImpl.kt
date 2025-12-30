package me.onetwo.upvy.domain.user.service

import me.onetwo.upvy.domain.content.repository.ContentRepository
import me.onetwo.upvy.domain.user.dto.UserProfileWithContentCountResponse
import me.onetwo.upvy.domain.user.exception.DuplicateNicknameException
import me.onetwo.upvy.domain.user.exception.UserProfileNotFoundException
import me.onetwo.upvy.domain.user.model.UserProfile
import me.onetwo.upvy.domain.user.repository.UserProfileRepository
import me.onetwo.upvy.infrastructure.storage.ImageUploadService
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 프로필과 콘텐츠 개수를 함께 담는 데이터 클래스
 *
 * @property profile 사용자 프로필
 * @property contentCount 콘텐츠 개수
 */
private data class ProfileWithCount(
    val profile: UserProfile,
    val contentCount: Long
)

/**
 * 사용자 프로필 관리 서비스 구현체
 *
 * 프로필 생성, 수정, 조회 등의 비즈니스 로직을 처리합니다.
 *
 * @property userProfileRepository 사용자 프로필 Repository
 * @property userService 사용자 서비스 (사용자 존재 여부 확인용)
 * @property imageUploadService 이미지 업로드 서비스
 * @property contentRepository 콘텐츠 레포지토리 (콘텐츠 개수 조회용)
 */
@Service
@Transactional(readOnly = true)
class UserProfileServiceImpl(
    private val userProfileRepository: UserProfileRepository,
    private val userService: UserService,
    private val imageUploadService: ImageUploadService,
    private val contentRepository: ContentRepository
) : UserProfileService {

    /**
     * 사용자 프로필 생성
     *
     * @param userId 사용자 ID
     * @param nickname 닉네임 (고유해야 함)
     * @param profileImageUrl 프로필 이미지 URL (선택 사항)
     * @param bio 자기소개 (선택 사항)
     * @return 생성된 프로필 정보
     * @throws DuplicateNicknameException 닉네임이 중복된 경우
     */
    @Transactional
    override fun createProfile(
        userId: UUID,
        nickname: String,
        profileImageUrl: String?,
        bio: String?
    ): Mono<UserProfile> {
        // 사용자 존재 여부 확인
        return userService.getUserById(userId)
            .flatMap {
                // Parallel check: userId와 nickname 존재 여부를 동시에 확인하여 성능 향상
                Mono.zip(
                    userProfileRepository.existsByUserId(userId),
                    userProfileRepository.existsByNickname(nickname)
                ) { userExists, nicknameExists ->
                    userExists to nicknameExists
                }.flatMap { (userExists, nicknameExists) ->

                    if (userExists) {
                        Mono.error(IllegalStateException("이미 프로필이 존재합니다. User ID: $userId"))
                    } else if (nicknameExists) {
                        Mono.error(DuplicateNicknameException(nickname))
                    } else {
                        val profile = UserProfile(
                            userId = userId,
                            nickname = nickname,
                            profileImageUrl = profileImageUrl,
                            bio = bio
                        )
                        userProfileRepository.save(profile)
                    }
                }
            }
    }

    /**
     * 사용자 ID로 프로필 조회
     *
     * @param userId 사용자 ID
     * @return 프로필 정보
     * @throws UserProfileNotFoundException 프로필을 찾을 수 없는 경우
     */
    override fun getProfileByUserId(userId: UUID): Mono<UserProfile> {
        return userProfileRepository.findByUserId(userId)
            .switchIfEmpty(Mono.error(UserProfileNotFoundException("프로필을 찾을 수 없습니다. User ID: $userId")))
    }

    /**
     * 닉네임으로 프로필 조회
     *
     * @param nickname 닉네임
     * @return 프로필 정보
     * @throws UserProfileNotFoundException 프로필을 찾을 수 없는 경우
     */
    override fun getProfileByNickname(nickname: String): Mono<UserProfile> {
        return userProfileRepository.findByNickname(nickname)
            .switchIfEmpty(Mono.error(UserProfileNotFoundException("프로필을 찾을 수 없습니다. Nickname: $nickname")))
    }

    /**
     * 닉네임 중복 확인
     *
     * @param nickname 확인할 닉네임
     * @return 중복 여부 (true: 중복, false: 사용 가능)
     */
    override fun isNicknameDuplicated(nickname: String): Mono<Boolean> {
        return userProfileRepository.existsByNickname(nickname)
    }

    /**
     * 프로필 업데이트
     *
     * 닉네임, 프로필 이미지, 자기소개를 업데이트합니다.
     *
     * @param userId 사용자 ID
     * @param nickname 새 닉네임 (선택 사항)
     * @param profileImageUrl 새 프로필 이미지 URL (선택 사항)
     * @param bio 새 자기소개 (선택 사항)
     * @return 업데이트된 프로필 정보
     * @throws UserProfileNotFoundException 프로필을 찾을 수 없는 경우
     * @throws DuplicateNicknameException 닉네임이 중복된 경우
     */
    @Transactional
    override fun updateProfile(
        userId: UUID,
        nickname: String?,
        profileImageUrl: String?,
        bio: String?
    ): Mono<UserProfile> {
        return getProfileByUserId(userId)
            .flatMap { currentProfile ->
                // 닉네임 변경 시 중복 확인
                val nicknameCheck = if (nickname != null && nickname != currentProfile.nickname) {
                    userProfileRepository.existsByNickname(nickname)
                        .flatMap { exists ->
                            if (exists) {
                                Mono.error<Boolean>(DuplicateNicknameException(nickname))
                            } else {
                                Mono.just(false)
                            }
                        }
                } else {
                    Mono.just(false)
                }

                nicknameCheck.flatMap {
                    val updatedProfile = currentProfile.copy(
                        nickname = nickname ?: currentProfile.nickname,
                        profileImageUrl = profileImageUrl ?: currentProfile.profileImageUrl,
                        bio = bio ?: currentProfile.bio
                    )
                    userProfileRepository.update(updatedProfile)
                }
            }
    }

    /**
     * 팔로워 수 증가
     *
     * @param userId 사용자 ID
     * @return 업데이트된 프로필 정보
     * @throws UserProfileNotFoundException 프로필을 찾을 수 없는 경우
     */
    @Transactional
    override fun incrementFollowerCount(userId: UUID): Mono<UserProfile> {
        return getProfileByUserId(userId)
            .flatMap { profile ->
                val updatedProfile = profile.copy(followerCount = profile.followerCount + 1)
                userProfileRepository.update(updatedProfile)
            }
    }

    /**
     * 팔로워 수 감소
     *
     * @param userId 사용자 ID
     * @return 업데이트된 프로필 정보
     * @throws UserProfileNotFoundException 프로필을 찾을 수 없는 경우
     */
    @Transactional
    override fun decrementFollowerCount(userId: UUID): Mono<UserProfile> {
        return getProfileByUserId(userId)
            .flatMap { profile ->
                val updatedProfile = profile.copy(
                    followerCount = maxOf(0, profile.followerCount - 1)
                )
                userProfileRepository.update(updatedProfile)
            }
    }

    /**
     * 팔로잉 수 증가
     *
     * @param userId 사용자 ID
     * @return 업데이트된 프로필 정보
     * @throws UserProfileNotFoundException 프로필을 찾을 수 없는 경우
     */
    @Transactional
    override fun incrementFollowingCount(userId: UUID): Mono<UserProfile> {
        return getProfileByUserId(userId)
            .flatMap { profile ->
                val updatedProfile = profile.copy(followingCount = profile.followingCount + 1)
                userProfileRepository.update(updatedProfile)
            }
    }

    /**
     * 팔로잉 수 감소
     *
     * @param userId 사용자 ID
     * @return 업데이트된 프로필 정보
     * @throws UserProfileNotFoundException 프로필을 찾을 수 없는 경우
     */
    @Transactional
    override fun decrementFollowingCount(userId: UUID): Mono<UserProfile> {
        return getProfileByUserId(userId)
            .flatMap { profile ->
                val updatedProfile = profile.copy(
                    followingCount = maxOf(0, profile.followingCount - 1)
                )
                userProfileRepository.update(updatedProfile)
            }
    }

    /**
     * 프로필 이미지 업로드
     *
     * FilePart를 받아서 이미지를 처리하고 S3에 업로드합니다.
     *
     * ### 처리 흐름
     * 1. FilePart에서 바이트 배열과 Content-Type 추출
     * 2. ImageUploadService를 통해 S3 업로드
     * 3. 업로드된 이미지 URL 반환
     *
     * @param userId 사용자 ID
     * @param filePart 업로드할 이미지 파일
     * @return 업로드된 이미지 URL을 담은 Mono
     * @throws IllegalArgumentException 이미지 유효성 검증 실패 시
     */
    override fun uploadProfileImage(userId: UUID, filePart: FilePart): Mono<String> {
        // FilePart에서 바이트 배열과 Content-Type 추출
        return filePart.content()
            .map { dataBuffer ->
                val bytes = ByteArray(dataBuffer.readableByteCount())
                dataBuffer.read(bytes)
                bytes
            }
            .reduce { acc, bytes -> acc + bytes }
            .flatMap { imageBytes ->
                val contentType = filePart.headers().contentType?.toString() ?: "application/octet-stream"
                imageUploadService.uploadProfileImage(userId, imageBytes, contentType)
            }
    }

    /**
     * 사용자 ID로 프로필과 콘텐츠 개수를 함께 조회합니다.
     *
     * 프로필 정보와 콘텐츠 개수를 병렬로 조회하여 UserProfileWithContentCountResponse DTO로 반환합니다.
     * 프로필 화면에서 사용됩니다.
     *
     * @param userId 사용자 ID
     * @return 콘텐츠 개수가 포함된 프로필 응답 DTO
     * @throws UserProfileNotFoundException 프로필을 찾을 수 없는 경우
     */
    override fun getProfileWithContentCount(userId: UUID): Mono<UserProfileWithContentCountResponse> {
        return Mono.zip(
            getProfileByUserId(userId),
            contentRepository.countByCreatorId(userId)
        ) { profile, contentCount ->
            ProfileWithCount(profile, contentCount)
        }.map { data ->
            UserProfileWithContentCountResponse.from(data.profile, data.contentCount)
        }
    }
}
