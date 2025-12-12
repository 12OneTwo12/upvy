package me.onetwo.upvy.domain.user.repository

import me.onetwo.upvy.domain.user.model.OAuthProvider
import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.model.UserAuthenticationMethod
import me.onetwo.upvy.domain.user.model.UserRole
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest
import me.onetwo.upvy.jooq.generated.tables.references.USER_AUTHENTICATION_METHODS
import me.onetwo.upvy.jooq.generated.tables.references.USERS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Mono

/**
 * UserAuthenticationMethodRepository 통합 테스트
 *
 * 계정 통합 아키텍처 (Account Linking)에서 사용자의 인증 수단을 관리하는
 * Repository의 데이터베이스 연동을 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("사용자 인증 수단 Repository 통합 테스트")
class UserAuthenticationMethodRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var authMethodRepository: UserAuthenticationMethodRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var testUser: User
    private val passwordEncoder = BCryptPasswordEncoder()

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
        Mono.from(dslContext.deleteFrom(USER_AUTHENTICATION_METHODS)).block()
        Mono.from(dslContext.deleteFrom(USERS)).block()
    }

    @Nested
    @DisplayName("save - 인증 수단 저장")
    inner class Save {

        @Test
        @DisplayName("OAuth 인증 수단 저장 시, ID와 함께 저장된다")
        fun save_OAuthAuthMethod_SavesWithId() {
            // Given: OAuth 인증 수단 (Google)
            val authMethod = UserAuthenticationMethod(
                userId = testUser.id!!,
                provider = OAuthProvider.GOOGLE,
                providerId = "google-123",
                password = null,
                emailVerified = true,
                isPrimary = true
            )

            // When: 저장
            val saved = authMethodRepository.save(authMethod).block()!!

            // Then: ID가 생성되고 데이터가 올바르게 저장됨
            assertThat(saved.id).isNotNull()
            assertThat(saved.userId).isEqualTo(testUser.id!!)
            assertThat(saved.provider).isEqualTo(OAuthProvider.GOOGLE)
            assertThat(saved.providerId).isEqualTo("google-123")
            assertThat(saved.password).isNull()
            assertThat(saved.emailVerified).isTrue()
            assertThat(saved.isPrimary).isTrue()
        }

        @Test
        @DisplayName("이메일 인증 수단 저장 시, 암호화된 비밀번호와 함께 저장된다")
        fun save_EmailAuthMethod_SavesWithEncryptedPassword() {
            // Given: 이메일 인증 수단
            val rawPassword = "password123"
            val encryptedPassword = passwordEncoder.encode(rawPassword)
            val authMethod = UserAuthenticationMethod(
                userId = testUser.id!!,
                provider = OAuthProvider.EMAIL,
                providerId = null,
                password = encryptedPassword,
                emailVerified = false,
                isPrimary = false
            )

            // When: 저장
            val saved = authMethodRepository.save(authMethod).block()!!

            // Then: 암호화된 비밀번호가 저장됨
            assertThat(saved.id).isNotNull()
            assertThat(saved.provider).isEqualTo(OAuthProvider.EMAIL)
            assertThat(saved.providerId).isNull()
            assertThat(saved.password).isEqualTo(encryptedPassword)
            assertThat(passwordEncoder.matches(rawPassword, saved.password)).isTrue()
            assertThat(saved.emailVerified).isFalse()
        }

        @Test
        @DisplayName("ID가 있는 인증 수단 저장 시, UPDATE가 실행된다")
        fun save_WithExistingId_Updates() {
            // Given: 저장된 인증 수단
            val saved = authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.GOOGLE,
                    providerId = "google-123",
                    emailVerified = false
                )
            ).block()!!

            // When: emailVerified를 true로 업데이트
            val updated = authMethodRepository.save(
                saved.copy(emailVerified = true)
            ).block()!!

            // Then: 업데이트됨
            assertThat(updated.id).isEqualTo(saved.id)
            assertThat(updated.emailVerified).isTrue()
        }
    }

    @Nested
    @DisplayName("findAllByUserId - 사용자의 모든 인증 수단 조회")
    inner class FindAllByUserId {

        @Test
        @DisplayName("사용자가 여러 인증 수단을 가진 경우, 모두 조회된다")
        fun findAllByUserId_WithMultipleAuthMethods_ReturnsAll() {
            // Given: 사용자가 Google + Email 인증 수단 보유
            authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.GOOGLE,
                    providerId = "google-123",
                    isPrimary = true
                )
            ).block()

            authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.EMAIL,
                    password = passwordEncoder.encode("password"),
                    emailVerified = true,
                    isPrimary = false
                )
            ).block()

            // When: 사용자의 모든 인증 수단 조회
            val results = authMethodRepository.findAllByUserId(testUser.id!!).collectList().block()!!

            // Then: 2개 조회됨 (primary가 먼저, 생성 시각 순)
            assertThat(results).hasSize(2)
            assertThat(results[0].provider).isEqualTo(OAuthProvider.GOOGLE)
            assertThat(results[0].isPrimary).isTrue()
            assertThat(results[1].provider).isEqualTo(OAuthProvider.EMAIL)
            assertThat(results[1].isPrimary).isFalse()
        }

        @Test
        @DisplayName("인증 수단이 없는 경우, 빈 리스트를 반환한다")
        fun findAllByUserId_WithNoAuthMethods_ReturnsEmptyList() {
            // Given: 인증 수단 없음
            // When: 조회
            val results = authMethodRepository.findAllByUserId(testUser.id!!).collectList().block()!!

            // Then: 빈 리스트
            assertThat(results).isEmpty()
        }

        @Test
        @DisplayName("삭제된 인증 수단은 조회되지 않는다")
        fun findAllByUserId_WithDeletedAuthMethod_ExcludesDeleted() {
            // Given: 인증 수단 저장 후 삭제
            val saved = authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.GOOGLE,
                    providerId = "google-123"
                )
            ).block()!!

            authMethodRepository.softDelete(saved.id!!).block()

            // When: 조회
            val results = authMethodRepository.findAllByUserId(testUser.id!!).collectList().block()!!

            // Then: 삭제된 데이터는 조회되지 않음
            assertThat(results).isEmpty()
        }
    }

    @Nested
    @DisplayName("findByUserIdAndProvider - 특정 provider 인증 수단 조회")
    inner class FindByUserIdAndProvider {

        @Test
        @DisplayName("사용자의 Google 인증 수단 조회 시, 해당 인증 수단을 반환한다")
        fun findByUserIdAndProvider_WithGoogle_ReturnsGoogleAuthMethod() {
            // Given: Google 인증 수단 저장
            authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.GOOGLE,
                    providerId = "google-123"
                )
            ).block()

            // When: Google 인증 수단 조회
            val result = authMethodRepository.findByUserIdAndProvider(
                testUser.id!!,
                OAuthProvider.GOOGLE
            ).block()

            // Then: 조회됨
            assertThat(result).isNotNull
            assertThat(result!!.provider).isEqualTo(OAuthProvider.GOOGLE)
            assertThat(result.providerId).isEqualTo("google-123")
        }

        @Test
        @DisplayName("해당 provider 인증 수단이 없는 경우, Mono.empty()를 반환한다")
        fun findByUserIdAndProvider_WithNonExistentProvider_ReturnsEmpty() {
            // Given: Google 인증 수단만 저장
            authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.GOOGLE,
                    providerId = "google-123"
                )
            ).block()

            // When: Naver 인증 수단 조회
            val result = authMethodRepository.findByUserIdAndProvider(
                testUser.id!!,
                OAuthProvider.NAVER
            ).block()

            // Then: 조회되지 않음
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("findByProviderAndProviderId - OAuth provider와 providerId로 조회")
    inner class FindByProviderAndProviderId {

        @Test
        @DisplayName("OAuth 로그인 시 기존 사용자 찾기에 성공한다")
        fun findByProviderAndProviderId_WithExistingOAuth_ReturnsAuthMethod() {
            // Given: Google 인증 수단 저장
            authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.GOOGLE,
                    providerId = "google-123"
                )
            ).block()

            // When: provider와 providerId로 조회
            val result = authMethodRepository.findByProviderAndProviderId(
                OAuthProvider.GOOGLE,
                "google-123"
            ).block()

            // Then: 기존 사용자의 인증 수단 조회됨
            assertThat(result).isNotNull
            assertThat(result!!.userId).isEqualTo(testUser.id!!)
            assertThat(result.provider).isEqualTo(OAuthProvider.GOOGLE)
            assertThat(result.providerId).isEqualTo("google-123")
        }

        @Test
        @DisplayName("존재하지 않는 providerId로 조회 시, Mono.empty()를 반환한다")
        fun findByProviderAndProviderId_WithNonExistent_ReturnsEmpty() {
            // Given: 데이터 없음
            // When: 조회
            val result = authMethodRepository.findByProviderAndProviderId(
                OAuthProvider.GOOGLE,
                "non-existent-id"
            ).block()

            // Then: 조회되지 않음
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("findByUserIdAndProviderAndEmailVerified - 이메일 검증 상태로 조회")
    inner class FindByUserIdAndProviderAndEmailVerified {

        @Test
        @DisplayName("검증된 이메일 인증 수단만 조회된다")
        fun findByUserIdAndProviderAndEmailVerified_WithVerified_ReturnsVerifiedAuthMethod() {
            // Given: 검증된 이메일 인증 수단 저장
            authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.EMAIL,
                    password = passwordEncoder.encode("password"),
                    emailVerified = true
                )
            ).block()

            // When: 검증된 이메일 인증 수단 조회
            val result = authMethodRepository.findByUserIdAndProviderAndEmailVerified(
                testUser.id!!,
                OAuthProvider.EMAIL,
                true
            ).block()

            // Then: 조회됨
            assertThat(result).isNotNull
            assertThat(result!!.provider).isEqualTo(OAuthProvider.EMAIL)
            assertThat(result.emailVerified).isTrue()
        }

        @Test
        @DisplayName("미검증 이메일 인증 수단은 조회되지 않는다")
        fun findByUserIdAndProviderAndEmailVerified_WithUnverified_ReturnsEmpty() {
            // Given: 미검증 이메일 인증 수단 저장
            authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.EMAIL,
                    password = passwordEncoder.encode("password"),
                    emailVerified = false
                )
            ).block()

            // When: 검증된 이메일 인증 수단 조회
            val result = authMethodRepository.findByUserIdAndProviderAndEmailVerified(
                testUser.id!!,
                OAuthProvider.EMAIL,
                true
            ).block()

            // Then: 조회되지 않음
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("updatePassword - 비밀번호 업데이트")
    inner class UpdatePassword {

        @Test
        @DisplayName("이메일 인증 수단의 비밀번호를 업데이트한다")
        fun updatePassword_UpdatesPasswordSuccessfully() {
            // Given: 이메일 인증 수단 저장
            val saved = authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.EMAIL,
                    password = passwordEncoder.encode("old-password"),
                    emailVerified = true
                )
            ).block()!!

            // When: 비밀번호 업데이트
            val newPassword = passwordEncoder.encode("new-password")
            val updateCount = authMethodRepository.updatePassword(saved.id!!, newPassword).block()!!

            // Then: 업데이트됨
            assertThat(updateCount).isEqualTo(1)

            val updated = authMethodRepository.findByUserIdAndProvider(
                testUser.id!!,
                OAuthProvider.EMAIL
            ).block()!!
            assertThat(updated.password).isEqualTo(newPassword)
        }

        @Test
        @DisplayName("삭제된 인증 수단의 비밀번호는 업데이트되지 않는다")
        fun updatePassword_WithDeletedAuthMethod_DoesNotUpdate() {
            // Given: 삭제된 이메일 인증 수단
            val saved = authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.EMAIL,
                    password = passwordEncoder.encode("password")
                )
            ).block()!!

            authMethodRepository.softDelete(saved.id!!).block()

            // When: 비밀번호 업데이트 시도
            val updateCount = authMethodRepository.updatePassword(
                saved.id!!,
                passwordEncoder.encode("new-password")
            ).block()!!

            // Then: 업데이트되지 않음
            assertThat(updateCount).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("updateEmailVerified - 이메일 검증 상태 업데이트")
    inner class UpdateEmailVerified {

        @Test
        @DisplayName("이메일 검증 상태를 true로 업데이트한다")
        fun updateEmailVerified_ToTrue_Updates() {
            // Given: 미검증 이메일 인증 수단
            val saved = authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.EMAIL,
                    password = passwordEncoder.encode("password"),
                    emailVerified = false
                )
            ).block()!!

            // When: 이메일 검증 상태를 true로 업데이트
            val updateCount = authMethodRepository.updateEmailVerified(saved.id!!, true).block()!!

            // Then: 업데이트됨
            assertThat(updateCount).isEqualTo(1)

            val updated = authMethodRepository.findByUserIdAndProvider(
                testUser.id!!,
                OAuthProvider.EMAIL
            ).block()!!
            assertThat(updated.emailVerified).isTrue()
        }
    }

    @Nested
    @DisplayName("setPrimary - 주 인증 수단 설정")
    inner class SetPrimary {

        @Test
        @DisplayName("새로운 인증 수단을 primary로 설정하면, 기존 primary는 해제된다")
        fun setPrimary_UnsetsPreviousPrimary() {
            // Given: 2개의 인증 수단 (Google이 primary)
            val googleAuth = authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.GOOGLE,
                    providerId = "google-123",
                    isPrimary = true
                )
            ).block()!!

            val emailAuth = authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.EMAIL,
                    password = passwordEncoder.encode("password"),
                    isPrimary = false
                )
            ).block()!!

            // When: Email을 primary로 설정
            authMethodRepository.setPrimary(testUser.id!!, emailAuth.id!!).block()

            // Then: Email이 primary, Google은 primary 해제
            val allAuthMethods = authMethodRepository.findAllByUserId(testUser.id!!).collectList().block()!!

            val updatedGoogle = allAuthMethods.first { it.provider == OAuthProvider.GOOGLE }
            val updatedEmail = allAuthMethods.first { it.provider == OAuthProvider.EMAIL }

            assertThat(updatedGoogle.isPrimary).isFalse()
            assertThat(updatedEmail.isPrimary).isTrue()
        }
    }

    @Nested
    @DisplayName("softDelete - 인증 수단 삭제 (Soft Delete)")
    inner class SoftDelete {

        @Test
        @DisplayName("인증 수단 삭제 시, deletedAt이 설정된다")
        fun softDelete_SetsDeletedAt() {
            // Given: 인증 수단 저장
            val saved = authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.GOOGLE,
                    providerId = "google-123"
                )
            ).block()!!

            // When: 삭제
            val deleteCount = authMethodRepository.softDelete(saved.id!!).block()!!

            // Then: 삭제됨 (조회되지 않음)
            assertThat(deleteCount).isEqualTo(1)

            val result = authMethodRepository.findByUserIdAndProvider(
                testUser.id!!,
                OAuthProvider.GOOGLE
            ).block()

            assertThat(result).isNull()
        }

        @Test
        @DisplayName("이미 삭제된 인증 수단은 재삭제되지 않는다")
        fun softDelete_AlreadyDeleted_DoesNotDelete() {
            // Given: 삭제된 인증 수단
            val saved = authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.GOOGLE,
                    providerId = "google-123"
                )
            ).block()!!

            authMethodRepository.softDelete(saved.id!!).block()

            // When: 재삭제 시도
            val deleteCount = authMethodRepository.softDelete(saved.id!!).block()!!

            // Then: 삭제되지 않음 (이미 deletedAt이 NULL이 아님)
            assertThat(deleteCount).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("Audit Trail - 생성/수정/삭제 이력 추적")
    inner class AuditTrail {

        @Test
        @DisplayName("인증 수단 생성 시, createdAt과 updatedAt이 자동 설정된다")
        fun save_SetsCreatedAtAndUpdatedAt() {
            // Given & When: 인증 수단 저장
            val saved = authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.GOOGLE,
                    providerId = "google-123"
                )
            ).block()!!

            // Then: createdAt, updatedAt 설정됨
            assertThat(saved.createdAt).isNotNull()
            assertThat(saved.updatedAt).isNotNull()
        }

        @Test
        @DisplayName("인증 수단 업데이트 시, updatedAt이 갱신된다")
        fun update_UpdatesUpdatedAt() {
            // Given: 인증 수단 저장
            val saved = authMethodRepository.save(
                UserAuthenticationMethod(
                    userId = testUser.id!!,
                    provider = OAuthProvider.EMAIL,
                    password = passwordEncoder.encode("password"),
                    emailVerified = false
                )
            ).block()!!

            val originalUpdatedAt = saved.updatedAt

            // When: emailVerified 업데이트
            authMethodRepository.updateEmailVerified(saved.id!!, true).block()

            // Then: updatedAt 갱신됨
            val updated = authMethodRepository.findByUserIdAndProvider(
                testUser.id!!,
                OAuthProvider.EMAIL
            ).block()!!

            assertThat(updated.updatedAt).isAfter(originalUpdatedAt)
        }
    }
}
