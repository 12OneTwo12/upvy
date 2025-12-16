package me.onetwo.upvy.domain.auth.terms.service

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.domain.auth.terms.dto.TermsAgreementRequest
import me.onetwo.upvy.domain.auth.terms.model.AgreementAction
import me.onetwo.upvy.domain.auth.terms.model.TermsType
import me.onetwo.upvy.domain.auth.terms.model.TermsVersions
import me.onetwo.upvy.domain.auth.terms.model.UserTermsAgreement
import me.onetwo.upvy.domain.auth.terms.model.UserTermsAgreementHistory
import me.onetwo.upvy.domain.auth.terms.repository.TermsAgreementRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * TermsAgreementService 단위 테스트
 *
 * 약관 동의 Service의 비즈니스 로직을 검증합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("약관 동의 Service 단위 테스트")
class TermsAgreementServiceImplTest : BaseReactiveTest {

    private lateinit var termsAgreementRepository: TermsAgreementRepository
    private lateinit var termsAgreementService: TermsAgreementService

    private val testUserId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        termsAgreementRepository = mockk()
        termsAgreementService = TermsAgreementServiceImpl(termsAgreementRepository)
    }

    @Nested
    @DisplayName("agreeToTerms - 약관 동의 처리")
    inner class AgreeToTerms {

        @Test
        @DisplayName("신규 약관 동의 시, 모든 필수 약관이 저장되고 이력이 생성된다")
        fun agreeToTerms_NewAgreement_SavesAndCreatesHistory() {
            // Given: 신규 약관 동의 요청
            val request = TermsAgreementRequest(
                serviceTermsAgreed = true,
                privacyPolicyAgreed = true,
                communityGuidelinesAgreed = true,
                marketingAgreed = false
            )
            val ipAddress = "127.0.0.1"
            val userAgent = "Mozilla/5.0"

            // 기존 약관 동의 없음
            every { termsAgreementRepository.findByUserId(testUserId) } returns Mono.empty()

            // 저장 모킹
            val savedAgreementSlot = slot<UserTermsAgreement>()
            every { termsAgreementRepository.save(capture(savedAgreementSlot)) } answers {
                Mono.just(savedAgreementSlot.captured.copy(id = UUID.randomUUID()))
            }

            // 이력 저장 모킹 (3개: SERVICE_TERMS, PRIVACY_POLICY, COMMUNITY_GUIDELINES)
            every { termsAgreementRepository.saveHistory(any()) } returns Mono.empty()

            // When: 약관 동의 처리
            val result = termsAgreementService.agreeToTerms(testUserId, request, ipAddress, userAgent)

            // Then: 약관 동의가 저장되고 응답이 반환됨
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.userId).isEqualTo(testUserId.toString())
                    assertThat(response.serviceTermsAgreed).isTrue()
                    assertThat(response.serviceTermsVersion).isEqualTo(TermsVersions.CURRENT_SERVICE_TERMS_VERSION)
                    assertThat(response.serviceTermsAgreedAt).isNotNull()
                    assertThat(response.privacyPolicyAgreed).isTrue()
                    assertThat(response.privacyPolicyVersion).isEqualTo(TermsVersions.CURRENT_PRIVACY_POLICY_VERSION)
                    assertThat(response.privacyPolicyAgreedAt).isNotNull()
                    assertThat(response.communityGuidelinesAgreed).isTrue()
                    assertThat(response.communityGuidelinesVersion).isEqualTo(TermsVersions.CURRENT_COMMUNITY_GUIDELINES_VERSION)
                    assertThat(response.communityGuidelinesAgreedAt).isNotNull()
                    assertThat(response.marketingAgreed).isFalse()
                    assertThat(response.isAllRequiredAgreed).isTrue()
                }
                .verifyComplete()

            // 저장 및 이력 기록 확인
            verify(exactly = 1) { termsAgreementRepository.save(any()) }
            verify(exactly = 3) { termsAgreementRepository.saveHistory(any()) }
        }

        @Test
        @DisplayName("마케팅 동의 포함 시, 4개의 이력이 생성된다")
        fun agreeToTerms_WithMarketing_CreatesFourHistories() {
            // Given: 마케팅 동의 포함
            val request = TermsAgreementRequest(
                serviceTermsAgreed = true,
                privacyPolicyAgreed = true,
                communityGuidelinesAgreed = true,
                marketingAgreed = true  // 마케팅 동의
            )
            val ipAddress = "127.0.0.1"
            val userAgent = "Mozilla/5.0"

            // 기존 약관 동의 없음
            every { termsAgreementRepository.findByUserId(testUserId) } returns Mono.empty()

            // 저장 모킹
            val savedAgreementSlot = slot<UserTermsAgreement>()
            every { termsAgreementRepository.save(capture(savedAgreementSlot)) } answers {
                Mono.just(savedAgreementSlot.captured.copy(id = UUID.randomUUID()))
            }

            // 이력 저장 모킹
            every { termsAgreementRepository.saveHistory(any()) } returns Mono.empty()

            // When: 약관 동의 처리
            val result = termsAgreementService.agreeToTerms(testUserId, request, ipAddress, userAgent)

            // Then: 4개의 이력 생성 (필수 3개 + 마케팅 1개)
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.marketingAgreed).isTrue()
                    assertThat(response.marketingVersion).isEqualTo(TermsVersions.CURRENT_MARKETING_VERSION)
                    assertThat(response.marketingAgreedAt).isNotNull()
                }
                .verifyComplete()

            verify(exactly = 4) { termsAgreementRepository.saveHistory(any()) }
        }

        @Test
        @DisplayName("기존 약관 동의 업데이트 시, UPDATED 액션으로 이력이 생성된다")
        fun agreeToTerms_UpdateExisting_CreatesUpdatedHistory() {
            // Given: 기존 약관 동의 존재
            val existingAgreement = UserTermsAgreement(
                id = UUID.randomUUID(),
                userId = testUserId,
                serviceTermsAgreed = true,
                serviceTermsVersion = TermsVersions.CURRENT_SERVICE_TERMS_VERSION,
                serviceTermsAgreedAt = Instant.now().minusSeconds(3600),
                privacyPolicyAgreed = true,
                privacyPolicyVersion = TermsVersions.CURRENT_PRIVACY_POLICY_VERSION,
                privacyPolicyAgreedAt = Instant.now().minusSeconds(3600),
                communityGuidelinesAgreed = true,
                communityGuidelinesVersion = TermsVersions.CURRENT_COMMUNITY_GUIDELINES_VERSION,
                communityGuidelinesAgreedAt = Instant.now().minusSeconds(3600),
                marketingAgreed = false
            )

            val request = TermsAgreementRequest(
                serviceTermsAgreed = true,
                privacyPolicyAgreed = true,
                communityGuidelinesAgreed = true,
                marketingAgreed = true  // 신규 마케팅 동의
            )

            // 기존 약관 동의 반환
            every { termsAgreementRepository.findByUserId(testUserId) } returns Mono.just(existingAgreement)

            // 저장 모킹
            every { termsAgreementRepository.save(any()) } returns Mono.just(existingAgreement)

            // 이력 저장 모킹
            val historySlots = mutableListOf<UserTermsAgreementHistory>()
            every { termsAgreementRepository.saveHistory(capture(historySlots)) } returns Mono.empty()

            // When: 약관 동의 처리
            val result = termsAgreementService.agreeToTerms(testUserId, request, "127.0.0.1", "Mozilla/5.0")

            // Then: 이력의 action이 UPDATED 또는 AGREED
            StepVerifier.create(result)
                .assertNext { /* success */ }
                .verifyComplete()

            verify(exactly = 4) { termsAgreementRepository.saveHistory(any()) }

            // 필수 약관은 UPDATED, 마케팅은 AGREED
            val serviceTermsHistory = historySlots.find { it.termsType == TermsType.SERVICE_TERMS }
            val marketingHistory = historySlots.find { it.termsType == TermsType.MARKETING_CONSENT }

            assertThat(serviceTermsHistory?.action).isEqualTo(AgreementAction.UPDATED)
            assertThat(marketingHistory?.action).isEqualTo(AgreementAction.AGREED)
        }

        @Test
        @DisplayName("IP 주소와 User Agent가 이력에 저장된다")
        fun agreeToTerms_SavesIpAndUserAgent() {
            // Given: 약관 동의 요청
            val request = TermsAgreementRequest(
                serviceTermsAgreed = true,
                privacyPolicyAgreed = true,
                communityGuidelinesAgreed = true,
                marketingAgreed = false
            )
            val ipAddress = "192.168.1.1"
            val userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X)"

            // 기존 약관 동의 없음
            every { termsAgreementRepository.findByUserId(testUserId) } returns Mono.empty()

            // 저장 모킹
            every { termsAgreementRepository.save(any()) } returns Mono.just(
                UserTermsAgreement(id = UUID.randomUUID(), userId = testUserId)
            )

            // 이력 저장 모킹
            val historySlots = mutableListOf<UserTermsAgreementHistory>()
            every { termsAgreementRepository.saveHistory(capture(historySlots)) } returns Mono.empty()

            // When: 약관 동의 처리
            val result = termsAgreementService.agreeToTerms(testUserId, request, ipAddress, userAgent)

            // Then: IP와 User Agent가 저장됨
            StepVerifier.create(result)
                .assertNext { /* success */ }
                .verifyComplete()

            historySlots.forEach { history ->
                assertThat(history.ipAddress).isEqualTo(ipAddress)
                assertThat(history.userAgent).isEqualTo(userAgent)
            }
        }
    }

    @Nested
    @DisplayName("getTermsAgreement - 약관 동의 상태 조회")
    inner class GetTermsAgreement {

        @Test
        @DisplayName("약관 동의가 존재하는 경우, 약관 동의 정보를 반환한다")
        fun getTermsAgreement_WhenExists_ReturnsAgreement() {
            // Given: 기존 약관 동의
            val now = Instant.now()
            val agreement = UserTermsAgreement(
                id = UUID.randomUUID(),
                userId = testUserId,
                serviceTermsAgreed = true,
                serviceTermsVersion = TermsVersions.CURRENT_SERVICE_TERMS_VERSION,
                serviceTermsAgreedAt = now,
                privacyPolicyAgreed = true,
                privacyPolicyVersion = TermsVersions.CURRENT_PRIVACY_POLICY_VERSION,
                privacyPolicyAgreedAt = now,
                communityGuidelinesAgreed = true,
                communityGuidelinesVersion = TermsVersions.CURRENT_COMMUNITY_GUIDELINES_VERSION,
                communityGuidelinesAgreedAt = now,
                marketingAgreed = false
            )

            every { termsAgreementRepository.findByUserId(testUserId) } returns Mono.just(agreement)

            // When: 약관 동의 조회
            val result = termsAgreementService.getTermsAgreement(testUserId)

            // Then: 약관 동의 정보 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.userId).isEqualTo(testUserId.toString())
                    assertThat(response.serviceTermsAgreed).isTrue()
                    assertThat(response.serviceTermsVersion).isEqualTo(TermsVersions.CURRENT_SERVICE_TERMS_VERSION)
                    assertThat(response.privacyPolicyAgreed).isTrue()
                    assertThat(response.communityGuidelinesAgreed).isTrue()
                    assertThat(response.marketingAgreed).isFalse()
                    assertThat(response.isAllRequiredAgreed).isTrue()
                }
                .verifyComplete()

            verify(exactly = 1) { termsAgreementRepository.findByUserId(testUserId) }
        }

        @Test
        @DisplayName("약관 동의가 존재하지 않는 경우, 모든 필드가 false인 응답을 반환한다")
        fun getTermsAgreement_WhenNotExists_ReturnsDefaultResponse() {
            // Given: 약관 동의 없음
            every { termsAgreementRepository.findByUserId(testUserId) } returns Mono.empty()

            // When: 약관 동의 조회
            val result = termsAgreementService.getTermsAgreement(testUserId)

            // Then: 기본값 응답 (모든 필드 false)
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.userId).isEqualTo(testUserId.toString())
                    assertThat(response.serviceTermsAgreed).isFalse()
                    assertThat(response.serviceTermsVersion).isNull()
                    assertThat(response.serviceTermsAgreedAt).isNull()
                    assertThat(response.privacyPolicyAgreed).isFalse()
                    assertThat(response.privacyPolicyVersion).isNull()
                    assertThat(response.privacyPolicyAgreedAt).isNull()
                    assertThat(response.communityGuidelinesAgreed).isFalse()
                    assertThat(response.communityGuidelinesVersion).isNull()
                    assertThat(response.communityGuidelinesAgreedAt).isNull()
                    assertThat(response.marketingAgreed).isFalse()
                    assertThat(response.marketingVersion).isNull()
                    assertThat(response.marketingAgreedAt).isNull()
                    assertThat(response.isAllRequiredAgreed).isFalse()
                }
                .verifyComplete()

            verify(exactly = 1) { termsAgreementRepository.findByUserId(testUserId) }
        }

        @Test
        @DisplayName("필수 약관 중 하나라도 미동의 시, isAllRequiredAgreed가 false다")
        fun getTermsAgreement_NotAllAgreed_IsAllRequiredAgreedIsFalse() {
            // Given: 커뮤니티 가이드라인 미동의
            val agreement = UserTermsAgreement(
                id = UUID.randomUUID(),
                userId = testUserId,
                serviceTermsAgreed = true,
                serviceTermsVersion = TermsVersions.CURRENT_SERVICE_TERMS_VERSION,
                serviceTermsAgreedAt = Instant.now(),
                privacyPolicyAgreed = true,
                privacyPolicyVersion = TermsVersions.CURRENT_PRIVACY_POLICY_VERSION,
                privacyPolicyAgreedAt = Instant.now(),
                communityGuidelinesAgreed = false,  // 미동의
                marketingAgreed = false
            )

            every { termsAgreementRepository.findByUserId(testUserId) } returns Mono.just(agreement)

            // When: 약관 동의 조회
            val result = termsAgreementService.getTermsAgreement(testUserId)

            // Then: isAllRequiredAgreed가 false
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.isAllRequiredAgreed).isFalse()
                }
                .verifyComplete()
        }
    }
}
