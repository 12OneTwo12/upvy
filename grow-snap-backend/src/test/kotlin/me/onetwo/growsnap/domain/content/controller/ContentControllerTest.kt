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
import java.time.LocalDateTime
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
                .uri("/api/v1/contents/upload-url")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
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
                            fieldWithPath("fileSize").description("파일 크기 (bytes)")
                        ),
                        responseFields(
                            fieldWithPath("contentId").description("생성된 콘텐츠 ID"),
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
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            every { contentService.createContent(any(), any()) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("/api/v1/contents")
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
                            fieldWithPath("contentId").description("업로드된 콘텐츠 ID"),
                            fieldWithPath("title").description("콘텐츠 제목"),
                            fieldWithPath("description").description("콘텐츠 설명").optional(),
                            fieldWithPath("category").description("카테고리"),
                            fieldWithPath("tags").description("태그 목록"),
                            fieldWithPath("language").description("언어 코드 (ko, en)"),
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
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            every { contentService.getContent(contentId) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("/api/v1/contents/{contentId}", contentId)
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

            every { contentService.getContent(contentId) } returns Mono.empty()

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("/api/v1/contents/$contentId")
                .exchange()
                .expectStatus().isNotFound
        }
    }
}
