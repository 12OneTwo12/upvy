package me.onetwo.upvy.domain.interaction.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.interaction.dto.ShareLinkResponse
import me.onetwo.upvy.domain.interaction.dto.ShareResponse
import me.onetwo.upvy.domain.interaction.service.ShareService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.infrastructure.config.RestDocsConfiguration
import me.onetwo.upvy.util.mockUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.PayloadDocumentation.*
import org.springframework.restdocs.request.RequestDocumentation.*
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.util.UUID

@WebFluxTest(ShareController::class)
@Import(RestDocsConfiguration::class, TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("공유 Controller 테스트")
class ShareControllerTest : BaseReactiveTest() {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var shareService: ShareService

    @Nested
    @DisplayName("POST /api/v1/videos/{contentId}/share - 콘텐츠 공유")
    inner class ShareVideo {

        @Test
        @DisplayName("유효한 요청으로 콘텐츠 공유 시, 200 OK와 공유 응답을 반환한다")
        fun shareVideo_WithValidRequest_ReturnsShareResponse() {
            // Given: 공유 요청
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID().toString()
            val response = ShareResponse(
                contentId = contentId,
                shareCount = 5
            )

            every { shareService.shareContent(userId, UUID.fromString(contentId)) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/share", contentId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(contentId)
                .jsonPath("$.shareCount").isEqualTo(5)
                .consumeWith(
                    document(
                        "share-create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("콘텐츠 ID")
                        ),
                        responseFields(
                            fieldWithPath("contentId").description("콘텐츠 ID"),
                            fieldWithPath("shareCount").description("공유 수")
                        )
                    )
                )

            verify(exactly = 1) { shareService.shareContent(userId, UUID.fromString(contentId)) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/videos/{contentId}/share-link - 공유 링크 조회")
    inner class GetShareLink {

        @Test
        @DisplayName("공유 링크 조회 시, 200 OK와 공유 링크를 반환한다")
        fun getShareLink_WithValidRequest_ReturnsShareLink() {
            // Given: 공유 링크 조회 요청
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID().toString()
            val response = ShareLinkResponse(
                contentId = contentId,
                shareUrl = "https://upvy.com/share/$contentId"
            )

            every { shareService.getShareLink(UUID.fromString(contentId)) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/share-link", contentId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(contentId)
                .jsonPath("$.shareUrl").isEqualTo("https://upvy.com/share/$contentId")
                .consumeWith(
                    document(
                        "share-link-get",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("콘텐츠 ID")
                        ),
                        responseFields(
                            fieldWithPath("contentId").description("콘텐츠 ID"),
                            fieldWithPath("shareUrl").description("공유 URL")
                        )
                    )
                )

            verify(exactly = 1) { shareService.getShareLink(UUID.fromString(contentId)) }
        }
    }
}
