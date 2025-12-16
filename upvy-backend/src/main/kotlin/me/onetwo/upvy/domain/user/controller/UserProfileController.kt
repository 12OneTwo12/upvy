package me.onetwo.upvy.domain.user.controller

import jakarta.validation.Valid
import me.onetwo.upvy.domain.content.dto.ContentPageResponse
import me.onetwo.upvy.domain.content.service.ContentService
import me.onetwo.upvy.domain.user.dto.CreateProfileRequest
import me.onetwo.upvy.domain.user.dto.ImageUploadResponse
import me.onetwo.upvy.domain.user.dto.NicknameCheckResponse
import me.onetwo.upvy.domain.user.dto.UpdateProfileRequest
import me.onetwo.upvy.domain.user.dto.UserProfileResponse
import me.onetwo.upvy.domain.user.dto.UserProfileWithContentCountResponse
import me.onetwo.upvy.domain.user.service.UserProfileService
import me.onetwo.upvy.infrastructure.security.util.toUserId
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.common.dto.CursorPageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

/**
 * 사용자 프로필 관리 Controller
 *
 * 프로필 생성, 조회, 수정, 닉네임 중복 확인, 이미지 업로드 API를 제공합니다.
 *
 * **참고**: 회원가입 시 프로필은 자동 생성되지 않으며, 사용자가 직접 생성해야 합니다.
 */
@RestController
@RequestMapping(ApiPaths.API_V1_PROFILES)
class UserProfileController(
    private val userProfileService: UserProfileService,
    private val contentService: ContentService
) {

    /**
     * 프로필 생성
     *
     * 회원가입 후 최초 1회 프로필을 생성합니다.
     * 프로필은 사용자당 1개만 생성 가능하며, 이미 존재하는 경우 409 Conflict를 반환합니다.
     *
     * @param principal 인증된 사용자 Principal (Spring Security에서 자동 주입)
     * @param request 프로필 생성 요청 (닉네임 필수, 프로필 이미지 URL과 자기소개 선택)
     * @return 201 Created - 생성된 프로필 정보
     * @throws IllegalStateException 이미 프로필이 존재하는 경우 (409 Conflict)
     * @throws DuplicateNicknameException 닉네임이 중복된 경우 (409 Conflict)
     */
    @PostMapping
    fun createProfile(
        principal: Mono<Principal>,
        @Valid @RequestBody request: CreateProfileRequest
    ): Mono<ResponseEntity<UserProfileResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                userProfileService.createProfile(
                    userId = userId,
                    nickname = request.nickname,
                    profileImageUrl = request.profileImageUrl,
                    bio = request.bio
                )
            }
            .map { profile -> ResponseEntity.status(HttpStatus.CREATED).body(UserProfileResponse.from(profile)) }
    }

    /**
     * 내 프로필 조회
     *
     * @param principal 인증된 사용자 Principal (Spring Security에서 자동 주입)
     * @return 프로필 정보 (콘텐츠 개수 포함)
     */
    @GetMapping("/me")
    fun getMyProfile(
        principal: Mono<Principal>
    ): Mono<ResponseEntity<UserProfileWithContentCountResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                userProfileService.getProfileWithContentCount(userId)
            }
            .map { ResponseEntity.ok(it) }
    }

    /**
     * 사용자 ID로 프로필 조회
     *
     * @param targetUserId 조회할 사용자 ID
     * @return 프로필 정보 (콘텐츠 개수 포함)
     */
    @GetMapping("/{targetUserId}")
    fun getProfileByUserId(
        @PathVariable targetUserId: UUID
    ): Mono<ResponseEntity<UserProfileWithContentCountResponse>> {
        return userProfileService.getProfileWithContentCount(targetUserId)
            .map { ResponseEntity.ok(it) }
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
     * 사용자의 콘텐츠 목록을 커서 기반 페이징으로 조회합니다.
     *
     * 프로필 화면의 콘텐츠 그리드에서 사용됩니다.
     * 인증된 사용자의 경우 인터랙션 정보에 사용자별 상태 (isLiked, isSaved)가 포함됩니다.
     *
     * @param targetUserId 조회할 사용자 ID
     * @param principal 인증된 사용자 Principal (선택, 인터랙션 정보용)
     * @param cursor 이전 페이지의 마지막 콘텐츠 ID (null이면 첫 페이지)
     * @param limit 페이지당 항목 수 (기본값: 20, 최대: 100)
     * @return 콘텐츠 페이지 응답
     */
    @GetMapping("/{targetUserId}/contents")
    fun getUserContents(
        @PathVariable targetUserId: UUID,
        principal: Mono<Principal>?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ): Mono<ResponseEntity<ContentPageResponse>> {
        // Principal에서 userId 추출 (비인증 사용자는 null)
        val userIdMono = principal?.toUserId() ?: Mono.empty()

        return userIdMono
            .defaultIfEmpty(UUID(0, 0)) // 비인증 사용자용 기본 UUID
            .flatMap { userId ->
                val pageRequest = CursorPageRequest(cursor = cursor, limit = limit)
                contentService.getContentsByCreatorWithCursor(targetUserId, userId, pageRequest)
            }
            .map { contents -> ResponseEntity.ok(contents) }
    }
}
