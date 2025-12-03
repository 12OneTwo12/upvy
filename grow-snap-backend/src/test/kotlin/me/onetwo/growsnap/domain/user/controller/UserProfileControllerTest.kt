package me.onetwo.growsnap.domain.user.controller

import java.util.UUID

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.domain.content.dto.ContentResponse
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.service.ContentService
import me.onetwo.growsnap.domain.feed.dto.InteractionInfoResponse
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

    @MockkBean
    private lateinit var contentService: ContentService

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

    @Test
    @DisplayName("사용자의 콘텐츠 목록 조회 성공 - 인증된 사용자")
    fun getUserContents_AuthenticatedUser_Success() {
        // Given
        val targetUserId = UUID.randomUUID()
        val contentId1 = UUID.randomUUID()
        val contentId2 = UUID.randomUUID()

        val testContents = listOf(
            ContentResponse(
                id = contentId1.toString(),
                creatorId = targetUserId.toString(),
                contentType = ContentType.VIDEO,
                url = "https://example.com/video1.mp4",
                photoUrls = null,
                thumbnailUrl = "https://example.com/thumb1.jpg",
                duration = 30,
                width = 1080,
                height = 1920,
                status = ContentStatus.PUBLISHED,
                title = "Test Video 1",
                description = "Test Description 1",
                category = Category.PROGRAMMING,
                tags = listOf("test", "video"),
                language = "ko",
                interactions = InteractionInfoResponse(
                    likeCount = 10,
                    commentCount = 5,
                    saveCount = 3,
                    shareCount = 2,
                    viewCount = 100,
                    isLiked = true,
                    isSaved = false
                ),
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now()
            ),
            ContentResponse(
                id = contentId2.toString(),
                creatorId = targetUserId.toString(),
                contentType = ContentType.PHOTO,
                url = "https://example.com/photo1.jpg",
                photoUrls = listOf("https://example.com/photo1.jpg", "https://example.com/photo2.jpg"),
                thumbnailUrl = "https://example.com/photo1.jpg",
                duration = null,
                width = 1080,
                height = 1350,
                status = ContentStatus.PUBLISHED,
                title = "Test Photo Album",
                description = "Test Photo Description",
                category = Category.TRAVEL,
                tags = listOf("test", "photo"),
                language = "ko",
                interactions = InteractionInfoResponse(
                    likeCount = 20,
                    commentCount = 8,
                    saveCount = 5,
                    shareCount = 3,
                    viewCount = 200,
                    isLiked = false,
                    isSaved = true
                ),
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now()
            )
        )

        val pageResponse = me.onetwo.growsnap.domain.content.dto.ContentPageResponse(
            content = testContents,
            nextCursor = null,
            hasNext = false,
            count = 2
        )

        every { contentService.getContentsByCreatorWithCursor(targetUserId, testUserId, any()) } returns
                Mono.just(pageResponse)

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .get()
            .uri("${ApiPaths.API_V1_PROFILES}/{targetUserId}/contents", targetUserId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(2)
            .jsonPath("$.content[0].id").isEqualTo(contentId1.toString())
            .jsonPath("$.content[0].interactions.isLiked").isEqualTo(true)
            .jsonPath("$.content[1].id").isEqualTo(contentId2.toString())
            .jsonPath("$.content[1].interactions.isSaved").isEqualTo(true)
            .jsonPath("$.hasNext").isEqualTo(false)
            .jsonPath("$.count").isEqualTo(2)
            .consumeWith(
                document(
                    "profile-get-user-contents",
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("targetUserId").description("조회할 사용자 ID (UUID)")
                    ),
                    responseFields(
                        fieldWithPath("content").description("콘텐츠 목록"),
                        fieldWithPath("content[].id").description("콘텐츠 ID"),
                        fieldWithPath("content[].creatorId").description("크리에이터 ID"),
                        fieldWithPath("content[].contentType").description("콘텐츠 타입 (VIDEO | PHOTO)"),
                        fieldWithPath("content[].url").description("비디오 URL (VIDEO 타입만)").optional(),
                        fieldWithPath("content[].photoUrls").description("사진 URL 목록 (PHOTO 타입만)").optional(),
                        fieldWithPath("content[].thumbnailUrl").description("썸네일 URL"),
                        fieldWithPath("content[].duration").description("비디오 길이 (초, VIDEO 타입만)").optional(),
                        fieldWithPath("content[].width").description("미디어 너비"),
                        fieldWithPath("content[].height").description("미디어 높이"),
                        fieldWithPath("content[].status").description("콘텐츠 상태 (DRAFT | PUBLISHED | ARCHIVED)"),
                        fieldWithPath("content[].title").description("콘텐츠 제목"),
                        fieldWithPath("content[].description").description("콘텐츠 설명").optional(),
                        fieldWithPath("content[].category").description("카테고리"),
                        fieldWithPath("content[].tags").description("태그 목록"),
                        fieldWithPath("content[].language").description("언어 코드"),
                        fieldWithPath("content[].interactions").description("인터랙션 정보"),
                        fieldWithPath("content[].interactions.likeCount").description("좋아요 수"),
                        fieldWithPath("content[].interactions.commentCount").description("댓글 수"),
                        fieldWithPath("content[].interactions.saveCount").description("저장 수"),
                        fieldWithPath("content[].interactions.shareCount").description("공유 수"),
                        fieldWithPath("content[].interactions.viewCount").description("조회 수"),
                        fieldWithPath("content[].interactions.isLiked").description("사용자의 좋아요 여부 (인증된 경우)"),
                        fieldWithPath("content[].interactions.isSaved").description("사용자의 저장 여부 (인증된 경우)"),
                        fieldWithPath("content[].createdAt").description("생성일시"),
                        fieldWithPath("content[].updatedAt").description("수정일시"),
                        fieldWithPath("nextCursor").description("다음 페이지 커서 (없으면 null)").optional(),
                        fieldWithPath("hasNext").description("다음 페이지 존재 여부"),
                        fieldWithPath("count").description("현재 페이지의 항목 수")
                    )
                )
            )

        verify(exactly = 1) {
            contentService.getContentsByCreatorWithCursor(targetUserId, testUserId, any())
        }
    }

    @Test
    @DisplayName("사용자의 콘텐츠 목록 조회 성공 - 빈 목록")
    fun getUserContents_EmptyList_Success() {
        // Given
        val targetUserId = UUID.randomUUID()
        val emptyPageResponse = me.onetwo.growsnap.domain.content.dto.ContentPageResponse(
            content = emptyList(),
            nextCursor = null,
            hasNext = false,
            count = 0
        )

        every { contentService.getContentsByCreatorWithCursor(targetUserId, testUserId, any()) } returns
                Mono.just(emptyPageResponse)

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .get()
            .uri("${ApiPaths.API_V1_PROFILES}/{targetUserId}/contents", targetUserId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.content.length()").isEqualTo(0)
            .jsonPath("$.hasNext").isEqualTo(false)
            .jsonPath("$.count").isEqualTo(0)
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
