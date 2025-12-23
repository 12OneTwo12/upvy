package me.onetwo.upvy.domain.tag.controller

import io.mockk.every
import io.mockk.mockk
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.tag.model.Tag
import me.onetwo.upvy.domain.tag.service.TagService
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import java.time.Instant

/**
 * TagController 테스트
 *
 * @see TagController
 */
@WebFluxTest(TagController::class)
@Import(
    TagControllerTest.TestConfig::class,
    TestSecurityConfig::class
)
@ActiveProfiles("test")
@DisplayName("Tag Controller 테스트")
class TagControllerTest : BaseReactiveTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var tagService: TagService

    @TestConfiguration
    class TestConfig {
        @Bean
        fun tagService(): TagService = mockk(relaxed = true)
    }

    @Nested
    @DisplayName("GET /api/v1/tags/popular - 인기 태그 조회")
    inner class GetPopularTags {

        @Test
        @DisplayName("인기 태그 조회 시, 200 OK와 태그 목록을 반환한다")
        fun getPopularTags_ReturnsTagList() {
            // Given: 인기 태그 데이터
            val tags = listOf(
                Tag(
                    id = 1L,
                    name = "프로그래밍",
                    normalizedName = "프로그래밍",
                    usageCount = 100,
                    createdAt = Instant.now()
                ),
                Tag(
                    id = 2L,
                    name = "과학",
                    normalizedName = "과학",
                    usageCount = 80,
                    createdAt = Instant.now()
                ),
                Tag(
                    id = 3L,
                    name = "수학",
                    normalizedName = "수학",
                    usageCount = 60,
                    createdAt = Instant.now()
                )
            )

            every { tagService.getPopularTags(any()) } returns Flux.fromIterable(tags)

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("/api/v1/tags/popular?limit=20")
                .exchange()
                .expectStatus().isOk
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.tags").isArray
                .jsonPath("$.tags.length()").isEqualTo(3)
                .jsonPath("$.tags[0].id").isEqualTo(1)
                .jsonPath("$.tags[0].name").isEqualTo("프로그래밍")
                .jsonPath("$.tags[0].usageCount").isEqualTo(100)
                .jsonPath("$.tags[1].id").isEqualTo(2)
                .jsonPath("$.tags[1].name").isEqualTo("과학")
                .jsonPath("$.tags[1].usageCount").isEqualTo(80)
                .jsonPath("$.tags[2].id").isEqualTo(3)
                .jsonPath("$.tags[2].name").isEqualTo("수학")
                .jsonPath("$.tags[2].usageCount").isEqualTo(60)
                .jsonPath("$.count").isEqualTo(3)
        }

        @Test
        @DisplayName("인기 태그가 없을 경우, 빈 배열을 반환한다")
        fun getPopularTags_WhenNoTags_ReturnsEmptyList() {
            // Given: 태그가 없음
            every { tagService.getPopularTags(any()) } returns Flux.empty()

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("/api/v1/tags/popular?limit=20")
                .exchange()
                .expectStatus().isOk
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.tags").isArray
                .jsonPath("$.tags.length()").isEqualTo(0)
                .jsonPath("$.count").isEqualTo(0)
        }

        @Test
        @DisplayName("limit 파라미터 없이 요청 시, 기본값 20을 사용한다")
        fun getPopularTags_WithoutLimit_UsesDefaultValue() {
            // Given: 기본값 20개의 태그
            every { tagService.getPopularTags(20) } returns Flux.empty()

            // When & Then: limit 파라미터 없이 요청
            webTestClient
                .get()
                .uri("/api/v1/tags/popular")
                .exchange()
                .expectStatus().isOk
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.tags").isArray
                .jsonPath("$.count").isEqualTo(0)
        }

        @Test
        @DisplayName("인증 없이 접근 가능해야 한다")
        fun getPopularTags_NoAuthenticationRequired() {
            // Given: 인증되지 않은 요청
            every { tagService.getPopularTags(any()) } returns Flux.empty()

            // When & Then: 인증 없이도 접근 가능
            webTestClient
                .get()
                .uri("/api/v1/tags/popular")
                .exchange()
                .expectStatus().isOk
        }
    }
}
