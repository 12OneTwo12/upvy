package me.onetwo.growsnap.domain.user.controller

import jakarta.validation.Valid
import me.onetwo.growsnap.domain.content.dto.ContentResponse
import me.onetwo.growsnap.domain.content.service.ContentService
import me.onetwo.growsnap.domain.user.dto.ImageUploadResponse
import me.onetwo.growsnap.domain.user.dto.NicknameCheckResponse
import me.onetwo.growsnap.domain.user.dto.UpdateProfileRequest
import me.onetwo.growsnap.domain.user.dto.UserProfileResponse
import me.onetwo.growsnap.domain.user.service.UserProfileService
import me.onetwo.growsnap.infrastructure.security.util.toUserId
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

/**
 * 사용자 프로필 관리 Controller
 *
 * 프로필 조회, 수정, 닉네임 중복 확인, 이미지 업로드 API를 제공합니다.
 *
 * **참고**: 프로필은 OAuth 회원가입 시 자동 생성됩니다.
 */
@RestController
@RequestMapping(ApiPaths.API_V1_PROFILES)
class UserProfileController(
    private val userProfileService: UserProfileService,
    private val contentService: ContentService
) {

    /**
     * 내 프로필 조회
     *
     * @param principal 인증된 사용자 Principal (Spring Security에서 자동 주입)
     * @return 프로필 정보
     */
    @GetMapping("/me")
    fun getMyProfile(
        principal: Mono<Principal>
    ): Mono<ResponseEntity<UserProfileResponse>> {
        return principal
            .toUserId()
            .flatMap { userId -> userProfileService.getProfileByUserId(userId) }
            .map { profile -> ResponseEntity.ok(UserProfileResponse.from(profile)) }
    }

    /**
     * 사용자 ID로 프로필 조회
     *
     * @param targetUserId 조회할 사용자 ID
     * @return 프로필 정보
     */
    @GetMapping("/{targetUserId}")
    fun getProfileByUserId(
        @PathVariable targetUserId: UUID
    ): Mono<ResponseEntity<UserProfileResponse>> {
        return userProfileService.getProfileByUserId(targetUserId)
            .map { profile -> ResponseEntity.ok(UserProfileResponse.from(profile)) }
    }

    /**
     * 닉네임으로 프로필 조회
     *
     * @param nickname 조회할 닉네임
     * @return 프로필 정보
     */
    @GetMapping("/nickname/{nickname}")
    fun getProfileByNickname(
        @PathVariable nickname: String
    ): Mono<ResponseEntity<UserProfileResponse>> {
        return userProfileService.getProfileByNickname(nickname)
            .map { profile -> ResponseEntity.ok(UserProfileResponse.from(profile)) }
    }

    /**
     * 프로필 수정
     *
     * @param principal 인증된 사용자 Principal (Spring Security에서 자동 주입)
     * @param request 프로필 수정 요청
     * @return 수정된 프로필 정보
     */
    @PatchMapping
    fun updateProfile(
        principal: Mono<Principal>,
        @Valid @RequestBody request: UpdateProfileRequest
    ): Mono<ResponseEntity<UserProfileResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                userProfileService.updateProfile(
                    userId = userId,
                    nickname = request.nickname,
                    profileImageUrl = request.profileImageUrl,
                    bio = request.bio
                )
            }
            .map { profile -> ResponseEntity.ok(UserProfileResponse.from(profile)) }
    }

    /**
     * 닉네임 중복 확인
     *
     * @param nickname 확인할 닉네임
     * @return 중복 여부
     */
    @GetMapping("/check/nickname/{nickname}")
    fun checkNickname(
        @PathVariable nickname: String
    ): Mono<ResponseEntity<NicknameCheckResponse>> {
        return userProfileService.isNicknameDuplicated(nickname)
            .map { isDuplicated -> ResponseEntity.ok(NicknameCheckResponse(nickname, isDuplicated)) }
    }

    /**
     * 프로필 이미지 업로드
     *
     * 이미지는 리사이징된 후 S3에 저장되며, 업로드된 이미지 URL을 반환합니다.
     *
     * @param principal 인증된 사용자 Principal (Spring Security에서 자동 주입)
     * @param filePart 업로드할 이미지 파일
     * @return 업로드된 이미지 URL
     */
    @PostMapping("/image")
    fun uploadProfileImage(
        principal: Mono<Principal>,
        @RequestPart("file") filePart: Mono<FilePart>
    ): Mono<ResponseEntity<ImageUploadResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                filePart.flatMap { file ->
                    userProfileService.uploadProfileImage(userId, file)
                }
            }
            .map { imageUrl ->
                ResponseEntity.status(HttpStatus.CREATED).body(ImageUploadResponse(imageUrl))
            }
    }

    /**
     * 사용자의 콘텐츠 목록 조회
     *
     * 프로필 화면의 콘텐츠 그리드에서 사용됩니다.
     * 인증된 사용자의 경우 인터랙션 정보에 사용자별 상태 (isLiked, isSaved)가 포함됩니다.
     *
     * @param targetUserId 조회할 사용자 ID
     * @param principal 인증된 사용자 Principal (선택, 인터랙션 정보용)
     * @return 콘텐츠 목록
     */
    @GetMapping("/{targetUserId}/contents")
    fun getUserContents(
        @PathVariable targetUserId: UUID,
        principal: Mono<Principal>?
    ): Mono<ResponseEntity<List<ContentResponse>>> {
        // Principal에서 userId 추출 (비인증 사용자는 null)
        val userIdMono = principal?.toUserId() ?: Mono.empty()

        return userIdMono
            .defaultIfEmpty(UUID(0, 0)) // 비인증 사용자용 기본 UUID
            .flatMapMany { userId ->
                contentService.getContentsByCreator(targetUserId, userId)
            }
            .collectList()
            .map { contents -> ResponseEntity.ok(contents) }
    }
}
