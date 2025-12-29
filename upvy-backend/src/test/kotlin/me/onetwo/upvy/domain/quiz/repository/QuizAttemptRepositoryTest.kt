package me.onetwo.upvy.domain.quiz.repository

import me.onetwo.upvy.domain.content.model.Content
import me.onetwo.upvy.domain.content.model.ContentType
import me.onetwo.upvy.domain.content.repository.ContentRepository
import me.onetwo.upvy.domain.quiz.model.Quiz
import me.onetwo.upvy.domain.quiz.model.QuizAttempt
import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.model.UserRole
import me.onetwo.upvy.domain.user.model.UserStatus
import me.onetwo.upvy.domain.user.repository.UserRepository
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest
import me.onetwo.upvy.jooq.generated.tables.references.QUIZ_ATTEMPT_ANSWERS
import me.onetwo.upvy.jooq.generated.tables.references.QUIZ_ATTEMPTS
import me.onetwo.upvy.jooq.generated.tables.references.QUIZ_OPTIONS
import me.onetwo.upvy.jooq.generated.tables.references.QUIZZES
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
import java.util.UUID

/**
 * QuizAttemptRepository 통합 테스트
 *
 * 실제 MySQL Testcontainer를 사용하여 Repository 계층을 테스트합니다.
 * Count 최적화 및 집계 쿼리를 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("QuizAttemptRepository 통합 테스트")
class QuizAttemptRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var quizAttemptRepository: QuizAttemptRepository

    @Autowired
    private lateinit var quizRepository: QuizRepository

    @Autowired
    private lateinit var contentRepository: ContentRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var testUser: User
    private lateinit var testUser2: User
    private lateinit var testContent: Content
    private lateinit var testQuiz: Quiz

    @BeforeEach
    fun setUp() {
        // 테스트용 사용자 생성
        testUser = userRepository.save(
            User(
                email = "quiz-attempt-test@example.com",
                role = UserRole.USER,
                status = UserStatus.ACTIVE
            )
        ).block()!!

        testUser2 = userRepository.save(
            User(
                email = "quiz-attempt-test2@example.com",
                role = UserRole.USER,
                status = UserStatus.ACTIVE
            )
        ).block()!!

        // 테스트용 콘텐츠 생성
        testContent = contentRepository.save(
            Content(
                creatorId = testUser.id!!,
                contentType = ContentType.QUIZ,
                url = "https://example.com/quiz",
                thumbnailUrl = "https://example.com/thumbnail.jpg",
                width = 1080,
                height = 1920,
                createdBy = testUser.id.toString()
            )
        ).block()!!

        // 테스트용 퀴즈 생성
        testQuiz = quizRepository.save(
            Quiz(
                contentId = testContent.id!!,
                question = "Kotlin에서 val과 var의 차이는?",
                allowMultipleAnswers = false,
                createdBy = testUser.id.toString(),
                updatedBy = testUser.id.toString()
            )
        ).block()!!
    }

    @AfterEach
    fun tearDown() {
        // Quiz 관련 테이블 정리
        Mono.from(dslContext.deleteFrom(QUIZ_ATTEMPT_ANSWERS))
            .then(Mono.from(dslContext.deleteFrom(QUIZ_ATTEMPTS)))
            .then(Mono.from(dslContext.deleteFrom(QUIZ_OPTIONS)))
            .then(Mono.from(dslContext.deleteFrom(QUIZZES)))
            .block()
    }

    @Nested
    @DisplayName("save - 퀴즈 시도 저장")
    inner class Save {

        @Test
        @DisplayName("시도를 저장하면, UUID가 부여된다")
        fun save_WithValidAttempt_ReturnsAttemptWithId() {
            // Given: 새로운 시도
            val attempt = QuizAttempt(
                quizId = testQuiz.id!!,
                userId = testUser.id!!,
                attemptNumber = 1,
                isCorrect = true
            )

            // When: 저장
            val savedAttempt = quizAttemptRepository.save(attempt).block()!!

            // Then: UUID가 부여되고 createdAt이 설정됨
            assertThat(savedAttempt.id).isNotNull()
            assertThat(savedAttempt.quizId).isEqualTo(testQuiz.id)
            assertThat(savedAttempt.userId).isEqualTo(testUser.id)
            assertThat(savedAttempt.attemptNumber).isEqualTo(1)
            assertThat(savedAttempt.isCorrect).isTrue()
            assertThat(savedAttempt.createdAt).isNotNull()
        }

        @Test
        @DisplayName("동일한 사용자가 여러 번 시도할 수 있다")
        fun save_MultipleAttemptsFromSameUser_SavesSuccessfully() {
            // Given: 동일한 사용자의 세 번의 시도
            val attempt1 = QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 1, isCorrect = false)
            val attempt2 = QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 2, isCorrect = false)
            val attempt3 = QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 3, isCorrect = true)

            // When: 각각 저장
            val saved1 = quizAttemptRepository.save(attempt1).block()!!
            val saved2 = quizAttemptRepository.save(attempt2).block()!!
            val saved3 = quizAttemptRepository.save(attempt3).block()!!

            // Then: 모두 저장됨
            assertThat(saved1.id).isNotNull()
            assertThat(saved2.id).isNotNull()
            assertThat(saved3.id).isNotNull()
            assertThat(saved1.attemptNumber).isEqualTo(1)
            assertThat(saved2.attemptNumber).isEqualTo(2)
            assertThat(saved3.attemptNumber).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("findById - ID로 시도 조회")
    inner class FindById {

        @Test
        @DisplayName("존재하는 시도 ID로 조회하면, 시도를 반환한다")
        fun findById_WhenAttemptExists_ReturnsAttempt() {
            // Given: 시도 저장
            val savedAttempt = quizAttemptRepository.save(
                QuizAttempt(
                    quizId = testQuiz.id!!,
                    userId = testUser.id!!,
                    attemptNumber = 1,
                    isCorrect = true
                )
            ).block()!!

            // When: ID로 조회
            val foundAttempt = quizAttemptRepository.findById(savedAttempt.id!!).block()

            // Then: 시도가 조회됨
            assertThat(foundAttempt).isNotNull
            assertThat(foundAttempt!!.id).isEqualTo(savedAttempt.id)
            assertThat(foundAttempt.quizId).isEqualTo(testQuiz.id)
            assertThat(foundAttempt.userId).isEqualTo(testUser.id)
        }

        @Test
        @DisplayName("존재하지 않는 시도 ID로 조회하면, 빈 Mono를 반환한다")
        fun findById_WhenAttemptNotExists_ReturnsEmpty() {
            // When: 존재하지 않는 ID로 조회
            val foundAttempt = quizAttemptRepository.findById(UUID.randomUUID()).block()

            // Then: null 반환
            assertThat(foundAttempt).isNull()
        }
    }

    @Nested
    @DisplayName("findByQuizIdAndUserId - 퀴즈 ID와 사용자 ID로 시도 조회")
    inner class FindByQuizIdAndUserId {

        @Test
        @DisplayName("특정 사용자의 퀴즈 시도를 최신순으로 조회한다")
        fun findByQuizIdAndUserId_WithMultipleAttempts_ReturnsOrderedByCreatedAtDesc() {
            // Given: 사용자의 3번의 시도
            val attempt1 = quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 1, isCorrect = false)
            ).block()!!
            Thread.sleep(10) // createdAt 차이를 보장
            val attempt2 = quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 2, isCorrect = false)
            ).block()!!
            Thread.sleep(10)
            val attempt3 = quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 3, isCorrect = true)
            ).block()!!

            // When: 사용자의 시도 조회
            val attempts = quizAttemptRepository.findByQuizIdAndUserId(testQuiz.id!!, testUser.id!!).collectList().block()!!

            // Then: 최신순 (3, 2, 1 순서)
            assertThat(attempts).hasSize(3)
            assertThat(attempts[0].id).isEqualTo(attempt3.id)
            assertThat(attempts[1].id).isEqualTo(attempt2.id)
            assertThat(attempts[2].id).isEqualTo(attempt1.id)
        }

        @Test
        @DisplayName("다른 사용자의 시도는 조회되지 않는다")
        fun findByQuizIdAndUserId_OnlyReturnsSpecificUserAttempts() {
            // Given: 두 사용자의 시도
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 1, isCorrect = true)
            ).block()
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser2.id!!, attemptNumber = 1, isCorrect = false)
            ).block()

            // When: testUser의 시도만 조회
            val attempts = quizAttemptRepository.findByQuizIdAndUserId(testQuiz.id!!, testUser.id!!).collectList().block()!!

            // Then: testUser의 시도만 반환
            assertThat(attempts).hasSize(1)
            assertThat(attempts[0].userId).isEqualTo(testUser.id)
        }

        @Test
        @DisplayName("시도가 없으면, 빈 Flux를 반환한다")
        fun findByQuizIdAndUserId_WhenNoAttempts_ReturnsEmptyFlux() {
            // When: 시도가 없는 사용자 조회
            val attempts = quizAttemptRepository.findByQuizIdAndUserId(testQuiz.id!!, testUser.id!!).collectList().block()!!

            // Then: 빈 목록
            assertThat(attempts).isEmpty()
        }
    }

    @Nested
    @DisplayName("findByQuizId - 퀴즈 ID로 모든 시도 조회")
    inner class FindByQuizId {

        @Test
        @DisplayName("퀴즈의 모든 사용자 시도를 최신순으로 조회한다")
        fun findByQuizId_WithMultipleUsers_ReturnsAllAttemptsOrderedByCreatedAtDesc() {
            // Given: 두 사용자의 시도
            val attempt1 = quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 1, isCorrect = true)
            ).block()!!
            Thread.sleep(10)
            val attempt2 = quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser2.id!!, attemptNumber = 1, isCorrect = false)
            ).block()!!

            // When: 퀴즈의 모든 시도 조회
            val attempts = quizAttemptRepository.findByQuizId(testQuiz.id!!).collectList().block()!!

            // Then: 최신순으로 모든 시도 반환
            assertThat(attempts).hasSize(2)
            assertThat(attempts[0].id).isEqualTo(attempt2.id)
            assertThat(attempts[1].id).isEqualTo(attempt1.id)
        }

        @Test
        @DisplayName("시도가 없는 퀴즈는 빈 Flux를 반환한다")
        fun findByQuizId_WhenNoAttempts_ReturnsEmptyFlux() {
            // Given: 시도가 없는 새로운 퀴즈
            val newContent = contentRepository.save(
                Content(
                    creatorId = testUser.id!!,
                    contentType = ContentType.QUIZ,
                    url = "https://example.com/quiz2",
                    thumbnailUrl = "https://example.com/thumbnail2.jpg",
                    width = 1080,
                    height = 1920,
                    createdBy = testUser.id.toString()
                )
            ).block()!!

            val newQuiz = quizRepository.save(
                Quiz(
                    contentId = newContent.id!!,
                    question = "New Quiz",
                    allowMultipleAnswers = false,
                    createdBy = testUser.id.toString(),
                    updatedBy = testUser.id.toString()
                )
            ).block()!!

            // When: 시도 조회
            val attempts = quizAttemptRepository.findByQuizId(newQuiz.id!!).collectList().block()!!

            // Then: 빈 목록
            assertThat(attempts).isEmpty()
        }
    }

    @Nested
    @DisplayName("getNextAttemptNumber - 다음 시도 번호 조회")
    inner class GetNextAttemptNumber {

        @Test
        @DisplayName("시도 기록이 없으면, 1을 반환한다")
        fun getNextAttemptNumber_WhenNoAttempts_ReturnsOne() {
            // When: 시도 기록이 없는 상태에서 다음 번호 조회
            val nextNumber = quizAttemptRepository.getNextAttemptNumber(testQuiz.id!!, testUser.id!!).block()!!

            // Then: 1 반환
            assertThat(nextNumber).isEqualTo(1)
        }

        @Test
        @DisplayName("시도 기록이 있으면, 최대 번호 + 1을 반환한다")
        fun getNextAttemptNumber_WhenAttemptsExist_ReturnsMaxPlusOne() {
            // Given: 3번의 시도
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 1, isCorrect = false)
            ).block()
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 2, isCorrect = false)
            ).block()
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 3, isCorrect = true)
            ).block()

            // When: 다음 번호 조회
            val nextNumber = quizAttemptRepository.getNextAttemptNumber(testQuiz.id!!, testUser.id!!).block()!!

            // Then: 4 반환
            assertThat(nextNumber).isEqualTo(4)
        }

        @Test
        @DisplayName("다른 사용자의 시도는 고려하지 않는다")
        fun getNextAttemptNumber_OnlyConsidersSameUser() {
            // Given: user1의 2번 시도, user2의 5번 시도
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 1, isCorrect = false)
            ).block()
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 2, isCorrect = true)
            ).block()

            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser2.id!!, attemptNumber = 5, isCorrect = true)
            ).block()

            // When: user1의 다음 번호 조회
            val nextNumber = quizAttemptRepository.getNextAttemptNumber(testQuiz.id!!, testUser.id!!).block()!!

            // Then: 3 반환 (user2의 5는 고려하지 않음)
            assertThat(nextNumber).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("countByQuizIdAndUserId - 특정 사용자의 시도 횟수 조회")
    inner class CountByQuizIdAndUserId {

        @Test
        @DisplayName("시도가 있으면, 정확한 개수를 반환한다")
        fun countByQuizIdAndUserId_WhenAttemptsExist_ReturnsCorrectCount() {
            // Given: 사용자의 3번의 시도
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 1, isCorrect = false)
            ).block()
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 2, isCorrect = false)
            ).block()
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 3, isCorrect = true)
            ).block()

            // When: 시도 횟수 조회
            val count = quizAttemptRepository.countByQuizIdAndUserId(testQuiz.id!!, testUser.id!!).block()!!

            // Then: 3 반환
            assertThat(count).isEqualTo(3)
        }

        @Test
        @DisplayName("시도가 없으면, 0을 반환한다")
        fun countByQuizIdAndUserId_WhenNoAttempts_ReturnsZero() {
            // When: 시도가 없는 사용자의 횟수 조회
            val count = quizAttemptRepository.countByQuizIdAndUserId(testQuiz.id!!, testUser.id!!).block()!!

            // Then: 0 반환
            assertThat(count).isEqualTo(0)
        }

        @Test
        @DisplayName("다른 사용자의 시도는 개수에 포함되지 않는다")
        fun countByQuizIdAndUserId_OnlyCountsSpecificUser() {
            // Given: user1의 2번 시도, user2의 5번 시도
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 1, isCorrect = false)
            ).block()
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 2, isCorrect = true)
            ).block()

            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser2.id!!, attemptNumber = 1, isCorrect = true)
            ).block()

            // When: user1의 시도 횟수 조회
            val count = quizAttemptRepository.countByQuizIdAndUserId(testQuiz.id!!, testUser.id!!).block()!!

            // Then: 2 반환 (user2의 시도는 제외)
            assertThat(count).isEqualTo(2)
        }
    }

    @Nested
    @DisplayName("countByQuizId - 퀴즈의 전체 시도 횟수 조회 (최적화)")
    inner class CountByQuizId {

        @Test
        @DisplayName("여러 사용자의 모든 시도를 집계한다")
        fun countByQuizId_WithMultipleUsers_ReturnsAllAttemptsCount() {
            // Given: 두 사용자의 총 5번 시도
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 1, isCorrect = false)
            ).block()
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 2, isCorrect = true)
            ).block()

            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser2.id!!, attemptNumber = 1, isCorrect = false)
            ).block()
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser2.id!!, attemptNumber = 2, isCorrect = false)
            ).block()
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser2.id!!, attemptNumber = 3, isCorrect = true)
            ).block()

            // When: 전체 시도 횟수 조회
            val count = quizAttemptRepository.countByQuizId(testQuiz.id!!).block()!!

            // Then: 5 반환
            assertThat(count).isEqualTo(5)
        }

        @Test
        @DisplayName("시도가 없으면, 0을 반환한다")
        fun countByQuizId_WhenNoAttempts_ReturnsZero() {
            // When: 시도가 없는 퀴즈의 횟수 조회
            val count = quizAttemptRepository.countByQuizId(testQuiz.id!!).block()!!

            // Then: 0 반환
            assertThat(count).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("countDistinctUsersByQuizId - 퀴즈를 시도한 고유 사용자 수 조회 (최적화)")
    inner class CountDistinctUsersByQuizId {

        @Test
        @DisplayName("여러 사용자의 시도를 고유 사용자 수로 집계한다")
        fun countDistinctUsersByQuizId_WithMultipleUsers_ReturnsDistinctUserCount() {
            // Given: user1의 2번 시도, user2의 3번 시도 (총 2명의 고유 사용자)
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 1, isCorrect = false)
            ).block()
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 2, isCorrect = true)
            ).block()

            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser2.id!!, attemptNumber = 1, isCorrect = false)
            ).block()
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser2.id!!, attemptNumber = 2, isCorrect = false)
            ).block()
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser2.id!!, attemptNumber = 3, isCorrect = true)
            ).block()

            // When: 고유 사용자 수 조회
            val distinctUserCount = quizAttemptRepository.countDistinctUsersByQuizId(testQuiz.id!!).block()!!

            // Then: 2 반환 (총 시도 5번이지만 고유 사용자는 2명)
            assertThat(distinctUserCount).isEqualTo(2)
        }

        @Test
        @DisplayName("시도가 없으면, 0을 반환한다")
        fun countDistinctUsersByQuizId_WhenNoAttempts_ReturnsZero() {
            // When: 시도가 없는 퀴즈의 고유 사용자 수 조회
            val distinctUserCount = quizAttemptRepository.countDistinctUsersByQuizId(testQuiz.id!!).block()!!

            // Then: 0 반환
            assertThat(distinctUserCount).isEqualTo(0)
        }

        @Test
        @DisplayName("한 사용자만 시도했으면, 1을 반환한다")
        fun countDistinctUsersByQuizId_WithSingleUser_ReturnsOne() {
            // Given: 한 사용자의 여러 번 시도
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 1, isCorrect = false)
            ).block()
            quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = testUser.id!!, attemptNumber = 2, isCorrect = true)
            ).block()

            // When: 고유 사용자 수 조회
            val distinctUserCount = quizAttemptRepository.countDistinctUsersByQuizId(testQuiz.id!!).block()!!

            // Then: 1 반환
            assertThat(distinctUserCount).isEqualTo(1)
        }
    }
}
