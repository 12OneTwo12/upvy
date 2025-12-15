package me.onetwo.upvy.domain.auth.terms.repository

import me.onetwo.upvy.domain.auth.terms.model.AgreementAction
import me.onetwo.upvy.domain.auth.terms.model.TermsType
import me.onetwo.upvy.domain.auth.terms.model.TermsVersions
import me.onetwo.upvy.domain.auth.terms.model.UserTermsAgreement
import me.onetwo.upvy.domain.auth.terms.model.UserTermsAgreementHistory
import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.model.UserRole
import me.onetwo.upvy.domain.user.repository.UserRepository
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest
import me.onetwo.upvy.jooq.generated.tables.references.USERS
import me.onetwo.upvy.jooq.generated.tables.references.USER_TERMS_AGREEMENTS
import me.onetwo.upvy.jooq.generated.tables.references.USER_TERMS_AGREEMENT_HISTORY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * TermsAgreementRepository 통합 테스트
 *
 * 약관 동의 Repository의 데이터베이스 연동을 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("약관 동의 Repository 통합 테스트")
class TermsAgreementRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var termsAgreementRepository: TermsAgreementRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        // Given: 테스트용 사용자 생성
        testUser = userRepository.save(
            User(
                email = "test@example.com",
                role = UserRole.USER
            )
        ).block()!!
    }

    @AfterEach
    fun tearDown() {
        // 테스트 데이터 정리
        Mono.from(dslContext.deleteFrom(USER_TERMS_AGREEMENT_HISTORY)).block()
        Mono.from(dslContext.deleteFrom(USER_TERMS_AGREEMENTS)).block()
        Mono.from(dslContext.deleteFrom(USERS)).block()
    }

    @Nested
    @DisplayName("findByUserId - 사용자 ID로 약관 동의 조회")
    inner class FindByUserId {

        @Test
        @DisplayName("약관 동의가 존재하는 경우, 약관 동의 정보를 반환한다")
        fun findByUserId_WhenExists_ReturnsAgreement() {
            // Given: 약관 동의 저장
            val now = Instant.now()
            val agreement = UserTermsAgreement(
                userId = testUser.id!!,
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
            termsAgreementRepository.save(agreement).block()!!

            // When: 사용자 ID로 조회
            val found = termsAgreementRepository.findByUserId(testUser.id!!).block()!!

            // Then: 약관 동의 정보가 올바르게 반환됨
            assertThat(found.id).isNotNull()
            assertThat(found.userId).isEqualTo(testUser.id!!)
            assertThat(found.serviceTermsAgreed).isTrue()
            assertThat(found.serviceTermsVersion).isEqualTo(TermsVersions.CURRENT_SERVICE_TERMS_VERSION)
            assertThat(found.serviceTermsAgreedAt).isNotNull()
            assertThat(found.privacyPolicyAgreed).isTrue()
            assertThat(found.privacyPolicyVersion).isEqualTo(TermsVersions.CURRENT_PRIVACY_POLICY_VERSION)
            assertThat(found.privacyPolicyAgreedAt).isNotNull()
            assertThat(found.communityGuidelinesAgreed).isTrue()
            assertThat(found.communityGuidelinesVersion).isEqualTo(TermsVersions.CURRENT_COMMUNITY_GUIDELINES_VERSION)
            assertThat(found.communityGuidelinesAgreedAt).isNotNull()
            assertThat(found.marketingAgreed).isFalse()
            assertThat(found.isAllRequiredAgreed()).isTrue()
        }

        @Test
        @DisplayName("약관 동의가 존재하지 않는 경우, empty Mono를 반환한다")
        fun findByUserId_WhenNotExists_ReturnsEmpty() {
            // Given: 약관 동의 없음

            // When: 존재하지 않는 사용자 ID로 조회
            val found = termsAgreementRepository.findByUserId(testUser.id!!).blockOptional()

            // Then: empty Mono
            assertThat(found).isEmpty
        }

        @Test
        @DisplayName("삭제된 약관 동의는 조회되지 않는다 (Soft Delete)")
        fun findByUserId_DeletedAgreement_NotReturned() {
            // Given: 약관 동의 저장 후 삭제 (Soft Delete)
            val agreement = UserTermsAgreement(
                userId = testUser.id!!,
                serviceTermsAgreed = true,
                serviceTermsVersion = TermsVersions.CURRENT_SERVICE_TERMS_VERSION,
                serviceTermsAgreedAt = Instant.now(),
                privacyPolicyAgreed = true,
                privacyPolicyVersion = TermsVersions.CURRENT_PRIVACY_POLICY_VERSION,
                privacyPolicyAgreedAt = Instant.now(),
                communityGuidelinesAgreed = true,
                communityGuidelinesVersion = TermsVersions.CURRENT_COMMUNITY_GUIDELINES_VERSION,
                communityGuidelinesAgreedAt = Instant.now()
            )
            val saved = termsAgreementRepository.save(agreement).block()!!

            // Soft Delete 처리
            Mono.from(
                dslContext.update(USER_TERMS_AGREEMENTS)
                    .set(USER_TERMS_AGREEMENTS.DELETED_AT, Instant.now())
                    .where(USER_TERMS_AGREEMENTS.ID.eq(saved.id.toString()))
            ).block()

            // When: 사용자 ID로 조회
            val found = termsAgreementRepository.findByUserId(testUser.id!!).blockOptional()

            // Then: 조회되지 않음
            assertThat(found).isEmpty
        }
    }

    @Nested
    @DisplayName("save - 약관 동의 저장 및 업데이트")
    inner class Save {

        @Test
        @DisplayName("신규 약관 동의 저장 시, ID와 Audit Trail이 설정된다")
        fun save_NewAgreement_SavesWithIdAndAuditTrail() {
            // Given: 신규 약관 동의
            val now = Instant.now()
            val agreement = UserTermsAgreement(
                userId = testUser.id!!,
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

            // When: 저장
            val saved = termsAgreementRepository.save(agreement).block()!!

            // Then: ID와 Audit Trail 설정됨
            assertThat(saved.id).isNotNull()
            assertThat(saved.userId).isEqualTo(testUser.id!!)
            assertThat(saved.createdAt).isNotNull()
            assertThat(saved.createdBy).isEqualTo(testUser.id.toString())
            assertThat(saved.updatedAt).isNotNull()
            assertThat(saved.updatedBy).isEqualTo(testUser.id.toString())
            assertThat(saved.deletedAt).isNull()
        }

        @Test
        @DisplayName("기존 약관 동의 업데이트 시, 변경된 내용이 반영된다")
        fun save_ExistingAgreement_Updates() {
            // Given: 기존 약관 동의 저장
            val now = Instant.now()
            val original = UserTermsAgreement(
                userId = testUser.id!!,
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
            termsAgreementRepository.save(original).block()!!

            // When: 마케팅 동의 업데이트
            val updated = original.copy(
                marketingAgreed = true,
                marketingVersion = TermsVersions.CURRENT_MARKETING_VERSION,
                marketingAgreedAt = now
            )
            termsAgreementRepository.save(updated).block()!!

            // Then: 변경 사항이 반영됨
            val found = termsAgreementRepository.findByUserId(testUser.id!!).block()!!
            assertThat(found.marketingAgreed).isTrue()
            assertThat(found.marketingVersion).isEqualTo(TermsVersions.CURRENT_MARKETING_VERSION)
            assertThat(found.marketingAgreedAt).isNotNull()
        }

        @Test
        @DisplayName("모든 필수 약관 동의 시, isAllRequiredAgreed가 true를 반환한다")
        fun save_AllRequiredAgreed_IsAllRequiredAgreedReturnsTrue() {
            // Given: 모든 필수 약관 동의
            val now = Instant.now()
            val agreement = UserTermsAgreement(
                userId = testUser.id!!,
                serviceTermsAgreed = true,
                serviceTermsVersion = TermsVersions.CURRENT_SERVICE_TERMS_VERSION,
                serviceTermsAgreedAt = now,
                privacyPolicyAgreed = true,
                privacyPolicyVersion = TermsVersions.CURRENT_PRIVACY_POLICY_VERSION,
                privacyPolicyAgreedAt = now,
                communityGuidelinesAgreed = true,
                communityGuidelinesVersion = TermsVersions.CURRENT_COMMUNITY_GUIDELINES_VERSION,
                communityGuidelinesAgreedAt = now
            )

            // When: 저장
            val saved = termsAgreementRepository.save(agreement).block()!!

            // Then: isAllRequiredAgreed가 true
            assertThat(saved.isAllRequiredAgreed()).isTrue()
        }

        @Test
        @DisplayName("필수 약관 중 하나라도 미동의 시, isAllRequiredAgreed가 false를 반환한다")
        fun save_NotAllRequiredAgreed_IsAllRequiredAgreedReturnsFalse() {
            // Given: 커뮤니티 가이드라인 미동의
            val now = Instant.now()
            val agreement = UserTermsAgreement(
                userId = testUser.id!!,
                serviceTermsAgreed = true,
                serviceTermsVersion = TermsVersions.CURRENT_SERVICE_TERMS_VERSION,
                serviceTermsAgreedAt = now,
                privacyPolicyAgreed = true,
                privacyPolicyVersion = TermsVersions.CURRENT_PRIVACY_POLICY_VERSION,
                privacyPolicyAgreedAt = now,
                communityGuidelinesAgreed = false  // 미동의
            )

            // When: 저장
            val saved = termsAgreementRepository.save(agreement).block()!!

            // Then: isAllRequiredAgreed가 false
            assertThat(saved.isAllRequiredAgreed()).isFalse()
        }
    }

    @Nested
    @DisplayName("saveHistory - 약관 동의 이력 저장")
    inner class SaveHistory {

        @Test
        @DisplayName("약관 동의 이력 저장 시, 데이터베이스에 저장된다")
        fun saveHistory_SavesHistory() {
            // Given: 약관 동의 이력
            val now = Instant.now()
            val history = UserTermsAgreementHistory(
                userId = testUser.id!!,
                termsType = TermsType.SERVICE_TERMS,
                termsVersion = TermsVersions.CURRENT_SERVICE_TERMS_VERSION,
                action = AgreementAction.AGREED,
                agreedAt = now,
                ipAddress = "127.0.0.1",
                userAgent = "Mozilla/5.0"
            )

            // When: 이력 저장
            termsAgreementRepository.saveHistory(history).block()

            // Then: 데이터베이스에 저장됨
            val count = Mono.from(
                dslContext.selectCount()
                    .from(USER_TERMS_AGREEMENT_HISTORY)
                    .where(USER_TERMS_AGREEMENT_HISTORY.USER_ID.eq(testUser.id.toString()))
            ).block()!!.value1()

            assertThat(count).isEqualTo(1)
        }

        @Test
        @DisplayName("여러 약관 동의 이력 저장 시, 모두 저장된다")
        fun saveHistory_MultipleSaves_AllSaved() {
            // Given: 3개의 약관 동의 이력
            val now = Instant.now()
            val history1 = UserTermsAgreementHistory(
                userId = testUser.id!!,
                termsType = TermsType.SERVICE_TERMS,
                termsVersion = TermsVersions.CURRENT_SERVICE_TERMS_VERSION,
                action = AgreementAction.AGREED,
                agreedAt = now,
                ipAddress = "127.0.0.1",
                userAgent = "Mozilla/5.0"
            )
            val history2 = UserTermsAgreementHistory(
                userId = testUser.id!!,
                termsType = TermsType.PRIVACY_POLICY,
                termsVersion = TermsVersions.CURRENT_PRIVACY_POLICY_VERSION,
                action = AgreementAction.AGREED,
                agreedAt = now,
                ipAddress = "127.0.0.1",
                userAgent = "Mozilla/5.0"
            )
            val history3 = UserTermsAgreementHistory(
                userId = testUser.id!!,
                termsType = TermsType.COMMUNITY_GUIDELINES,
                termsVersion = TermsVersions.CURRENT_COMMUNITY_GUIDELINES_VERSION,
                action = AgreementAction.AGREED,
                agreedAt = now,
                ipAddress = "127.0.0.1",
                userAgent = "Mozilla/5.0"
            )

            // When: 3개 모두 저장
            termsAgreementRepository.saveHistory(history1).block()
            termsAgreementRepository.saveHistory(history2).block()
            termsAgreementRepository.saveHistory(history3).block()

            // Then: 데이터베이스에 3개 저장됨
            val count = Mono.from(
                dslContext.selectCount()
                    .from(USER_TERMS_AGREEMENT_HISTORY)
                    .where(USER_TERMS_AGREEMENT_HISTORY.USER_ID.eq(testUser.id.toString()))
            ).block()!!.value1()

            assertThat(count).isEqualTo(3)
        }

        @Test
        @DisplayName("IP 주소와 User Agent가 저장된다 (법적 증빙)")
        fun saveHistory_SavesIpAndUserAgent() {
            // Given: IP와 User Agent 포함 이력
            val now = Instant.now()
            val history = UserTermsAgreementHistory(
                userId = testUser.id!!,
                termsType = TermsType.SERVICE_TERMS,
                termsVersion = TermsVersions.CURRENT_SERVICE_TERMS_VERSION,
                action = AgreementAction.AGREED,
                agreedAt = now,
                ipAddress = "192.168.1.1",
                userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X)"
            )

            // When: 저장
            termsAgreementRepository.saveHistory(history).block()

            // Then: IP와 User Agent가 저장됨
            val saved = Mono.from(
                dslContext.select(
                    USER_TERMS_AGREEMENT_HISTORY.IP_ADDRESS,
                    USER_TERMS_AGREEMENT_HISTORY.USER_AGENT
                )
                    .from(USER_TERMS_AGREEMENT_HISTORY)
                    .where(USER_TERMS_AGREEMENT_HISTORY.USER_ID.eq(testUser.id.toString()))
            ).block()!!

            assertThat(saved.get(USER_TERMS_AGREEMENT_HISTORY.IP_ADDRESS)).isEqualTo("192.168.1.1")
            assertThat(saved.get(USER_TERMS_AGREEMENT_HISTORY.USER_AGENT)).contains("iPhone")
        }
    }
}
