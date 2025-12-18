package me.onetwo.upvy.infrastructure.common.controller

import me.onetwo.upvy.config.TestSecurityConfig
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
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * WellKnownController 테스트
 *
 * Universal Links (iOS)와 App Links (Android)를 위한
 * .well-known 엔드포인트 테스트
 *
 * @see WellKnownController
 */
@WebFluxTest(WellKnownController::class)
@Import(RestDocsConfiguration::class, TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("Well-Known Controller 테스트")
class WellKnownControllerTest : BaseReactiveTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Nested
    @DisplayName("GET /.well-known/apple-app-site-association - iOS Universal Links 검증 파일 조회")
    inner class GetAppleAppSiteAssociation {

        @Test
        @DisplayName("iOS Universal Links 검증 파일 조회 시, 200 OK와 올바른 JSON 형식을 반환한다")
        fun getAppleAppSiteAssociation_ReturnsValidJson() {
            // Given: iOS가 Universal Links 검증을 요청
            // (No authentication required - public endpoint)

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("/.well-known/apple-app-site-association")
                .exchange()
                .expectStatus().isOk
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.applinks").exists()
                .jsonPath("$.applinks.apps").isEmpty
                .jsonPath("$.applinks.details").isArray
                .jsonPath("$.applinks.details[0].appID").isNotEmpty
                .jsonPath("$.applinks.details[0].paths").isArray
                .jsonPath("$.applinks.details[0].paths[0]").isEqualTo("/watch/*")
                .jsonPath("$.webcredentials").exists()
                .jsonPath("$.webcredentials.apps").isArray
                .consumeWith(
                    document(
                        "well-known-apple-app-site-association",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint())
                    )
                )
        }

        @Test
        @DisplayName("apple-app-site-association 파일은 인증 없이 접근 가능해야 한다")
        fun getAppleAppSiteAssociation_NoAuthenticationRequired() {
            // Given: 인증되지 않은 요청 (iOS 플랫폼의 자동 검증)

            // When & Then: 인증 없이도 접근 가능
            webTestClient
                .get()
                .uri("/.well-known/apple-app-site-association")
                .exchange()
                .expectStatus().isOk
        }
    }

    @Nested
    @DisplayName("GET /.well-known/assetlinks.json - Android App Links 검증 파일 조회")
    inner class GetAssetLinks {

        @Test
        @DisplayName("Android App Links 검증 파일 조회 시, 200 OK와 올바른 JSON 형식을 반환한다")
        fun getAssetLinks_ReturnsValidJson() {
            // Given: Android가 App Links 검증을 요청
            // (No authentication required - public endpoint)

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("/.well-known/assetlinks.json")
                .exchange()
                .expectStatus().isOk
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$").isArray
                .jsonPath("$[0].relation").isArray
                .jsonPath("$[0].relation[0]").isEqualTo("delegate_permission/common.handle_all_urls")
                .jsonPath("$[0].target").exists()
                .jsonPath("$[0].target.namespace").isEqualTo("android_app")
                .jsonPath("$[0].target.package_name").isEqualTo("com.upvy.app")
                .jsonPath("$[0].target.sha256_cert_fingerprints").isArray
                .jsonPath("$[0].target.sha256_cert_fingerprints[0]").isNotEmpty
                .consumeWith(
                    document(
                        "well-known-assetlinks",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint())
                    )
                )
        }

        @Test
        @DisplayName("assetlinks.json 파일은 인증 없이 접근 가능해야 한다")
        fun getAssetLinks_NoAuthenticationRequired() {
            // Given: 인증되지 않은 요청 (Android 플랫폼의 자동 검증)

            // When & Then: 인증 없이도 접근 가능
            webTestClient
                .get()
                .uri("/.well-known/assetlinks.json")
                .exchange()
                .expectStatus().isOk
        }
    }

    @Nested
    @DisplayName("GET /watch/{contentId} - 콘텐츠 링크 Fallback 리다이렉트")
    inner class WatchRedirect {

        @Test
        @DisplayName("iOS User-Agent로 접근 시, App Store로 302 리다이렉트한다")
        fun watchRedirect_WithIOSUserAgent_RedirectsToAppStore() {
            // Given: iPhone에서 브라우저로 링크를 연 경우
            val contentId = "test-content-id"
            val userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)"

            // When & Then: App Store로 리다이렉트
            webTestClient
                .get()
                .uri("/watch/{contentId}", contentId)
                .header("User-Agent", userAgent)
                .exchange()
                .expectStatus().isFound
                .expectHeader().location("https://apps.apple.com/app/upvy/id6756291696")
                .expectBody().isEmpty
        }

        @Test
        @DisplayName("iPad User-Agent로 접근 시, App Store로 302 리다이렉트한다")
        fun watchRedirect_WithIPadUserAgent_RedirectsToAppStore() {
            // Given: iPad에서 브라우저로 링크를 연 경우
            val contentId = "test-content-id"
            val userAgent = "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X)"

            // When & Then: App Store로 리다이렉트
            webTestClient
                .get()
                .uri("/watch/{contentId}", contentId)
                .header("User-Agent", userAgent)
                .exchange()
                .expectStatus().isFound
                .expectHeader().location("https://apps.apple.com/app/upvy/id6756291696")
        }

        @Test
        @DisplayName("iPod User-Agent로 접근 시, App Store로 302 리다이렉트한다")
        fun watchRedirect_WithIPodUserAgent_RedirectsToAppStore() {
            // Given: iPod touch에서 브라우저로 링크를 연 경우
            val contentId = "test-content-id"
            val userAgent = "Mozilla/5.0 (iPod; CPU iPhone OS 17_0 like Mac OS X)"

            // When & Then: App Store로 리다이렉트
            webTestClient
                .get()
                .uri("/watch/{contentId}", contentId)
                .header("User-Agent", userAgent)
                .exchange()
                .expectStatus().isFound
                .expectHeader().location("https://apps.apple.com/app/upvy/id6756291696")
        }

        @Test
        @DisplayName("Android User-Agent로 접근 시, Play Store로 302 리다이렉트한다")
        fun watchRedirect_WithAndroidUserAgent_RedirectsToPlayStore() {
            // Given: Android에서 브라우저로 링크를 연 경우
            val contentId = "test-content-id"
            val userAgent = "Mozilla/5.0 (Linux; Android 13)"

            // When & Then: Play Store로 리다이렉트
            webTestClient
                .get()
                .uri("/watch/{contentId}", contentId)
                .header("User-Agent", userAgent)
                .exchange()
                .expectStatus().isFound
                .expectHeader().location("https://play.google.com/store/apps/details?id=com.upvy.app")
                .expectBody().isEmpty
        }

        @Test
        @DisplayName("Desktop/Unknown User-Agent로 접근 시, Docs 홈페이지로 302 리다이렉트한다")
        fun watchRedirect_WithDesktopUserAgent_RedirectsToDocsHomepage() {
            // Given: Desktop 브라우저에서 링크를 연 경우
            val contentId = "test-content-id"
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"

            // When & Then: Docs 홈페이지로 리다이렉트
            webTestClient
                .get()
                .uri("/watch/{contentId}", contentId)
                .header("User-Agent", userAgent)
                .exchange()
                .expectStatus().isFound
                .expectHeader().location("https://upvy.org")
                .expectBody().isEmpty
        }

        @Test
        @DisplayName("User-Agent 헤더가 없을 경우, Docs 홈페이지로 302 리다이렉트한다")
        fun watchRedirect_WithoutUserAgent_RedirectsToDocsHomepage() {
            // Given: User-Agent 헤더가 없는 요청
            val contentId = "test-content-id"

            // When & Then: 기본값으로 Docs 홈페이지로 리다이렉트
            webTestClient
                .get()
                .uri("/watch/{contentId}", contentId)
                .exchange()
                .expectStatus().isFound
                .expectHeader().location("https://upvy.org")
        }

        @Test
        @DisplayName("/watch 엔드포인트는 인증 없이 접근 가능해야 한다")
        fun watchRedirect_NoAuthenticationRequired() {
            // Given: 인증되지 않은 요청 (브라우저에서 링크 클릭)
            val contentId = "test-content-id"

            // When & Then: 인증 없이도 접근 가능
            webTestClient
                .get()
                .uri("/watch/{contentId}", contentId)
                .exchange()
                .expectStatus().isFound
        }
    }
}
