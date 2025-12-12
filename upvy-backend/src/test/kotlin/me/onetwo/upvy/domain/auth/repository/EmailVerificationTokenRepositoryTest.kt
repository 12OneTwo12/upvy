package me.onetwo.upvy.domain.auth.repository

import me.onetwo.upvy.domain.auth.model.EmailVerificationToken
import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.model.UserRole
import me.onetwo.upvy.domain.user.repository.UserRepository
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest
import me.onetwo.upvy.jooq.generated.tables.references.EMAIL_VERIFICATION_TOKENS
import me.onetwo.upvy.jooq.generated.tables.references.USERS
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
import java.util.UUID

/**
 * EmailVerificationTokenRepository 통합 테스트
 *
 * 이메일 인증 토큰 Repository의 데이터베이스 연동을 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("이메일 인증 토큰 Repository 통합 테스트")
class EmailVerificationTokenRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var emailVerificationTokenRepository: EmailVerificationTokenRepository

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
        Mono.from(dslContext.deleteFrom(EMAIL_VERIFICATION_TOKENS)).block()
        Mono.from(dslContext.deleteFrom(USERS)).block()
    }

    @Nested
    @DisplayName("save - 인증 토큰 저장")
    inner class Save {

        @Test
        @DisplayName("인증 토큰 저장 시, ID와 함께 저장된다")
        fun save_SavesWithId() {
            // Given: 인증 토큰 생성
            val token = EmailVerificationToken.create(testUser.id!!)

            // When: 저장
            val saved = emailVerificationTokenRepository.save(token).block()!!

            // Then: ID가 생성되고 데이터가 올바르게 저장됨
            assertThat(saved.id).isNotNull()
            assertThat(saved.userId).isEqualTo(testUser.id!!)
            assertThat(saved.token).isNotBlank()
            assertThat(saved.expiresAt).isAfter(Instant.now())
            assertThat(saved.createdAt).isNotNull()
            assertThat(saved.updatedAt).isNotNull()
            assertThat(saved.deletedAt).isNull()
        }

        @Test
        @DisplayName("토큰 만료 시각은 현재 시각보다 미래여야 한다")
        fun save_ExpiresAtIsInFuture() {
            // Given: 인증 토큰 생성
            val token = EmailVerificationToken.create(testUser.id!!)

            // When: 저장
            val saved = emailVerificationTokenRepository.save(token).block()!!

            // Then: expiresAt이 현재 시각보다 미래
            assertThat(saved.expiresAt).isAfter(Instant.now())
        }
    }

    @Nested
    @DisplayName("findByToken - 토큰 문자열로 조회")
    inner class FindByToken {

        @Test
        @DisplayName("유효한 토큰으로 조회 시, 인증 토큰을 반환한다")
        fun findByToken_WithValidToken_ReturnsToken() {
            // Given: 인증 토큰 저장
            val token = EmailVerificationToken.create(testUser.id!!)
            val saved = emailVerificationTokenRepository.save(token).block()!!

            // When: 토큰 문자열로 조회
            val result = emailVerificationTokenRepository.findByToken(saved.token).block()

            // Then: 조회됨
            assertThat(result).isNotNull
            assertThat(result!!.id).isEqualTo(saved.id)
            assertThat(result.userId).isEqualTo(testUser.id!!)
            assertThat(result.token).isEqualTo(saved.token)
        }

        @Test
        @DisplayName("존재하지 않는 토큰으로 조회 시, Mono.empty()를 반환한다")
        fun findByToken_WithNonExistentToken_ReturnsEmpty() {
            // Given: 데이터 없음
            // When: 존재하지 않는 토큰으로 조회
            val result = emailVerificationTokenRepository.findByToken("non-existent-token").block()

            // Then: 조회되지 않음
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("삭제된 토큰은 조회되지 않는다")
        fun findByToken_WithDeletedToken_ReturnsEmpty() {
            // Given: 인증 토큰 저장 후 삭제
            val token = EmailVerificationToken.create(testUser.id!!)
            val saved = emailVerificationTokenRepository.save(token).block()!!
            emailVerificationTokenRepository.softDelete(saved.id!!).block()

            // When: 삭제된 토큰으로 조회
            val result = emailVerificationTokenRepository.findByToken(saved.token).block()

            // Then: 조회되지 않음
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("findLatestByUserId - 사용자의 최근 토큰 조회")
    inner class FindLatestByUserId {

        @Test
        @DisplayName("사용자가 여러 토큰을 가진 경우, 가장 최근 토큰을 반환한다")
        fun findLatestByUserId_WithMultipleTokens_ReturnsLatest() {
            // Given: 3개의 인증 토큰 생성 (시간 간격을 두고)
            val token1 = EmailVerificationToken(
                userId = testUser.id!!,
                token = UUID.randomUUID().toString(),
                expiresAt = Instant.now().plusSeconds(86400),
                createdAt = Instant.now().minusSeconds(200),
                updatedAt = Instant.now().minusSeconds(200)
            )
            emailVerificationTokenRepository.save(token1).block()

            val token2 = EmailVerificationToken(
                userId = testUser.id!!,
                token = UUID.randomUUID().toString(),
                expiresAt = Instant.now().plusSeconds(86400),
                createdAt = Instant.now().minusSeconds(100),
                updatedAt = Instant.now().minusSeconds(100)
            )
            emailVerificationTokenRepository.save(token2).block()

            val token3 = EmailVerificationToken(
                userId = testUser.id!!,
                token = UUID.randomUUID().toString(),
                expiresAt = Instant.now().plusSeconds(86400),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            val latestToken = emailVerificationTokenRepository.save(token3).block()!!

            // When: 최근 토큰 조회
            val result = emailVerificationTokenRepository.findLatestByUserId(testUser.id!!).block()

            // Then: 가장 최근 토큰이 조회됨
            assertThat(result).isNotNull
            assertThat(result!!.id).isEqualTo(latestToken.id)
            assertThat(result.token).isEqualTo(latestToken.token)
        }

        @Test
        @DisplayName("사용자가 토큰이 없는 경우, Mono.empty()를 반환한다")
        fun findLatestByUserId_WithNoTokens_ReturnsEmpty() {
            // Given: 토큰 없음
            // When: 최근 토큰 조회
            val result = emailVerificationTokenRepository.findLatestByUserId(testUser.id!!).block()

            // Then: 조회되지 않음
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("삭제된 토큰은 제외되고 최근 토큰을 반환한다")
        fun findLatestByUserId_ExcludesDeletedTokens() {
            // Given: 2개의 토큰 생성, 최신 토큰을 삭제
            val token1 = EmailVerificationToken(
                userId = testUser.id!!,
                token = UUID.randomUUID().toString(),
                expiresAt = Instant.now().plusSeconds(86400),
                createdAt = Instant.now().minusSeconds(100),
                updatedAt = Instant.now().minusSeconds(100)
            )
            val oldToken = emailVerificationTokenRepository.save(token1).block()!!

            val token2 = EmailVerificationToken(
                userId = testUser.id!!,
                token = UUID.randomUUID().toString(),
                expiresAt = Instant.now().plusSeconds(86400),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            val latestToken = emailVerificationTokenRepository.save(token2).block()!!

            // 최신 토큰 삭제
            emailVerificationTokenRepository.softDelete(latestToken.id!!).block()

            // When: 최근 토큰 조회
            val result = emailVerificationTokenRepository.findLatestByUserId(testUser.id!!).block()

            // Then: 삭제되지 않은 이전 토큰이 조회됨
            assertThat(result).isNotNull
            assertThat(result!!.id).isEqualTo(oldToken.id)
        }
    }

    @Nested
    @DisplayName("softDelete - 인증 토큰 삭제 (Soft Delete)")
    inner class SoftDelete {

        @Test
        @DisplayName("인증 토큰 삭제 시, deletedAt이 설정된다")
        fun softDelete_SetsDeletedAt() {
            // Given: 인증 토큰 저장
            val token = EmailVerificationToken.create(testUser.id!!)
            val saved = emailVerificationTokenRepository.save(token).block()!!

            // When: 삭제
            emailVerificationTokenRepository.softDelete(saved.id!!).block()

            // Then: 삭제됨 (조회되지 않음)
            val result = emailVerificationTokenRepository.findByToken(saved.token).block()
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("이미 삭제된 토큰은 재삭제되지 않는다")
        fun softDelete_AlreadyDeleted_DoesNotDelete() {
            // Given: 삭제된 인증 토큰
            val token = EmailVerificationToken.create(testUser.id!!)
            val saved = emailVerificationTokenRepository.save(token).block()!!
            emailVerificationTokenRepository.softDelete(saved.id!!).block()

            // When: 재삭제 시도 (에러가 발생하지 않아야 함)
            emailVerificationTokenRepository.softDelete(saved.id!!).block()

            // Then: 여전히 조회되지 않음
            val result = emailVerificationTokenRepository.findByToken(saved.token).block()
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("softDeleteAllByUserId - 사용자의 모든 토큰 삭제")
    inner class SoftDeleteAllByUserId {

        @Test
        @DisplayName("사용자의 모든 토큰이 삭제된다")
        fun softDeleteAllByUserId_DeletesAllTokens() {
            // Given: 사용자의 3개 토큰 생성
            val token1 = EmailVerificationToken.create(testUser.id!!)
            emailVerificationTokenRepository.save(token1).block()

            val token2 = EmailVerificationToken.create(testUser.id!!)
            emailVerificationTokenRepository.save(token2).block()

            val token3 = EmailVerificationToken.create(testUser.id!!)
            emailVerificationTokenRepository.save(token3).block()

            // When: 사용자의 모든 토큰 삭제
            emailVerificationTokenRepository.softDeleteAllByUserId(testUser.id!!).block()

            // Then: 모든 토큰이 조회되지 않음
            val result = emailVerificationTokenRepository.findLatestByUserId(testUser.id!!).block()
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("다른 사용자의 토큰은 삭제되지 않는다")
        fun softDeleteAllByUserId_DoesNotDeleteOtherUserTokens() {
            // Given: 2명의 사용자, 각각 토큰 보유
            val user1 = testUser
            val user2 = userRepository.save(
                User(
                    email = "other@example.com",
                    role = UserRole.USER
                )
            ).block()!!

            val user1Token = EmailVerificationToken.create(user1.id!!)
            emailVerificationTokenRepository.save(user1Token).block()

            val user2Token = EmailVerificationToken.create(user2.id!!)
            val savedUser2Token = emailVerificationTokenRepository.save(user2Token).block()!!

            // When: user1의 모든 토큰 삭제
            emailVerificationTokenRepository.softDeleteAllByUserId(user1.id!!).block()

            // Then: user1 토큰은 삭제되고, user2 토큰은 남아있음
            val user1Result = emailVerificationTokenRepository.findLatestByUserId(user1.id!!).block()
            val user2Result = emailVerificationTokenRepository.findLatestByUserId(user2.id!!).block()

            assertThat(user1Result).isNull()
            assertThat(user2Result).isNotNull
            assertThat(user2Result!!.id).isEqualTo(savedUser2Token.id)
        }
    }

    @Nested
    @DisplayName("Token Validation - 토큰 유효성 검증")
    inner class TokenValidation {

        @Test
        @DisplayName("만료되지 않은 토큰은 유효하다")
        fun isValid_NotExpiredToken_ReturnsTrue() {
            // Given: 만료되지 않은 토큰
            val token = EmailVerificationToken.create(testUser.id!!)

            // When & Then: 유효함
            assertThat(token.isExpired()).isFalse()
            assertThat(token.isValid()).isTrue()
        }

        @Test
        @DisplayName("만료된 토큰은 유효하지 않다")
        fun isValid_ExpiredToken_ReturnsFalse() {
            // Given: 만료된 토큰 (expiresAt이 과거)
            val expiredToken = EmailVerificationToken(
                userId = testUser.id!!,
                token = UUID.randomUUID().toString(),
                expiresAt = Instant.now().minusSeconds(3600), // 1시간 전
                createdAt = Instant.now().minusSeconds(7200),
                updatedAt = Instant.now().minusSeconds(7200)
            )

            // When & Then: 만료됨
            assertThat(expiredToken.isExpired()).isTrue()
            assertThat(expiredToken.isValid()).isFalse()
        }

        @Test
        @DisplayName("삭제된 토큰은 유효하지 않다")
        fun isValid_DeletedToken_ReturnsFalse() {
            // Given: 삭제된 토큰
            val deletedToken = EmailVerificationToken(
                userId = testUser.id!!,
                token = UUID.randomUUID().toString(),
                expiresAt = Instant.now().plusSeconds(86400),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                deletedAt = Instant.now()
            )

            // When & Then: 유효하지 않음
            assertThat(deletedToken.isValid()).isFalse()
        }

        @Test
        @DisplayName("기본 토큰 유효 기간은 5분이다")
        fun create_DefaultExpiryIs5Minutes() {
            // Given: 새 토큰 생성
            val token = EmailVerificationToken.create(testUser.id!!)

            // When & Then: 만료 시각이 약 5분 후
            val expectedExpiresAt = Instant.now().plusSeconds(60 * 5)
            val diff = token.expiresAt.epochSecond - expectedExpiresAt.epochSecond

            // 5초 이내 오차 허용
            assertThat(diff).isBetween(-5L, 5L)
        }
    }

    @Nested
    @DisplayName("Audit Trail - 생성/수정/삭제 이력 추적")
    inner class AuditTrail {

        @Test
        @DisplayName("토큰 생성 시, createdAt과 updatedAt이 자동 설정된다")
        fun save_SetsCreatedAtAndUpdatedAt() {
            // Given: 인증 토큰 생성
            val token = EmailVerificationToken.create(testUser.id!!)

            // When: 저장
            val saved = emailVerificationTokenRepository.save(token).block()!!

            // Then: createdAt, updatedAt 설정됨
            assertThat(saved.createdAt).isNotNull()
            assertThat(saved.updatedAt).isNotNull()
        }

        @Test
        @DisplayName("토큰 삭제 시, deletedAt과 updatedAt이 설정된다")
        fun softDelete_SetsDeletedAtAndUpdatedAt() {
            // Given: 인증 토큰 저장
            val token = EmailVerificationToken.create(testUser.id!!)
            val saved = emailVerificationTokenRepository.save(token).block()!!

            // When: 삭제
            emailVerificationTokenRepository.softDelete(saved.id!!).block()

            // Then: deletedAt과 updatedAt이 설정됨 (DB 직접 조회로 확인)
            val deletedRecord = Mono.from(
                dslContext.selectFrom(EMAIL_VERIFICATION_TOKENS)
                    .where(EMAIL_VERIFICATION_TOKENS.ID.eq(saved.id!!))
            ).block()

            assertThat(deletedRecord).isNotNull
            assertThat(deletedRecord!!.deletedAt).isNotNull()
            assertThat(deletedRecord.updatedAt).isAfter(saved.updatedAt)
        }
    }
}
