package me.onetwo.upvy.domain.quiz.repository

import me.onetwo.upvy.domain.content.model.Content
import me.onetwo.upvy.domain.content.model.ContentType
import me.onetwo.upvy.domain.content.repository.ContentRepository
import me.onetwo.upvy.domain.quiz.model.Quiz
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
 * QuizOptionRepository 통합 테스트
 *
 * 실제 MySQL Testcontainer를 사용하여 Repository 계층을 테스트합니다.
 * Soft Delete 및 displayOrder 정렬을 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("QuizOptionRepository 통합 테스트")
class QuizOptionRepositoryTest : AbstractIntegrationTest() {

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

    @BeforeEach
    fun setUp() {
        // 테스트용 사용자 생성
        testUser = userRepository.save(
            User(
                email = "quiz-option-test@example.com",
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
    @DisplayName("save - 퀴즈 보기 저장")
    inner class Save {

        @Test
        @DisplayName("보기를 저장하면, UUID가 부여된다")
        fun save_WithValidOption_ReturnsOptionWithId() {
            // Given: 새로운 보기
            val option = QuizOption(
                quizId = testQuiz.id!!,
                optionText = "val은 불변, var는 가변",
                isCorrect = true,
                displayOrder = 1,
                createdBy = testUser.id.toString(),
                updatedBy = testUser.id.toString()
            )

            // When: 저장
            val savedOption = quizOptionRepository.save(option).block()!!

            // Then: UUID가 부여되고 Audit Trail이 설정됨
            assertThat(savedOption.id).isNotNull()
            assertThat(savedOption.quizId).isEqualTo(testQuiz.id)
            assertThat(savedOption.optionText).isEqualTo("val은 불변, var는 가변")
            assertThat(savedOption.isCorrect).isTrue()
            assertThat(savedOption.displayOrder).isEqualTo(1)
            assertThat(savedOption.createdAt).isNotNull()
            assertThat(savedOption.updatedAt).isNotNull()
        }

        @Test
        @DisplayName("여러 보기를 저장할 수 있다")
        fun save_MultipleOptions_SavesSuccessfully() {
            // Given: 4개의 보기
            val option1 = QuizOption(quizId = testQuiz.id!!, optionText = "옵션 1", isCorrect = true, displayOrder = 1, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            val option2 = QuizOption(quizId = testQuiz.id!!, optionText = "옵션 2", isCorrect = false, displayOrder = 2, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            val option3 = QuizOption(quizId = testQuiz.id!!, optionText = "옵션 3", isCorrect = false, displayOrder = 3, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            val option4 = QuizOption(quizId = testQuiz.id!!, optionText = "옵션 4", isCorrect = false, displayOrder = 4, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())

            // When: 각각 저장
            val saved1 = quizOptionRepository.save(option1).block()!!
            val saved2 = quizOptionRepository.save(option2).block()!!
            val saved3 = quizOptionRepository.save(option3).block()!!
            val saved4 = quizOptionRepository.save(option4).block()!!

            // Then: 모두 저장됨
            assertThat(saved1.id).isNotNull()
            assertThat(saved2.id).isNotNull()
            assertThat(saved3.id).isNotNull()
            assertThat(saved4.id).isNotNull()

            val allOptions = quizOptionRepository.findByQuizId(testQuiz.id!!).collectList().block()!!
            assertThat(allOptions).hasSize(4)
        }
    }

    @Nested
    @DisplayName("findByQuizId - 퀴즈 ID로 보기 조회 (displayOrder 정렬)")
    inner class FindByQuizId {

        @Test
        @DisplayName("퀴즈의 보기를 displayOrder 순서로 조회한다")
        fun findByQuizId_WithMultipleOptions_ReturnsOrderedByDisplayOrder() {
            // Given: displayOrder가 섞인 4개의 보기
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "세 번째", isCorrect = false, displayOrder = 3, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "첫 번째", isCorrect = true, displayOrder = 1, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "네 번째", isCorrect = false, displayOrder = 4, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "두 번째", isCorrect = false, displayOrder = 2, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()

            // When: 보기 조회
            val options = quizOptionRepository.findByQuizId(testQuiz.id!!).collectList().block()!!

            // Then: displayOrder 순서로 정렬됨 (1, 2, 3, 4)
            assertThat(options).hasSize(4)
            assertThat(options[0].optionText).isEqualTo("첫 번째")
            assertThat(options[1].optionText).isEqualTo("두 번째")
            assertThat(options[2].optionText).isEqualTo("세 번째")
            assertThat(options[3].optionText).isEqualTo("네 번째")
        }

        @Test
        @DisplayName("보기가 없는 퀴즈는 빈 Flux를 반환한다")
        fun findByQuizId_WhenNoOptions_ReturnsEmptyFlux() {
            // Given: 보기가 없는 새로운 퀴즈
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

            // When: 보기 조회
            val options = quizOptionRepository.findByQuizId(newQuiz.id!!).collectList().block()!!

            // Then: 빈 목록
            assertThat(options).isEmpty()
        }

        @Test
        @DisplayName("삭제된 보기는 조회되지 않는다 (Soft Delete)")
        fun findByQuizId_WhenOptionsDeleted_ReturnsOnlyNonDeleted() {
            // Given: 4개의 보기 저장 후 2개 삭제
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 1", isCorrect = true, displayOrder = 1, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 2", isCorrect = false, displayOrder = 2, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 3", isCorrect = false, displayOrder = 3, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 4", isCorrect = false, displayOrder = 4, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()

            // 모든 보기 삭제 (Soft Delete)
            quizOptionRepository.deleteByQuizId(testQuiz.id!!, testUser.id!!).block()

            // When: 보기 조회
            val options = quizOptionRepository.findByQuizId(testQuiz.id!!).collectList().block()!!

            // Then: 삭제된 보기는 조회되지 않음
            assertThat(options).isEmpty()
        }
    }

    @Nested
    @DisplayName("findById - ID로 보기 조회")
    inner class FindById {

        @Test
        @DisplayName("존재하는 보기 ID로 조회하면, 보기를 반환한다")
        fun findById_WhenOptionExists_ReturnsOption() {
            // Given: 보기 저장
            val savedOption = quizOptionRepository.save(
                QuizOption(
                    quizId = testQuiz.id!!,
                    optionText = "val은 불변",
                    isCorrect = true,
                    displayOrder = 1,
                    createdBy = testUser.id.toString(),
                    updatedBy = testUser.id.toString()
                )
            ).block()!!

            // When: ID로 조회
            val foundOption = quizOptionRepository.findById(savedOption.id!!).block()

            // Then: 보기가 조회됨
            assertThat(foundOption).isNotNull
            assertThat(foundOption!!.id).isEqualTo(savedOption.id)
            assertThat(foundOption.optionText).isEqualTo("val은 불변")
            assertThat(foundOption.isCorrect).isTrue()
        }

        @Test
        @DisplayName("존재하지 않는 보기 ID로 조회하면, 빈 Mono를 반환한다")
        fun findById_WhenOptionNotExists_ReturnsEmpty() {
            // When: 존재하지 않는 ID로 조회
            val foundOption = quizOptionRepository.findById(UUID.randomUUID()).block()

            // Then: null 반환
            assertThat(foundOption).isNull()
        }

        @Test
        @DisplayName("삭제된 보기 ID로 조회하면, 빈 Mono를 반환한다 (Soft Delete)")
        fun findById_WhenOptionDeleted_ReturnsEmpty() {
            // Given: 보기 저장 후 삭제
            val savedOption = quizOptionRepository.save(
                QuizOption(
                    quizId = testQuiz.id!!,
                    optionText = "삭제될 보기",
                    isCorrect = false,
                    displayOrder = 1,
                    createdBy = testUser.id.toString(),
                    updatedBy = testUser.id.toString()
                )
            ).block()!!

            quizOptionRepository.deleteByQuizId(testQuiz.id!!, testUser.id!!).block()

            // When: 삭제된 보기 ID로 조회
            val foundOption = quizOptionRepository.findById(savedOption.id!!).block()

            // Then: null 반환 (soft delete)
            assertThat(foundOption).isNull()
        }
    }

    @Nested
    @DisplayName("findByIdIn - 여러 ID로 보기 조회")
    inner class FindByIdIn {

        @Test
        @DisplayName("여러 보기 ID로 조회하면, 해당하는 보기들을 반환한다")
        fun findByIdIn_WithMultipleIds_ReturnsMatchingOptions() {
            // Given: 4개의 보기 저장
            val option1 = quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 1", isCorrect = true, displayOrder = 1, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()!!
            val option2 = quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 2", isCorrect = false, displayOrder = 2, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()!!
            val option3 = quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 3", isCorrect = false, displayOrder = 3, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()!!
            val option4 = quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 4", isCorrect = false, displayOrder = 4, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()!!

            // When: option1, option3 ID로 조회
            val optionIds = listOf(option1.id!!, option3.id!!)
            val foundOptions = quizOptionRepository.findByIdIn(optionIds).collectList().block()!!

            // Then: option1, option3만 반환
            assertThat(foundOptions).hasSize(2)
            assertThat(foundOptions.map { it.id }).containsExactlyInAnyOrder(option1.id, option3.id)
        }

        @Test
        @DisplayName("빈 ID 목록으로 조회하면, 빈 Flux를 반환한다")
        fun findByIdIn_WithEmptyList_ReturnsEmptyFlux() {
            // When: 빈 목록으로 조회
            val foundOptions = quizOptionRepository.findByIdIn(emptyList()).collectList().block()!!

            // Then: 빈 목록
            assertThat(foundOptions).isEmpty()
        }

        @Test
        @DisplayName("삭제된 보기는 조회되지 않는다 (Soft Delete)")
        fun findByIdIn_WhenSomeOptionsDeleted_ReturnsOnlyNonDeleted() {
            // Given: 2개의 보기 저장
            val option1 = quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 1", isCorrect = true, displayOrder = 1, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()!!
            val option2 = quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 2", isCorrect = false, displayOrder = 2, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()!!

            // 모든 보기 삭제
            quizOptionRepository.deleteByQuizId(testQuiz.id!!, testUser.id!!).block()

            // When: 삭제된 보기 ID로 조회
            val optionIds = listOf(option1.id!!, option2.id!!)
            val foundOptions = quizOptionRepository.findByIdIn(optionIds).collectList().block()!!

            // Then: 삭제된 보기는 조회되지 않음
            assertThat(foundOptions).isEmpty()
        }
    }

    @Nested
    @DisplayName("deleteByQuizId - 퀴즈 ID로 보기 삭제 (Soft Delete)")
    inner class DeleteByQuizId {

        @Test
        @DisplayName("퀴즈의 모든 보기를 삭제하면, deletedAt이 설정된다")
        fun deleteByQuizId_WithExistingOptions_SetsDeletedAt() {
            // Given: 4개의 보기
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 1", isCorrect = true, displayOrder = 1, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 2", isCorrect = false, displayOrder = 2, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 3", isCorrect = false, displayOrder = 3, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 4", isCorrect = false, displayOrder = 4, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()

            // When: 퀴즈의 모든 보기 삭제
            quizOptionRepository.deleteByQuizId(testQuiz.id!!, testUser.id!!).block()

            // Then: soft delete되어 조회되지 않음
            val remainingOptions = quizOptionRepository.findByQuizId(testQuiz.id!!).collectList().block()!!
            assertThat(remainingOptions).isEmpty()
        }

        @Test
        @DisplayName("보기가 없는 퀴즈를 삭제해도 오류 없이 처리된다")
        fun deleteByQuizId_WhenNoOptions_CompletesWithoutError() {
            // Given: 보기가 없는 새로운 퀴즈
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

            // When & Then: 오류 없이 완료됨
            quizOptionRepository.deleteByQuizId(newQuiz.id!!, testUser.id!!).block()

            val options = quizOptionRepository.findByQuizId(newQuiz.id!!).collectList().block()!!
            assertThat(options).isEmpty()
        }
    }

    @Nested
    @DisplayName("countByQuizId - 퀴즈의 보기 개수 조회")
    inner class CountByQuizId {

        @Test
        @DisplayName("퀴즈의 보기 개수를 반환한다")
        fun countByQuizId_WithMultipleOptions_ReturnsCorrectCount() {
            // Given: 4개의 보기
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 1", isCorrect = true, displayOrder = 1, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 2", isCorrect = false, displayOrder = 2, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 3", isCorrect = false, displayOrder = 3, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 4", isCorrect = false, displayOrder = 4, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()

            // When: 보기 개수 조회
            val count = quizOptionRepository.countByQuizId(testQuiz.id!!).block()!!

            // Then: 4 반환
            assertThat(count).isEqualTo(4)
        }

        @Test
        @DisplayName("보기가 없으면, 0을 반환한다")
        fun countByQuizId_WhenNoOptions_ReturnsZero() {
            // When: 보기가 없는 퀴즈의 개수 조회
            val count = quizOptionRepository.countByQuizId(testQuiz.id!!).block()!!

            // Then: 0 반환
            assertThat(count).isEqualTo(0)
        }

        @Test
        @DisplayName("삭제된 보기는 개수에 포함되지 않는다 (Soft Delete)")
        fun countByQuizId_WhenSomeOptionsDeleted_CountsOnlyNonDeleted() {
            // Given: 4개의 보기 저장 후 전체 삭제
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 1", isCorrect = true, displayOrder = 1, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()
            quizOptionRepository.save(
                QuizOption(quizId = testQuiz.id!!, optionText = "옵션 2", isCorrect = false, displayOrder = 2, createdBy = testUser.id.toString(), updatedBy = testUser.id.toString())
            ).block()

            quizOptionRepository.deleteByQuizId(testQuiz.id!!, testUser.id!!).block()

            // When: 보기 개수 조회
            val count = quizOptionRepository.countByQuizId(testQuiz.id!!).block()!!

            // Then: 0 반환 (삭제된 보기는 제외)
            assertThat(count).isEqualTo(0)
        }
    }
}
