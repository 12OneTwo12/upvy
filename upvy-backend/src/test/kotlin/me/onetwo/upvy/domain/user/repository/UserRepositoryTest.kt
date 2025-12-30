package me.onetwo.upvy.domain.user.repository
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest

import java.util.UUID
import me.onetwo.upvy.domain.user.exception.UserNotFoundException
import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.model.UserRole
import me.onetwo.upvy.jooq.generated.tables.references.FOLLOWS
import me.onetwo.upvy.jooq.generated.tables.references.USER_PROFILES
import me.onetwo.upvy.jooq.generated.tables.references.USERS
import org.jooq.DSLContext
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * UserRepository 통합 테스트
 *
 * 실제 데이터베이스(H2)를 사용하여 CRUD 기능을 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("사용자 Repository 테스트")
class UserRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    

    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        // 테스트 데이터 초기화
        testUser = User(
            email = "test@example.com",
            role = UserRole.USER
        )
    }

    @AfterEach
    fun tearDown() {
        // Delete in correct order to avoid FK constraint violations
        Mono.from(dslContext.deleteFrom(FOLLOWS)).block()
        Mono.from(dslContext.deleteFrom(USER_PROFILES)).block()
        Mono.from(dslContext.deleteFrom(USERS)).block()
    }

    @Test
    @DisplayName("사용자 저장 성공")
    fun save_Success() {
        // When
        val savedUser = userRepository.save(testUser).block()!!

        // Then
        assertNotNull(savedUser.id)
        assertEquals(testUser.email, savedUser.email)
        assertEquals(testUser.role, savedUser.role)
    }

    @Test
    @DisplayName("이메일로 사용자 조회 성공")
    fun findByEmail_ExistingUser_ReturnsUser() {
        // Given
        val savedUser = userRepository.save(testUser).block()!!

        // When
        val foundUser = userRepository.findByEmail(testUser.email).block()

        // Then
        assertNotNull(foundUser)
        assertEquals(savedUser.id, foundUser?.id)
        assertEquals(savedUser.email, foundUser?.email)
    }

    @Test
    @DisplayName("이메일로 사용자 조회 - 존재하지 않는 경우")
    fun findByEmail_NonExistingUser_ReturnsNull() {
        // When
        val result = userRepository.findByEmail("nonexistent@example.com")

        // Then
        StepVerifier.create(result)
            .expectError(UserNotFoundException::class.java)
            .verify()
    }

    @Test
    @DisplayName("ID로 사용자 조회 성공")
    fun findById_ExistingUser_ReturnsUser() {
        // Given
        val savedUser = userRepository.save(testUser).block()!!

        // When
        val foundUser = userRepository.findById(savedUser.id!!).block()

        // Then
        assertNotNull(foundUser)
        assertEquals(savedUser.id, foundUser?.id)
        assertEquals(savedUser.email, foundUser?.email)
    }

    @Test
    @DisplayName("ID로 사용자 조회 - 존재하지 않는 경우")
    fun findById_NonExistingUser_ReturnsNull() {
        // When
        val result = userRepository.findById(UUID.randomUUID())

        // Then
        StepVerifier.create(result)
            .expectError(UserNotFoundException::class.java)
            .verify()
    }

    @Test
    @DisplayName("중복 이메일로 저장 시도 - 예외 발생")
    fun save_DuplicateEmail_ThrowsException() {
        // Given
        userRepository.save(testUser).block()!!

        // When & Then
        val duplicateUser = testUser.copy()
        assertThrows(Exception::class.java) {
            userRepository.save(duplicateUser).block()!!
        }
    }
}
