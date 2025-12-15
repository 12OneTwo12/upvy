package me.onetwo.upvy.domain.auth.terms.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.auth.terms.dto.TermsAgreementRequest
import me.onetwo.upvy.domain.auth.terms.dto.TermsAgreementResponse
import me.onetwo.upvy.domain.auth.terms.model.TermsVersions
import me.onetwo.upvy.domain.auth.terms.service.TermsAgreementService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.util.mockUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * TermsAgreementController REST Docs 테스트
 *
 * 약관 동의 API의 REST Docs 문서화와 Validation 검증을 테스트합니다.
 */
@WebFluxTest(TermsAgreementController::class)
@AutoConfigureRestDocs
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("약관 동의 컨트롤러 REST Docs 테스트")
class TermsAgreementControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var termsAgreementService: TermsAgreementService

    @Nested
    @DisplayName("POST /api/v1/auth/terms-agreement - 약관 동의 처리")
    inner class AgreeToTerms {

        @Test
        @DisplayName("모든 필수 약관 동의 시, 201 Created와 약관 동의 정보를 반환한다")
        fun agreeToTerms_WithAllRequiredTerms_Returns201Created() {
            // Given
            val userId = UUID.randomUUID()
            val request = TermsAgreementRequest(
                serviceTermsAgreed = true,
                privacyPolicyAgreed = true,
                communityGuidelinesAgreed = true,
                marketingAgreed = false
            )

            val now = Instant.now()
            val response = TermsAgreementResponse(
                userId = userId.toString(),
                serviceTermsAgreed = true,
                serviceTermsVersion = TermsVersions.CURRENT_SERVICE_TERMS_VERSION,
                serviceTermsAgreedAt = now,
                privacyPolicyAgreed = true,
                privacyPolicyVersion = TermsVersions.CURRENT_PRIVACY_POLICY_VERSION,
                privacyPolicyAgreedAt = now,
                communityGuidelinesAgreed = true,
                communityGuidelinesVersion = TermsVersions.CURRENT_COMMUNITY_GUIDELINES_VERSION,
                communityGuidelinesAgreedAt = now,
                marketingAgreed = false,
                marketingVersion = null,
                marketingAgreedAt = null,
                isAllRequiredAgreed = true
            )

            every {
                termsAgreementService.agreeToTerms(userId, request, any(), any())
            } returns Mono.just(response)

            // When & Then
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1_AUTH}/terms-agreement")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .consumeWith(
                    document(
                        "auth/terms-agreement-agree",
                        requestFields(
                            fieldWithPath("serviceTermsAgreed")
                                .description("서비스 이용약관 동의 여부 (필수, true 필요)"),
                            fieldWithPath("privacyPolicyAgreed")
                                .description("개인정보 처리방침 동의 여부 (필수, true 필요)"),
                            fieldWithPath("communityGuidelinesAgreed")
                                .description("커뮤니티 가이드라인 동의 여부 (필수, true 필요)"),
                            fieldWithPath("marketingAgreed")
                                .description("마케팅 정보 수신 동의 여부 (선택, 기본값: false)")
                        ),
                        responseFields(
                            fieldWithPath("userId")
                                .description("사용자 UUID"),
                            fieldWithPath("serviceTermsAgreed")
                                .description("서비스 이용약관 동의 여부"),
                            fieldWithPath("serviceTermsVersion")
                                .description("서비스 이용약관 버전 (예: v1.0)"),
                            fieldWithPath("serviceTermsAgreedAt")
                                .description("서비스 이용약관 동의 시각 (ISO-8601)"),
                            fieldWithPath("privacyPolicyAgreed")
                                .description("개인정보 처리방침 동의 여부"),
                            fieldWithPath("privacyPolicyVersion")
                                .description("개인정보 처리방침 버전"),
                            fieldWithPath("privacyPolicyAgreedAt")
                                .description("개인정보 처리방침 동의 시각"),
                            fieldWithPath("communityGuidelinesAgreed")
                                .description("커뮤니티 가이드라인 동의 여부"),
                            fieldWithPath("communityGuidelinesVersion")
                                .description("커뮤니티 가이드라인 버전"),
                            fieldWithPath("communityGuidelinesAgreedAt")
                                .description("커뮤니티 가이드라인 동의 시각"),
                            fieldWithPath("marketingAgreed")
                                .description("마케팅 정보 수신 동의 여부 (선택)"),
                            fieldWithPath("marketingVersion")
                                .description("마케팅 정보 수신 동의 버전 (동의하지 않았으면 null)"),
                            fieldWithPath("marketingAgreedAt")
                                .description("마케팅 정보 수신 동의 시각 (동의하지 않았으면 null)"),
                            fieldWithPath("isAllRequiredAgreed")
                                .description("모든 필수 약관 동의 여부 (서비스 이용약관, 개인정보 처리방침, 커뮤니티 가이드라인)")
                        )
                    )
                )

            verify(exactly = 1) {
                termsAgreementService.agreeToTerms(userId, request, any(), any())
            }
        }

        @Test
        @DisplayName("마케팅 동의 포함 시, 201 Created와 마케팅 동의 정보를 반환한다")
        fun agreeToTerms_WithMarketingConsent_Returns201WithMarketingInfo() {
            // Given
            val userId = UUID.randomUUID()
            val request = TermsAgreementRequest(
                serviceTermsAgreed = true,
                privacyPolicyAgreed = true,
                communityGuidelinesAgreed = true,
                marketingAgreed = true  // 마케팅 동의
            )

            val now = Instant.now()
            val response = TermsAgreementResponse(
                userId = userId.toString(),
                serviceTermsAgreed = true,
                serviceTermsVersion = TermsVersions.CURRENT_SERVICE_TERMS_VERSION,
                serviceTermsAgreedAt = now,
                privacyPolicyAgreed = true,
                privacyPolicyVersion = TermsVersions.CURRENT_PRIVACY_POLICY_VERSION,
                privacyPolicyAgreedAt = now,
                communityGuidelinesAgreed = true,
                communityGuidelinesVersion = TermsVersions.CURRENT_COMMUNITY_GUIDELINES_VERSION,
                communityGuidelinesAgreedAt = now,
                marketingAgreed = true,
                marketingVersion = TermsVersions.CURRENT_MARKETING_VERSION,
                marketingAgreedAt = now,
                isAllRequiredAgreed = true
            )

            every {
                termsAgreementService.agreeToTerms(userId, request, any(), any())
            } returns Mono.just(response)

            // When & Then
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1_AUTH}/terms-agreement")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.marketingAgreed").isEqualTo(true)
                .jsonPath("$.marketingVersion").isEqualTo(TermsVersions.CURRENT_MARKETING_VERSION)
                .jsonPath("$.marketingAgreedAt").isNotEmpty

            verify(exactly = 1) {
                termsAgreementService.agreeToTerms(userId, request, any(), any())
            }
        }

        @Test
        @DisplayName("서비스 이용약관 미동의 시, 400 Bad Request를 반환한다")
        fun agreeToTerms_WithoutServiceTerms_Returns400BadRequest() {
            // Given
            val userId = UUID.randomUUID()
            val invalidRequest = mapOf(
                "serviceTermsAgreed" to false,  // 필수 약관 미동의
                "privacyPolicyAgreed" to true,
                "communityGuidelinesAgreed" to true,
                "marketingAgreed" to false
            )

            // When & Then
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1_AUTH}/terms-agreement")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("개인정보 처리방침 미동의 시, 400 Bad Request를 반환한다")
        fun agreeToTerms_WithoutPrivacyPolicy_Returns400BadRequest() {
            // Given
            val userId = UUID.randomUUID()
            val invalidRequest = mapOf(
                "serviceTermsAgreed" to true,
                "privacyPolicyAgreed" to false,  // 필수 약관 미동의
                "communityGuidelinesAgreed" to true,
                "marketingAgreed" to false
            )

            // When & Then
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1_AUTH}/terms-agreement")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("커뮤니티 가이드라인 미동의 시, 400 Bad Request를 반환한다")
        fun agreeToTerms_WithoutCommunityGuidelines_Returns400BadRequest() {
            // Given
            val userId = UUID.randomUUID()
            val invalidRequest = mapOf(
                "serviceTermsAgreed" to true,
                "privacyPolicyAgreed" to true,
                "communityGuidelinesAgreed" to false,  // 필수 약관 미동의
                "marketingAgreed" to false
            )

            // When & Then
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1_AUTH}/terms-agreement")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("필수 필드 누락 시, 400 Bad Request를 반환한다")
        fun agreeToTerms_WithMissingFields_Returns400BadRequest() {
            // Given
            val userId = UUID.randomUUID()
            val invalidRequest = mapOf(
                "serviceTermsAgreed" to true
                // privacyPolicyAgreed, communityGuidelinesAgreed 누락
            )

            // When & Then
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1_AUTH}/terms-agreement")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/terms-agreement - 약관 동의 상태 조회")
    inner class GetTermsAgreement {

        @Test
        @DisplayName("약관 동의가 존재하는 경우, 200 OK와 약관 동의 정보를 반환한다")
        fun getTermsAgreement_WhenExists_Returns200WithAgreementInfo() {
            // Given
            val userId = UUID.randomUUID()
            val now = Instant.now()
            val response = TermsAgreementResponse(
                userId = userId.toString(),
                serviceTermsAgreed = true,
                serviceTermsVersion = TermsVersions.CURRENT_SERVICE_TERMS_VERSION,
                serviceTermsAgreedAt = now,
                privacyPolicyAgreed = true,
                privacyPolicyVersion = TermsVersions.CURRENT_PRIVACY_POLICY_VERSION,
                privacyPolicyAgreedAt = now,
                communityGuidelinesAgreed = true,
                communityGuidelinesVersion = TermsVersions.CURRENT_COMMUNITY_GUIDELINES_VERSION,
                communityGuidelinesAgreedAt = now,
                marketingAgreed = false,
                marketingVersion = null,
                marketingAgreedAt = null,
                isAllRequiredAgreed = true
            )

            every {
                termsAgreementService.getTermsAgreement(userId)
            } returns Mono.just(response)

            // When & Then
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri("${ApiPaths.API_V1_AUTH}/terms-agreement")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .consumeWith(
                    document(
                        "auth/terms-agreement-get",
                        responseFields(
                            fieldWithPath("userId")
                                .description("사용자 UUID"),
                            fieldWithPath("serviceTermsAgreed")
                                .description("서비스 이용약관 동의 여부"),
                            fieldWithPath("serviceTermsVersion")
                                .description("서비스 이용약관 버전"),
                            fieldWithPath("serviceTermsAgreedAt")
                                .description("서비스 이용약관 동의 시각"),
                            fieldWithPath("privacyPolicyAgreed")
                                .description("개인정보 처리방침 동의 여부"),
                            fieldWithPath("privacyPolicyVersion")
                                .description("개인정보 처리방침 버전"),
                            fieldWithPath("privacyPolicyAgreedAt")
                                .description("개인정보 처리방침 동의 시각"),
                            fieldWithPath("communityGuidelinesAgreed")
                                .description("커뮤니티 가이드라인 동의 여부"),
                            fieldWithPath("communityGuidelinesVersion")
                                .description("커뮤니티 가이드라인 버전"),
                            fieldWithPath("communityGuidelinesAgreedAt")
                                .description("커뮤니티 가이드라인 동의 시각"),
                            fieldWithPath("marketingAgreed")
                                .description("마케팅 정보 수신 동의 여부 (선택)"),
                            fieldWithPath("marketingVersion")
                                .description("마케팅 정보 수신 동의 버전 (동의하지 않았으면 null)"),
                            fieldWithPath("marketingAgreedAt")
                                .description("마케팅 정보 수신 동의 시각 (동의하지 않았으면 null)"),
                            fieldWithPath("isAllRequiredAgreed")
                                .description("모든 필수 약관 동의 여부")
                        )
                    )
                )
                .jsonPath("$.userId").isEqualTo(userId.toString())
                .jsonPath("$.isAllRequiredAgreed").isEqualTo(true)

            verify(exactly = 1) {
                termsAgreementService.getTermsAgreement(userId)
            }
        }

        @Test
        @DisplayName("약관 동의가 존재하지 않는 경우, 200 OK와 기본값 응답을 반환한다")
        fun getTermsAgreement_WhenNotExists_Returns200WithDefaultResponse() {
            // Given
            val userId = UUID.randomUUID()
            val defaultResponse = TermsAgreementResponse(
                userId = userId.toString(),
                serviceTermsAgreed = false,
                serviceTermsVersion = null,
                serviceTermsAgreedAt = null,
                privacyPolicyAgreed = false,
                privacyPolicyVersion = null,
                privacyPolicyAgreedAt = null,
                communityGuidelinesAgreed = false,
                communityGuidelinesVersion = null,
                communityGuidelinesAgreedAt = null,
                marketingAgreed = false,
                marketingVersion = null,
                marketingAgreedAt = null,
                isAllRequiredAgreed = false
            )

            every {
                termsAgreementService.getTermsAgreement(userId)
            } returns Mono.just(defaultResponse)

            // When & Then
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri("${ApiPaths.API_V1_AUTH}/terms-agreement")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.userId").isEqualTo(userId.toString())
                .jsonPath("$.serviceTermsAgreed").isEqualTo(false)
                .jsonPath("$.privacyPolicyAgreed").isEqualTo(false)
                .jsonPath("$.communityGuidelinesAgreed").isEqualTo(false)
                .jsonPath("$.marketingAgreed").isEqualTo(false)
                .jsonPath("$.isAllRequiredAgreed").isEqualTo(false)

            verify(exactly = 1) {
                termsAgreementService.getTermsAgreement(userId)
            }
        }
    }
}
