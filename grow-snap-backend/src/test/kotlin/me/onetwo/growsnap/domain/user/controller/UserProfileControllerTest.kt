package me.onetwo.growsnap.domain.user.controller

import java.util.UUID

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.domain.user.dto.UpdateProfileRequest
import me.onetwo.growsnap.domain.user.exception.DuplicateNicknameException
import me.onetwo.growsnap.domain.user.exception.UserProfileNotFoundException
import me.onetwo.growsnap.domain.user.model.UserProfile
import me.onetwo.growsnap.domain.user.service.UserProfileService
import me.onetwo.growsnap.util.mockUser
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.PayloadDocumentation.*
import org.springframework.restdocs.request.RequestDocumentation.*
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Mono

/**
 * UserProfileController 단위 테스트 + Spring Rest Docs
 */
@WebFluxTest(controllers = [UserProfileController::class])
@Import(TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("사용자 프로필 Controller 테스트")
class UserProfileControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var userProfileService: UserProfileService

    private val testUserId = UUID.randomUUID()
    private val testProfile = UserProfile(
        id = 1L,
        userId = testUserId,
        nickname = "testnick",
        profileImageUrl = "https://example.com/profile.jpg",
        bio = "테스트 자기소개",
        followerCount = 10,
        followingCount = 5
    )

    @Test
    @DisplayName("내 프로필 조회 성공")
    fun getMyProfile_Success() {
        // Given
        every { userProfileService.getProfileByUserId(testUserId) } returns Mono.just(testProfile)
        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .get()
            .uri("${ApiPaths.API_V1_PROFILES}/me")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.nickname").isEqualTo("testnick")
            .jsonPath("$.followerCount").isEqualTo(10)
            .jsonPath("$.followingCount").isEqualTo(5)
            .consumeWith(
                document(
                    "profile-get-me",
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("id").description("프로필 ID"),
                        fieldWithPath("userId").description("사용자 ID"),
                        fieldWithPath("nickname").description("닉네임"),
                        fieldWithPath("profileImageUrl").description("프로필 이미지 URL"),
                        fieldWithPath("bio").description("자기소개"),
                        fieldWithPath("followerCount").description("팔로워 수"),
                        fieldWithPath("followingCount").description("팔로잉 수"),
                        fieldWithPath("createdAt").description("생성일시"),
                        fieldWithPath("updatedAt").description("수정일시")
                    )
                )
            )
    }

    @Test
    @DisplayName("사용자 ID로 프로필 조회 성공")
    fun getProfileByUserId_Success() {
        // Given
        val targetUserId = UUID.randomUUID()
        every { userProfileService.getProfileByUserId(targetUserId) } returns Mono.just(testProfile)
        // When & Then
        webTestClient.get()
            .uri("${ApiPaths.API_V1_PROFILES}/{targetUserId}", targetUserId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.nickname").isEqualTo("testnick")
            .consumeWith(
                document(
                    "profile-get-by-userid",
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("targetUserId").description("조회할 사용자 ID (UUID)")
                    ),
                    responseFields(
                        fieldWithPath("id").description("프로필 ID"),
                        fieldWithPath("userId").description("사용자 ID"),
                        fieldWithPath("nickname").description("닉네임"),
                        fieldWithPath("profileImageUrl").description("프로필 이미지 URL"),
                        fieldWithPath("bio").description("자기소개"),
                        fieldWithPath("followerCount").description("팔로워 수"),
                        fieldWithPath("followingCount").description("팔로잉 수"),
                        fieldWithPath("createdAt").description("생성일시"),
                        fieldWithPath("updatedAt").description("수정일시")
                    )
                )
            )
    }

    @Test
    @DisplayName("닉네임으로 프로필 조회 성공")
    fun getProfileByNickname_Success() {
        // Given
        val nickname = "testnick"
        every { userProfileService.getProfileByNickname(nickname) } returns Mono.just(testProfile)
        // When & Then
        webTestClient.get()
            .uri("${ApiPaths.API_V1_PROFILES}/nickname/{nickname}", nickname)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.nickname").isEqualTo(nickname)
            .consumeWith(
                document(
                    "profile-get-by-nickname",
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("nickname").description("조회할 닉네임")
                    ),
                    responseFields(
                        fieldWithPath("id").description("프로필 ID"),
                        fieldWithPath("userId").description("사용자 ID"),
                        fieldWithPath("nickname").description("닉네임"),
                        fieldWithPath("profileImageUrl").description("프로필 이미지 URL"),
                        fieldWithPath("bio").description("자기소개"),
                        fieldWithPath("followerCount").description("팔로워 수"),
                        fieldWithPath("followingCount").description("팔로잉 수"),
                        fieldWithPath("createdAt").description("생성일시"),
                        fieldWithPath("updatedAt").description("수정일시")
                    )
                )
            )
    }

    @Test
    @DisplayName("프로필 조회 실패 - 프로필 없음")
    fun getProfile_NotFound() {
        // Given
        val nonExistentId = UUID.randomUUID()
        every { userProfileService.getProfileByUserId(nonExistentId) } throws
                UserProfileNotFoundException("프로필을 찾을 수 없습니다.")

        // When & Then
        webTestClient.get()
            .uri("${ApiPaths.API_V1_PROFILES}/{targetUserId}", nonExistentId)
            .exchange()
            .expectStatus().is4xxClientError
    }

    @Test
    @DisplayName("프로필 수정 성공")
    fun updateProfile_Success() {
        // Given
        val request = UpdateProfileRequest(
            nickname = "updatednick",
            bio = "수정된 자기소개"
        )

        every {
            userProfileService.updateProfile(testUserId, request.nickname, request.profileImageUrl, request.bio)
        } returns Mono.just(testProfile.copy(nickname = "updatednick", bio = "수정된 자기소개"))

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .patch()
            .uri(ApiPaths.API_V1_PROFILES)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.nickname").isEqualTo("updatednick")
            .jsonPath("$.bio").isEqualTo("수정된 자기소개")
            .consumeWith(
                document(
                    "profile-update",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        fieldWithPath("nickname").description("변경할 닉네임 (선택)").optional(),
                        fieldWithPath("profileImageUrl").description("변경할 프로필 이미지 URL (선택)").optional(),
                        fieldWithPath("bio").description("변경할 자기소개 (선택)").optional()
                    ),
                    responseFields(
                        fieldWithPath("id").description("프로필 ID"),
                        fieldWithPath("userId").description("사용자 ID"),
                        fieldWithPath("nickname").description("닉네임"),
                        fieldWithPath("profileImageUrl").description("프로필 이미지 URL"),
                        fieldWithPath("bio").description("자기소개"),
                        fieldWithPath("followerCount").description("팔로워 수"),
                        fieldWithPath("followingCount").description("팔로잉 수"),
                        fieldWithPath("createdAt").description("생성일시"),
                        fieldWithPath("updatedAt").description("수정일시")
                    )
                )
            )
    }

    @Test
    @DisplayName("닉네임 중복 확인 - 중복됨")
    fun checkNickname_Duplicated() {
        // Given
        val nickname = "duplicatenick"
        every { userProfileService.isNicknameDuplicated(nickname) } returns Mono.just(true)
        // When & Then
        webTestClient.get()
            .uri("${ApiPaths.API_V1_PROFILES}/check/nickname/{nickname}", nickname)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.nickname").isEqualTo(nickname)
            .jsonPath("$.isDuplicated").isEqualTo(true)
            .consumeWith(
                document(
                    "profile-check-nickname",
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("nickname").description("확인할 닉네임")
                    ),
                    responseFields(
                        fieldWithPath("nickname").description("확인한 닉네임"),
                        fieldWithPath("isDuplicated").description("중복 여부 (true: 중복, false: 사용 가능)")
                    )
                )
            )
    }

    @Test
    @DisplayName("닉네임 중복 확인 - 사용 가능")
    fun checkNickname_Available() {
        // Given
        val nickname = "availablenick"
        every { userProfileService.isNicknameDuplicated(nickname) } returns Mono.just(false)
        // When & Then
        webTestClient.get()
            .uri("${ApiPaths.API_V1_PROFILES}/check/nickname/{nickname}", nickname)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.isDuplicated").isEqualTo(false)
    }

    @Test
    @DisplayName("프로필 이미지 업로드 성공")
    fun uploadProfileImage_Success() {
        // Given: 테스트용 이미지 파일 생성
        val testImageBytes = createTestImage()
        val uploadedImageUrl = "https://test-bucket.s3.amazonaws.com/profile-images/$testUserId/profile_${testUserId}_123456.jpg"

        every {
            userProfileService.uploadProfileImage(testUserId, any())
        } returns Mono.just(uploadedImageUrl)

        // When & Then: Multipart 파일 업로드
        val multipartBodyBuilder = MultipartBodyBuilder()
        multipartBodyBuilder.part("file", testImageBytes)
            .contentType(MediaType.IMAGE_JPEG)
            .filename("test-profile.jpg")

        webTestClient
            .mutateWith(mockUser(testUserId))
            .post()
            .uri("${ApiPaths.API_V1_PROFILES}/image")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(multipartBodyBuilder.build()))
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.imageUrl").isEqualTo(uploadedImageUrl)
            .consumeWith(
                document(
                    "profile-upload-image",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("imageUrl").description("업로드된 이미지 URL (S3)")
                    )
                )
            )

        verify(exactly = 1) {
            userProfileService.uploadProfileImage(testUserId, any())
        }
    }

    @Test
    @DisplayName("프로필 이미지 업로드 실패 - 잘못된 파일 형식")
    fun uploadProfileImage_InvalidFileType() {
        // Given: 잘못된 파일 형식 (text/plain)
        val invalidFile = "invalid file content".toByteArray()

        every {
            userProfileService.uploadProfileImage(testUserId, any())
        } returns Mono.error(IllegalArgumentException("Unsupported image type"))

        // When & Then
        val multipartBodyBuilder = MultipartBodyBuilder()
        multipartBodyBuilder.part("file", invalidFile)
            .contentType(MediaType.TEXT_PLAIN)
            .filename("test.txt")

        webTestClient
            .mutateWith(mockUser(testUserId))
            .post()
            .uri("${ApiPaths.API_V1_PROFILES}/image")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(multipartBodyBuilder.build()))
            .exchange()
            .expectStatus().is4xxClientError
    }

    /**
     * 테스트용 JPEG 이미지 바이트 배열 생성
     */
    private fun createTestImage(): ByteArray {
        val bufferedImage = java.awt.image.BufferedImage(
            100,
            100,
            java.awt.image.BufferedImage.TYPE_INT_RGB
        )

        val graphics = bufferedImage.createGraphics()
        graphics.color = java.awt.Color.BLUE
        graphics.fillRect(0, 0, 100, 100)
        graphics.dispose()

        val outputStream = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(bufferedImage, "jpg", outputStream)
        return outputStream.toByteArray()
    }
}
