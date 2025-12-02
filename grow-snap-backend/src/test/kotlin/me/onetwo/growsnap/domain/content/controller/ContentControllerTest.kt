package me.onetwo.growsnap.domain.content.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import me.onetwo.growsnap.domain.content.dto.ContentCreateRequest
import me.onetwo.growsnap.domain.content.dto.ContentResponse
import me.onetwo.growsnap.domain.content.dto.ContentUploadUrlRequest
import me.onetwo.growsnap.domain.content.dto.ContentUploadUrlResponse
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.content.service.ContentService
import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.util.mockUser
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.time.Instant
import ChronoUnit
import java.util.UUID

@WebFluxTest(ContentController::class)
@Import(TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("콘텐츠 Controller 테스트")
class ContentControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var contentService: ContentService

    @Nested
    @DisplayName("POST /api/v1/contents/upload-url - Presigned URL 생성")
    inner class GenerateUploadUrl {

        @Test
        @DisplayName("유효한 요청으로 Presigned URL 생성 시, 200과 URL 정보를 반환한다")
        fun generateUploadUrl_WithValidRequest_Returns200AndUrlInfo() {
            // Given: 테스트 데이터
            val userId = UUID.randomUUID()
            val request = ContentUploadUrlRequest(
                contentType = ContentType.VIDEO,
                fileName = "test.mp4",
                fileSize = 1000000L
            )

            val response = ContentUploadUrlResponse(
                contentId = UUID.randomUUID().toString(),
                uploadUrl = "https://s3.amazonaws.com/presigned-url",
                expiresIn = 900
            )

            every { contentService.generateUploadUrl(any(), any()) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1_CONTENTS}/upload-url")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.contentId").isNotEmpty
                .jsonPath("$.uploadUrl").isEqualTo(response.uploadUrl)
                .jsonPath("$.expiresIn").isEqualTo(900)
                .consumeWith(
                    document(
                        "content-generate-upload-url",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                            fieldWithPath("contentType").description("콘텐츠 타입 (VIDEO, PHOTO)"),
                            fieldWithPath("fileName").description("파일 이름"),
                            fieldWithPath("fileSize").description("파일 크기 (bytes)"),
                            fieldWithPath("mimeType").optional().description("MIME 타입 (제공되지 않으면 fileName에서 추론)")
                        ),
                        responseFields(
                            fieldWithPath("contentId").description("생성된 콘텐츠 ID (upload token)"),
                            fieldWithPath("uploadUrl").description("S3 Presigned Upload URL"),
                            fieldWithPath("expiresIn").description("URL 만료 시간 (초)")
                        )
                    )
                )
        }
    }

    @Nested
    @DisplayName("POST /api/v1/contents - 콘텐츠 생성")
    inner class CreateContent {

        @Test
        @DisplayName("유효한 요청으로 콘텐츠 생성 시, 201과 콘텐츠 정보를 반환한다")
        fun createContent_WithValidRequest_Returns201AndContentInfo() {
            // Given: 테스트 데이터
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            val request = ContentCreateRequest(
                contentId = contentId.toString(),
                title = "Test Video",
                description = "Test Description",
                category = Category.PROGRAMMING,
                tags = listOf("test", "video"),
                language = "ko",
                thumbnailUrl = "https://s3.amazonaws.com/thumbnail.jpg",
                duration = 60,
                width = 1920,
                height = 1080
            )

            val response = ContentResponse(
                id = contentId.toString(),
                creatorId = userId.toString(),
                contentType = ContentType.VIDEO,
                url = "https://s3.amazonaws.com/video.mp4",
                photoUrls = null,
                thumbnailUrl = request.thumbnailUrl,
                duration = request.duration,
                width = request.width,
                height = request.height,
                status = ContentStatus.PUBLISHED,
                title = request.title,
                description = request.description,
                category = request.category,
                tags = request.tags,
                language = request.language,
                interactions = me.onetwo.growsnap.domain.feed.dto.InteractionInfoResponse(
                    likeCount = 0,
                    commentCount = 0,
                    saveCount = 0,
                    shareCount = 0,
                    viewCount = 0,
                    isLiked = false,
                    isSaved = false
                ),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            every { contentService.createContent(any(), any()) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1_CONTENTS}")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").isEqualTo(contentId.toString())
                .jsonPath("$.title").isEqualTo(request.title)
                .jsonPath("$.category").isEqualTo("PROGRAMMING")
                .consumeWith(
                    document(
                        "content-create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                            fieldWithPath("contentId").description("업로드된 콘텐츠 ID (upload token)"),
                            fieldWithPath("title").description("콘텐츠 제목"),
                            fieldWithPath("description").description("콘텐츠 설명").optional(),
                            fieldWithPath("category").description("카테고리"),
                            fieldWithPath("tags").description("태그 목록"),
                            fieldWithPath("language").description("언어 코드 (ko, en)"),
                            fieldWithPath("photoUrls").description("사진 URL 목록 (PHOTO 타입인 경우)").optional(),
                            fieldWithPath("thumbnailUrl").description("썸네일 URL"),
                            fieldWithPath("duration").description("동영상 길이 (초)").optional(),
                            fieldWithPath("width").description("가로 해상도"),
                            fieldWithPath("height").description("세로 해상도")
                        ),
                        responseFields(
                            fieldWithPath("id").description("콘텐츠 ID"),
                            fieldWithPath("creatorId").description("생성자 ID"),
                            fieldWithPath("contentType").description("콘텐츠 타입"),
                            fieldWithPath("url").description("콘텐츠 URL"),
                            fieldWithPath("photoUrls").description("사진 URL 목록 (PHOTO 타입인 경우)").optional(),
                            fieldWithPath("thumbnailUrl").description("썸네일 URL"),
                            fieldWithPath("duration").description("동영상 길이 (초)").optional(),
                            fieldWithPath("width").description("가로 해상도"),
                            fieldWithPath("height").description("세로 해상도"),
                            fieldWithPath("status").description("콘텐츠 상태"),
                            fieldWithPath("title").description("제목"),
                            fieldWithPath("description").description("설명").optional(),
                            fieldWithPath("category").description("카테고리"),
                            fieldWithPath("tags").description("태그 목록"),
                            fieldWithPath("language").description("언어"),
                            fieldWithPath("interactions.likeCount").description("좋아요 수").optional(),
                            fieldWithPath("interactions.commentCount").description("댓글 수").optional(),
                            fieldWithPath("interactions.saveCount").description("저장 수").optional(),
                            fieldWithPath("interactions.shareCount").description("공유 수").optional(),
                            fieldWithPath("interactions.viewCount").description("조회수").optional(),
                            fieldWithPath("interactions.isLiked").description("현재 사용자의 좋아요 여부").optional(),
                            fieldWithPath("interactions.isSaved").description("현재 사용자의 저장 여부").optional(),
                            fieldWithPath("createdAt").description("생성 시각"),
                            fieldWithPath("updatedAt").description("수정 시각")
                        )
                    )
                )
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contents/{contentId} - 콘텐츠 조회")
    inner class GetContent {

        @Test
        @DisplayName("존재하는 콘텐츠 조회 시, 200과 콘텐츠 정보를 반환한다")
        fun getContent_WhenContentExists_Returns200AndContentInfo() {
            // Given: 테스트 데이터
            val contentId = UUID.randomUUID()

            val response = ContentResponse(
                id = contentId.toString(),
                creatorId = UUID.randomUUID().toString(),
                contentType = ContentType.VIDEO,
                url = "https://s3.amazonaws.com/video.mp4",
                photoUrls = null,
                thumbnailUrl = "https://s3.amazonaws.com/thumbnail.jpg",
                duration = 60,
                width = 1920,
                height = 1080,
                status = ContentStatus.PUBLISHED,
                title = "Test Video",
                description = "Test Description",
                category = Category.PROGRAMMING,
                tags = listOf("test"),
                language = "ko",
                interactions = me.onetwo.growsnap.domain.feed.dto.InteractionInfoResponse(
                    likeCount = 10,
                    commentCount = 5,
                    saveCount = 3,
                    shareCount = 2,
                    viewCount = 100,
                    isLiked = false,
                    isSaved = false
                ),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            every { contentService.getContent(contentId, any()) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", contentId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(contentId.toString())
                .jsonPath("$.title").isEqualTo("Test Video")
                .consumeWith(
                    document(
                        "content-get",
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("콘텐츠 ID")
                        ),
                        responseFields(
                            fieldWithPath("id").description("콘텐츠 ID"),
                            fieldWithPath("creatorId").description("생성자 ID"),
                            fieldWithPath("contentType").description("콘텐츠 타입"),
                            fieldWithPath("url").description("콘텐츠 URL"),
                            fieldWithPath("photoUrls").description("사진 URL 목록 (PHOTO 타입인 경우)").optional(),
                            fieldWithPath("thumbnailUrl").description("썸네일 URL"),
                            fieldWithPath("duration").description("동영상 길이 (초)").optional(),
                            fieldWithPath("width").description("가로 해상도"),
                            fieldWithPath("height").description("세로 해상도"),
                            fieldWithPath("status").description("콘텐츠 상태"),
                            fieldWithPath("title").description("제목"),
                            fieldWithPath("description").description("설명").optional(),
                            fieldWithPath("category").description("카테고리"),
                            fieldWithPath("tags").description("태그 목록"),
                            fieldWithPath("language").description("언어"),
                            fieldWithPath("interactions.likeCount").description("좋아요 수").optional(),
                            fieldWithPath("interactions.commentCount").description("댓글 수").optional(),
                            fieldWithPath("interactions.saveCount").description("저장 수").optional(),
                            fieldWithPath("interactions.shareCount").description("공유 수").optional(),
                            fieldWithPath("interactions.viewCount").description("조회수").optional(),
                            fieldWithPath("interactions.isLiked").description("현재 사용자의 좋아요 여부").optional(),
                            fieldWithPath("interactions.isSaved").description("현재 사용자의 저장 여부").optional(),
                            fieldWithPath("createdAt").description("생성 시각"),
                            fieldWithPath("updatedAt").description("수정 시각")
                        )
                    )
                )
        }

        @Test
        @DisplayName("존재하지 않는 콘텐츠 조회 시, 404를 반환한다")
        fun getContent_WhenContentNotExists_Returns404() {
            // Given: 존재하지 않는 contentId
            val contentId = UUID.randomUUID()

            every { contentService.getContent(contentId, any()) } returns Mono.empty()

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1_CONTENTS}/$contentId")
                .exchange()
                .expectStatus().isNotFound
        }

        @Test
        @DisplayName("서비스에서 NoSuchElementException 발생 시, 404를 반환한다")
        fun getContent_WhenServiceThrowsNoSuchElementException_Returns404() {
            // Given: 존재하지 않는 contentId
            val contentId = UUID.randomUUID()

            every { contentService.getContent(contentId, any()) } returns Mono.error(NoSuchElementException("Content not found"))

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1_CONTENTS}/$contentId")
                .exchange()
                .expectStatus().isNotFound
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contents/me - 내 콘텐츠 목록 조회")
    inner class GetMyContents {

        @Test
        @DisplayName("인증된 사용자의 콘텐츠 목록 조회 시, 200과 콘텐츠 목록을 반환한다")
        fun getMyContents_WithAuthenticatedUser_Returns200AndContentList() {
            // Given: 테스트 데이터
            val userId = UUID.randomUUID()

            val response1 = ContentResponse(
                id = UUID.randomUUID().toString(),
                creatorId = userId.toString(),
                contentType = ContentType.VIDEO,
                url = "https://s3.amazonaws.com/video1.mp4",
                photoUrls = null,
                thumbnailUrl = "https://s3.amazonaws.com/thumbnail1.jpg",
                duration = 60,
                width = 1920,
                height = 1080,
                status = ContentStatus.PUBLISHED,
                title = "My Video 1",
                description = "Description 1",
                category = Category.PROGRAMMING,
                tags = listOf("test1"),
                language = "ko",
                interactions = me.onetwo.growsnap.domain.feed.dto.InteractionInfoResponse(
                    likeCount = 10,
                    commentCount = 5,
                    saveCount = 3,
                    shareCount = 2,
                    viewCount = 100,
                    isLiked = false,
                    isSaved = false
                ),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            val response2 = ContentResponse(
                id = UUID.randomUUID().toString(),
                creatorId = userId.toString(),
                contentType = ContentType.PHOTO,
                url = "https://s3.amazonaws.com/photo1.jpg",
                photoUrls = listOf("https://s3.amazonaws.com/photo1.jpg", "https://s3.amazonaws.com/photo2.jpg"),
                thumbnailUrl = "https://s3.amazonaws.com/thumbnail2.jpg",
                duration = null,
                width = 1080,
                height = 1350,
                status = ContentStatus.PUBLISHED,
                title = "My Photo 1",
                description = "Description 2",
                category = Category.HEALTH,
                tags = listOf("test2"),
                language = "ko",
                interactions = me.onetwo.growsnap.domain.feed.dto.InteractionInfoResponse(
                    likeCount = 20,
                    commentCount = 10,
                    saveCount = 5,
                    shareCount = 3,
                    viewCount = 200,
                    isLiked = false,
                    isSaved = false
                ),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            every { contentService.getContentsByCreator(userId, userId) } returns reactor.core.publisher.Flux.just(response1, response2)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri("${ApiPaths.API_V1_CONTENTS}/me")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$[0].id").isNotEmpty
                .jsonPath("$[0].title").isEqualTo("My Video 1")
                .jsonPath("$[1].id").isNotEmpty
                .jsonPath("$[1].title").isEqualTo("My Photo 1")
                .consumeWith(
                    document(
                        "content-get-my-contents",
                        preprocessResponse(prettyPrint()),
                        responseFields(
                            fieldWithPath("[].id").description("콘텐츠 ID"),
                            fieldWithPath("[].creatorId").description("생성자 ID"),
                            fieldWithPath("[].contentType").description("콘텐츠 타입"),
                            fieldWithPath("[].url").description("콘텐츠 URL").optional(),
                            fieldWithPath("[].photoUrls").description("사진 URL 목록 (PHOTO 타입인 경우)").optional(),
                            fieldWithPath("[].thumbnailUrl").description("썸네일 URL"),
                            fieldWithPath("[].duration").description("동영상 길이 (초)").optional(),
                            fieldWithPath("[].width").description("가로 해상도"),
                            fieldWithPath("[].height").description("세로 해상도"),
                            fieldWithPath("[].status").description("콘텐츠 상태"),
                            fieldWithPath("[].title").description("제목"),
                            fieldWithPath("[].description").description("설명").optional(),
                            fieldWithPath("[].category").description("카테고리"),
                            fieldWithPath("[].tags").description("태그 목록"),
                            fieldWithPath("[].language").description("언어"),
                            fieldWithPath("[].interactions.likeCount").description("좋아요 수").optional(),
                            fieldWithPath("[].interactions.commentCount").description("댓글 수").optional(),
                            fieldWithPath("[].interactions.saveCount").description("저장 수").optional(),
                            fieldWithPath("[].interactions.shareCount").description("공유 수").optional(),
                            fieldWithPath("[].interactions.viewCount").description("조회수").optional(),
                            fieldWithPath("[].interactions.isLiked").description("현재 사용자의 좋아요 여부").optional(),
                            fieldWithPath("[].interactions.isSaved").description("현재 사용자의 저장 여부").optional(),
                            fieldWithPath("[].createdAt").description("생성 시각"),
                            fieldWithPath("[].updatedAt").description("수정 시각")
                        )
                    )
                )
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/contents/{contentId} - 콘텐츠 수정")
    inner class UpdateContent {

        @Test
        @DisplayName("PHOTO 타입 콘텐츠의 사진 목록 수정 시, 200과 수정된 콘텐츠를 반환한다")
        fun updateContent_WithPhotoUrls_Returns200AndUpdatedContent() {
            // Given: 수정 요청 데이터
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            val request = me.onetwo.growsnap.domain.content.dto.ContentUpdateRequest(
                title = "Updated Title",
                description = "Updated Description",
                category = Category.HEALTH,
                photoUrls = listOf(
                    "https://s3.amazonaws.com/new1.jpg",
                    "https://s3.amazonaws.com/new2.jpg"
                )
            )

            val response = ContentResponse(
                id = contentId.toString(),
                creatorId = userId.toString(),
                contentType = ContentType.PHOTO,
                url = "https://s3.amazonaws.com/photo.jpg",
                photoUrls = listOf(
                    "https://s3.amazonaws.com/new1.jpg",
                    "https://s3.amazonaws.com/new2.jpg"
                ),
                thumbnailUrl = "https://s3.amazonaws.com/thumb.jpg",
                duration = null,
                width = 1080,
                height = 1080,
                status = ContentStatus.PUBLISHED,
                title = "Updated Title",
                description = "Updated Description",
                category = Category.HEALTH,
                tags = listOf("health", "photo"),
                language = "ko",
                interactions = me.onetwo.growsnap.domain.feed.dto.InteractionInfoResponse(
                    likeCount = 5,
                    commentCount = 3,
                    saveCount = 2,
                    shareCount = 1,
                    viewCount = 50,
                    isLiked = false,
                    isSaved = false
                ),
                createdAt = Instant.now().minus(1, ChronoUnit.DAYS),
                updatedAt = Instant.now()
            )

            every { contentService.updateContent(userId, contentId, request) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .patch()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", contentId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(contentId.toString())
                .jsonPath("$.title").isEqualTo("Updated Title")
                .jsonPath("$.description").isEqualTo("Updated Description")
                .jsonPath("$.contentType").isEqualTo("PHOTO")
                .jsonPath("$.photoUrls").isArray
                .jsonPath("$.photoUrls.length()").isEqualTo(2)
                .jsonPath("$.photoUrls[0]").isEqualTo("https://s3.amazonaws.com/new1.jpg")
                .jsonPath("$.photoUrls[1]").isEqualTo("https://s3.amazonaws.com/new2.jpg")
                .consumeWith(
                    document(
                        "contents/update-photo-content",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("수정할 콘텐츠 ID")
                        ),
                        requestFields(
                            fieldWithPath("title").description("콘텐츠 제목 (선택, 200자 이하)").optional(),
                            fieldWithPath("description").description("콘텐츠 설명 (선택, 2000자 이하)").optional(),
                            fieldWithPath("category").description("카테고리 (선택)").optional(),
                            fieldWithPath("tags").description("태그 목록 (선택)").optional(),
                            fieldWithPath("language").description("언어 코드 (선택, 2-10자)").optional(),
                            fieldWithPath("photoUrls").description("사진 URL 목록 (선택, PHOTO 타입만, 1-10장)").optional()
                        ),
                        responseFields(
                            fieldWithPath("id").description("콘텐츠 ID"),
                            fieldWithPath("creatorId").description("크리에이터 ID"),
                            fieldWithPath("contentType").description("콘텐츠 타입 (VIDEO, PHOTO)"),
                            fieldWithPath("url").description("콘텐츠 URL"),
                            fieldWithPath("photoUrls").description("사진 URL 목록 (PHOTO 타입만)").optional(),
                            fieldWithPath("thumbnailUrl").description("썸네일 URL").optional(),
                            fieldWithPath("duration").description("재생 시간 (초, VIDEO만)").optional(),
                            fieldWithPath("width").description("가로 크기 (픽셀)"),
                            fieldWithPath("height").description("세로 크기 (픽셀)"),
                            fieldWithPath("status").description("콘텐츠 상태 (DRAFT, PUBLISHED 등)"),
                            fieldWithPath("title").description("제목"),
                            fieldWithPath("description").description("설명").optional(),
                            fieldWithPath("category").description("카테고리"),
                            fieldWithPath("tags").description("태그 목록"),
                            fieldWithPath("language").description("언어 코드"),
                            fieldWithPath("interactions.likeCount").description("좋아요 수").optional(),
                            fieldWithPath("interactions.commentCount").description("댓글 수").optional(),
                            fieldWithPath("interactions.saveCount").description("저장 수").optional(),
                            fieldWithPath("interactions.shareCount").description("공유 수").optional(),
                            fieldWithPath("interactions.viewCount").description("조회수").optional(),
                            fieldWithPath("interactions.isLiked").description("현재 사용자의 좋아요 여부").optional(),
                            fieldWithPath("interactions.isSaved").description("현재 사용자의 저장 여부").optional(),
                            fieldWithPath("createdAt").description("생성일시"),
                            fieldWithPath("updatedAt").description("수정일시")
                        )
                    )
                )
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/contents/{contentId} - 콘텐츠 삭제")
    inner class DeleteContent {

        @Test
        @DisplayName("본인이 작성한 콘텐츠 삭제 시, 204를 반환한다")
        fun deleteContent_WhenOwner_Returns204() {
            // Given: 테스트 데이터
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { contentService.deleteContent(userId, contentId) } returns Mono.empty()

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .delete()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", contentId)
                .exchange()
                .expectStatus().isNoContent
                .expectBody()
                .consumeWith(
                    document(
                        "content-delete",
                        pathParameters(
                            parameterWithName("contentId").description("삭제할 콘텐츠 ID")
                        )
                    )
                )
        }

        @Test
        @DisplayName("다른 사용자의 콘텐츠 삭제 시, 403을 반환한다")
        fun deleteContent_WhenNotOwner_Returns403() {
            // Given: 권한 없는 사용자
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { contentService.deleteContent(userId, contentId) } returns Mono.error(IllegalAccessException("권한이 없습니다"))

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .delete()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", contentId)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        @DisplayName("존재하지 않는 콘텐츠 삭제 시, 404를 반환한다")
        fun deleteContent_WhenContentNotExists_Returns404() {
            // Given: 존재하지 않는 contentId
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { contentService.deleteContent(userId, contentId) } returns Mono.error(NoSuchElementException("콘텐츠를 찾을 수 없습니다"))

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .delete()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", contentId)
                .exchange()
                .expectStatus().isNotFound
        }
    }
}
