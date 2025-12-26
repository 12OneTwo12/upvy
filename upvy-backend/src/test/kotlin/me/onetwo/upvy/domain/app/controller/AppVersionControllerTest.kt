package me.onetwo.upvy.domain.app.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.app.dto.AppVersionCheckRequest
import me.onetwo.upvy.domain.app.dto.AppVersionCheckResponse
import me.onetwo.upvy.domain.app.exception.AppVersionNotFoundException
import me.onetwo.upvy.domain.app.model.Platform
import me.onetwo.upvy.domain.app.service.AppVersionService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.infrastructure.config.RestDocsConfiguration
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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

@WebFluxTest(AppVersionController::class)
@Import(RestDocsConfiguration::class, TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("앱 버전 Controller 테스트")
class AppVersionControllerTest : BaseReactiveTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var appVersionService: AppVersionService

    @Nested
    @DisplayName("POST /api/v1/app-version/check - 앱 버전 체크")
    inner class CheckVersionTest {

        @Test
        @DisplayName("현재 버전이 최소 버전보다 낮으면 강제 업데이트가 필요하다")
        fun needsUpdateWhenBelowMinimum() {
            // Given
            val request = AppVersionCheckRequest(
                platform = "IOS",
                currentVersion = "1.0.0"
            )

            val response = AppVersionCheckResponse(
                needsUpdate = true,
                isLatestVersion = false,
                latestVersion = "1.4.2",
                minimumVersion = "1.2.0",
                storeUrl = "https://apps.apple.com/app/upvy/id123456789"
            )

            every { appVersionService.checkVersion(Platform.IOS, "1.0.0") } returns Mono.just(response)

            // When & Then
            webTestClient
                .post()
                .uri("${ApiPaths.API_V1_APP_VERSION}/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.needsUpdate").isEqualTo(true)
                .jsonPath("$.isLatestVersion").isEqualTo(false)
                .jsonPath("$.latestVersion").isEqualTo("1.4.2")
                .jsonPath("$.minimumVersion").isEqualTo("1.2.0")
                .jsonPath("$.storeUrl").isEqualTo("https://apps.apple.com/app/upvy/id123456789")
                .consumeWith(
                    document(
                        "app-version-check-needs-update",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                            fieldWithPath("platform").description("플랫폼 (IOS, ANDROID)"),
                            fieldWithPath("currentVersion").description("현재 앱 버전 (시맨틱 버전 형식: major.minor.patch)")
                        ),
                        responseFields(
                            fieldWithPath("needsUpdate").description("강제 업데이트 필요 여부"),
                            fieldWithPath("isLatestVersion").description("최신 버전 여부"),
                            fieldWithPath("latestVersion").description("최신 버전"),
                            fieldWithPath("minimumVersion").description("최소 지원 버전"),
                            fieldWithPath("storeUrl").description("앱스토어/플레이스토어 URL (업데이트 필요 시에만 제공)")
                        )
                    )
                )
        }

        @Test
        @DisplayName("현재 버전이 최신 버전이면 업데이트가 불필요하다")
        fun noUpdateNeededWhenLatest() {
            // Given
            val request = AppVersionCheckRequest(
                platform = "IOS",
                currentVersion = "1.4.2"
            )

            val response = AppVersionCheckResponse(
                needsUpdate = false,
                isLatestVersion = true,
                latestVersion = "1.4.2",
                minimumVersion = "1.0.0",
                storeUrl = null
            )

            every { appVersionService.checkVersion(Platform.IOS, "1.4.2") } returns Mono.just(response)

            // When & Then
            webTestClient
                .post()
                .uri("${ApiPaths.API_V1_APP_VERSION}/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.needsUpdate").isEqualTo(false)
                .jsonPath("$.isLatestVersion").isEqualTo(true)
                .jsonPath("$.latestVersion").isEqualTo("1.4.2")
                .jsonPath("$.minimumVersion").isEqualTo("1.0.0")
                .jsonPath("$.storeUrl").isEmpty
                .consumeWith(
                    document(
                        "app-version-check-no-update",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                            fieldWithPath("platform").description("플랫폼 (IOS, ANDROID)"),
                            fieldWithPath("currentVersion").description("현재 앱 버전 (시맨틱 버전 형식: major.minor.patch)")
                        ),
                        responseFields(
                            fieldWithPath("needsUpdate").description("강제 업데이트 필요 여부"),
                            fieldWithPath("isLatestVersion").description("최신 버전 여부"),
                            fieldWithPath("latestVersion").description("최신 버전"),
                            fieldWithPath("minimumVersion").description("최소 지원 버전"),
                            fieldWithPath("storeUrl").description("앱스토어/플레이스토어 URL (업데이트 불필요 시 null)")
                        )
                    )
                )
        }

        @Test
        @DisplayName("Android 플랫폼도 정상 동작한다")
        fun worksForAndroid() {
            // Given
            val request = AppVersionCheckRequest(
                platform = "ANDROID",
                currentVersion = "1.3.0"
            )

            val response = AppVersionCheckResponse(
                needsUpdate = false,
                isLatestVersion = false,
                latestVersion = "1.4.2",
                minimumVersion = "1.0.0",
                storeUrl = null
            )

            every { appVersionService.checkVersion(Platform.ANDROID, "1.3.0") } returns Mono.just(response)

            // When & Then
            webTestClient
                .post()
                .uri("${ApiPaths.API_V1_APP_VERSION}/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.needsUpdate").isEqualTo(false)
                .jsonPath("$.isLatestVersion").isEqualTo(false)
        }

        @Test
        @DisplayName("플랫폼에 대한 버전 설정이 없으면 404 Not Found를 반환한다")
        fun returns404WhenNoConfigFound() {
            // Given
            val request = AppVersionCheckRequest(
                platform = "IOS",
                currentVersion = "1.0.0"
            )

            every { appVersionService.checkVersion(Platform.IOS, "1.0.0") } returns
                Mono.error(AppVersionNotFoundException("IOS"))

            // When & Then
            webTestClient
                .post()
                .uri("${ApiPaths.API_V1_APP_VERSION}/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound
        }

        @Test
        @DisplayName("잘못된 플랫폼 형식이면 400 Bad Request를 반환한다")
        fun returns400WhenInvalidPlatform() {
            // Given: 잘못된 플랫폼 (WINDOWS는 존재하지 않음)
            val invalidRequest = """
                {
                    "platform": "WINDOWS",
                    "currentVersion": "1.0.0"
                }
            """.trimIndent()

            // When & Then
            webTestClient
                .post()
                .uri("${ApiPaths.API_V1_APP_VERSION}/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("잘못된 버전 형식이면 400 Bad Request를 반환한다")
        fun returns400WhenInvalidVersionFormat() {
            // Given: 잘못된 버전 형식
            val invalidRequest = """
                {
                    "platform": "IOS",
                    "currentVersion": "1.0"
                }
            """.trimIndent()

            // When & Then
            webTestClient
                .post()
                .uri("${ApiPaths.API_V1_APP_VERSION}/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("플랫폼 필드가 누락되면 400 Bad Request를 반환한다")
        fun returns400WhenPlatformMissing() {
            // Given: platform 필드 누락
            val invalidRequest = """
                {
                    "currentVersion": "1.0.0"
                }
            """.trimIndent()

            // When & Then
            webTestClient
                .post()
                .uri("${ApiPaths.API_V1_APP_VERSION}/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("currentVersion 필드가 누락되면 400 Bad Request를 반환한다")
        fun returns400WhenCurrentVersionMissing() {
            // Given: currentVersion 필드 누락
            val invalidRequest = """
                {
                    "platform": "IOS"
                }
            """.trimIndent()

            // When & Then
            webTestClient
                .post()
                .uri("${ApiPaths.API_V1_APP_VERSION}/check")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest
        }
    }
}
