package me.onetwo.upvy.domain.quiz.repository

import me.onetwo.upvy.domain.content.model.Content
import me.onetwo.upvy.domain.content.model.ContentType
import me.onetwo.upvy.domain.content.repository.ContentRepository
import me.onetwo.upvy.domain.quiz.model.Quiz
import me.onetwo.upvy.domain.quiz.model.QuizAttempt
import me.onetwo.upvy.domain.quiz.model.QuizAttemptAnswer
import me.onetwo.upvy.domain.quiz.model.QuizOption
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
import org.jooq.DSLContext
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
 * QuizAttemptAnswerRepository 통합 테스트
 *
 * 실제 MySQL Testcontainer를 사용하여 Repository 계층을 테스트합니다.
 * Batch INSERT 최적화 및 GROUP BY 집계 쿼리를 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("QuizAttemptAnswerRepository 통합 테스트")
class QuizAttemptAnswerRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var quizAttemptAnswerRepository: QuizAttemptAnswerRepository

    @Autowired
    private lateinit var quizAttemptRepository: QuizAttemptRepository

    @Autowired
    private lateinit var quizOptionRepository: QuizOptionRepository

    @Autowired
    private lateinit var quizRepository: QuizRepository

    @Autowired
    private lateinit var contentRepository: ContentRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var testUser: User
    private lateinit var testContent: Content
    private lateinit var testQuiz: Quiz
    private lateinit var testOption1: QuizOption
    private lateinit var testOption2: QuizOption
    private lateinit var testAttempt: QuizAttempt

    @BeforeEach
    fun setUp() {
        // 테스트용 사용자 생성
        testUser = userRepository.save(
            User(
                email = "quiz-attempt-answer-test@example.com",
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
                allowMultipleAnswers = true,
                createdBy = testUser.id.toString(),
                updatedBy = testUser.id.toString()
            )
        ).block()!!

        // 테스트용 퀴즈 옵션 생성
        testOption1 = quizOptionRepository.save(
            QuizOption(
                quizId = testQuiz.id!!,
                optionText = "val은 불변, var는 가변",
                isCorrect = true,
                displayOrder = 1,
                createdBy = testUser.id.toString(),
                updatedBy = testUser.id.toString()
            )
        ).block()!!

        testOption2 = quizOptionRepository.save(
            QuizOption(
                quizId = testQuiz.id!!,
                optionText = "val은 가변, var는 불변",
                isCorrect = false,
                displayOrder = 2,
                createdBy = testUser.id.toString(),
                updatedBy = testUser.id.toString()
            )
        ).block()!!

        // 테스트용 시도 생성
        testAttempt = quizAttemptRepository.save(
            QuizAttempt(
                quizId = testQuiz.id!!,
                userId = testUser.id!!,
                isCorrect = true,
                attemptNumber = 1
            )
        ).block()!!
    }

    @AfterEach
    fun tearDown() {
        // Quiz 관련 테이블 정리 (외래 키 순서 고려)
        Mono.from(dslContext.deleteFrom(QUIZ_ATTEMPT_ANSWERS))
            .then(Mono.from(dslContext.deleteFrom(QUIZ_ATTEMPTS)))
            .then(Mono.from(dslContext.deleteFrom(QUIZ_OPTIONS)))
            .then(Mono.from(dslContext.deleteFrom(QUIZZES)))
            .block()
    }

    @Nested
    @DisplayName("save - 퀴즈 시도 답변 저장")
    inner class Save {

        @Test
        @DisplayName("답변을 저장하면, UUID가 부여된다")
        fun save_WithValidAnswer_ReturnsAnswerWithId() {
            // Given: 새로운 답변
            val answer = QuizAttemptAnswer(
                attemptId = testAttempt.id!!,
                optionId = testOption1.id!!
            )

            // When: 저장
            val savedAnswer = quizAttemptAnswerRepository.save(answer).block()!!

            // Then: UUID가 부여되고 createdAt이 설정됨
            assertThat(savedAnswer.id).isNotNull()
            assertThat(savedAnswer.attemptId).isEqualTo(testAttempt.id)
            assertThat(savedAnswer.optionId).isEqualTo(testOption1.id)
            assertThat(savedAnswer.createdAt).isNotNull()
        }

        @Test
        @DisplayName("동일한 시도에 여러 답변을 저장할 수 있다 (복수 정답)")
        fun save_MultipleAnswersForSameAttempt_SavesSuccessfully() {
            // Given: 동일한 시도에 대한 두 개의 답변
            val answer1 = QuizAttemptAnswer(
                attemptId = testAttempt.id!!,
                optionId = testOption1.id!!
            )
            val answer2 = QuizAttemptAnswer(
                attemptId = testAttempt.id!!,
                optionId = testOption2.id!!
            )

            // When: 각각 저장
            val savedAnswer1 = quizAttemptAnswerRepository.save(answer1).block()!!
            val savedAnswer2 = quizAttemptAnswerRepository.save(answer2).block()!!

            // Then: 두 답변 모두 저장됨
            assertThat(savedAnswer1.id).isNotNull()
            assertThat(savedAnswer2.id).isNotNull()
            assertThat(savedAnswer1.id).isNotEqualTo(savedAnswer2.id)

            val allAnswers = quizAttemptAnswerRepository.findByAttemptId(testAttempt.id!!).collectList().block()!!
            assertThat(allAnswers).hasSize(2)
        }
    }

    @Nested
    @DisplayName("saveAll - 일괄 저장 (Batch INSERT 최적화)")
    inner class SaveAll {

        @Test
        @DisplayName("여러 답변을 일괄 저장하면, 모든 답변이 UUID를 부여받는다")
        fun saveAll_WithMultipleAnswers_ReturnsSavedAnswers() {
            // Given: 여러 답변 목록
            val answers = listOf(
                QuizAttemptAnswer(
                    attemptId = testAttempt.id!!,
                    optionId = testOption1.id!!
                ),
                QuizAttemptAnswer(
                    attemptId = testAttempt.id!!,
                    optionId = testOption2.id!!
                )
            )

            // When: 일괄 저장
            val savedAnswers = quizAttemptAnswerRepository.saveAll(answers).collectList().block()!!

            // Then: 모든 답변이 UUID를 부여받음
            assertThat(savedAnswers).hasSize(2)
            assertThat(savedAnswers[0].id).isNotNull()
            assertThat(savedAnswers[1].id).isNotNull()
            assertThat(savedAnswers[0].createdAt).isNotNull()
            assertThat(savedAnswers[1].createdAt).isNotNull()
        }

        @Test
        @DisplayName("빈 목록을 저장하면, 빈 Flux를 반환한다")
        fun saveAll_WithEmptyList_ReturnsEmptyFlux() {
            // When: 빈 목록 저장
            val savedAnswers = quizAttemptAnswerRepository.saveAll(emptyList()).collectList().block()!!

            // Then: 빈 목록 반환
            assertThat(savedAnswers).isEmpty()
        }

        @Test
        @DisplayName("Batch INSERT로 저장된 답변들이 실제 데이터베이스에 저장된다")
        fun saveAll_BatchInsert_PersistsToDatabase() {
            // Given: 2개의 답변 (서로 다른 보기)
            val answers = listOf(
                QuizAttemptAnswer(attemptId = testAttempt.id!!, optionId = testOption1.id!!),
                QuizAttemptAnswer(attemptId = testAttempt.id!!, optionId = testOption2.id!!)
            )

            // When: 일괄 저장
            quizAttemptAnswerRepository.saveAll(answers).collectList().block()

            // Then: 데이터베이스에서 조회 가능
            val foundAnswers = quizAttemptAnswerRepository.findByAttemptId(testAttempt.id!!).collectList().block()!!
            assertThat(foundAnswers).hasSize(2)
        }
    }

    @Nested
    @DisplayName("findByAttemptId - 시도 ID로 답변 조회")
    inner class FindByAttemptId {

        @Test
        @DisplayName("시도 ID로 답변을 조회하면, 모든 선택한 보기를 반환한다")
        fun findByAttemptId_WhenAnswersExist_ReturnsAllAnswers() {
            // Given: 시도에 대한 답변 저장
            val answers = listOf(
                QuizAttemptAnswer(attemptId = testAttempt.id!!, optionId = testOption1.id!!),
                QuizAttemptAnswer(attemptId = testAttempt.id!!, optionId = testOption2.id!!)
            )
            quizAttemptAnswerRepository.saveAll(answers).collectList().block()

            // When: 시도 ID로 조회
            val foundAnswers = quizAttemptAnswerRepository.findByAttemptId(testAttempt.id!!).collectList().block()!!

            // Then: 모든 답변이 조회됨
            assertThat(foundAnswers).hasSize(2)
            assertThat(foundAnswers.map { it.optionId }).containsExactlyInAnyOrder(testOption1.id, testOption2.id)
        }

        @Test
        @DisplayName("답변이 없는 시도 ID로 조회하면, 빈 Flux를 반환한다")
        fun findByAttemptId_WhenNoAnswers_ReturnsEmptyFlux() {
            // Given: 답변이 없는 새로운 시도
            val newAttempt = quizAttemptRepository.save(
                QuizAttempt(
                    quizId = testQuiz.id!!,
                    userId = testUser.id!!,
                    isCorrect = false,
                    attemptNumber = 2
                )
            ).block()!!

            // When: 답변 조회
            val foundAnswers = quizAttemptAnswerRepository.findByAttemptId(newAttempt.id!!).collectList().block()!!

            // Then: 빈 목록 반환
            assertThat(foundAnswers).isEmpty()
        }
    }

    @Nested
    @DisplayName("countByAttemptId - 시도 ID로 답변 개수 조회")
    inner class CountByAttemptId {

        @Test
        @DisplayName("시도에 답변이 있으면, 정확한 개수를 반환한다")
        fun countByAttemptId_WhenAnswersExist_ReturnsCorrectCount() {
            // Given: 시도에 대한 2개의 답변 저장
            val answers = listOf(
                QuizAttemptAnswer(attemptId = testAttempt.id!!, optionId = testOption1.id!!),
                QuizAttemptAnswer(attemptId = testAttempt.id!!, optionId = testOption2.id!!)
            )
            quizAttemptAnswerRepository.saveAll(answers).collectList().block()

            // When: 답변 개수 조회
            val count = quizAttemptAnswerRepository.countByAttemptId(testAttempt.id!!).block()!!

            // Then: 2개 반환
            assertThat(count).isEqualTo(2)
        }

        @Test
        @DisplayName("시도에 답변이 없으면, 0을 반환한다")
        fun countByAttemptId_WhenNoAnswers_ReturnsZero() {
            // Given: 답변이 없는 새로운 시도
            val newAttempt = quizAttemptRepository.save(
                QuizAttempt(
                    quizId = testQuiz.id!!,
                    userId = testUser.id!!,
                    isCorrect = false,
                    attemptNumber = 2
                )
            ).block()!!

            // When: 답변 개수 조회
            val count = quizAttemptAnswerRepository.countByAttemptId(newAttempt.id!!).block()!!

            // Then: 0 반환
            assertThat(count).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("deleteByAttemptId - 시도 ID로 답변 삭제 (Hard Delete)")
    inner class DeleteByAttemptId {

        @Test
        @DisplayName("시도 ID로 답변을 삭제하면, 모든 답변이 삭제된다")
        fun deleteByAttemptId_WithExistingAnswers_DeletesAllAnswers() {
            // Given: 시도에 대한 답변 저장
            val answers = listOf(
                QuizAttemptAnswer(attemptId = testAttempt.id!!, optionId = testOption1.id!!),
                QuizAttemptAnswer(attemptId = testAttempt.id!!, optionId = testOption2.id!!)
            )
            quizAttemptAnswerRepository.saveAll(answers).collectList().block()

            // When: 시도의 모든 답변 삭제
            quizAttemptAnswerRepository.deleteByAttemptId(testAttempt.id!!).block()

            // Then: 답변이 조회되지 않음
            val remainingAnswers = quizAttemptAnswerRepository.findByAttemptId(testAttempt.id!!).collectList().block()!!
            assertThat(remainingAnswers).isEmpty()

            val count = quizAttemptAnswerRepository.countByAttemptId(testAttempt.id!!).block()!!
            assertThat(count).isEqualTo(0)
        }

        @Test
        @DisplayName("답변이 없는 시도 ID로 삭제해도 오류 없이 처리된다")
        fun deleteByAttemptId_WhenNoAnswers_CompletesWithoutError() {
            // Given: 답변이 없는 새로운 시도
            val newAttempt = quizAttemptRepository.save(
                QuizAttempt(
                    quizId = testQuiz.id!!,
                    userId = testUser.id!!,
                    isCorrect = false,
                    attemptNumber = 2
                )
            ).block()!!

            // When & Then: 오류 없이 완료됨
            quizAttemptAnswerRepository.deleteByAttemptId(newAttempt.id!!).block()

            val count = quizAttemptAnswerRepository.countByAttemptId(newAttempt.id!!).block()!!
            assertThat(count).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("getSelectionCountsByQuizId - 퀴즈 보기별 선택 횟수 조회 (GROUP BY 최적화)")
    inner class GetSelectionCountsByQuizId {

        @Test
        @DisplayName("퀴즈의 각 보기별 선택 횟수를 집계한다")
        fun getSelectionCountsByQuizId_WithMultipleAttempts_ReturnsSelectionCounts() {
            // Given: 여러 사용자의 시도 및 답변
            val user2 = userRepository.save(
                User(email = "user2@example.com", role = UserRole.USER, status = UserStatus.ACTIVE)
            ).block()!!

            val attempt1 = testAttempt // 이미 생성된 시도
            val attempt2 = quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = user2.id!!, isCorrect = true, attemptNumber = 1)
            ).block()!!
            val attempt3 = quizAttemptRepository.save(
                QuizAttempt(quizId = testQuiz.id!!, userId = user2.id!!, isCorrect = false, attemptNumber = 2)
            ).block()!!

            // 시도 1: option1 선택
            quizAttemptAnswerRepository.save(
                QuizAttemptAnswer(attemptId = attempt1.id!!, optionId = testOption1.id!!)
            ).block()

            // 시도 2: option1, option2 선택
            quizAttemptAnswerRepository.saveAll(
                listOf(
                    QuizAttemptAnswer(attemptId = attempt2.id!!, optionId = testOption1.id!!),
                    QuizAttemptAnswer(attemptId = attempt2.id!!, optionId = testOption2.id!!)
                )
            ).collectList().block()

            // 시도 3: option2 선택
            quizAttemptAnswerRepository.save(
                QuizAttemptAnswer(attemptId = attempt3.id!!, optionId = testOption2.id!!)
            ).block()

            // When: 퀴즈의 보기별 선택 횟수 조회
            val selectionCounts = quizAttemptAnswerRepository.getSelectionCountsByQuizId(testQuiz.id!!).block()!!

            // Then: option1은 2번, option2는 2번 선택됨
            assertThat(selectionCounts[testOption1.id]).isEqualTo(2)
            assertThat(selectionCounts[testOption2.id]).isEqualTo(2)
        }

        @Test
        @DisplayName("시도가 없는 퀴즈는 빈 Map을 반환한다")
        fun getSelectionCountsByQuizId_WhenNoAttempts_ReturnsEmptyMap() {
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

            // When: 선택 횟수 조회
            val selectionCounts = quizAttemptAnswerRepository.getSelectionCountsByQuizId(newQuiz.id!!).block()!!

            // Then: 빈 Map 반환
            assertThat(selectionCounts).isEmpty()
        }

        @Test
        @DisplayName("한 번도 선택되지 않은 보기는 Map에 포함되지 않는다")
        fun getSelectionCountsByQuizId_UnselectedOptions_NotIncludedInMap() {
            // Given: option3 추가 (선택되지 않음)
            val option3 = quizOptionRepository.save(
                QuizOption(
                    quizId = testQuiz.id!!,
                    optionText = "선택되지 않는 보기",
                    isCorrect = false,
                    displayOrder = 3,
                    createdBy = testUser.id.toString(),
                    updatedBy = testUser.id.toString()
                )
            ).block()!!

            // option1만 선택
            quizAttemptAnswerRepository.save(
                QuizAttemptAnswer(attemptId = testAttempt.id!!, optionId = testOption1.id!!)
            ).block()

            // When: 선택 횟수 조회
            val selectionCounts = quizAttemptAnswerRepository.getSelectionCountsByQuizId(testQuiz.id!!).block()!!

            // Then: option1만 Map에 포함
            assertThat(selectionCounts).containsKey(testOption1.id)
            assertThat(selectionCounts).doesNotContainKey(testOption2.id)
            assertThat(selectionCounts).doesNotContainKey(option3.id)
        }
    }
}
