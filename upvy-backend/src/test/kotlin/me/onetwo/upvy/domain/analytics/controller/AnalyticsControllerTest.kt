package me.onetwo.upvy.domain.analytics.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.analytics.dto.ViewEventRequest
import me.onetwo.upvy.domain.analytics.service.AnalyticsService
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.infrastructure.config.RestDocsConfiguration
import me.onetwo.upvy.infrastructure.common.ApiPaths
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import me.onetwo.upvy.util.mockUser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.PayloadDocumentation.*
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Analytics 컨트롤러 테스트
 *
 * 사용자 시청 이벤트 추적 API를 검증합니다.
 *
 * **참고**: 좋아요/저장/공유 인터랙션은 각 도메인 컨트롤러에서
 * Spring Event를 통해 처리되므로, 이 컨트롤러에서는 테스트하지 않습니다.
 */
@WebFluxTest(AnalyticsController::class)
@Import(RestDocsConfiguration::class, TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("Analytics Controller 테스트")
class AnalyticsControllerTest : BaseReactiveTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var analyticsService: AnalyticsService

    @Nested
    @DisplayName("POST /api/v1/analytics/views - 시청 이벤트 기록")
    inner class TrackViewEvent {

        @Test
        @DisplayName("유효한 요청으로 시청 이벤트 기록 시, 204 No Content를 반환한다")
        fun trackViewEvent_WithValidRequest_ReturnsNoContent() {
            // Given: 유효한 시청 이벤트 요청
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val request = ViewEventRequest(
                contentId = contentId,
                watchedDuration = 120,
                completionRate = 85,
                skipped = false
            )

            every { analyticsService.trackViewEvent(userId, request) } returns Mono.empty()

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1_ANALYTICS}/views")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNoContent
                .expectBody()
                .consumeWith(
                    document(
                        "analytics-view-track",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                            fieldWithPath("contentId").description("콘텐츠 ID"),
                            fieldWithPath("watchedDuration").description("시청 시간 (초)"),
                            fieldWithPath("completionRate").description("완료율 (0-100)"),
                            fieldWithPath("skipped").description("스킵 여부")
                        )
                    )
                )

            verify(exactly = 1) { analyticsService.trackViewEvent(userId, request) }
        }

        @Test
        @DisplayName("contentId가 null인 경우, 400 Bad Request를 반환한다")
        fun trackViewEvent_WithNullContentId_ReturnsBadRequest() {
            // Given: contentId가 null인 요청
            val userId = UUID.randomUUID()
            val request = mapOf(
                "contentId" to null,
                "watchedDuration" to 120,
                "completionRate" to 85,
                "skipped" to false
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1_ANALYTICS}/views")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest

            verify(exactly = 0) { analyticsService.trackViewEvent(any(), any()) }
        }

        @Test
        @DisplayName("completionRate가 100을 초과하는 경우, 400 Bad Request를 반환한다")
        fun trackViewEvent_WithInvalidCompletionRate_ReturnsBadRequest() {
            // Given: completionRate가 100 초과
            val userId = UUID.randomUUID()
            val request = mapOf(
                "contentId" to UUID.randomUUID().toString(),
                "watchedDuration" to 120,
                "completionRate" to 150,
                "skipped" to false
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1_ANALYTICS}/views")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest

            verify(exactly = 0) { analyticsService.trackViewEvent(any(), any()) }
        }

        @Test
        @DisplayName("스킵 이벤트(3초 이내) 기록 시, 204 No Content를 반환한다")
        fun trackViewEvent_WithSkipEvent_ReturnsNoContent() {
            // Given: 스킵 이벤트 (시청 시간 3초 이하)
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val request = ViewEventRequest(
                contentId = contentId,
                watchedDuration = 2,
                completionRate = 5,
                skipped = true
            )

            every { analyticsService.trackViewEvent(userId, request) } returns Mono.empty()

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1_ANALYTICS}/views")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNoContent

            verify(exactly = 1) { analyticsService.trackViewEvent(userId, request) }
        }

        @Test
        @DisplayName("watchedDuration이 포함된 시청 이벤트 기록 시, 정상 처리된다")
        fun trackViewEvent_WithWatchedDuration_ReturnsNoContent() {
            // Given: watchedDuration이 포함된 시청 이벤트
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val request = ViewEventRequest(
                contentId = contentId,
                watchedDuration = 300,  // 5분 시청
                completionRate = 60,
                skipped = false
            )

            every { analyticsService.trackViewEvent(userId, request) } returns Mono.empty()

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1_ANALYTICS}/views")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNoContent

            verify(exactly = 1) { analyticsService.trackViewEvent(userId, request) }
        }
    }
}
